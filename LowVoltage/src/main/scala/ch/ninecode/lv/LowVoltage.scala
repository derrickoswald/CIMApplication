package ch.ninecode.lv

import java.net.URI
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.mfi._
import ch.ninecode.gl._
import ch.ninecode.model._

//  <md:FullModel rdf:about="sias_current">
//        <md:Model.created>2017-06-01T23:00:20</md:Model.created>
//        <md:Model.description>NIS Strom CIM export (http://nis.ch/produkte#nisStrom)</md:Model.description>
//        <md:Model.modelingAuthoritySet>http://9code.ch/</md:Model.modelingAuthoritySet>
//        <md:Model.profile>https://github.com/derrickoswald/CIMReader</md:Model.profile>
//        <md:Model.scenarioTime>2017-05-31T23:32:36</md:Model.scenarioTime>
//        <md:Model.version>103</md:Model.version>
//  </md:FullModel>
case class Header (
    created: Calendar,
    description: String,
    modelingAuthoritySet: String,
    profile: String,
    scenarioTime: Calendar,
    version: String)

case class LowVoltage (session: SparkSession, storage_level: StorageLevel, options: LowVoltageOptions)
{
    if (options.verbose)
    {
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.lv.LowVoltage").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.mfi.PowerFeeding$").setLevel (org.apache.log4j.Level.INFO)
    }
    val log: Logger = LoggerFactory.getLogger (getClass)

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

    def getCIMheader (gridlabd: GridLABD): String =
    {
        val file = options.files.head.split (",")(0) // need to watch out for comma separated file list
        val lead = "<md:FullModel"
        val trail = "</md:FullModel>"

        val raw = if ((gridlabd.workdir_scheme == "file") || (gridlabd.workdir_scheme == ""))
        {
            val in = Files.newInputStream (java.nio.file.FileSystems.getDefault.getPath (file))
            val buffer = new Array[Byte] (4 * 1024)
            in.read (buffer)
            in.close ()
            new String (buffer, java.nio.charset.StandardCharsets.UTF_8)
        }
        else
        {
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (gridlabd.workdir_uri), hdfs_configuration)

            val in = hdfs.open (new Path (file))
            val buffer = new Array[Byte] (4 * 1024)
            in.read (0L, buffer, 0, buffer.length)
            in.close ()
            new String (buffer, java.nio.charset.StandardCharsets.UTF_8)
        }
        val start = raw.indexOf (lead)
        val end = raw.indexOf (trail)
        if ((-1 != start) && (-1 != end))
           raw.substring (start, end + trail.length ())
        else
            ""
    }

    def toDate (string: String): Calendar = javax.xml.bind.DatatypeConverter.parseDateTime (string)

    def parseHeader (string: String): Header =
    {
        val x = scala.xml.XML.loadString (string)
        try
        {
            Header (
                toDate ((x \\ "Model.created").head.text),
                (x \\ "Model.description").head.text,
                (x \\ "Model.modelingAuthoritySet").head.text,
                (x \\ "Model.profile").head.text,
                toDate ((x \\ "Model.scenarioTime").head.text),
                (x \\ "Model.version").head.text
                )
        }
        catch 
        {
            case e: Exception => log.error ("exception caught parsing rdf header: " + e)
            val now = Calendar.getInstance ()
            Header (now, "no description", "no modeling authority", "no profile", now, "no version")
        }
    }

    def rename (gridlabd: GridLABD, new_name: String)
    {
        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create (gridlabd.workdir_uri), hdfs_configuration)

        val output = new Path (gridlabd.workdir_slash)
        val target = new Path ("/" + new_name + "/")
        hdfs.rename (output, target)
    }

    def generate (gridlabd: GridLABD, trafokreise: RDD[Trafokreis]): Unit =
    {
        val start = System.nanoTime()

        def doit (trafokreis: Trafokreis): Int =
        {
            val generator = new LowVoltageGLMGenerator (!options.three, _DateFormat, trafokreis)
            gridlabd.export (generator)
            1
        }
        log.info ("exporting: " + trafokreise.count() + " transformer service areas")
        val files = trafokreise.map (doit).cache
        val fc = files.fold (0)(_+_)
        log.info ("exported: " + fc + " transformer service areas")

        val write = System.nanoTime()
        log.info ("export: " + (write - start) / 1e9 + " seconds")
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
        reader_options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
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
        var topo = System.nanoTime ()
        val tns = session.sparkContext.getPersistentRDDs.filter(_._2.name == "TopologicalNode")
        if (tns.isEmpty || tns.head._2.isEmpty)
        {
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = false,
                    storage = storage_level))
            log.info (ele.count () + " elements")
            topo = System.nanoTime ()
            log.info ("topology: " + (topo - read) / 1e9 + " seconds")
        }

        // prepare for precalculation
        val topological_nodes = true
        val gridlabd = new GridLABD (session, topological_nodes, !options.three, storage_level, options.workdir)

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (topological_nodes)

        // get the existing photo-voltaic installations keyed by terminal
        val solar = Solar (session, topological_nodes, storage_level)
        val sdata = solar.getSolarInstallations

        // determine the set of transformers to work on
        val transformers = if (null != trafos)
        {
            val selected = tdata.filter (x => trafos.contains (x.transformer.id))
            selected.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))
        }
        else
        {
            // do all low voltage power transformers
            val niederspannug = tdata.filter (td => td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))
        }

        val prepare = System.nanoTime ()
        log.info ("prepare: " + (prepare - topo) / 1e9 + " seconds")

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)
            val pf = new PowerFeeding (session)
            pf.threshold_calculation (initial, sdata, transformers, gridlabd, storage_level)
        }

        val houses = precalc_results.has

        val trafo_list: RDD[TransformerSet] = houses.keyBy (_.source_obj).groupByKey.join (transformers.keyBy (_.transformer_name)).values.map (_._2)
        log.info ("" + trafo_list.count + " transformers to process")

        val precalc = System.nanoTime ()
        log.info ("precalculation: " + (precalc - prepare) / 1e9 + " seconds")

        val vertices = precalc_results.vertices.filter(_.source_obj != null).keyBy(_.source_obj.trafo_id)
        val edges  = precalc_results.edges.filter(_._1 != null)
        val has = precalc_results.has.keyBy(_.source_obj)
        val grouped_precalc_results = vertices.groupWith(edges, has)

        val trafokreise = trafo_list.keyBy(_.transformer_name).leftOuterJoin(grouped_precalc_results)

        val raw = getCIMheader (gridlabd)
        val header = if ("" != raw)
            parseHeader (raw)
        else
            Header (Calendar.getInstance(), "generated header", "", "", Calendar.getInstance(), "")
        val t0 = header.scenarioTime

        def makeTrafokreis (start: Calendar) (arg: (String, (TransformerSet, Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])]))): Trafokreis =
        {
            arg match
            {
                case (tk, (tx, Some (x))) =>
                    Trafokreis (start, tk, tx, x._1, x._2, x._3, EinspeiseleistungOptions ())
                case _ =>
                    null
            }
        }
        val filtered_trafos = trafokreise.filter(_._2._2.isDefined).map (makeTrafokreis (t0))
        log.info ("filtered_trafos: " + filtered_trafos.count)
        val terminals = session.sparkContext.getPersistentRDDs.filter(_._2.name == "Terminal").head._2.asInstanceOf[RDD[Terminal]]
        generate (gridlabd, filtered_trafos)

        // rename to the created date
        val format = new java.text.SimpleDateFormat("yyyyMMdd")
        val timestamp = format.format (header.created.getTime)
        rename (gridlabd, timestamp)

        log.info ("finished " + trafo_list.count + " trafokreis")

        trafo_list.count
    }
}
