package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.SparkSession

case class PutFileFunction (path: String, data: Array[Byte]) extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder
        response.add ("filesystem", uri.toString)
        val file: Path = new Path (hdfs.getUri.toString, path)
        response.add ("path", path)
        response.add ("size", data.length)
        // write the file
        try
        {
            hdfs.mkdirs (if (path.endsWith ("/")) file else file.getParent, new FsPermission("ugoa-rwx"))
            hdfs.setPermission (file.getParent, new FsPermission("ugoa-rwx")) // "-"  WTF?

            if (0 != data.length && !path.endsWith ("/"))
            {
                val out = hdfs.create (file)
                out.write (data)
                out.close ()
            }
        }
        catch
        {
            case e: Exception =>
                response.add ("error", e.getMessage)
        }
        response.build
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
