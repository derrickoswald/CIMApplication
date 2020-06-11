package ch.ninecode.sm

import java.util.HashMap

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.testutil.TestUtil

class SmartMeterSuite extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list)

    def readFile (context: SQLContext, filename: String, use_topolocial_nodes: Boolean): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String]()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", use_topolocial_nodes.toString)
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        context.read.format ("ch.ninecode.cim").options (options).load (files: _*)
    }

    test ("Basic")
    {
        session: SparkSession =>
            println ("placeholder")
    }
}
