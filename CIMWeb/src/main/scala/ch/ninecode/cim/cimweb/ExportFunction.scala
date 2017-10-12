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
        val file: Path = new Path ("/tmp/" + island + "/" + island + ".rdf")
        export.exportIsland (island, file.toString)
        scala.io.Source.fromFile (file.toString, "utf-8").getLines.mkString ("\n")
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is ExportFunction (island = %s)".format (island))
        sb.toString
    }
}
