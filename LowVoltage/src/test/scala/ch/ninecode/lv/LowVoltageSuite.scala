package ch.ninecode.lv

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.Element
import ch.ninecode.net.Net
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.Util

class LowVoltageSuite extends TestUtil
{
    override val classesToRegister: Array[Class[_]] = Array.concat (CIMClasses.list, GridLABD.classes, Net.classes, Util.classes)
    def readFile (session: SparkSession, filename: String): RDD[Element] =
    {
        implicit val spark: SparkSession = session
        val files = filename.split (",")
        val options = Map[String, String](
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.make_edges" -> "false",
            "ch.ninecode.cim.do_join" -> "false",
            "ch.ninecode.cim.do_topo" -> "false",
            "ch.ninecode.cim.do_topo_islands" -> "false")
        val elements: DataFrame = session.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        println (s"${elements.count} elements")
        get[Element]("Elements")
    }

    test ("Basic")
    {
        session: SparkSession =>
            println ("placeholder")
    }
}
