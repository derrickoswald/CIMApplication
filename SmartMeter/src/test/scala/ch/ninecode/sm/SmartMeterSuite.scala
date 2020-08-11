package ch.ninecode.sm

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import ch.ninecode.testutil.TestUtil

class SmartMeterSuite extends TestUtil
{
    def readFile (context: SQLContext, filename: String, use_topolocial_nodes: Boolean): DataFrame =
    {
        val files = filename.split (",")
        val options = Map [String, String](
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.make_edges" -> "false",
            "ch.ninecode.cim.do_join" -> "false",
            "ch.ninecode.cim.do_topo" -> use_topolocial_nodes.toString,
            "ch.ninecode.cim.do_topo_islands" -> "false")
        context.read.format ("ch.ninecode.cim").options (options).load (files: _*)
    }

    test ("Basic")
    {
        session: SparkSession =>
            println ("placeholder")
    }
}
