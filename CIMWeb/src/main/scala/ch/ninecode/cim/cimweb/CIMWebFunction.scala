package ch.ninecode.cim.cimweb

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder

import javax.json.Json
import javax.json.JsonStructure

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMFunction.Return

abstract class CIMWebFunction extends CIMFunction
{
    override def getReturnType: Return = Return.JSON

    var jars: Array[String] = new Array[String] (0)

    def setJars (newjars: Array[String]): Unit = jars = newjars

    override def getJars: Array[String] = jars

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = s"/tmp/${ Random.nextInt (99999999) }.jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (s"${ret}ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    // build a file system configuration, including core-site.xml
    def hdfs_configuration: Configuration =
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
    def uri: URI = FileSystem.getDefaultUri (hdfs_configuration)
    // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

    def hdfs: FileSystem = FileSystem.get (uri, hdfs_configuration)

    override def executeResultSet (spark: SparkSession): Dataset[Row] =
    {
        val schema = StructType (Seq (StructField (name = "error", dataType = StringType, nullable = false)))
        spark.sqlContext.createDataFrame (Seq (Row ("execute called on wrong method signature")).asJava, schema)
    }

    override def executeString (spark: SparkSession): String =
        "error: execute called on wrong method signature"

    override def executeJSON (spark: SparkSession): JsonStructure =
        Json.createObjectBuilder.add ("error", "execute called on wrong method signature").build

    override def toString: String =
    {
        val ret = getReturnType match
        {
            case Return.Dataset => "executeResultSet"
            case Return.String => "executeString"
            case Return.JSON => "executeJSON"
        }
        s"${getReturnType.toString} $ret  (session) [${jars.mkString (",")}]"
    }
}
