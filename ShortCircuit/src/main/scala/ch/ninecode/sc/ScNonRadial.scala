package ch.ninecode.sc

import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.Island.island_id
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerIsland
import ch.ninecode.net.TransformerServiceArea
import ch.ninecode.sc.ScEdge.resistanceAt
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement

case class ScNonRadial (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions)
{

    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)
    implicit val storage: StorageLevel = storage_level

    type Trafo = String
    type Mrid = String

    def run_loadflow (
        transformers: RDD[TransformerIsland],
        cleaned_trace_results: RDD[ScResult]): RDD[(Trafo, Mrid, Impedanzen, Branch)] =
    {
        // find transformers where there are non-radial networks and fix them
        def need_load_flow (error: String): Boolean =
            error.startsWith("FATAL: non-radial network detected") ||
                error.startsWith("INVALID: 3 transformer windings") ||
                error.startsWith("INVALID: low voltage")

        val problem_trafos: Array[String] = cleaned_trace_results
            .filter(result => result.errors.exists(need_load_flow))
            .map(_.tx)
            .distinct
            .collect()

        val gridlab_islands: RDD[TransformerIsland] = transformers.filter(trafoisland =>
        {
            val meshedNetwork = trafoisland.transformers.length > 1
            val errors = problem_trafos.contains(trafoisland.transformers.head.transformer_name)
            meshedNetwork || errors
        })

        val gridlab_results: RDD[(Trafo, Mrid, Impedanzen, Branch)] =
            fix(gridlab_islands).setName("fixed_results")
        gridlab_results
    }

    // execute GridLAB-D to approximate the impedances and replace the error records
    @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.AsInstanceOf"))
    def fix (problem_transformers: RDD[TransformerIsland]): RDD[(Trafo, Mrid, Impedanzen, Branch)] =
    {
        val n = problem_transformers.count
        log.info(s"""performing load-flow for $n non-radial network${if (n > 1) "s" else ""}""")

        // transformer area calculations
        val tsa = TransformerServiceArea(session, storage_level, calculate_public_lighting = options.calculate_public_lighting)
        val trafos_islands: RDD[(identifier, island_id)] = tsa.getTransformerServiceAreas.map(_.swap).setName("trafos_islands") // (trafosetid, islandid)
        def set_island (island: TransformerIsland): Iterable[(identifier, identifier)] =
        {
            for (set <- island.transformers)
                yield (set.transformer_name, set.transformer_name)
        }

        val trafo_island_mapping: RDD[(identifier, island_id)] = problem_transformers
            .flatMap(set_island)
            .join(trafos_islands)
            .values
            .setName("trafo_island_mapping")

        if (!trafo_island_mapping.filter(_._2 != "").isEmpty)
        {
            val island_helper = new ShortCircuitIsland(session, storage_level, options)
            val graph_stuff = island_helper.queryNetwork(trafo_island_mapping) // ([nodes], [edges])
            val areas = graph_stuff._1.groupByKey.join(graph_stuff._2.groupByKey).persist(storage_level)

            // set up simulations
            val now = javax.xml.bind.DatatypeConverter.parseDateTime("2018-07-19T12:00:00")

            def notTheTransformers (island: TransformerIsland): GLMEdge => Boolean =
            {
                val trafos = island.transformers.flatMap(
                    trafo =>
                    {
                        Set(trafo.transformer_name) ++ trafo.transformers.map(x => x.transformer.id).toSet
                    }
                ).toSet

                {
                    case t: GLMTransformerEdge =>
                        !trafos.contains(t.transformer.transformer_name)
                    case _ => true
                }
            }

            val simulations: RDD[SimulationTransformerServiceArea] = areas
                .join(problem_transformers.keyBy(_.island_name))
                .values
                .map(
                    x =>
                    {
                        val ((nodes, edges), island) = x.asInstanceOf[((Iterable[SimulationNode], Iterable[GLMEdge]), TransformerIsland)]
                        SimulationTransformerServiceArea(
                            island = island,
                            nodes = nodes,
                            edges = edges.filter(notTheTransformers(island)),
                            start_time = now,
                            directory = island.island_name)
                    })
                .setName("simulations")
                .persist(storage_level)

            // perform remedial simulations produces (trafoid, nodeid, equipment, voltage, trafo.Z, Branch)
            val results: RDD[(Trafo, Mrid, Impedanzen, Branch)] =
                remedial(simulations, options.low_temperature, isMax = true).persist(storage_level)
            log.info("""ran %s experiments""".format(results.count()))

            results
        }
        else
        {
            log.info("TopologicalIsland elements not found, cannot use GridLAB-D to fix radial network errors")
            spark.sparkContext.emptyRDD[(Trafo, Mrid, Impedanzen, Branch)]
        }
    }

    /**
     * Perform gridlabd via Spark pipe() and collect the experimental results.
     *
     * @param gridlabd    the object to solve the .glm files and read the recorders
     * @param one_phase   if <code>true</code>, create single phase results, otherwise three phase results
     * @param isMax       If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents],
     *                    otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @param simulations the simulations with experiments
     * @return an RDD of tuples with the transformer id, node mrid, attached equipment mrid,
     *         nominal node voltage, and impedance at the node
     */
    def solve_and_analyse (
        gridlabd: GridLABD,
        one_phase: Boolean,
        isMax: Boolean,
        simulations: RDD[SimulationTransformerServiceArea]): RDD[(Trafo, Mrid, Impedanzen, Branch)] =
    {
        val b4_solve = System.nanoTime()
        val trafos = simulations.map(_.island.island_name)
        val gridlabFailures = gridlabd.solve(trafos)
        val solved = System.nanoTime()
        if (gridlabFailures.isEmpty)
            log.info("solve: %s seconds successful".format((solved - b4_solve) / 1e9))
        else
        {
            log.error("solve: %s seconds failed".format((solved - b4_solve) / 1e9))
            gridlabFailures.foreach(failure =>
            {
                log.error(s"${failure.trafoID} has failures: ")
                failure.errorMessages.foreach(log.error)
            })
        }
        val output = read_output_files(one_phase, gridlabd.workdir_slash, trafos.collect).setName("output")

        val read = System.nanoTime()
        log.info("read: %s seconds".format((read - solved) / 1e9))

        val values: RDD[(island_id, Iterable[ThreePhaseComplexDataElement])] = output
            .map(x => (s"${x._1}_${x._2.millis}", x._2))
            .groupByKey
            .setName("values")
        val groups = simulations.flatMap(
            (simulation: SimulationTransformerServiceArea) => simulation.experiments.map(
                (experiment: ScExperiment) => (s"${experiment.trafo}_${experiment.t1.getTimeInMillis}", (simulation, experiment))))
            .setName("groups")

        val exp: RDD[((SimulationTransformerServiceArea, ScExperiment), Iterable[ThreePhaseComplexDataElement])] = groups
            .join(values)
            .values
            .setName("exp")

        val z: RDD[(Trafo, Mrid, Impedanzen, Branch)] = exp
            .map(evaluate)
            .setName("z")

        val anal = System.nanoTime()
        log.info("analyse: %s seconds".format((anal - read) / 1e9))
        z
    }

    /**
     * Apply a GridLAB-D load flow analysis as a remedial work-around for mesh (non-radial) networks.
     * Exports GridLAB-D model files, adding player files of short-circuits (actually just a low impedance)
     * for each node of interest ina time-multiplexed window of "experiments". It then executes the load-flow
     * and time demultuplexes each experiment to generate the impedance of the network as seen at each node (of interest).
     *
     * @param simulations the RDD of transformer service areas to which this analysis should be applied
     * @param temperature the temerature at which to evaluate the impedances (°C)
     * @param isMax       If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents], otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @return the RDD of tuples with the transformer id, node mrid, attached equipment mrid, nominal node voltage, supplying transformer impedance and network
     */
    def remedial (
        simulations: RDD[SimulationTransformerServiceArea],
        temperature: Double,
        isMax: Boolean): RDD[(Trafo, Mrid, Impedanzen, Branch)] =
    {
        // for dates without time zones, the timezone of the machine is used:
        //    date +%Z
        // timezone can be set on each node of the cluster with:
        //    dpkg-reconfigure tzdata
        // then choose Europe and then choose Zürich
        //
        // all dates generated by this program include the time zone
        val USE_UTC = true
        val _DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        if (USE_UTC)
            _DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        else
            _DateFormat.setTimeZone(TimeZone.getTimeZone("CET"))

        def generate (gridlabd: GridLABD, trafokreis: SimulationTransformerServiceArea): Unit =
        {
            val generator = ScGLMGenerator(one_phase = true, temperature = temperature, date_format = _DateFormat, trafokreis, isMax = isMax)
            gridlabd.export(generator)
        }

        val gridlabd = new GridLABD(session, storage_level = storage_level, workdir = options.workdir, cable_impedance_limit = options.cable_impedance_limit)
        val experiments = simulations.flatMap(
            x =>
            {
                generate(gridlabd, x)
                x.experiments
            }
        ).persist(storage_level)
            .setName("experiments")

        def short (exp: ScExperiment): Array[Byte] =
        {
            val gigaohm = Complex(1e9)

            def addrow (time: Calendar, impedance: Complex): String =
            {
                val timestamp = _DateFormat.format(time.getTime)
                s"$timestamp,${impedance.re},${impedance.im}"
            }

            val content = List(
                addrow(exp.t0, gigaohm), // gridlab extends the first and last rows till infinity -> make them zero
                addrow(exp.t1, exp.impedance),
                addrow(exp.t2, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero
            )
            content.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
        }

        def generate_player_file (gridlabd: GridLABD)(experiment: ScExperiment): Int =
        {
            if (false)
            {
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_R.csv", short(experiment))
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_S.csv", short(experiment))
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_T.csv", short(experiment))
            }
            else
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + ".csv", short(experiment))
            1
        }

        val n = experiments.map(generate_player_file(gridlabd)).setName("n")
        log.info("""running %s experiments""".format(n.count))

        solve_and_analyse(gridlabd = gridlabd, one_phase = true, isMax, simulations)
    }

    /**
     * Evaluate the results of an experiment.
     *
     * An experiment is a GridLAB-D load-flow evaluation of a transformer service area where the house under test is
     * set as a load of a constant impedance (100 ohms) and we have captured the voltage dump (the voltage at each node)
     * under these conditions.
     * The transformer service area has the nodes and edges that were used in generating the .glm file for GridLAB-D.
     * So the task is to determine the current direction in each edge by examining the difference in voltage between
     * the two terminal nodes of each edge and from that determine an equivalent Branch circuit (series and
     * parallel components) with the edge impedances.
     *
     * @param exp all the data, the simulation and specific experiment plus all the voltage readings
     * @return a tuple with the transformer id, node mrid, attached equipment mrid, nominal node voltage, secondary impedance of the source transformer and an equivalent circuit
     */
    def evaluate (exp: ((SimulationTransformerServiceArea, ScExperiment), Iterable[ThreePhaseComplexDataElement])): (Trafo, Mrid, Impedanzen, Branch) =
    {
        val trafokreis: SimulationTransformerServiceArea = exp._1._1
        val experiment: ScExperiment = exp._1._2
        val edges: Iterable[GLMEdge] = exp._1._1.edges
        val data: Iterable[ThreePhaseComplexDataElement] = exp._2
        val trafo_lv_nodes: Array[Array[String]] = trafokreis.island.transformers.map(x =>
        {
            val trafos: Array[TransformerData] = x.transformers
            val nodes = trafos(0).nodes
            for (node <- nodes.tail)
                yield node.id
        })
        val flatten_trafo_lv_nodes: Array[String] = trafo_lv_nodes.flatten


        // get directed edges hi→lo voltage = Branch from→to
        val graph_edges: Iterable[Branch] = edges.flatMap(
            x =>
            {
                data.find(y => y.element == x.cn1) match
                {
                    case Some(voltage1) =>
                        data.find(y => y.element == x.cn2) match
                        {
                            case Some(voltage2) =>
                                val v1 = voltage1.value_a.modulus
                                val v2 = voltage2.value_a.modulus
                                x match
                                {
                                    case switch: GLMSwitchEdge =>
                                        makeSwitchBranch(switch, flatten_trafo_lv_nodes, experiment.mrid, v1, v2)
                                    case cable: GLMLineEdge =>
                                        makeCableBranch(cable, voltage1, voltage2, v1, v2)
                                    case transformer: GLMTransformerEdge =>
                                        makeTransformerBranch(transformer, v1, v2)
                                    case _ =>
                                        log.error(s"unexpected edge type ${x.toString}")
                                        if (v1 > v2)
                                            List(SimpleBranch(x.cn1, x.cn2, 0.0, x.id, "", None, ""))
                                        else
                                            List(SimpleBranch(x.cn2, x.cn1, 0.0, x.id, "", None, ""))
                                }
                            case None =>
                                List()
                        }
                    case None =>
                        List()
                }
            }
        )

        val branches = new ScBranches().reduce_branches(graph_edges, flatten_trafo_lv_nodes, experiment)


        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers
        // finding the first is not sufficient
        val twig: Option[Branch] = branches.find(
            branch =>
            {
                val isThreeWindingTrafo = trafo_lv_nodes.filter(_.length > 1)
                if (isThreeWindingTrafo.nonEmpty)
                {
                    if (trafo_lv_nodes.length > 1)
                    {
                        log.error(s"meshed 3-windig-trafos detected (not supported) for ${experiment.mrid}")
                        false
                    } else
                    {
                        val lvnodes = trafo_lv_nodes.head
                        ((experiment.mrid == branch.to) && lvnodes.contains(branch.from)) ||
                            ((experiment.mrid == branch.from) && lvnodes.contains(branch.to))
                    }
                } else
                {
                    val lvnodes_string = flatten_trafo_lv_nodes.toList.sorted.mkString("_")
                    ((experiment.mrid == branch.to) && (lvnodes_string == branch.from)) ||
                        ((experiment.mrid == branch.from) && (lvnodes_string == branch.to))
                }
            }
        )

        // compute the impedance from start to end
        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers
        val tx = StartingTrafos(0L, 0L, trafokreis.island.transformers(0))
        val (path, impedance) = twig match
        {
            case Some(branch) =>
                val _branch = if (experiment.mrid == branch.to) branch else branch.reverse
                val v = experiment.voltage / _branch.voltageRatio
                (_branch, tx.lv_impedance(v))
            case _ =>
                val b: Branch = if (flatten_trafo_lv_nodes.contains(experiment.mrid))
                    SimpleBranch(experiment.mrid, experiment.mrid, 0.0, experiment.mrid, "", None, "")
                else
                {
                    val lv = flatten_trafo_lv_nodes.mkString(",")
                    val trace = branches.map(_.asString).mkString("\n")
                    log.error(s"complex branch network from $lv to ${experiment.mrid}\n$trace")
                    // get the total current to the energy consumer
                    val directs = branches.filter(experiment.mrid == _.to)
                    val sum = directs.map(_.current).sum
                    // generate a fake impedance
                    ComplexBranch(flatten_trafo_lv_nodes.mkString(","), experiment.mrid, sum, branches.toArray)
                }
                (b, tx.lv_impedance(experiment.voltage))
        }
        (experiment.trafo, experiment.mrid, impedance, path)
    }


    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def makeCableBranch (cable: GLMLineEdge, voltage1: ThreePhaseComplexDataElement, voltage2: ThreePhaseComplexDataElement, v1: Double, v2: Double): List[Branch] =
    {
        // Adjust this threshold according the chosen "default_maximum_voltage_error" in gridlabd
        if (Math.abs(v1 - v2) < 1e-5)
            List()
        else
        {
            val line = cable.lines.head
            val dist_km = line.Conductor.len / 1000.0
            var z = Impedanzen(
                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
            for (l <- cable.lines.tail)
            {
                val z1 = Impedanzen(
                    Complex(resistanceAt(options.low_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                    Complex(resistanceAt(options.low_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km),
                    Complex(resistanceAt(options.high_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                    Complex(resistanceAt(options.high_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km))
                z = Impedanzen(
                    z.impedanz_low.parallel_impedanz(z1.impedanz_low),
                    z.null_impedanz_low.parallel_impedanz(z1.null_impedanz_low),
                    z.impedanz_high.parallel_impedanz(z1.impedanz_high),
                    z.null_impedanz_high.parallel_impedanz(z1.null_impedanz_high))
            }
            val name = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
            if (v1 > v2)
                List(SimpleBranch(cable.cn1, cable.cn2, ((voltage1.value_a - voltage2.value_a) / z.impedanz_low).modulus, cable.id, name, None, "", z))
            else
                List(SimpleBranch(cable.cn2, cable.cn1, ((voltage2.value_a - voltage1.value_a) / z.impedanz_low).modulus, cable.id, name, None, "", z))
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def makeSwitchBranch (switch: GLMSwitchEdge, lvnodes: Array[String], mrid: String, v1: Double, v2: Double): List[Branch] =
    {
        if (!switch.closed)
            List()
        else
        {
            val rating = if (switch.fuse) Some(switch.ratedCurrent) else None
            val name = switch.data.switches.head.asSwitch.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
            val stds = switch.data.switches.flatMap(_.standard).toArray.distinct
            val std = if (0 == stds.length) "" else stds(0) // ToDo: what if parallel switches have different standards?
            if (lvnodes.contains(switch.cn1))
                List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
            else
                if (lvnodes.contains(switch.cn2))
                    List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
                else
                    if (mrid == switch.cn2)
                        List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
                    else
                        if (mrid == switch.cn1)
                            List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
                        else
                            if (v1 > v2)
                                List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
                            else
                                List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
        }
    }

    def makeTransformerBranch (transformer: GLMTransformerEdge, v1: Double, v2: Double): List[Branch] =
    {
        List(TransformerBranch(transformer.cn1, transformer.cn2, 0.0, transformer.transformer.transformer_name, transformer.id,
            transformer.transformer.power_rating, v1, v2, transformer.transformer.total_impedance_per_unit._1))

    }


    def read_output_files (one_phase: Boolean, workdir_slash: String, filenames: Array[String]): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        val pattern = java.util.regex.Pattern.compile("# output_data/([^.]*).csv run at (.*) on (\\d*) nodes")
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val path = filenames.map(file => s"$workdir_slash$file/output.txt").mkString(",")
        val files = session.sparkContext.wholeTextFiles(path, executors)

        def read (f: String): TraversableOnce[ThreePhaseComplexDataElement] =
        {
            var experiment: String = ""
            var timestamp: Long = 0L
            var records: Int = 0
            val units = "Volts"
            val content = f.split("\n")
            val nothing = ThreePhaseComplexDataElement("", 0L, 0.0, 0.0, 0.0, "")

            def makeResult (c: String): ThreePhaseComplexDataElement =
            {
                if (c.startsWith("#"))
                {
                    val matcher = pattern.matcher(c)
                    if (matcher.find)
                    {
                        val dump = c.substring(matcher.start(1), matcher.end(1))
                        experiment = if (dump.endsWith("_voltdump")) dump.substring(0, dump.length - 9) else dump
                        timestamp = toTimeStamp(c.substring(matcher.start(2), matcher.end(2)))
                        records = c.substring(matcher.start(3), matcher.end(3)).toInt
                    }
                    nothing
                }
                else
                    if (c.startsWith("node_name"))
                        nothing
                    else
                    {
                        val c_arr = c.split(",")
                        if (c_arr.length == 7)
                            if (one_phase)
                                ThreePhaseComplexDataElement(c_arr(0), timestamp, Complex(toD(c_arr(1)), toD(c_arr(2))), Complex(0.0), Complex(0.0), units)
                            else
                                ThreePhaseComplexDataElement(c_arr(0), timestamp, Complex(toD(c_arr(1)), toD(c_arr(2))), Complex(toD(c_arr(3)), toD(c_arr(4))), Complex(toD(c_arr(5)), toD(c_arr(6))), units)
                        else
                        {
                            log.error(s"""$experiment voltage dump text "$c" cannot be interpreted as three phase complex $units""")
                            nothing
                        }
                    }
            }

            content.map(makeResult).filter("" != _.element)
        }

        files.map(extract_trafo).flatMapValues(read)
    }

    // convert GridLAB-D output date-time into unix epoch long value
    val toTimeStamp: String => Long =
    {
        val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        string =>
            try
            {
                date_format.parse(string).getTime
            }
            catch
            {
                case pe: ParseException =>
                    log.warn(pe.getMessage)
                    0L
            }
    }

    // extract TRAxxx from the path name returned by wholeTextFiles
    def extract_trafo (k: (String, String)): (String, String) =
    {
        val (path, contents) = k
        val trafo_pattern = ".*/(.*)/output.txt"
        val trafo = path.replaceAll(trafo_pattern, "$1")
        (trafo, contents)
    }

    def toD (string: String): Double =
    {
        try
        {
            string.toDouble
        }
        catch
        {
            case _: NumberFormatException =>
                string match
                {
                    case "inf" => Double.PositiveInfinity
                    case "-inf" => Double.NegativeInfinity
                    case "nan" => Double.NaN
                    case "-nan" => Double.NaN
                    case _ => 0.0
                }
            case _: Throwable => 0.0
        }
    }

}
