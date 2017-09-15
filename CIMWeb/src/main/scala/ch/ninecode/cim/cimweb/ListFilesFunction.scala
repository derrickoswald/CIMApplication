package ch.ninecode.cim.cimweb

import java.io.File
import java.io.StringWriter
import java.net.URI
import java.util.HashMap
import javax.json.Json
import javax.json.JsonStructure
import javax.json.JsonWriterFactory
import javax.json.stream.JsonGenerator
import javax.ws.rs.core.MediaType

import scala.collection.JavaConversions._

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.hadoop.security.AccessControlException
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return

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
        val temp: String = root.toString
        val prefix: String = if (path.endsWith ("/")) if (temp.endsWith ("/")) temp else temp + "/" else temp
        response.add ("root", prefix)
        if (debug)
        {
            val configuration = Json.createObjectBuilder
            for (pair <- hdfs_configuration)
                configuration.add (pair.getKey, pair.getValue)
            response.add ("configuration", configuration)
        }
        // read the list of files
        val files = Json.createArrayBuilder
        try
        {
            val statuses: Array[FileStatus] = hdfs.listStatus (root)
            for (fs <- statuses)
            {
                val file = Json.createObjectBuilder
                val name: String = fs.getPath.toString
                file.add ("path", if (name.startsWith (prefix)) name.substring (prefix.length) else name)
                file.add ("size", fs.getLen)
                file.add ("modification_time", fs.getModificationTime)
                file.add ("access_time", fs.getAccessTime)
                file.add ("group", fs.getGroup)
                file.add ("owner", fs.getOwner)
                val permission: FsPermission = fs.getPermission
                file.add ("permission", permission.toString)
                file.add ("replication", fs.getReplication)
                file.add ("block_size", fs.getBlockSize)
                file.add ("is_directory", fs.isDirectory)
                file.add ("is_sym_link", fs.isSymlink)
                files.add (file)
            }
        }
        catch
        {
            case access: AccessControlException =>
                response.add ("error", access.getMessage) // Permission denied: user=root, access=READ_EXECUTE, inode=\"/tmp\":derrick:derrick:drwx-wx-wx
        }
        response.add ("files", files)
        jsonString (response.build)
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
