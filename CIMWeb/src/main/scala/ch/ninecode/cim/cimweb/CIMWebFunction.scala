package ch.ninecode.cim.cimweb

import java.io.File
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.HashMap
import javax.json.Json
import javax.json.JsonStructure
import javax.json.JsonWriterFactory
import javax.json.stream.JsonGenerator
import javax.ws.rs.core.MediaType

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMFunction.Return

abstract class CIMWebFunction extends CIMFunction
{
    override def getReturnType: Return = Return.String

    override def getMimeType: String = MediaType.APPLICATION_JSON

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
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    // build a file system configuration, including core-site.xml
    lazy val hdfs_configuration: Configuration =
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
    lazy val uri: URI = FileSystem.getDefaultUri (hdfs_configuration)
    // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

    lazy val hdfs: FileSystem = FileSystem.get (uri, hdfs_configuration)

    /**
     *
     * @todo Eliminate the need to convert the JSON to a String and back
     */
    protected val FACTORY_INSTANCE: JsonWriterFactory =
    {
        val properties = new HashMap[String, Boolean](1)
        properties.put (JsonGenerator.PRETTY_PRINTING, true)
        Json.createWriterFactory (properties)
    }

    protected def getPrettyJsonWriterFactory: JsonWriterFactory = FACTORY_INSTANCE

    protected def jsonString (data: JsonStructure): String =
    {
        val string = new StringWriter
        val writer = getPrettyJsonWriterFactory.createWriter (string)
        writer.write (data)
        writer.close ()
        string.toString
    }


    override def execute (spark: SparkSession): Dataset[Row] =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def execute (spark: SparkSession, mime_type: String): String =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def toString: String =
    {
        val sb: StringBuilder = new StringBuilder
        sb.append (getReturnType.toString)
        sb.append (" execute (session")
        sb.append (getReturnType match { case Return.Dataset => "" case Return.String => ", " + getMimeType })
        sb.append (") [")
        sb.append (jars.mkString (","))
        sb.append ("]")
        sb.toString
    }
}