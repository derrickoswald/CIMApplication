package ch.ninecode.on

import java.io.Closeable
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.TimeZone

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CHIM
import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.DefaultSource
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.LineDetails
import ch.ninecode.net.Net
import ch.ninecode.net.TransformerSet
import ch.ninecode.net.Transformers
import ch.ninecode.net.Voltages
import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkInitializer
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Util

case class OneOfN (session: SparkSession, options: OneOfNOptions) extends CIMRDD
{
    if (options.verbose)
    {
        org.apache.log4j.LogManager.getLogger("ch.ninecode.on.OneOfN").setLevel(org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger("ch.ninecode.on.Feeder").setLevel(org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger("ch.ninecode.cim.CIMNetworkTopologyProcessor").setLevel(org.apache.log4j.Level.INFO)
    }
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)
    implicit val storage: StorageLevel = options.cim_options.storage

    // for dates without time zones, the timezone of the machine is used:
    //    date +%Z
    // timezone can be set on each node of the cluster with:
    //    dpkg-reconfigure tzdata
    // then choose Europe and then choose Zürich
    //
    // all dates generated by this program include the time zone
    val use_utc = true
    val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    if (use_utc)
        date_format.setTimeZone(TimeZone.getTimeZone("UTC"))

    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block(resource)
        }
        finally
        {
            resource.close()
        }
    }

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
        {
            case StorageLevel.NONE => "NONE"
            case StorageLevel.DISK_ONLY => "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 => "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY => "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 => "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER => "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 => "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK => "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 => "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER => "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 => "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP => "OFF_HEAP"
            case _ => ""
        }
    }

    def externalCable (equipment: Equipment): Boolean =
    {
        equipment.PowerSystemResource.PSRType == "PSRType_Underground" ||
            equipment.PowerSystemResource.PSRType == "PSRType_Overhead"
    }

    def deleteSubstationElements (): RDD[Element] =
    {
        // determine feeders as medium voltage (1e3 < V < 50e3) Connector in substations (PSRType_Substation)
        val feeder = Feeder(session, storage)
        val keep = feeder.feeders
        log.info(s"${keep.count} medium voltage feeders")

        // create an RDD of elements in substations (PSRType_Substation)
        val markers = get[PowerSystemResource].filter(_.PSRType == "PSRType_Substation")

        // create an RDD of EquipmentContainer id values for these elements
        val containers = get[Equipment].keyBy(_.id).join(markers.keyBy(_.id)).map(x => x._2._1.EquipmentContainer).distinct.map(x => (x, x))

        // delete all CIM elements and their terminals where EquipmentContainer is in that RDD
        // except for equipment (cables) with PSRType_Underground or PSRType_Overhead
        // and excluding the feeder objects from the first step
        val elements = getOrElse[Element]
        val kelements = elements.keyBy(_.id).persist(storage)
        val in_station = get[Equipment].filter(!externalCable(_)).keyBy(_.EquipmentContainer).join(containers)
            .map(x => (x._2._1.id, x._2._1.id))
            .join(kelements)
            .map(x => (x._1, x._2._2))
        val doomed = in_station.subtractByKey(keep.keyBy(_.id))
        val doomed_terminals = get[Terminal].keyBy(_.ConductingEquipment).join(doomed)
            .map(x => (x._2._1.id, x._2._1.id))
            .join(kelements)
            .map(x => (x._1, x._2._2))
        val new_elements = kelements.subtractByKey(doomed.union(doomed_terminals)).map(_._2)

        // update Elements named RDD
        put(new_elements, true)
        val _ = kelements.unpersist(false)

        // update all persistent RDD
        CHIM.apply_to_all_classes(
            subsetter =>
            {
                if (session.sparkContext.getPersistentRDDs.exists(_._2.name == subsetter.cls))
                    subsetter.make(session.sqlContext, new_elements, storage, "%s")
            }
        )

        new_elements
    }

    def run (): Long =
    {
        val start = System.nanoTime()


        // read the file
        val reader_options = options.cim_options.toMap ++
            Map[String, String](
                "ch.ninecode.cim.do_topo" -> "false",
                "ch.ninecode.cim.do_topo_islands" -> "false")
        val elements = session.read.format("ch.ninecode.cim").options(reader_options).load(options.cim_options.files: _*)
            .persist(storage)
        log.info(s"${elements.count} elements")

        val read = System.nanoTime()
        log.info(s"read: ${(read - start) / 1e9} seconds")

        // eliminate elements in substations
        val new_elements = deleteSubstationElements()
        put(new_elements, true)
        log.info(s"${new_elements.count} elements after substation deletion")

        // identify topological nodes
        val ntp = CIMNetworkTopologyProcessor(
            session,
            CIMTopologyOptions(
                identify_islands = true,
                force_retain_switches = ForceTrue,
                force_retain_fuses = Unforced,
                storage = storage,
                debug = true))
        val ele = ntp.process.persist(storage)
        put(ele, true)
        log.info(s"${ele.count} elements after topology generation")

        val topo = System.nanoTime()
        log.info(s"topology: ${(topo - read) / 1e9} seconds")

        //        val export = new CIMExport (session)
        //        export.exportAll (options.files.head.replace (".", "_with_topology."), "PSRType_Substation removed")

        // get all the transformers
        val _transformers = Transformers(session, storage)
        val transformer_data = _transformers.getTransformers(transformer_filter = _ => true)
        log.info(s"${transformer_data.count} transformers")

        // feeder service area calculations
        val feeder = Feeder(session, storage)
        val nodes_feeders = feeder.identifyFeeders.filter(_._2 != null) // (nodeid, feederid)
        log.info(s"${nodes_feeders.count} feeders")

        // get a map of voltage for each TopologicalNode starting from Terminal elements
        log.info("creating nodes")
        val voltages = Voltages(session, storage).getVoltages
        val end_voltages = getOrElse[PowerTransformerEnd].map(
            x =>
            {
                val voltage = voltages.getOrElse(x.TransformerEnd.BaseVoltage, x.ratedU * 1000.0)
                (x.TransformerEnd.Terminal, voltage)
            }
        )
        val zeros = end_voltages.filter(_._2 == 0.0)
        if (!zeros.isEmpty())
            log.warn(s"""${zeros.count} transformer ends with no nominal voltage, e.g. ${zeros.take(5).map(_._1).mkString(",")}""")
        val equipment_voltages = getOrElse[Terminal].keyBy(_.ConductingEquipment).join(getOrElse[ConductingEquipment].keyBy(_.id)).values.map(
            x =>
            {
                val voltage = voltages.getOrElse(x._2.BaseVoltage, 0.0)
                (x._1.id, voltage)
            }
        )
        val nodevoltages = end_voltages.filter(_._2 != 0.0).union(equipment_voltages.filter(_._2 != 0))
            .join(getOrElse[Terminal].keyBy(_.id)).values
            .map(x => (x._2.TopologicalNode, x._1))

        // put it all together
        val ff = nodes_feeders.join(get[TopologicalNode].keyBy(_.id)).leftOuterJoin(nodevoltages).map(x => (x._1, (x._2._1._1, x._2._1._2, x._2._2))) // (nodeid, (feederid, TopologicalNode, voltage?))
        val nodes: RDD[(String, FeederNode)] = ff.leftOuterJoin(feeder.feederNodes).values // ((feederid, TopologicalNode, voltage?), feeder?)
            .map(
                x =>
                {
                    val ((id, node, voltage), feeder) = x
                    (id, FeederNode(node.id, voltages.getOrElse(node.BaseVoltage, voltage.getOrElse(0.0)), feeder))
                }).persist(storage)
        if (options.verbose)
            log.info(s"${nodes.count} nodes")

        // get equipment with nodes & terminals
        log.info("creating edges")
        val gg: RDD[(String, Iterable[(String, Terminal)])] = get[Terminal].map(x => (x.ConductingEquipment, (x.TopologicalNode, x))).groupByKey // (equipmentid, [(nodeid, terminal)])
        // eliminate 0Ω links
        val hh = gg.filter(x => x._2.groupBy(_._1).size > 1)
        val eq: RDD[(Iterable[(String, Terminal)], Element)] = get[ConductingEquipment]
            .keyBy(_.id).join(getOrElse[Element].keyBy(_.id)).map(x => (x._1, x._2._2)) // (elementid, Element)
            .join(hh).values.map(_.swap) // ([(nodeid, terminal)], Element)
            // eliminate edges with only one end
            .filter(x => (x._1.size > 1) && x._1.map(_._1).forall(_ != null)) // ([(nodeid, terminal)], Element)
        // index by feeder
        val jj: RDD[(String, (Iterable[(String, Terminal)], Element))] = eq.flatMap(x => x._1.map(y => (y._1, x))).join(nodes_feeders).values.distinct.map(_.swap) // (feederid, ([(nodeid, Terminal)], Element)
        // ToDo: is it better to groupBy feeder first?
        val kk: RDD[Iterable[(String, (Iterable[(String, Terminal)], Element))]] = jj.keyBy(x => x._2._1.map(_._1).toArray.sortWith(_ < _).mkString("_")).groupByKey.values // [(feederid, ([(nodeid, Terminal)], Element)]

        // make edges
        // ToDo: fix this collect
        val transformers = transformer_data.groupBy(_.terminal1.TopologicalNode).values.map(_.toArray).map(TransformerSet(_)).collect

        LineDetails.CIM_BASE_TEMPERATURE = options.base_temperature

        @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
        def make_edge (transformers: Array[TransformerSet])(args: Iterable[(Iterable[(String, Terminal)], Element)]): GLMEdge =
        {
            // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
            val id_cn_1 = args.head._1.head._2.TopologicalNode
            val id_cn_2 = args.head._1.drop(1).head._2.TopologicalNode
            AbgangKreis.toGLMEdge(transformers)(args.map(_._2), id_cn_1, id_cn_2)
        }

        // make one edge for each unique feeder it's in
        val edges: RDD[(String, GLMEdge)] = kk.flatMap(x => x.map(_._1).toArray.distinct.map(y => (y, make_edge(transformers)(x.filter(_._1 == y).map(_._2))))).persist(storage)
        if (options.verbose)
            log.info(s"${edges.count} edges")

        // keep only nodes we need
        val needed_nodes: RDD[(String, FeederNode)] = edges.flatMap(x => List((x._2.cn1, x._2.cn1), (x._2.cn2, x._2.cn2))).join(nodes.keyBy(_._2._id)).map(_._2._2)

        @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
        def pickbest (nodelist: Map[String, FeederNode])(arg: (String, Iterable[GLMEdge])): GLMEdge =
        {
            val withcount = arg._2.map(
                edge =>
                {
                    val n = (nodelist.get(edge.cn1), nodelist.get(edge.cn2)) match
                    {
                        case (Some(n1), Some(n2)) => List(n1, n2)
                        case (Some(n1), None) => List(n1)
                        case (None, Some(n2)) => List(n2)
                        case _ => List() // ?
                    }
                    (edge, n)
                }
            )
            val two = withcount.filter(_._2.size >= 2)
            if (two.nonEmpty)
                two.head._1
            else
            {
                val one = withcount.filter(_._2.nonEmpty)
                if (one.nonEmpty)
                    one.head._1
                else
                    withcount.head._1
            }
        }

        @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
        def toFeederArea (x: (String, (Iterable[FeederNode], Iterable[GLMEdge], FeederMetadata))): FeederArea =
        {
            val nodes = x._2._1.groupBy(_.id).map(y => y._2.head) // distinct
            // to handle the ganged transformers that have only one node connected into the network
            // check against the list of nodes and if there are more than one edge with the same id keep only those with both ends in the topology
            val nodelist = nodes.map(x => (x._id, x)).toMap

            val edges = x._2._2.groupBy(_.id).map(pickbest(nodelist))
            FeederArea(x._1, x._2._3, nodes, edges)
        }

        // OK, so there are nodes and edges identified by feeder, one (duplicate) node and edge for each feeder
        log.info("creating models")
        val feeders = needed_nodes.groupByKey.join(edges.groupByKey).join(feeder.feederStations.keyBy(_.id))
            .map(x => (x._1, (x._2._1._1, x._2._1._2, x._2._2))) // (feederid, ([FeederNode], [GLMEdge], FeederMetadata)
            .map(toFeederArea).persist(storage)
        log.info(s"${feeders.count} feeders")

        def generate (gridlabd: GridLABD, area: FeederArea): Int =
        {
            if (options.verbose) // re-set the log level on each worker
                org.apache.log4j.LogManager.getLogger("ch.ninecode.on.OneOfN").setLevel(org.apache.log4j.Level.INFO)

            LineDetails.CIM_BASE_TEMPERATURE = options.base_temperature
            val generator = OneOfNGLMGenerator(one_phase = !options.three, temperature = options.temperature, date_format = date_format, area, voltages)
            gridlabd.export(generator)

            // to make the glm files testable, we add a player file for the switches generated by a bash file of the form:
            //     for file in \
            //     file1 \
            //     file2 \
            //     file3
            //     do
            //         echo 1970-01-01 00:00:00 UTC,CLOSED>$file.csv
            //     done
            val switches = (area.edges.filter(_.isInstanceOf[PlayerSwitchEdge]).map(_.id) ++ generator.swing_nodes.map(_.id)).mkString(" \\\n")
            val UNIX_EPOC: String = date_format.format(0L)
            val text =
                """for file in \
                  |%s
                  |do
                  |    echo %s,%s>$file.csv
                  |done""".stripMargin.format(switches, UNIX_EPOC, "CLOSED").getBytes(StandardCharsets.UTF_8)
            gridlabd.writeInputFile(generator.name + "/input_data", "gen", text)
            log.info("%10s %8s %s".format(area.feeder, area.metadata.station, area.metadata.description))
            1
        }

        val gridlabd = new GridLABD(session, storage_level = storage, workdir = options.workdir)
        log.info("exporting models")
        val count = feeders.map(generate(gridlabd, _)).sum.longValue

        // to test all the generated glm files, change to the output directory and run
        // for filename in STA*; do echo $filename; pushd $filename/input_data > /dev/null; ./gen; cd ..; gridlabd $filename; popd > /dev/null; done;

        count
    }
}

