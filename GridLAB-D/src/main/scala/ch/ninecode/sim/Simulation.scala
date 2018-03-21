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

import scala.collection.JavaConverters._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar (calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    val just_date: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd")
    just_date.setCalendar (calendar)

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

    def queryplayers (island: String) (player: SimulationPlayer): SimulationPlayer =
    {
        val numbind = player.rdfquery.split ("%s").length - 1
        val sql = if (0 < numbind)
        {
            var ss = (for (i <- 1 to 2) yield island).toArray
            player.rdfquery.format (ss: _*)
        }
        else
            player.rdfquery
        log.info ("""executing "%s" as %s""".format (player.title, sql))
        val query = SimulationSparkQuery (session, sql)
        val resultset = query.execute ()
        player.copy (jsons = stringify (resultset))
    }

    def queryrecorders (island: String) (recorder: SimulationRecorder): SimulationRecorder =
    {
        val numbind = recorder.query.split ("%s").length - 1
        val sql = if (0 < numbind)
        {
            var ss = (for (i <- 1 to 2) yield island).toArray
            recorder.query.format (ss: _*)
        }
        else
            recorder.query
        log.info ("""executing "%s" as %s""".format (recorder.title, sql))
        val query = SimulationSparkQuery (session, sql)
        val resultset = query.execute ()
        recorder.copy (jsons = stringify (resultset))
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
        glm_date_format.format (time) + "," + real + "," + imag
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

    def generate_player_csv (player: SimulationPlayer, date: String, start: String, end: String): Seq[SimulationPlayer] =
    {
        log.info ("""generating "%s" date: %s [%s, %s)""".format (player.title, date, start, end))
        var ret = List[SimulationPlayer]()
        val range = "date = '%s' and time >= '%s' and time < '%s'".format (date, start, end)
        val jsons = destringify (player.jsons)
        jsons.foreach (
            x ⇒
            {
                val json = x.asScala
                val substitutions: Array[String] = player.bind.map (y ⇒ json(y).asInstanceOf[JsonString].getString)
                val sql = player.cassandraquery.format (substitutions: _*) + " and " + range + " order by time allow filtering"
                log.info ("""executing "%s" as %s""".format (player.title, sql))
                val query = SimulationCassandraQuery (session, sql)
                val resultset: Seq[JsonObject] = query.execute ()
                val count = resultset.length
                val set =
                    if (0 == count)
                    {
                        log.warn ("""no records found for "%s" as %s""".format (player.title, sql))
                        // substitute zero player
                        // "1970-01-01 00:00:00,0.0,0.0"
                        val b = Json.createObjectBuilder ()
                        b.add ("time", 0L)
                        b.add ("real", 0.0)
                        b.add ("imag", 0.0)
                        List (b.build ())
                    }
                    else
                        resultset
                val text = set.map (format).mkString ("\n")
                val name = "input_data/" + json("name").asInstanceOf[JsonString].getString + "_" + date + ".csv"
                write_player_csv (name, text)
                val n: SimulationPlayer = player.copy (jsons = stringify (Seq(x)), file = name, count = count)
                ret = ret :+ n
            }
        )
        ret
    }

    def generate_recorder_csv (recorder: SimulationRecorder, date: String, start: String, end: String): Seq[SimulationRecorder] =
    {
        log.info ("""resolving "%s" date: %s [%s, %s)""".format (recorder.title, date, start, end))
        var ret = List[SimulationRecorder]()
        val jsons = destringify (recorder.jsons)
        jsons.foreach (
            x ⇒
            {
                val json = x.asScala
                val name = "output_data/" + json("name").asInstanceOf[JsonString].getString + "_" + date + ".csv"
                val n: SimulationRecorder = recorder.copy (jsons = stringify (Seq(x)), file = name)
                ret = ret :+ n
            }
        )
        ret
    }

    def iso_parse (s: String): Calendar =
    {
        val ret = Calendar.getInstance ()
        ret.setTime (iso_date_format.parse (s))
        ret
    }

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    def make_tasks (job: SimulationJob): Seq[SimulationTask] =
    {
        log.info ("""preparing simulation job "%s"""".format (job.name))
        var ret = List[SimulationTask]()

        // get all transformer secondary TopologicalIsland names
        val sql =
            """select
              |    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, n.TopologicalIsland
              |from
              |    Terminal t,
              |    PowerTransformer p,
              |    TopologicalNode n
              |where
              |    t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID
              |and t.ACDCTerminal.sequenceNumber = 2
              |and t.TopologicalNode = n.IdentifiedObject.mRID""".stripMargin
        log.info ("""executing "%s"""".format (pack (sql)))
        val df = session.sql (sql)
        import session.implicits._
        val trafo_islands: Map[String, String] = df.map (row ⇒ (row.getString (0), row.getString (1))).collect.toMap
        job.transformers.foreach (
            transformer ⇒
            {
                // handle ganged transformers
                val names = transformer.split ("_").map (x ⇒ trafo_islands.getOrElse (x, null)).filter (_ != null)
                val island = if (0 == names.length) null else names(0)
                if (null == island)
                    log.error ("""topological island not found for transformer "%s" secondary""".format (transformer))
                else
                {
                    if (!names.forall (_ == island))
                        log.error ("""transformer "%s" has different topological islands (%s) on its secondary connections, using %s""".format (transformer, names.mkString (", "), island))

                    val players = job.players.map (queryplayers (island))
                    val recorders = job.recorders.map (queryrecorders (island))
                    // loop over all days
                    val start = iso_parse (job.interval("start"))
                    val startplus = start.clone.asInstanceOf[Calendar]
                    val end = iso_parse (job.interval("end"))
                    while (start.before (end))
                    {
                        // figure out the ending time as a midnight
                        startplus.add (Calendar.DAY_OF_MONTH, 1)
                        val next = just_date.format (startplus.getTime)
                        startplus.setTime (just_date.parse (next))
                        // set up for creating the CSV
                        val date = just_date.format (start.getTime)
                        val t0 = iso_date_format.format (start.getTime)
                        val t1 = iso_date_format.format (startplus.getTime)

                        val task = SimulationTask (
                            island,
                            start,
                            startplus,
                            players.flatMap (x ⇒ generate_player_csv (x, date, t0, t1)),
                            recorders.flatMap (x ⇒ generate_recorder_csv (x, date, t0, t1))
                        )
                        ret = ret :+ task
                        start.setTime (startplus.getTime)
                    }
                }
            }
        )
        ret
    }

    def execute (task: SimulationTask): Unit =
    {
        log.info (task.island + " from " + iso_date_format.format (task.start.getTime) + " to " + iso_date_format.format (task.end.getTime))
        task.players.foreach (x ⇒ { destringify (x.jsons).foreach (dump); log.info ( x.file + " " + x.count + " records ") })
        task.recorders.foreach (x ⇒ { destringify (x.jsons).foreach (dump); log.info ( x.file + " every " + x.interval + " seconds ") })
    }

    def process (batch: Seq[SimulationJob]): Unit =
    {
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        read (ajob.cim, ajob.cimreaderoptions)
        val tasks = batch.flatMap (make_tasks)
        val executors = Math.max (1, session.sparkContext.getExecutorMemoryStatus.keys.size - 1)
        val simulations = session.sparkContext.parallelize (tasks, executors)
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
            classOf[ch.ninecode.sim.SimulationCassandraQuery],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationRecorder],
            classOf[ch.ninecode.sim.SimulationSparkQuery],
            classOf[ch.ninecode.sim.SimulationTask]
        )
    }
}
