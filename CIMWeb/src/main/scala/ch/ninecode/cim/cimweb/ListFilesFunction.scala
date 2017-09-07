package ch.ninecode.cim.cimweb

import java.io.{File, StringWriter}
import java.net.URI
import java.util
import java.util.{HashMap, Map}
import javax.json._
import javax.json.stream.JsonGenerator
import javax.ws.rs.core.MediaType

import ch.ninecode.cim.connector.CIMFunction.Return
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileStatus, FileSystem, Path}
import org.apache.spark.sql.{Dataset, Row, SparkSession}

case class ListFilesFunction (path: String, debug: Boolean) extends CIMWebFunction
{
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

    override def getReturnType: Return = Return.String

    override def getMimeType: String = MediaType.APPLICATION_JSON

    override def execute (spark: SparkSession): Dataset[Row] =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def execute (spark: SparkSession, mime_type: String): String =
    {
        // build a file system configuration, including core-site.xml
        val hdfs_configuration: Configuration = new Configuration ()
        if (null == hdfs_configuration.getResource ("core-site.xml"))
        {
            val hadoop_conf: String = System.getenv ("HADOOP_CONF_DIR")
            if (null != hadoop_conf)
            {
                val site: Path = new Path (hadoop_conf, "core-site.xml")
                val f: File = new File (site.toString)
                if (f.exists && !f.isDirectory)
                    hdfs_configuration.addResource (site)
            }
        }

        // get the file system
        val uri: URI = FileSystem.getDefaultUri (hdfs_configuration)
        // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))
        val hdfs: FileSystem = FileSystem.get (uri, hdfs_configuration)
        val root: Path = new Path (hdfs.getUri.toString, path)
        // form the response
        val response = Json.createObjectBuilder
        response.add ("filesystem", uri.toString)
        response.add ("root", root.toString)
        if (debug)
        {
            val configuration = Json.createObjectBuilder
            val i1: util.Iterator[Map.Entry[String, String]] = hdfs_configuration.iterator ()
            while (i1.hasNext)
            {
                val pair: Map.Entry[String, String] = i1.next
                val key: String = pair.getKey
                val value: String = pair.getValue
                configuration.add (key, value)
            }
            response.add ("configuration", configuration)
            val environment = Json.createObjectBuilder
            val env: util.Map[String, String] = System.getenv
            val i2: util.Iterator[String] = env.keySet.iterator ()
            while (i2.hasNext)
            {
                val key = i2.next
                val value: String = env.get (key)
                environment.add (key, value)
            }
            response.add ("environment", environment)
        }
        // read the list of files
        val statuses: Array[FileStatus] = hdfs.listStatus (root)
        val files = Json.createArrayBuilder
        val prefix: String = root.toString
        for (fs <- statuses)
        {
            var path: String = fs.getPath.toString
            if (path.startsWith (prefix))
                path = path.substring (prefix.length)
            val file = Json.createObjectBuilder
            file.add ("path", path)
            file.add ("length", fs.getLen)
            file.add ("modification_time", fs.getModificationTime)
            file.add ("access_time", fs.getAccessTime)
            file.add ("is_directory", fs.isDirectory)
            files.add (file)
        }
        response.add ("files", files)
        jsonString (response.build);
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (path=")
        sb.append (path)
        sb.append (", debug=")
        sb.append (debug)
        sb.append (")")
        sb.toString
    }
}
