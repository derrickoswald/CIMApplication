package ch.ninecode.cim.cimweb

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.connector.CIMFunction.Return

final case class GetFileFunction (path: String) extends CIMWebFunction
{
    override def getReturnType: Return = Return.String

    override def executeString (spark: SparkSession): String =
    {
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

    override def toString: String = s"${super.toString} (path=$path)"
}
