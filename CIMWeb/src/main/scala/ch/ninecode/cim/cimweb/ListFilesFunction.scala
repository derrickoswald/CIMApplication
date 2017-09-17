package ch.ninecode.cim.cimweb

import javax.json.Json

import scala.collection.JavaConversions._

import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.hadoop.security.AccessControlException
import org.apache.spark.sql.SparkSession

case class ListFilesFunction (path: String, debug: Boolean) extends CIMWebFunction
{
    override def execute (spark: SparkSession, mime_type: String): String =
    {
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
