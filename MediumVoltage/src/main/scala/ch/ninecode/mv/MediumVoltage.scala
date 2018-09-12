package ch.ninecode.mv

import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Connector
import ch.ninecode.model.Element
import ch.ninecode.model.EquipmentContainer
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode

case class MediumVoltage (session: SparkSession, options: MediumVoltageOptions)
extends CIMRDD
with Serializable
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.mv.MediumVoltage").setLevel (org.apache.log4j.Level.INFO)
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

    def using[T <: Closeable, R](resource: T)(block: T => R): R =
    {
        try { block (resource) }
        finally { resource.close () }
    }

    def run (): Long =
    {
        val start = System.nanoTime ()

        // determine transformer list if any
        val trafos = if ("" != options.trafos)
            // do all transformers listed in the file
            Source.fromFile (options.trafos, "UTF-8").getLines ().filter (_ != "").toArray
        else
            null
        if ((null != trafos) && (0 == trafos.length))
        {
            log.error  ("no transformers to process")
            sys.exit (1)
        }

        // read the file
        val reader_options = new HashMap[String, String] ()
        reader_options ++= options.cim_reader_options
        reader_options.put ("path", options.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false")
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (options.files:_*)
        if (-1 != session.sparkContext.master.indexOf ("sandbox")) // are we in development
            elements.explain
        else
            log.info (elements.count () + " elements")

        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")

        val storage_level = options.cim_reader_options.find (_._1 == "StorageLevel") match
        {
            case Some ((_, storage)) => StorageLevel.fromString (storage)
            case _ => StorageLevel.fromString ("MEMORY_AND_DISK_SER")
        }

        // identify topological nodes if necessary
        val tns = session.sparkContext.getPersistentRDDs.filter(_._2.name == "TopologicalNode")
        if (tns.isEmpty || tns.head._2.isEmpty)
        {
            val ntp = new CIMNetworkTopologyProcessor (session, storage_level, force_retain_switches = true, force_retain_fuses = false)
            val ele = ntp.process (identify_islands = true)
            log.info (ele.count () + " elements")
        }

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (topological_nodes = true)

        // feeder service area calculations
        val feeder = Feeder (session, options.storage)
        val nodes_feeders = feeder.getFeederMap.filter (_._2 != null) // (nodeid, feederid)

        // get a map of voltages
        // ToDo: fix this 1kV multiplier on the voltages
        val voltages = get("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map(v ⇒ (v.id, v.nominalVoltage * 1000.0)).collectAsMap
        val ff: RDD[(String, TopologicalNode)] = nodes_feeders.join (get[TopologicalNode].keyBy (_.id)).values
        val nodes: RDD[(String, FeederNode)] = ff.keyBy (_._2.id).leftOuterJoin (feeder.feederNodes).values
                        .map (x ⇒ (x._1._1, FeederNode.toFeederNode (x._2.map (List(_)).orNull, x._1._2.id, voltages.getOrElse (x._1._2.BaseVoltage, 0.0))))

        // get equipment with nodes & terminals
        val gg: RDD[(String, Iterable[(String, Terminal)])] = get[Terminal].map (x ⇒ (x.ConductingEquipment, (x.TopologicalNode, x))).groupByKey // (equipmentid, [(nodeid, terminal)])
        // eliminate 0Ω links
        val hh = gg.filter (x ⇒ x._2.groupBy (_._1).size > 1)
        val eq: RDD[(Iterable[(String, Terminal)], Element)] = get[ConductingEquipment].keyBy (_.id).join (get[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (elementid, Element)
            .join (hh).values.map (_.swap) // ([(nodeid, terminal)], Element)
            // eliminate edges with only one end
            .filter (x ⇒ (x._1.size > 1) && x._1.map (_._1).forall (_ != null)) // ([(nodeid, terminal)], Element)
        // index by feeder
        val jj: RDD[(String, (Iterable[(String, Terminal)], Element))] = eq.flatMap (x ⇒ x._1.map (y ⇒ (y._1, x))).join (nodes_feeders).values.map (_.swap) // (feederid, ([(nodeid, Terminal)], Element)
        // ToDo: is it better to groupBy feeder first?
        val kk: RDD[Iterable[(String, (Iterable[(String, Terminal)], Element))]] = jj.keyBy (_._2._1.map (_._1).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values // [(feederid, ([(nodeid, Terminal)], Element)]
        // make one edge for each unique feeder it's in
        val ll: RDD[(String, Iterable[(Iterable[(String, Terminal)], Element)])] = kk.flatMap (x ⇒ x.map (_._1).toArray.distinct.map (y ⇒ (y, x.map (_._2))))

        // make edges
        // ToDo: fix this collect
        val transformers = tdata.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_)).collect
        def make_edge (transformers: Array[TransformerSet]) (args: Iterable[(Iterable[(String, Terminal)], Element)]): GLMEdge =
        {
            // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
            val id_cn_1 = args.head._1.head._2.TopologicalNode
            val id_cn_2 = args.head._1.tail.head._2.TopologicalNode
            AbgangKreis.toGLMEdge (transformers) (args.map (_._2), id_cn_1, id_cn_2)
        }
        val edges: RDD[(String, GLMEdge)] = ll.map (x ⇒ (x._1, make_edge (transformers) (x._2))).cache

        // OK, so there are nodes and edges identified by feeder, now we need to combine them
        // we need a "touches" list that says feeder A touches feeder B through open switch X
        val crosspoints: RDD[(String, PlayerSwitchEdge)] = edges.filter (_._2.isInstanceOf[PlayerSwitchEdge]).map (x ⇒ (x._1, x._2.asInstanceOf[PlayerSwitchEdge])) // (feederid, switch)
        val links: RDD[(String, String)] = crosspoints.flatMap (x ⇒ List ((x._1, x._2.cn1), (x._1, x._2.cn2))).map (_.swap).join (nodes_feeders).values.filter (x ⇒ x._1 != x._2) // (feederid, otherfeederid)
        val bidirection_links = links.flatMap (x ⇒ List (x, x.swap)).distinct // (feederid, otherfeederid)

        // this is the list of feeder elements with all attached feeder names - including it's own
        val feeder_mapping: RDD[(Element, Iterable[String])] = feeder.feeders.keyBy (_.id).join (bidirection_links).values.groupByKey.map (x ⇒ (x._1, Seq (x._1.id) ++ x._2))

        // expand the list of nodes and edges to include one copy for each feedermap element
        val more_nodes: RDD[(String, FeederNode)] = feeder_mapping.map (x ⇒ (x._1.id, x._2)).join (nodes).flatMap (x ⇒ x._2._1.map (y ⇒ (y, x._2._2)))
        val more_edges: RDD[(String, GLMEdge)] = feeder_mapping.map (x ⇒ (x._1.id, x._2)).join (edges).flatMap (x ⇒ x._2._1.map (y ⇒ (y, x._2._2)))

        val feeders = more_nodes.groupByKey.join (more_edges.groupByKey).join (feeder.feederStations.keyBy (_._4.id))
            .map (x ⇒ (x._1, (x._2._1._1, x._2._1._2, x._2._2))) // (feederid, ([FeederNode], [GLMEdge], (stationid, abgang#, header, feeder))
            .map (x ⇒ FeederArea (x._1, x._2._3._1, x._2._3._2, x._2._3._3, x._2._1.groupBy (_.id).map (y ⇒ y._2.head), x._2._2.groupBy (_.key).map (y ⇒ y._2.head)))

        def generate (gridlabd: GridLABD, area: FeederArea): Int =
        {
            val generator = MvGLMGenerator (one_phase = true, temperature = options.temperature, date_format = date_format, area, voltages)
            gridlabd.export (generator)
            val normally_open = "%s,OPEN".format (date_format.format (0L)).getBytes (StandardCharsets.UTF_8)
            val normally_closed = "%s,CLOSED".format (date_format.format (0L)).getBytes (StandardCharsets.UTF_8)
            // add a player file for each switch
            area.edges.filter (_.isInstanceOf[PlayerSwitchEdge]).map (_.asInstanceOf[PlayerSwitchEdge]).foreach (
                edge ⇒
                    gridlabd.writeInputFile (generator.name + "/input_data", edge.id + ".csv", if (feeder.switchClosed (edge.switch)) normally_closed else normally_open)
            )
            log.info (area.feeder)
            1
        }
        val gridlabd = new GridLABD (session, topological_nodes = true, one_phase = !options.three, storage_level = storage_level, workdir = options.workdir)
        val count = feeders.map (generate (gridlabd, _)).sum.longValue

        // for filename in STA*; do pushd $filename; gridlabd $filename; popd ; done;

        count
    }
}
