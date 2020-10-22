package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.hadoop.security.AccessControlException
import org.apache.spark.sql.SparkSession

case class ListFilesFunction (path: String, debug: Boolean) extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val root: Path = new Path(hdfs.getUri.toString, path)
        val temp: String = root.toString
        val prefix: String = if (path.endsWith("/")) if (temp.endsWith("/")) temp else s"$temp/" else temp
        // form the response
        val files = Json.createArrayBuilder
        val response = Json.createObjectBuilder
            .add("filesystem", uri.toString)
            .add("root", prefix)
        if (debug)
        {
            val configuration = Json.createObjectBuilder
            for (pair <- hdfs_configuration.asScala)
                configuration.add(pair.getKey, pair.getValue)
            val _ = response.add("configuration", configuration)
        }
        // read the list of files
        val _ = try
        {
            if (hdfs.exists(root))
            {
                val statuses: Array[FileStatus] = hdfs.listStatus(root)
                for (fs <- statuses)
                {
                    val name: String = fs.getPath.toString
                    val permission: FsPermission = fs.getPermission
                    val file = Json.createObjectBuilder
                        .add("path", if (name.startsWith(prefix)) name.substring(prefix.length) else name)
                        .add("size", fs.getLen)
                        .add("modification_time", fs.getModificationTime)
                        .add("access_time", fs.getAccessTime)
                        .add("group", fs.getGroup)
                        .add("owner", fs.getOwner)
                        .add("permission", permission.toString)
                        .add("replication", fs.getReplication)
                        .add("block_size", fs.getBlockSize)
                        .add("is_directory", fs.isDirectory)
                        .add("is_sym_link", fs.isSymlink)
                    files.add(file)
                }
            }
            else
                response.add("error", "path %s does not exist".format(root.toString))
        }
        catch
        {
            case access: AccessControlException =>
                response.add("error", access.getMessage) // Permission denied: user=root, access=READ_EXECUTE, inode=\"/tmp\":derrick:derrick:drwx-wx-wx
        }
        response.add("files", files).build
    }

    override def toString: String = s"${super.toString} (path=$path, debug=$debug)"
}
