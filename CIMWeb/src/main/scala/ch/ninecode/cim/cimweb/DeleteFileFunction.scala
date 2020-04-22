package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession

final case class DeleteFileFunction (path: String) extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val file: Path = new Path (hdfs.getUri.toString, path)
        val response = Json.createObjectBuilder
            .add ("filesystem", uri.toString)
            .add ("path", path)
        // delete the file or directory
        val _ = try
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
        s"${super.toString} (path=$path)"
    }
}
