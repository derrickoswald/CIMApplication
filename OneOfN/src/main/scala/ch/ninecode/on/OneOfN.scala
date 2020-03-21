package ch.ninecode.on

import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.TimeZone

import scala.collection.mutable

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CHIM
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.LineDetails
import ch.ninecode.net.TransformerSet
import ch.ninecode.net.Transformers

case class OneOfN (session: SparkSession, options: OneOfNOptions) extends CIMRDD
{
    if (options.verbose)
    {
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.on.OneOfN").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.on.Feeder").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.cim.CIMNetworkTopologyProcessor").setLevel (org.apache.log4j.Level.INFO)
    }
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // for dates without time zones, the timezone of the machine is used:
    //    date +%Z
    // timezone can be set on each node of the cluster with:
    //    dpkg-reconfigure tzdata
    // then choose Europe and then choose Zürich
    //
    // all dates generated by this program include the time zone
    val use_utc = true
    val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    if (use_utc)
        date_format.setTimeZone (TimeZone.getTimeZone ("UTC"))

    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
        {
            case StorageLevel.NONE ⇒ "NONE"
            case StorageLevel.DISK_ONLY ⇒ "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 ⇒ "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY ⇒ "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 ⇒ "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER ⇒ "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 ⇒ "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK ⇒ "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 ⇒ "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER ⇒ "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 ⇒ "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP ⇒ "OFF_HEAP"
            case _ ⇒ ""
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
        val feeder = Feeder (session, options.storage)
        val keep = feeder.feeders
        log.info (keep.count + " medium voltage feeders")

        // create an RDD of elements in substations (PSRType_Substation)
        val markers = get[PowerSystemResource].filter (_.PSRType == "PSRType_Substation")

        // create an RDD of EquipmentContainer id values for these elements
        val containers = get[Equipment].keyBy (_.id).join (markers.keyBy (_.id)).map (x ⇒ x._2._1.EquipmentContainer).distinct.map (x ⇒ (x, x))

        // delete all CIM elements and their terminals where EquipmentContainer is in that RDD
        // except for equipment (cables) with PSRType_Underground or PSRType_Overhead
        // and excluding the feeder objects from the first step
        val elements = get[Element]("Elements")
        val kelements = elements.keyBy (_.id).persist (options.storage)
        val in_station = get[Equipment].filter (!externalCable (_)).keyBy (_.EquipmentContainer).join (containers)
            .map (x ⇒ (x._2._1.id, x._2._1.id))
            .join (kelements)
            .map (x ⇒ (x._1, x._2._2))
        val doomed = in_station.subtractByKey (keep.keyBy (_.id))
        val doomed_terminals = get[Terminal].keyBy (_.ConductingEquipment).join (doomed)
            .map (x ⇒ (x._2._1.id, x._2._1.id))
            .join (kelements)
            .map (x ⇒ (x._1, x._2._2))
        val new_elements = kelements.subtractByKey (doomed.union (doomed_terminals)).map (_._2)

        // update Elements named RDD
        elements.unpersist (false)
        kelements.unpersist (false)
        elements.name = "old_Elements"
        new_elements.name = "Elements"
        new_elements.persist (options.storage)

        // update all persistent RDD
        CHIM.apply_to_all_classes (
            subsetter =>
            {
                if (session.sparkContext.getPersistentRDDs.exists (_._2.name == subsetter.cls))
                    subsetter.make (session.sqlContext, new_elements, options.storage)
            }
        )

        new_elements
    }

    def run (): Long =
    {
        val start = System.nanoTime ()

        // read the file
        val reader_options = new mutable.HashMap[String, String]()
        reader_options ++= options.cim_reader_options
        reader_options.put ("path", options.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false")
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        reader_options.put ("StorageLevel", storage_level_tostring (options.storage))
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (options.files: _*)
        log.info (elements.count + " elements")

        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")

        // eliminate elements in substations
        val new_elements = deleteSubstationElements ()
        log.info (new_elements.count + " elements after substation deletion")

        // identify topological nodes
        val ntp = CIMNetworkTopologyProcessor (session)
        val ele = ntp.process (
            CIMTopologyOptions (
                identify_islands = true,
                force_retain_switches = ForceTrue,
                force_retain_fuses = Unforced,
                storage = options.storage))
        log.info (ele.count + " elements after topology generation")

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        //        val export = new CIMExport (session)
        //        export.exportAll (options.files.head.replace (".", "_with_topology."), "PSRType_Substation removed")

        // get all the transformers
        val _transformers = new Transformers (session, options.storage)
        val transformer_data = _transformers.getTransformers (transformer_filter = transformer ⇒ true)

        // feeder service area calculations
        val feeder = Feeder (session, options.storage)
        val nodes_feeders = feeder.identifyFeeders.filter (_._2 != null) // (nodeid, feederid)

        // get a map of voltage for each TopologicalNode starting from Terminal elements
        // ToDo: fix these 1kV multiplier on the voltages
        log.info ("creating nodes")
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map (v ⇒ (v.id, v.nominalVoltage * 1000.0)).collectAsMap
        val end_voltages = getOrElse[PowerTransformerEnd].map (
            x ⇒
            {
                val voltage = voltages.getOrElse (x.TransformerEnd.BaseVoltage, x.ratedU * 1000.0)
                (x.TransformerEnd.Terminal, voltage)
            }
        )
        val zeros = end_voltages.filter (_._2 == 0.0)
        if (!zeros.isEmpty ())
            log.warn ("""%s transformer ends with no nominal voltage, e.g. %s""".format (zeros.count, zeros.take (5).map (_._1).mkString (",")))
        val equipment_voltages = getOrElse[Terminal].keyBy (_.ConductingEquipment).join (getOrElse[ConductingEquipment].keyBy (_.id)).values.map (
            x ⇒
            {
                val voltage = voltages.getOrElse (x._2.BaseVoltage, 0.0)
                (x._1.id, voltage)
            }
        )
        val nodevoltages = end_voltages.filter (_._2 != 0.0).union (equipment_voltages.filter (_._2 != 0))
            .join (getOrElse[Terminal].keyBy (_.id)).values
            .map (x ⇒ (x._2.TopologicalNode, x._1))

        // put it all together
        val ff = nodes_feeders.join (get[TopologicalNode].keyBy (_.id)).leftOuterJoin (nodevoltages).map (x ⇒ (x._1, (x._2._1._1, x._2._1._2, x._2._2))) // (nodeid, (feederid, TopologicalNode, voltage?))
    val nodes: RDD[(String, FeederNode)] = ff.leftOuterJoin (feeder.feederNodes).values // ((feederid, TopologicalNode, voltage?), feeder?)
        .map (x ⇒ (x._1._1, FeederNode (x._2.map (List (_)).orNull, x._1._2.id, voltages.getOrElse (x._1._2.BaseVoltage, x._1._3.getOrElse (0.0))))).persist (options.storage)
        if (options.verbose)
            log.info ("%s nodes".format (nodes.count))

        // get equipment with nodes & terminals
        log.info ("creating edges")
        val gg: RDD[(String, Iterable[(String, Terminal)])] = get[Terminal].map (x ⇒ (x.ConductingEquipment, (x.TopologicalNode, x))).groupByKey // (equipmentid, [(nodeid, terminal)])
    // eliminate 0Ω links
    val hh = gg.filter (x ⇒ x._2.groupBy (_._1).size > 1)
        val eq: RDD[(Iterable[(String, Terminal)], Element)] = get[ConductingEquipment]
            .keyBy (_.id).join (get[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (elementid, Element)
            .join (hh).values.map (_.swap) // ([(nodeid, terminal)], Element)
            // eliminate edges with only one end
            .filter (x ⇒ (x._1.size > 1) && x._1.map (_._1).forall (_ != null)) // ([(nodeid, terminal)], Element)
    // index by feeder
    val jj: RDD[(String, (Iterable[(String, Terminal)], Element))] = eq.flatMap (x ⇒ x._1.map (y ⇒ (y._1, x))).join (nodes_feeders).values.distinct.map (_.swap) // (feederid, ([(nodeid, Terminal)], Element)
    // ToDo: is it better to groupBy feeder first?
    val kk: RDD[Iterable[(String, (Iterable[(String, Terminal)], Element))]] = jj.keyBy (x ⇒ x._2._1.map (_._1).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values // [(feederid, ([(nodeid, Terminal)], Element)]

        // make edges
        // ToDo: fix this collect
        val transformers = transformer_data.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_)).collect

        LineDetails.CIM_BASE_TEMPERATURE = options.base_temperature
        def make_edge (transformers: Array[TransformerSet])(args: Iterable[(Iterable[(String, Terminal)], Element)]): GLMEdge =
        {
            // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
            val id_cn_1 = args.head._1.head._2.TopologicalNode
            val id_cn_2 = args.head._1.tail.head._2.TopologicalNode
            AbgangKreis.toGLMEdge (transformers)(args.map (_._2), id_cn_1, id_cn_2)
        }

        // make one edge for each unique feeder it's in
        val edges: RDD[(String, GLMEdge)] = kk.flatMap (x ⇒ x.map (_._1).toArray.distinct.map (y ⇒ (y, make_edge (transformers)(x.filter (_._1 == y).map (_._2))))).persist (options.storage)
        if (options.verbose)
            log.info ("%s edges".format (edges.count))

        // keep only nodes we need
        val needed_nodes: RDD[(String, FeederNode)] = edges.flatMap (x ⇒ List ((x._2.cn1, x._2.cn1), (x._2.cn2, x._2.cn2))).join (nodes.keyBy (_._2._id)).map (_._2._2)

        // OK, so there are nodes and edges identified by feeder, one (duplicate) node and edge for each feeder
        log.info ("creating models")
        val feeders = needed_nodes.groupByKey.join (edges.groupByKey).join (feeder.feederStations.keyBy (_.id))
            .map (x ⇒ (x._1, (x._2._1._1, x._2._1._2, x._2._2))) // (feederid, ([FeederNode], [GLMEdge], FeederMetadata)
            .map (
            x ⇒
            {
                val nodes = x._2._1.groupBy (_.id).map (y ⇒ y._2.head) // distinct
            // to handle the ganged transformers that have only one node connected into the network
            // check against the list of nodes and if there are more than one edge with the same id keep only those with both ends in the topology
            val nodelist = nodes.map (x ⇒ (x._id, x)).toMap

                def pickbest (arg: (String, Iterable[GLMEdge])): GLMEdge =
                {
                    val withcount = arg._2.map (
                        edge ⇒
                        {
                            val n = (nodelist.get (edge.cn1), nodelist.get (edge.cn2)) match
                            {
                                case (Some (n1), Some (n2)) ⇒ List (n1, n2)
                                case (Some (n1), None) ⇒ List (n1)
                                case (None, Some (n2)) ⇒ List (n2)
                                case _ ⇒ List () // ?
                            }
                            (edge, n)
                        }
                    )
                    val two = withcount.filter (_._2.size >= 2)
                    if (two.nonEmpty)
                        two.head._1
                    else
                    {
                        val one = withcount.filter (_._2.nonEmpty)
                        if (one.nonEmpty)
                            one.head._1
                        else
                            withcount.head._1
                    }
                }

                val edges = x._2._2.groupBy (_.id).map (pickbest)
                FeederArea (x._1, x._2._3, nodes, edges)
            }).persist (options.storage)
        log.info ("%s feeders".format (feeders.count))

        def generate (gridlabd: GridLABD, area: FeederArea): Int =
        {
            if (options.verbose) // re-set the log level on each worker
                org.apache.log4j.LogManager.getLogger ("ch.ninecode.on.OneOfN").setLevel (org.apache.log4j.Level.INFO)

            val generator = OneOfNGLMGenerator (one_phase = true, temperature = options.temperature, date_format = date_format, area, voltages)
            gridlabd.export (generator)

            // to make the glm files testable, we add a player file for the switches generated by a bash file of the form:
            //     for file in \
            //     file1 \
            //     file2 \
            //     file3
            //     do
            //         echo 1970-01-01 00:00:00 UTC,CLOSED>$file.csv
            //     done
            val switches = (area.edges.filter (_.isInstanceOf[PlayerSwitchEdge]).map (_.id) ++ generator.swing_nodes.map (_.id)).mkString (" \\\n")
            val UNIX_EPOC: String = date_format.format (0L)
            val text =
                """for file in \
                  |%s
                  |do
                  |    echo %s,%s>$file.csv
                  |done""".stripMargin.format (switches, UNIX_EPOC, "CLOSED").getBytes (StandardCharsets.UTF_8)
            gridlabd.writeInputFile (generator.name + "/input_data", "gen", text, "ugo-rwx")
            log.info ("%10s %8s %s".format (area.feeder, area.metadata.station, area.metadata.description))
            1
        }

        val gridlabd = new GridLABD (session, storage_level = options.storage, workdir = options.workdir)
        log.info ("exporting models")
        val count = feeders.map (generate (gridlabd, _)).sum.longValue

        // to test all the generated glm files, change to the output directory and run
        // for filename in STA*; do echo $filename; pushd $filename/input_data > /dev/null; ./gen; cd ..; gridlabd $filename; popd > /dev/null; done;

        count
    }
}

object OneOfN
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
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
}