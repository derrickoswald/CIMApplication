package ch.ninecode.cim.cimweb

import javax.json.Json

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.hadoop.io.Text

import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.connector.CIMFunction.Return

case class ExportFunction (island: String, path: String = null) extends CIMWebFunction
{
    jars = Array (jarForObject (this))

    override def getReturnType: Return = Return.String

    override def executeString (spark: SparkSession): String =
    {
        val export = new CIMExport (spark)
        val f = if (null == path) "/tmp/" + island + "/" + island + ".rdf" else if (path.startsWith ("/")) path else "/" + path
        export.exportIsland (island, f)
        val file = new Path (hdfs.getUri.toString, f)
        // ToDo: handle files bigger than 2GB
        val size = hdfs.getFileStatus (file).getLen.toInt
        if (null == path)
            // read the file
            try
            {
                val data = hdfs.open (file)
                val bytes = new Array[Byte] (size)
                data.readFully (0, bytes)
                Text.decode (bytes, 0, size)
            }
            catch
            {
                case e: Exception =>
                    e.getMessage
            }
        else
        {
            val result = Json.createObjectBuilder
            result.add ("path", file.toString)
            result.add ("size", size)
            result.build.toString
        }
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is ExportFunction (island = %s)".format (island))
        sb.toString
    }
}
