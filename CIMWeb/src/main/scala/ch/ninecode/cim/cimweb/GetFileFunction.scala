package ch.ninecode.cim.cimweb

import java.io.File
import java.net.URI

import javax.ws.rs.core.MediaType

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return

case class GetFileFunction (path: String) extends CIMWebFunction
{
    override def getReturnType: Return = Return.String

    override def getMimeType: String = MediaType.APPLICATION_XML

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
        val file: Path = new Path (hdfs.getUri.toString, path)
        // read the file
        try
        {
            val data = hdfs.open (file)
            // ToDo: handle files bigger than 2GB
            val size = hdfs.getFileStatus (file).getLen.toInt
            val bytes = new Array[Byte] (size)
            data.readFully (0, bytes)
            Text.decode (bytes, 0, size)
        }
        catch
        {
            case e: Exception =>
                e.getMessage
        }
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (path=")
        sb.append (path)
        sb.append (")")
        sb.toString
    }
}
