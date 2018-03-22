package ch.ninecode.cim.cimweb

import java.io.File
import java.io.StringReader
import java.net.URI
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonStructure

import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.cimweb.RESTfulJSONResult.FAIL
import ch.ninecode.cim.connector.CIMFunction.Return

/**
 * Simulate a glm file and the associated input_data.
 *
 * @param simulation the name of a JSON file on HDFS with the simulation to run
 */
case class GridLABSimulateFunction (simulation: String) extends CIMWebFunction
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    jars = Array (jarForObject (this))

    override def getReturnType: Return = Return.JSON

    def fail (message: String) : JsonObject =
    {
        Json.createObjectBuilder.add ("status", FAIL).add ("message", message).add ("result", Json.createObjectBuilder).build
    }

    def readJSON (simulation: String): JsonObject =
    {
        val file: Path = new Path (hdfs.getUri.toString, simulation)
        // read the file
        try
        {
            val data = hdfs.open (file)
            val size = hdfs.getFileStatus (file).getLen.toInt
            val bytes = new Array[Byte] (size)
            data.readFully (0, bytes)
            val text = Text.decode (bytes, 0, size)
            try
                Json.createReader (new StringReader (text)).readObject match
                {
                    case obj: JsonObject ⇒ obj
                    case _ ⇒ fail ("%s does not contain a JsonObject".format (simulation))
                }
            catch
            {
                case je: JsonException ⇒
                    fail ("%s could not be parsed as JSON".format (simulation))
            }
        }
        catch
        {
            case e: Exception =>
                fail (e.getMessage)
        }
    }

    def check (input: String): String =
    {
        if (input.contains ("FATAL") || input.contains ("ERROR") || input.contains ("FAIL") || input.contains ("command not found"))
            "gridlabd failed, message is: %s".format (input)
        else
            ""
    }

    def combine (a: String, b: String): String =
    {
        a + (if ((a != "") && (b != "")) "\n" else "") + b
    }

    def solve (workdir_path: String, files: RDD[String]): String =
    {
        // assumes gridlabd is installed on every node:
        // download gridlabd (e.g. latest stable release https://sourceforge.net/projects/gridlab-d/files/gridlab-d/Last%20stable%20release/gridlabd-3.2.0-1.x86_64.rpm/download)
        // convert the rpm to a deb usig alien:
        //   sudo alien gridlabd_3.2.0-2_amd64.rpm
        // install on every node:
        //   sudo dpkg -i gridlabd_3.2.0-2_amd64.deb

        val gridlabd =
            Array[String](
                "bash",
                "-c",
                "while read line; do " +
                    "export FILE=$line; " +
                    "HDFS_DIR=${HADOOP_HDFS_HOME:-$HADOOP_HOME}; " +
                    "HADOOP_USER_NAME=$SPARK_USER; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyToLocal " + workdir_path + "$FILE $FILE; " +
                    "pushd $FILE; " +
                    "gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                    "cat output_data/* > output.txt; " +
                    "cat input_data/* > input.txt; " +
                    "popd; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/$FILE.out " + workdir_path + "$FILE/$FILE.out; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output_data/* " + workdir_path + "$FILE/output_data; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/input.txt " + workdir_path + "$FILE/input.txt; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output.txt " + workdir_path + "$FILE/output.txt; " +
                    "cat $FILE/$FILE.out; " +
                    "rm -rf $FILE; " +
                    "done < /dev/stdin")

        val out = files.pipe (gridlabd)
        out.map (check).fold ("")(combine)
    }

    case class Record (mrid: String, `type`: String, units: String, file: String)

    //        "recorders": [
    //        {
    //            "name": "OEV105_topo_voltage_recorder",
    //            "parent": "OEV105_topo",
    //            "property": "voltage",
    //            "unit": "Volts",
    //            "interval": 900,
    //            "file": "output_data/OEV105_topo_voltage.csv"
    //        },
    def to_record (root: String) (item: JsonObject): Record =
        Record (
            item.getString ("parent", ""),
            item.getString ("property", ""),
            item.getString ("unit", ""),
            new Path (root, item.getString ("file", "")).toString)

    def makeschema (spark: SparkSession): (String, String)=
    {
        val sql =
         """| create table if not exists cimapplication.simulated_value_by_day (
            |   mrid text,
            |   type text,
            |   date date,
            |   time timestamp,
            |   interval int,
            |   real_a double,
            |   imag_a double,
            |   real_b double,
            |   imag_b double,
            |   real_c double,
            |   imag_c double,
            |   units text,
            |   primary key ((mrid,type,date),time)
            | ) with clustering order by (time asc);""".stripMargin
        CassandraConnector (spark.sparkContext.getConf).withSessionDo (session => session.execute (sql))
        ("cimapplication", "simulated_value_by_day")
    }

    def read_one_phase (record: Record): Iterable[(String, String, String, String, Double, Double, String)] =
    {
        //  # file...... output_data/ABG89331_topo_voltage.csv
        //  # date...... Fri Dec  1 13:53:21 2017
        //  # user...... (null)
        //  # host...... (null)
        //  # target.... meter 229
        //  # trigger... (none)
        //  # interval.. 900
        //  # limit..... 0
        //  # timestamp,voltage_A.real,voltage_A.imag
        //  2017-07-18 00:00:00 UTC,+391.088,-11.7489
        //  2017-07-18 00:15:00 UTC,+391.088,-11.7489
        //  2017-07-18 00:30:00 UTC,+391.088,-11.7489
        //  2017-07-18 00:45:00 UTC,+391.088,-11.7489

        // build a file system configuration, including core-site.xml
        def _hdfs_configuration: Configuration =
        {
            val configuration = new Configuration ()
            if (null == configuration.getResource ("core-site.xml"))
            {
                val hadoop_conf: String = System.getenv ("HADOOP_CONF_DIR")
                if (null != hadoop_conf)
                {
                    val site: Path = new Path (hadoop_conf, "core-site.xml")
                    val f: File = new File (site.toString)
                    if (f.exists && !f.isDirectory)
                        configuration.addResource (site)
                }
            }
            configuration
        }

        // get the file system
        def _uri: URI = FileSystem.getDefaultUri (_hdfs_configuration)
        // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

        def _hdfs: FileSystem = FileSystem.get (uri, _hdfs_configuration)

        def read (path: String): String =
        {
            val file: Path = new Path (_hdfs.getUri.toString, path)
            // read the file
            try
            {
                val data = _hdfs.open (file)
                // ToDo: handle files bigger than 2GB
                val size = _hdfs.getFileStatus (file).getLen.toInt
                val bytes = new Array[Byte] (size)
                data.readFully (0, bytes)
                Text.decode (bytes, 0, size)
            }
            catch
            {
                case exception: Exception =>
                    "FAILURE: " + exception.getMessage + "\n" + exception.getStackTrace.map (_.toString).mkString ("\n")
            }
        }

        var i = 0
        read (record.file).split (sys.props ("line.separator")).filter (!_.startsWith ("#")).map (
            s ⇒
            {
                val cols = s.split (",")
                if (3 == cols.length)
                    (record.mrid, record.`type`, cols(0).substring (0, 10), cols(0).substring (0, 19), cols(1).toDouble, cols(2).toDouble, record.units)
                else
                {
                    i = i + 1
                    if (i < 60)
                        (record.mrid, "broken", "1970-01-01", "1970-01-01T00:00:%02d".format (i), 0.0, 0.0, s)
                    else if (i < 120)
                        (record.mrid, "broken", "1970-01-01", "1970-01-01T00:01:%02d".format (i), 0.0, 0.0, s)
                    else
                        (record.mrid, "broken", "1970-01-01", "1970-01-01T00:02:%02d".format (i), 0.0, 0.0, s)
                }
            }
        ).toIterable
    }

    def read_three_phase (record: Record): Iterable[(String, String, String, String, Double, Double, Double, Double, Double, Double, String)] =
    {
        // build a file system configuration, including core-site.xml
        def _hdfs_configuration: Configuration =
        {
            val configuration = new Configuration ()
            if (null == configuration.getResource ("core-site.xml"))
            {
                val hadoop_conf: String = System.getenv ("HADOOP_CONF_DIR")
                if (null != hadoop_conf)
                {
                    val site: Path = new Path (hadoop_conf, "core-site.xml")
                    val f: File = new File (site.toString)
                    if (f.exists && !f.isDirectory)
                        configuration.addResource (site)
                }
            }
            configuration
        }

        // get the file system
        def _uri: URI = FileSystem.getDefaultUri (_hdfs_configuration)
        // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

        def _hdfs: FileSystem = FileSystem.get (_uri, _hdfs_configuration)

        def read (path: String): String =
        {
            val file: Path = new Path (_hdfs.getUri.toString, path)
            // read the file
            try
            {
                val data = _hdfs.open (file)
                // ToDo: handle files bigger than 2GB
                val size = _hdfs.getFileStatus (file).getLen.toInt
                val bytes = new Array[Byte] (size)
                data.readFully (0, bytes)
                Text.decode (bytes, 0, size)
            }
            catch
            {
                case exception: Exception =>
                    "FAILURE: " + exception.getMessage + "\n" + exception.getStackTrace.map (_.toString).mkString ("\n")
            }
        }

        var i = 0
        read (record.file).split (sys.props ("line.separator")).filter (!_.startsWith ("#")).map (
            s ⇒
            {
                val cols = s.split (",")
                if (7 == cols.length)
                    (record.mrid, record.`type`, cols(0).substring (0, 10), cols(0).substring (0, 19), cols(1).toDouble, cols(2).toDouble, cols(3).toDouble, cols(4).toDouble, cols(5).toDouble, cols(6).toDouble, record.units)
                else
                {
                    i = i + 1
                    if (i < 60)
                        (record.mrid, "broken", "1970-01-01", "1970-01-01T00:00:%02d".format (i), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, s)
                    else
                        (record.mrid, "broken", "1970-01-01", "1970-01-01T00:01:%02d".format (i), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, s)
                }
            }
        ).toIterable
    }

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val details = readJSON (simulation)
        if (OK == details.getString ("status", OK))
        {
            val glm = details.getString ("glm", null)
            if ("" != glm)
            {
                val index2 = glm.lastIndexOf ("/")
                val index1 = glm.lastIndexOf ("/", index2 - 1)
                if ((-1 != index2) && (-1 != index2))
                {
                    val workdir_path = glm.substring (0, index1 + 1)
                    val file = glm.substring (index1 + 1, index2)
                    // erase all the recorder files
                    val root = glm.substring (0, index2 + 1)
                    val recorders = details.getJsonArray ("recorders").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
                    for (element: JsonObject ← recorders)
                    {
                        val recorder_file = element.getString ("file", "")
                        hdfs.delete (new Path (root, recorder_file), false)
                    }
                    log.info ("solving %s".format (file))
                    val message = solve (workdir_path, spark.sparkContext.parallelize (Array (file)))
                    if (message == "")
                    {
                        log.info ("making schema")
                        val schema = makeschema (spark)
                        log.info ("saving simulation results")
                        val executors = spark.sparkContext.getExecutorMemoryStatus.keys.size - 1
                        val tasks = spark.sparkContext.parallelize (recorders.map (to_record (root)), executors)
                        if (true) // one phase
                        {
                            val rdd = tasks.flatMap (read_one_phase)
                            rdd.saveToCassandra (schema._1, schema._2, SomeColumns ("mrid", "type", "date", "time", "real_a", "imag_a", "units"))
                        }
                        else
                        {
                            val rdd = tasks.flatMap (read_three_phase)
                            rdd.saveToCassandra (schema._1, schema._2, SomeColumns ("mrid", "type", "date", "time", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c", "units"))
                        }
                        log.info ("simulation succeeded")
                        new RESTfulJSONResult (OK, "gridlab simulation ran").getJSON
                    }
                    else
                        new RESTfulJSONResult (FAIL, message).getJSON
                }
                else
                    // note repeated name in directory and file name e.g."/STA206/SmokeTest/SmokeTest.glm"
                    // implies "/STA206/SmokeTest/input_data/*" and "/STA206/SmokeTest/output_data/ exist"
                    new RESTfulJSONResult (FAIL, "glm %s does not contain the directory, e.g. '/STA206/SmokeTest/SmokeTest.glm'".format (glm)).getJSON
            }
            else
                new RESTfulJSONResult (FAIL, "property 'glm' not found in simulation object").getJSON
        }
        else
            new RESTfulJSONResult (details.getString ("status"), details.getString ("message")).getJSON

    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is GridLABExportFunction (simulation = %s)".format (simulation))
        sb.toString
    }
}
