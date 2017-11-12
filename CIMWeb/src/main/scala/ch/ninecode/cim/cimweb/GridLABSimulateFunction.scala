package ch.ninecode.cim.cimweb

import java.io.StringReader
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonStructure

import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

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
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/input.txt " + workdir_path + "$FILE; " +
                    "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output.txt " + workdir_path + "$FILE; " +
                    "cat $FILE/$FILE.out; " +
                    "rm -rf $FILE; " +
                    "done < /dev/stdin")

        val out = files.pipe (gridlabd)
        out.map (check).fold ("")(combine)
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
                    val recorders = details.getJsonArray ("recorders")
                    val root = glm.substring (0, index2 + 1)
                    for (element: JsonObject ← recorders.getValuesAs (classOf[JsonObject]).asScala) // ToDo: more robust checking
                    {
                        val recorder_file = element.getString ("file", "")
                        hdfs.delete (new Path (root, recorder_file), false)
                    }
                    val message = solve (workdir_path, spark.sparkContext.parallelize (Array (file)))
                    if (message == "")
                        new RESTfulJSONResult (OK, "gridlab simulation ran").getJSON
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
