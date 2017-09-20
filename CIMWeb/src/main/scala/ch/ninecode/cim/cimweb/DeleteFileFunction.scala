package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession

case class DeleteFileFunction (path: String) extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder
        response.add ("filesystem", uri.toString)
        val file: Path = new Path (hdfs.getUri.toString, path)
        response.add ("path", path)
        // delete the file or directory
        try
        {
            hdfs.delete (file, true)
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
