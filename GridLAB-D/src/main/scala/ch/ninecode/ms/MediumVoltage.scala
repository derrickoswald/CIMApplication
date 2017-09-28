package ch.ninecode.ms

import java.text.SimpleDateFormat
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.TData
import ch.ninecode.gl.Trace
import ch.ninecode.gl.Transformers
import ch.ninecode.gl.TransformerSet
import ch.ninecode.model.TopologicalNode

case class MediumVoltage (session: SparkSession, options: MediumVoltageOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.ms.MediumVoltage").setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

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

        // identify topological nodes if necessary
        val tns = session.sparkContext.getPersistentRDDs.filter(_._2.name == "TopologicalNode")
        if (tns.isEmpty || tns.head._2.isEmpty)
        {
            val ntp = new CIMNetworkTopologyProcessor (session, storage_level)
            val ele = ntp.process (identify_islands = true)
            log.info (ele.count () + " elements")
        }

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        // prepare for precalculation
        val gridlabd = new GridLABD (session, topological_nodes = true, !options.three, storage_level, options.workdir)

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (topological_nodes = true, options.short_circuit)

        // determine the set of transformers to work on
        /**
         * The name of the node associated with the hight voltage terminal.
         * @param pair The transformer information and topological node pair to get the island for.
         * @return The name of the TopologicalNode or ConnectivityNode.
         */
        def lv_island_name (pair: (TData, TopologicalNode)): (String, TData) =
        {
            val transformer = pair._1
            val island = pair._2.TopologicalIsland
            (island, transformer)
        }

        val tnodes = session.sparkContext.getPersistentRDDs.filter(_._2.name == "TopologicalNode").head._2.asInstanceOf[RDD[TopologicalNode]]
        // do all high voltage power transformer (low voltage) islands
        val islands = if (null != trafos)
        {
            val selected = tdata.filter ((x) => trafos.contains (x.transformer.id)).keyBy (_.node1).join (tnodes.keyBy (_.id)).values.map (lv_island_name).groupByKey
            selected.values.map (_.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet))
        }
        else
        {
            // get the high voltage transformers grouped by low voltage TopologicalIsland
            val hochpannug = tdata.filter ((td) => (td.voltage1 > 0.4) || (td.voltage0 > 16.0)).keyBy (_.node1).join (tnodes.keyBy (_.id)).values.map (lv_island_name).groupByKey
            // create a TransformerSet for each different low voltage TopologicalNode
            hochpannug.values.map (_.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet))
        }

        // determine the list of low voltage transformer sets
        val lv = tdata.filter (_.voltage1 <= 0.4).groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet)

        val prepare = System.nanoTime ()
        log.info ("prepare: " + (prepare - topo) / 1e9 + " seconds")

        log.info ("" + islands.count + " island(s) to process")

        val tasks = islands.collect
        println (tasks.map (_.head.transformer_name).mkString ("\n"))
        def doit (transformers: Iterable[TransformerSet]): Int =
        {
            // get the transformer low voltage pin topological node
            val nothing = PreNode ("", 0.0)
            val id = nothing.vertex_id (transformers.head.node1)
            // trace everything from that pin
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, nothing, storage_level, storage_level)
            val starting_nodes = Array[VertexId] (id)
            val trace = Trace (initial)
            val (nodes, edges) = trace.run (starting_nodes)
            // form the USTKreis data packet to send to the executor
            val ynodes = nodes.collect
            val yedges = edges.groupBy (_.key).values.collect
            val ust = USTKreis (transformers.toArray, ynodes, yedges, lv.collect)
            println (ust.trafokreis_key + " traced " + ynodes.length + " nodes and " + yedges.length + " edges")

            // create the GLMGenerator
            val generator = MediumVoltageGLMGenerator (!options.three, date_format, ust)
            gridlabd.export (generator)
            1
        }
        val done = tasks.map (doit)
        println (done.length.toString + " transformers processed")

        val calculate = System.nanoTime ()
        log.info ("calculate: " + (calculate - prepare) / 1e9 + " seconds")

        islands.count
    }
}