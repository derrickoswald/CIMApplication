package ch.ninecode.sc

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.Map
import scala.io.Source

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.Complex
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.Island
import ch.ninecode.gl.Island._
import ch.ninecode.gl.TData
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.gl.TransformerServiceArea
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers
import ch.ninecode.sc.ScEdge.resistanceAt
import ch.ninecode.model._

/**
 * Short circuit calculation.
 * Uses GraphX to trace the topology and generate the short circuit results at each node.
 *
 * @param session the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 * @param options options for short-circuit processing
 */
case class ShortCircuit (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions)
extends CIMRDD
with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val default_impendanz = Impedanzen (
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity))
    val default_node = ScNode ("", 0.0, null, null, null, null)

    def make_graph_vertices (v: ScNode): (VertexId, ScNode) =
    {
        (v.vertex_id (v.id_seq), v)
    }

    def make_graph_edges (e: ScEdge): Edge[ScEdge] =
    {
        Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
    }

    def topological_node_operator (arg: ((TopologicalNode, Terminal), ScEdge)): ScNode =
    {
        val node = arg._1._1
        val term = arg._1._2
        val edge = arg._2
        val voltage = if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2
        ScNode (node.id, voltage, null, null, null, null)
    }

    def edge_operator (voltages: Map[String, Double]) (arg: (Element, Iterable[(Terminal, Option[End])])): List[ScEdge] =
    {
        var ret = List[ScEdge] ()

        val element = arg._1
        val t_it = arg._2

        // get the ConductingEquipment
        var c = element
        while ((null != c) && !c.getClass.getName.endsWith (".ConductingEquipment"))
            c = c.sup

        if (null != c)
        {
            // get the equipment
            val equipment = c.asInstanceOf[ConductingEquipment]

            // sort terminals by sequence number
            // except if it has ends, and then sort so the primary is index 0
            def sortnumber (arg: (Terminal, Option[End])) = arg._2 match { case Some (end) ⇒ end.endNumber case None ⇒ arg._1.ACDCTerminal.sequenceNumber }
            // the equipment voltage - doesn't work for transformers
            val volt = 1000.0 * voltages.getOrElse (equipment.BaseVoltage, 0.0)
            val terminals = t_it.toArray.sortWith (sortnumber (_) < sortnumber (_)).map (terminal_end ⇒
                {
                    val voltage =
                        terminal_end._2 match
                        {
                            case Some (end) ⇒
                                1000.0 * voltages.getOrElse (end.BaseVoltage, 0.0)
                            case None ⇒
                                volt
                        }
                    val impedance = element match
                    {
                        case line: ACLineSegment ⇒
                            val dist_km = line.Conductor.len / 1000.0
                            Impedanzen (
                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
                        case transformer: PowerTransformer ⇒
                            terminal_end._2 match
                            {
                                case Some (end) ⇒
                                    val z = Complex (end.r, end.x)
                                    Impedanzen (z, z, z, z)
                                case None ⇒
                                    Impedanzen (0.0, 0.0, 0.0, 0.0) // no end on a transformer terminal? WTF?
                            }

                        case _ ⇒
                            Impedanzen (0.0, 0.0, 0.0, 0.0)
                    }
                    (terminal_end._1, voltage, impedance)
                })
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!terminals.map (_._2).contains (230.0))
            {
                // make a short-circuit edge for each pair of terminals, zero and one length lists of terminals have been filtered out
                val eq = terminals(0)._1.ConductingEquipment
                val id1 = terminals(0)._1.ACDCTerminal.id
                val node1 = terminals(0)._1.TopologicalNode
                val voltage1 = terminals(0)._2
                val z = terminals(0)._3
                for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                {
                    val node2 = terminals(i)._1.TopologicalNode
                    // eliminate edges with only one connectivity node, or the same connectivity node
                    if (null != node1 && null != node2 && "" != node1 && "" != node2 && node1 != node2)
                        ret = ret :+ ScEdge (
                            id1,
                            node1,
                            voltage1,
                            terminals(i)._1.ACDCTerminal.id,
                            node2,
                            terminals(i)._2,
                            terminals.length,
                            eq,
                            element,
                            z
                        )
                }
            }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?

        ret
    }

    def trafo_mapping (tdata: TransformerSet): StartingTrafos =
    {
        val pn = default_node
        val v0 = pn.vertex_id (tdata.node0)
        val v1 = pn.vertex_id (tdata.node1)
        StartingTrafos (v0, v1, tdata)
    }

    case class End (PowerTransformer: String, endNumber: Int, BaseVoltage: String, r: Double, x: Double)
    def make_ends_meet (arg: (Terminal, Option[PowerTransformerEnd])): (Terminal, Option[End]) =
    {
        val terminal = arg._1
        val end = arg._2 match
        {
            case Some (pte) ⇒
                Some (End (pte.PowerTransformer, pte.TransformerEnd.endNumber, pte.TransformerEnd.BaseVoltage, pte.r, pte.x))
            case None ⇒
                None
        }
        (terminal, end)
    }

    def get_inital_graph (): Graph[ScNode, ScEdge] =
    {
        // get a map of voltages
        val voltages = get[BaseVoltage].map (v ⇒ (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals in the topology
        val terminals = get[Terminal].filter (null != _.TopologicalNode)

        // handle transformers specially, by attaching PowerTransformerEnd objects to the terminals

        // get the transformer ends keyed by terminal, only one end can reference any one terminal
        val ends = get[PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal)
        // attach the ends to terminals
        val t = terminals.keyBy (_.id).leftOuterJoin (ends).values.map (make_ends_meet)
        // get the terminals keyed by equipment and filter for two (or more) terminals
        val terms = t.groupBy (_._1.ConductingEquipment).filter (_._2.size > 1)

        // map the terminal 'pairs' to edges
        val edges = get[Element]("Elements").keyBy (_.id).join (terms).values.flatMap (edge_operator (voltages))

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // map the topological nodes to short circuit nodes with voltages
        val nodes = get[TopologicalNode].keyBy (_.id).join (terminals.keyBy (_.TopologicalNode)).values.keyBy (_._2.id).join (tv).values.map (topological_node_operator).distinct

        // persist edges and nodes to avoid recompute
        val xedges = edges.map (make_graph_edges)
        val xnodes = nodes.map (make_graph_vertices)
        xedges.name = "xedges"
        xedges.persist (storage_level)
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        if (spark.sparkContext.getCheckpointDir.isDefined) { xedges.checkpoint (); xnodes.checkpoint () }

        Graph[ScNode, ScEdge] (xnodes, xedges, default_node, storage_level, storage_level)
    }

    def calculate_one (voltage: Double, impedanz: Complex, null_impedanz: Complex): ScIntermediate =
    {
        val root3 = Math.sqrt (3.0)

        // Einpoligen Kurzschlussstrom berechnen
        val ik_z = root3 * options.cmin * voltage
        val ik_n_sqrt1 = !impedanz
        val ik_n_sqrt2 = !null_impedanz
        val ik_n = 2 * ik_n_sqrt1 + ik_n_sqrt2
        val ik = ik_z / ik_n

        // Dreipoligen Kurzschlussstrom berechnen
        val ik3pol_n = root3 * !impedanz
        val ik3pol = options.cmax * voltage / ik3pol_n

        // Stosskurzschlussstrom berechnen
        // was       val ip = (1.02 + 0.98 * Math.exp (-3.0 * (trafo_r1 + netz_r1) / (trafo_x1 + Math.abs (netz_x1)))) * Math.sqrt (2) * ik3pol

        // maximum aperiodic short-circuit current according to IEC 60909-0, see for example:
        // http://at.dii.unipd.it/renato.gobbo/didattica/corsi/Componenti_tecnologie_elettrici/ABB_swithgear_manual_E11/ABB_11_E_03_druck.pdf pp71-80
        // http://studiecd.dk/cahiers_techniques/Calculation_of_short_circuit_currents.pdf pp7-10
        val kappa =
        if ((0.0 == impedanz.im) && (0.0 == impedanz.re))
            1.02 + 0.98 * Math.exp (-3.0)
        else if (0.0 == impedanz.im)
            0.0
        else
            1.02 + 0.98 * Math.exp (-3.0 * impedanz.re / impedanz.im)
        val ip = kappa * Math.sqrt (2) * ik3pol

        // short-circuit power at the point of common coupling
        val sk = (voltage * voltage) / !impedanz

        // maximum (motor) power (W) for repetition_rate<0.01/min and 0.01≤r<0.1 /min, override default settings for pf=1.0=cos(90), inrush=1x
        val imax3ph = MaximumStartingCurrent.max_current_3_phase (sk, impedanz, voltage, options)
        val imax1ph = MaximumStartingCurrent.max_current_1_phase (sk, impedanz, voltage, options)
        val imax2ph = MaximumStartingCurrent.max_current_2_phase (sk, impedanz, voltage, options)

        ScIntermediate (ik, ik3pol, ip, sk, imax3ph._1, imax1ph._1, imax2ph._1, imax3ph._2, imax1ph._2, imax2ph._2)
    }

    // compute the short-circuit values
    def calculate_short_circuit (arg: (ScNode, Int, String, String)): ScResult =
    {
        val node = arg._1
        val terminal = arg._2
        val equipment = arg._3
        val container = arg._4
        val v2 = node.voltage
        val (low, high) =
            if (node.invalidErrors)
                (ScIntermediate (), ScIntermediate ())
            else
                (calculate_one (v2, node.impedance.impedanz_low, node.impedance.null_impedanz_low),
                 calculate_one (v2, node.impedance.impedanz_high, node.impedance.null_impedanz_high))
        val costerm = MaximumStartingCurrent.costerm (node.impedance.impedanz_low, options)

        ScResult (node.id_seq, equipment, terminal, container,
            if (null == node.errors) List () else node.errors.map (_.toString),
            node.source, node.id_prev,
            node.impedance.impedanz_low.re, node.impedance.impedanz_low.im, node.impedance.null_impedanz_low.re, node.impedance.null_impedanz_low.im,
            low.ik,  low.ik3pol,  low.ip,  low.sk, costerm,
            low.imax_3ph_low,  low.imax_1ph_low,  low.imax_2ph_low,  low.imax_3ph_med,  low.imax_1ph_med,  low.imax_2ph_med,
            node.impedance.impedanz_high.re, node.impedance.impedanz_high.im, node.impedance.null_impedanz_high.re, node.impedance.null_impedanz_high.im,
            high.ik, high.ik3pol, high.ip, high.sk, null)
    }


    /**
     * Get the node part of the element name.
     *
     * @param s the element name as pulled out by filename stripping
     * @return the node (first) part of the element name
     */
    def extract_node (s: String): String =
    {
        val elementindex = s.indexOf ("%")
        if (-1 == elementindex) s else s.substring (0, elementindex)
    }

    /**
     * Yield live branches.
     *
     * Get the edges with current flowing through them.
     *
     * @param arg tuple of transformer and recorder value
     * @return tuple of the node, the edge and the magnitude of the current
     */
    def alive (arg: (String, ThreePhaseComplexDataElement)): Option[(String, String, Double)] =
    {
        val datum = arg._2
        datum.units match
        {
            case "Amps" ⇒
                val value = datum.value_a.modulus
                if (value > 1e-5)
                {
                    val elementindex = datum.element.indexOf ("%")
                    if (-1 == elementindex)
                        None
                    else
                        Some (datum.element.substring (0, elementindex), datum.element.substring (elementindex + 1), value)
                }
                else
                    None
            case _ ⇒
                None
        }
    }

    /**
     * Trace the path of the "short circuit" current.
     *
     * Identify the sequence of elements from the starting node until the end.
     * Where parallel paths exist make a List.
     *
     * @param start starting node (e.g. EnergyConsumer)
     * @param data list of live branches
     * @return the traced fuses
     */
    def traceroute (start: String, data: Iterable[(String, String, Double)]): Seq[String] =
    {
        // print out the data
        println ("""traceroute ("%s", List (%s))""".format (start, data.map (x ⇒ """("%s", "%s", %s )""".format (x._1, x._2, x._3)).mkString (",")))

        val tr = new TraceRoute ()
        tr.traceroute (start, data).map (_.asString)
    }

    /**
     * Convert current and voltage at a node into impedance.
     *
     * Computes the sum of currents into a node, to get the current flowing from the node to neutral,
     * and from this, and the voltage at the node, computes the impedance at the node.
     *
     * @param arg a tuple of the experimental conditions and the results (both voltage and current in the same Iterable)
     * @return a tuple with the transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
     */
    def toImpedance (arg: (ScExperiment, Option[Iterable[(String, ThreePhaseComplexDataElement)]])): (String, String, String, Double, Complex, List[String]) =
    {
        val (z, path): (Complex, List[String]) = arg._2 match
        {
            case Some (data) ⇒
                val voltage: Option[ThreePhaseComplexDataElement] = data.map (_._2).find (_.units == "Volts")
                val current: Iterable[Complex] = data.filter (x ⇒ x._2.units == "Amps" && arg._1.mrid == extract_node (x._2.element)).map (_._2.value_a)
                voltage match
                {
                    case Some (volts) ⇒
                        val v = volts.value_a
                        implicit val zero: Complex = Complex(0)
                        // val i = current.sum // ToDo: Complex implements Numeric[Complex] but we need the above implicit for some reason, could use current.foldLeft (zero)(_ + _)
                        val i = current.foldLeft (zero)((a, b) ⇒ if (b.re > 0.0) a + b else a) // take only the sum of positive currents into the node ToDo: what's really positive in complex numbers
                        if (i == zero)
                        {
                            log.error ("""zero current at %s in time_slot %d:%d""".format (arg._1.mrid, arg._1.slot*arg._1.window / 60, arg._1.slot*arg._1.window % 60))
                            (Complex (Double.PositiveInfinity, 0.0), null)
                        }
                        else
                        {
                            val z =  (arg._1.voltage - v) / i
                            val live: Iterable[(String, String, Double)] = data.flatMap (alive)
                            val route = traceroute (arg._1.mrid, live)
                            (z, route.toList)
                        }
                    case _ ⇒
                        (null, null)
                }
            case _ ⇒
                (null, null)
        }
        (arg._1.trafo, arg._1.mrid, arg._1.equipment, arg._1.voltage, z, path)
    }

//    /**
//     * Demultiplex file names into element and type.
//     *
//     * Converts a filename into the node mrid and type of recording - current or voltage.
//     * For example, for a node called PIN767 with a node id of PIN767_topo, there may be files with names like:
//     * <ul>
//     * <li>PIN767_topo%FLE7941_current.csv</li>
//     * <li>PIN767_topo%FLT1024_current.csv</li>
//     * <li>PIN767_topo_voltage.csv</li>
//     * </ul>
//     * which would produce the pairs:
//     * <ul>
//     * <li>("PIN767_topo", "Amps)</li>
//     * <li>("PIN767_topo", "Amps)</li>
//     * <li>("PIN767_topo", "Volts)</li>
//     * </ul>
//     *
//     * @param filename the filename to demultiplex
//     * @return the pair of node id and type of recorder
//     */
//    def special_filenameparser (filename: String): (String, String) =
//    {
//        val elementindex = filename.indexOf ("%")
//        val element = if (-1 == elementindex) filename.substring (0, filename.lastIndexOf ("_")) else filename.substring (0, elementindex)
//        val units = if (filename.endsWith ("_voltage.csv"))
//            "Volts"
//        else if (filename.endsWith ("_current.csv"))
//            "Amps"
//        else
//            ""
//        (element, units)
//    }

    /**
     * Get the name element (possible multiplexed, e.g. node%edge) and type of measurement.
     * @param filename the recorder file name
     * @return tuple of the element and type of recorder
     */
    def special_filenameparser (filename: String): (String, String) =
    {
        val element = filename.substring (0, filename.lastIndexOf ("_"))
        val units = if (filename.endsWith ("_voltage.csv"))
            "Volts"
        else if (filename.endsWith ("_current.csv"))
            "Amps"
        else
            ""
        (element, units)
    }

    /**
     * Perform gridlabd via Spark pipe() and collect the experimental results.
     *
     * @param gridlabd the object to solve the .glm files and read the recorders
     * @param one_phase if <code>true</code>, create single phase results, otherwise three phase results
     * @param experiments the experiments contained in the players, that can be extracted from the recorders
     * @return an RDD of tuples with the transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
     */
    def solve_and_analyse (gridlabd: GridLABD, one_phase: Boolean, experiments: RDD[ScExperiment]): RDD[(String, String, String, Double, Complex, List[String])] =
    {
        val b4_solve = System.nanoTime ()
        val trafos: RDD[String] = experiments.map (_.trafo).distinct
        val success = gridlabd.solve (trafos)
        val solved = System.nanoTime ()
        log.info ("solve: %s seconds %s".format ((solved - b4_solve) / 1e9, if (success) "successful" else "failed"))
        val output = gridlabd.read_output_files (one_phase, special_filenameparser) // (trafoid, value_3ph)
        val read = System.nanoTime ()
        log.info ("read: %s seconds".format ((read - solved) / 1e9))
        // key by trafo_time to join
        val values = output.keyBy (x ⇒ x._1 + "_" + x._2.millis.toString).groupByKey
        val exp = experiments.keyBy (x ⇒ x.trafo + "_" + x.t1.getTimeInMillis.toString)
        val dd = exp.leftOuterJoin (values)
        val z = dd.values.map (toImpedance) // (trafoid, (nodeid, equipment, voltage, impedance))
        val anal = System.nanoTime ()
        log.info ("analyse: %s seconds".format ((anal - read) / 1e9))
        z
    }

    /**
     * Apply a GridLAB-D load flow analysis as a remedial work-around for mesh (non-radial) networks.
     *
     * Exports GridLAB-D model files, adding player files of short-circuits (actually just a low impedance)
     * for each node of interest ina time-multiplexed window of "experiments". It then executes the load-flow
     * and time demultuplexes each experiment to generate the impedance of the network as seen at each node (of interest).
     *
     * @param simulations the RDD of transformer service areas to which this analysis should be applied
     * @param temperature the temerature at which to evaluate the impedances (°C)
     * @param isMax If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents], otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @return the RDD of tuples with the transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
     */
    def remedial (simulations: RDD[SimulationTransformerServiceArea], temperature: Double, isMax: Boolean): RDD[(String, String, String, Double, Complex, List[String])] =
    {
        // for dates without time zones, the timezone of the machine is used:
        //    date +%Z
        // timezone can be set on each node of the cluster with:
        //    dpkg-reconfigure tzdata
        // then choose Europe and then choose Zürich
        //
        // all dates generated by this program include the time zone
        val USE_UTC = true
        val _DateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        if (USE_UTC)
            _DateFormat.setTimeZone (TimeZone.getTimeZone ("UTC"))
        else
            _DateFormat.setTimeZone (TimeZone.getTimeZone ("CET"))

        def generate (gridlabd: GridLABD, trafokreis: SimulationTransformerServiceArea): Unit =
        {
            val generator = ScGLMGenerator (one_phase = true, temperature = temperature, date_format = _DateFormat, trafokreis, isMax = isMax)
            gridlabd.export (generator)
        }
        val gridlabd = new GridLABD (session, topological_nodes = true, one_phase = true, storage_level = storage_level, workdir = options.workdir)
        val experiments = simulations.flatMap (
            x ⇒
            {
                generate (gridlabd, x)
                x.experiments
            }
        ).cache
        def short (exp: ScExperiment): Array[Byte] =
        {
            val ret = new StringBuilder ()
            val gigaohm = Complex (1e9, 0)
            def addrow (time: Calendar, impedance: Complex): Unit =
            {
                ret.append (_DateFormat.format (time.getTime))
                ret.append (",")
                ret.append (impedance.re)
                ret.append (",")
                ret.append (impedance.im)
                ret.append ("\n")
            }
            addrow (exp.t0, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero
            addrow (exp.t1, exp.impedance)
            addrow (exp.t2, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero

            ret.toString.getBytes (StandardCharsets.UTF_8)
        }
        def generate_player_file (gridlabd: GridLABD) (experiment: ScExperiment): Int =
        {
            if (false)
            {
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_R.csv", short (experiment))
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_S.csv", short (experiment))
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_T.csv", short (experiment))
            }
            else
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + ".csv", short (experiment))
            1
        }
        val n = experiments.map (generate_player_file (gridlabd)).count
        log.info ("""running %s experiments at %s°C""".format (n, temperature))

        solve_and_analyse (gridlabd = gridlabd, one_phase = true, experiments)
    }

    def node_maker (rdd: RDD[(node_id, Iterable[(identifier, (Terminal, Element, BaseVoltage))])]): RDD[(identifier, GLMNode)] =
    {
        val ss = rdd.keyBy (_._2.head._2._2.id).join (get[ConductingEquipment].keyBy (_.id)).values.map (x ⇒ (x._1._1, x._1._2.map (y ⇒ (y._1, (y._2._1, x._2, y._2._3)))))
        ss.map (args ⇒ (args._2.head._1, SimulationNode (args._1, args._2.head._2._3.nominalVoltage * 1000.0, args._2.head._2._2.id, args._2.head._2._2.Equipment.PowerSystemResource.PSRType)))
    }

    def edge_maker (rdd: RDD[Iterable[(Iterable[(identifier, Terminal)], Element)]]): RDD[(identifier, GLMEdge)] =
    {
        rdd.map (
            args ⇒
            {
                // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
                val id_cn_1 = args.head._1.head._2.TopologicalNode
                val id_cn_2 = args.head._1.tail.head._2.TopologicalNode
                (args.head._1.head._1, GLMEdge.toGLMEdge (args.map (_._2), id_cn_1, id_cn_2))
            }
        )
    }

    // execute GridLAB-D to approximate the impedances and replace the error records
    def fix (problem_transformers: RDD[TransformerSet], original_results: RDD[ScResult]): RDD[ScResult] =
    {
        log.info ("performing load-flow for %s non-radial networks".format (problem_transformers.count))

        // transformer area calculations
        val tsa = TransformerServiceArea (session, storage_level)
        // only proceed if topological processing was done (there are TopologicalIslands)
        if (tsa.hasIslands)
        {
            val trafos_islands = tsa.getTransformerServiceAreas.map (_.swap) // (trafosetid, islandid)
            val problem_trafos_islands = problem_transformers.keyBy (x ⇒ x.transformer_name).join (trafos_islands).values // (transformerset, islandid)
            val island_helper = new Island (session, storage_level)
            val graph_stuff = island_helper.queryNetwork (problem_trafos_islands.map (x ⇒ (x._1.transformer_name, x._2)), node_maker, edge_maker) // ([nodes], [edges])
            val areas = graph_stuff._1.groupByKey.join (graph_stuff._2.groupByKey).cache
            // set up simulations
            val now = javax.xml.bind.DatatypeConverter.parseDateTime ("2018-07-19T12:00:00")
            val simulations = areas.join (problem_transformers.keyBy (_.transformer_name)).map (x ⇒ (x._1, x._2._2, x._2._1._1, x._2._1._2)) // (areaid, trafoset, [nodes], [edges])
                .map (
                    x ⇒
                    SimulationTransformerServiceArea (
                        simulation = x._1,
                        island = x._1,
                        transformer = x._2,
                        nodes = x._3,
                        edges = x._4,
                        start_time = now,
                        directory = x._2.transformer_name)
            )
            // perform remedial simulations
            val zlo: RDD[(String, String, String, Double, Complex, List[String])] = remedial (simulations, options.low_temperature, true).cache // (trafoid, nodeid, equipment, voltage, Z)
            log.info ("""ran %s experiments at low temperature""".format (zlo.count ()))
            val zhi: RDD[(String, String, String, Double, Complex, List[String])] =
                // currently there is no difference in gridlabd processing between high and low temperature analysis, so we can skip the high temperature analysis if the temperatures are the same
                if (options.low_temperature != options.high_temperature)
                {
                    val _z = remedial (simulations, options.high_temperature, false).cache // (trafoid, nodeid, equipment, voltage, Z)
                    log.info ("""ran %s experiments at high temperature""".format (_z.count ()))
                    _z
                }
                else
                    zlo
            val z: RDD[(String, String, String, Double, (Complex, Complex), List[String])] = zlo.keyBy (x ⇒ x._1 + x._2 + x._3).join (zhi.keyBy (x ⇒ x._1 + x._2 + x._3)).values
                .map (x ⇒ (x._1._1, x._1._2, x._1._3, x._1._4, (x._1._5, x._2._5), x._1._6))
            // map to the type returned by the trace, use the existing value where possible
            val original_keyed: RDD[(identifier, ScResult)] = original_results.keyBy (x ⇒ x.tx + "_" + x.node)
            // transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
            val new_nodes = z.keyBy (x ⇒ x._1 + "_" + x._2).leftOuterJoin (original_keyed).values.map (
                x ⇒
                {
                    val v = x._1._4
                    val z1_low = x._1._5._1
                    val z0_low = z1_low * 4.0 // approximately four times
                    val z1_hi = x._1._5._2
                    val z0_hi = z1_hi * 4.0 // approximately four times
                    val z = Impedanzen (z1_low, z0_low, z1_hi, z0_hi)
                    x._2 match
                    {
                        case Some (original) ⇒
                            (
                                ScNode (original.node, v, original.tx, original.prev, z, List(ScError (false, false, "computed by load-flow"))), // replace the errors
                                original.terminal, original.equipment, original.container
                            )
                        case None ⇒
                            (
                                ScNode (x._1._2, v, x._1._1, null, z, List()),
                                1, x._1._3, ""
                            )
                    }
                }
            )
            // calculate new short circuit result records
            val replacements = new_nodes.map (calculate_short_circuit)
            // merge them into the existing set
            val replacements_keyed = replacements.keyBy (x ⇒ x.tx + "_" + x.node)
            // ToDo: should we remove all records from the problem transformers?
            val some = original_keyed.subtractByKey (replacements_keyed)
            some.union (replacements_keyed).values
        }
        else
        {
            log.info ("TopologicalIsland elements not found, cannot use GridLAB-D to fix radial network errors")
            original_results
        }
    }

    def run (): RDD[ScResult] =
    {
        FData.fuse_sizing_table (options.fuse_table)
        assert (null != get[TopologicalNode], "no topology")

        val _transformers = new Transformers (spark, storage_level)
        val tdata = _transformers.getTransformerData (
            true,
            options.default_short_circuit_power_max,
            options.default_short_circuit_impedance_max,
            options.default_short_circuit_power_min,
            options.default_short_circuit_impedance_min)

        val transformers: Array[Array[TData]] = if (null != options.trafos && "" != options.trafos && "all" != options.trafos) {
            val trafos = Source.fromFile (options.trafos, "UTF-8").getLines ().filter (_ != "").toArray
            val selected = tdata.filter (t ⇒ { trafos.contains (t.transformer.id) })
            selected.groupBy (t ⇒ t.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        else {
            // do all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter (td ⇒ td.voltage0 > 1.0 && td.voltage1 <= 1.0)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }

        val transformersets = transformers.map (txs ⇒ TransformerSet (txs, options.default_transformer_power_rating, options.default_transformer_impedance))
        val starting_nodes = transformersets.map (trafo_mapping)
        log.info ("%s starting transformers".format (starting_nodes.length))

        // create the initial Graph with ScNode vertices
        def starting_map (starting_nodes: Array[StartingTrafos]) (id: VertexId, v: ScNode): ScNode =
        {
            starting_nodes.find (trafo ⇒ trafo.nsPin == id || trafo.osPin == id) match
            {
                case Some (node) ⇒
                    // assign source and impedances to starting transformer primary and secondary
                    if (node.osPin == id)
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, "network", node.primary_impedance, null)
                    else
                    {
                        val errors = if (node.transformer.total_impedance._2) List (ScError (false, true, "transformer has no impedance value, using default %s".format (options.default_transformer_impedance))) else null
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, "self", node.secondary_impedance, errors)
                    }
                case None ⇒
                    v
            }
        }

        val initial = get_inital_graph ()
        val initial_with_starting_nodes = initial.mapVertices (starting_map (starting_nodes)).persist (storage_level)
        val sct = ShortCircuitTrace (session, options)
        val graph = sct.trace (initial_with_starting_nodes)

        // get the visited nodes with their data
        val result = graph.vertices.filter (null != _._2.impedance).values
        result.setName ("scresult")
        result.persist (storage_level)

        log.info ("computing results")
        // join results with terminals to get equipment
        val d = result.keyBy (_.id_seq).join (get[Terminal].keyBy (_.TopologicalNode)).values
        // join with equipment to get containers
        val e = d.keyBy (_._2.ConductingEquipment).join (get[ConductingEquipment].keyBy (_.id)).map (x ⇒ (x._2._1._1, x._2._1._2, x._2._2))
        val f = e.keyBy (_._3.Equipment.EquipmentContainer).leftOuterJoin (get[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._2._1._1, x._2._1._2, x._2._1._3, x._2._2))
        // resolve to top level containers
        // the equipment container for a transformer could be a Station or a Bay or VoltageLevel ... the last two of which have a reference to their station
        def station_fn (arg: (ScNode, Terminal, ConductingEquipment, Option[Any])): (ScNode, Int, String, String) =
        {
            val node = arg._1
            val terminal = arg._2
            val equipment = arg._3
            val container = arg._4
            container match
            {
                case Some (station: Substation) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, station.id)
                case Some (bay: Bay)            => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, bay.Substation)
                case Some (level: VoltageLevel) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, level.Substation)
                case _ => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, null)
            }
        }
        val g: RDD[(ScNode, Int, String, String)] = f.map (station_fn)

        // compute results
        var results: RDD[ScResult] = g.map (calculate_short_circuit)

        // find transformers where there are non-radial networks and fix them
        val problem_trafos = results.filter (result ⇒ result.errors.exists (s ⇒ s.startsWith ("FATAL: non-radial network detected"))).map (result ⇒ result.tx).distinct.cache
        def toTransformerSet (trafo: String): TransformerSet = transformersets.find (_.transformer_name == trafo).get
        val problem_trafosets = problem_trafos.map (toTransformerSet)
        if (0 != problem_trafos.count)
            results = fix (problem_trafosets, results)

        results
    }
}

object ShortCircuit
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.sc.Impedanzen],
            classOf[ch.ninecode.sc.ScEdge],
            classOf[ch.ninecode.sc.ScError],
            classOf[ch.ninecode.sc.ScIntermediate],
            classOf[ch.ninecode.sc.ScMessage],
            classOf[ch.ninecode.sc.ScNode],
            classOf[ch.ninecode.sc.ScResult],
            classOf[ch.ninecode.sc.ShortCircuit],
            classOf[ch.ninecode.sc.ShortCircuitOptions],
            classOf[ch.ninecode.sc.StartingTrafos]
        )
    }
}
