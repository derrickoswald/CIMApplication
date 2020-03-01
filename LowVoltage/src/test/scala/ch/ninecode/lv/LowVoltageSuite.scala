package ch.ninecode.lv

import java.util

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.Element
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util._

class LowVoltageSuite extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list, GridLABD.classes, Util.classes)
    def readFile (session: SparkSession, filename: String): RDD[Element] =
    {
        val files = filename.split (",")
        val options = new util.HashMap[String, String]()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "false")
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val elements: DataFrame = session.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        println (elements.count () + " elements")
        session.sparkContext.getPersistentRDDs.filter (_._2.name == "Elements").head._2.asInstanceOf [RDD[Element]]
    }

    test ("Basic")
    {
        session: SparkSession ⇒
            println ("placeholder")
    }
}
