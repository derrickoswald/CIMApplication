package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.stream.JsonGenerator
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission

import scala.collection.JavaConverters._
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    date_format.setCalendar (calendar)

    /**
     * Get the scheme for the working directory.
     */
    val workdir_scheme: String =
    {
        val uri = new URI (options.workdir)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme
    }

    /**
     * Get the path component of the working directory.
     */
    val workdir_path: String =
    {
        val uri = new URI (options.workdir)
        if (null == uri.getPath)
            "/"
        else
            uri.getPath
    }

    /**
     * Get just the URI for the working directory.
     */
    val workdir_uri: String =
    {
        val uri = new URI (options.workdir)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/"
    }


    def read (rdf: String, reader_options: Map[String,String])
    {
        log.info ("""reading "%s"""".format (rdf))
        val start = System.nanoTime ()
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (rdf)
        log.info (elements.count () + " elements")
        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")
    }

    def dump (obj: JsonObject): Unit =
    {
        val o = obj.asScala
        val strings = o.map (x ⇒ x._1 + "=" + x._2.toString)
        log.info (strings.mkString (" "))
    }

    def stringify (resultset: Seq[JsonObject]): String =
    {
        val array = Json.createArrayBuilder
        for (i ← resultset.indices)
            array.add (resultset(i))
        val string = new StringWriter
        val properties = new util.HashMap[String, AnyRef](1)
        properties.put (JsonGenerator.PRETTY_PRINTING, "true")
        val writer = Json.createWriterFactory (properties).createWriter (string)
        writer.write (array.build)
        writer.close ()
        string.toString
    }

    def destringify (string: String): Seq[JsonObject] =
    {
        try
            Json.createReader (new StringReader (string)).readArray match
            {
                case obj: JsonArray ⇒
                    obj.getValuesAs (classOf [JsonObject]).asScala
                case _ ⇒
                    log.error ("""not a JsonArray""")
                    Seq()
            }
        catch
        {
            case je: JsonException ⇒
                log.error (""" string could not be parsed as JSON (%s)""".format (je.getMessage))
                Seq()
        }
    }

    def input (player: SimulationPlayer): SimulationPlayer =
    {
        log.info ("""executing "%s" as %s""".format (player.title, player.rdfquery))
        val query = SimulationSparkQuery (session, player.rdfquery)
        val resultset = query.execute ()
        player.copy (jsons = stringify (resultset))
    }

    def output (recorder: SimulationRecorder): SimulationRecorder =
    {
        log.info ("""executing "%s" as %s""".format (recorder.title, recorder.query))
        val query = SimulationSparkQuery (session, recorder.query)
        val resultset = query.execute ()
        recorder.copy (jsons = stringify (resultset))
    }

    def queryplayers (job: SimulationJob): SimulationJob =
    {
        val players = job.players.map (input)
        job.copy (players = players)
    }

    def queryrecorders (job: SimulationJob): SimulationJob =
    {
        val recorders = job.recorders.map (output)
        job.copy (recorders = recorders)
    }

    def using[T <: Closeable, R](resource: T)(block: T => R): R =
    {
        try { block (resource) }
        finally { resource.close () }
    }

    // make string like: 2017-07-18 00:00:00 UTC,0.4,0.0
    def format (obj: JsonObject): String =
    {
        var time = 0L
        var real = 0.0
        var imag = 0.0
        val o = obj.asScala
        o.foreach (
            x ⇒
                x._1 match
                {
                    case "time" ⇒ time = x._2.asInstanceOf[JsonNumber].longValue
                    case "real" ⇒ real = x._2.asInstanceOf[JsonNumber].doubleValue
                    case "imag" ⇒ imag = x._2.asInstanceOf[JsonNumber].doubleValue
                }
        )
        date_format.format (time) + "," + real + "," + imag
    }

    def write_player_csv (name: String, text: String): Unit =
    {
        if ((workdir_scheme == "file") || (workdir_scheme == "")) // local[*]
        {
            val file = new File (options.workdir + name)
            file.getParentFile.mkdirs
            if (null != text)
                using (new PrintWriter (file, "UTF-8"))
                {
                    writer =>
                        writer.write (text)

                }
        }
        else
        {
            val file = new Path (options.workdir + name)
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (workdir_uri), hdfs_configuration)
            hdfs.mkdirs (file.getParent, new FsPermission("ugoa-rwx"))
            hdfs.setPermission (file.getParent, new FsPermission("ugoa-rwx")) // "-"  WTF?
            if (null != text)
            {
                val bytes = text.getBytes ("UTF-8")
                val out = hdfs.create(file)
                out.write(bytes)
                out.close()
            }
        }
    }

    def generate_player_csv (player: SimulationPlayer, date: String, start: String, end: String): SimulationPlayer =
    {
        val range = "date = '%s' and time >= '%s' and time < '%s'".format (date, start, end)
        val jsons = destringify (player.jsons)
        val items = jsons.map (x ⇒
            {
                val json = x.asScala
                val substitutions: Array[String] = player.bind.map (y ⇒ json(y).asInstanceOf[JsonString].getString)
                val sql = player.cassandraquery.format (substitutions: _*) + " and " + range + " order by time allow filtering"
                log.info ("""executing "%s" as %s""".format (player.title, sql))
                val query = SimulationCassandraQuery (session, sql)
                val resultset: Seq[JsonObject] = query.execute ()
                val text =
                    if (0 == resultset.length)
                    {
                        log.warn ("""no records found for "%s" as %s""".format (player.title, sql))
                        // substitute zero player
                        // "1970-01-01 00:00:00,0.0,0.0"
                        val empty: Seq[JsonObject] =
                        {
                            val b = Json.createObjectBuilder ()
                            b.add ("time", 0L)
                            b.add ("real", 0.0)
                            b.add ("imag", 0.0)
                            List (b.build ())
                        }
                        empty.map (format).mkString ("\n")
                    }
                    else
                        resultset.map (format).mkString ("\n")
                val name = "input_data/" + json("mrid").asInstanceOf[JsonString].getString + "_" + date + ".csv"
                write_player_csv (name, text)
                val z = Json.createObjectBuilder ()
                json.foreach (x ⇒ z.add (x._1, x._2))
                z.add ("file", name)
                z.add ("count", resultset.length)
                z.build ()
            }
        )
        player.copy (jsons = stringify (items))
    }

    def generate_player (player: SimulationPlayer, start: String, end: String): SimulationPlayer =
    {
// ToDo: convert to java.util.Calendar and select (datestart dateend]
//        "interval": {
//            "start": "2017-07-18T00:00:00.000+0000",
//            "end": "2017-07-19T00:00:00.000+0000"
//        },
        val date = start.substring (0, start.indexOf ("T"))
        generate_player_csv (player, date, start, end)
    }

    def generate_players (job: SimulationJob): SimulationJob =
    {

        val players = job.players.map (player ⇒ generate_player (player, job.interval("start"), job.interval("end")))
        job.copy (players = players)
    }

    def execute (job: SimulationJob): Unit =
    {
        log.info ("""executing simulation job "%s"""".format (job.name))
        job.players.foreach (x ⇒ destringify (x.jsons).foreach (dump))
        job.recorders.foreach (x ⇒ destringify (x.jsons).foreach (dump))
    }

    def process (batch: Seq[SimulationJob]): Unit =
    {
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        read (ajob.cim, ajob.cimreaderoptions)
        val newbatch = batch.map (queryplayers)
        val notherbatch = newbatch.map (generate_players)
        val finalbatch = notherbatch.map (queryrecorders)
        val executors = Math.max (1, session.sparkContext.getExecutorMemoryStatus.keys.size - 1)
        val simulations = session.sparkContext.parallelize (finalbatch, executors)
        simulations.foreach (execute)
    }

    def run (): Unit =
    {
        var jobs = SimulationJob.getAll (options)
        // organize by same RDF and same options
        var batches = jobs.groupBy (job ⇒ job.cim + job.optionString)
        batches.values.foreach (process)
    }
}

object Simulation
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.sim.Simulation],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationRecorder]
        )
    }
}