object OneOfN extends SparkInitializer[OneOfNOptions] with Main
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array(
            classOf[ch.ninecode.on.AbgangKreis],
            classOf[ch.ninecode.on.EdgeData],
            classOf[ch.ninecode.on.Feeder],
            classOf[ch.ninecode.on.FeederArea],
            classOf[ch.ninecode.on.FeederMetadata],
            classOf[ch.ninecode.on.FeederNode],
            classOf[ch.ninecode.on.OneOfN],
            classOf[ch.ninecode.on.OneOfNGLMGenerator],
            classOf[ch.ninecode.on.OneOfNOptions],
            classOf[ch.ninecode.on.PlayerSwitchEdge],
            classOf[ch.ninecode.on.VertexData]
        )
    }

    /**
     * Generate a working directory matching the files.
     */
    def derive_work_dir (files: Seq[String]): String =
    {
        files.toList match
        {
            case paths :: _ =>
                val file = paths.split(",")(0).replace(" ", "%20")
                val uri = new URI(file)
                val scheme = uri.getScheme
                val auth = if (null == uri.getAuthority) "" else uri.getAuthority
                if (null == scheme)
                    "/simulation/"
                else
                    s"$scheme://$auth/simulation/"
            case _ =>
                "/simulation/"
        }
    }

    def run (options: OneOfNOptions): Unit =
    {
        if (options.verbose)
            LogManager.getLogger(getClass).setLevel(Level.INFO)
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                val session: SparkSession = createSession(options)
                time("execution: %s seconds")
                {
                    val on = OneOfN(session, options)
                    val _ = on.run()
                }
            }
            else
                log.error("no CIM files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error(s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit(1)
        }
        else
        {
            // set up default options
            val default = OneOfNOptions(
                main_options = MainOptions(application_name, application_version),
                spark_options = SparkOptions(
                    jars = Set(
                        jarForObject(new DefaultSource()),
                        jarForObject(this)
                    ).toArray,
                    kryo = Array.concat(
                        // register CIMReader classes
                        CIMClasses.list,
                        // register Net classes
                        Net.classes,
                        // register GridLAB-D classes
                        GridLABD.classes,
                        // register OneOfN classes
                        OneOfN.classes,
                        // register Util classes
                        Util.classes
                    )
                )
            )
            // parse the command line arguments
            new OneOfNOptionsParser(default).parse(args, default) match
            {
                case Some(o) =>
                    // execute the main program if everything checks out
                    val options = if ("" == o.workdir)
                        o.copy(workdir = derive_work_dir(o.cim_options.files))
                    else
                        o
                    run(options)
                    if (!options.main_options.unittest)
                        sys.exit(0)
                case None =>
                    sys.exit(1)
            }
        }
    }
}
