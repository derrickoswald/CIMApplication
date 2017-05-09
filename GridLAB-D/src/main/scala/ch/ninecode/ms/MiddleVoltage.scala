package ch.ninecode.ms

import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl._
import ch.ninecode.model._

case class MiddleVoltageOptions (
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    trafos: String = "",
    export_only: Boolean = false,
    erase: Boolean = false,
    short_circuit: String = "",
    files: Seq[String] = Seq()
)

case class MiddleVoltage (session: SparkSession, options: MiddleVoltageOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.ms.MiddleVoltage").setLevel (org.apache.log4j.Level.INFO)
    val log = LoggerFactory.getLogger (getClass)

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

        // identify topological nodes
        val ntp = new CIMNetworkTopologyProcessor (session, storage_level)
        val ele = ntp.process (false)
        log.info (ele.count () + " elements")

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        // prepare for precalculation
        val gridlabd = new GridLABD (session, !options.three, storage_level)
        gridlabd.HDFS_URI =
        {
            val name = options.files (0).replace (" ", "%20")
            val uri = new URI (name)
            if (null == uri.getScheme)
                ""
            else
                uri.getScheme + "://" + uri.getAuthority + "/"
        }

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (gridlabd.USE_TOPOLOGICAL_NODES, options.short_circuit)

        // determine the set of transformers to work on
        val transformers = if (null != trafos)
        {
            val selected = tdata.filter ((x) => trafos.contains (x.transformer.id))
            selected.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray)
        }
        else
        {
            // do all medium voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter ((td) => (td.voltage1 > 0.4) || (td.voltage0 > 16.0))
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray)
        }

        val prepare = System.nanoTime ()
        log.info ("prepare: " + (prepare - topo) / 1e9 + " seconds")

        log.info ("" + transformers.count + " transformers to process")

        val tx = transformers.collect
        tx.map ((x) => println (x(0)))
        def doit (transformers: Array[TData]): Int =
        {
            // get the transformer low voltage pin topological node
            val nothing = PreNode ("", 0.0)
            val name = gridlabd.node_name (transformers(0).terminal1)
            val id = nothing.vertex_id (name)
            // trace everything from that pin
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, nothing, storage_level, storage_level)
            val starting_nodes = Array[VertexId] (id)
            val trace = Trace (initial)
            val (nodes, edges) = trace.run (starting_nodes)
            val ynodes = nodes.collect
            val yedges = edges.groupBy (_.key).values.collect
            // create the GLMGenerator
            val ust = USTKreis (transformers, ynodes, yedges)
            println (ust.trafokreis_key + " traced " + ynodes.length + " nodes and " + yedges.length + " edges")
            val generator = MiddleVoltageGLMGenerator (!options.three, date_format, ust)
            gridlabd.export (generator)
            1
        }
        val xx = tx.map (doit)
        println (xx.length.toString + " transformers processed")

        val calculate = System.nanoTime ()
        log.info ("calculate: " + (calculate - prepare) / 1e9 + " seconds")

        transformers.count
    }

}