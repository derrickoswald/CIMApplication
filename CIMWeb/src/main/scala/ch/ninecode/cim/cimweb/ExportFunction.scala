package ch.ninecode.cim.cimweb

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.connector.CIMFunction.Return

case class ExportFunction (island: String) extends CIMWebFunction
{
    jars = Array (jarForObject (this))

    override def getReturnType: Return = Return.String

    override def executeString (spark: SparkSession): String =
    {
        val export = new CIMExport (spark)
        val file: Path = new Path (hdfs.getUri.toString, "/simulation/" + island + "/" + island + ".rdf")
        export.exportIsland (island, file.toString)
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
        sb.append (" is ExportFunction (island = %s)".format (island))
        sb.toString
    }
}
