package ch.ninecode.mfi

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.util.TestUtil
import com.sun.rowset.CachedRowSetImpl
import org.apache.spark.sql.SparkSession

class MFITestBase extends TestUtil
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list, GridLABD.classes, Einspeiseleistung.classes)
    val FILE_DEPOT = "data/"

    def runMFI (session: SparkSession, options: EinspeiseleistungOptions): Unit =
    {
        val begin = System.nanoTime ()
        val eins = Einspeiseleistung (session, options)
        val count = eins.run ()
        val total = System.nanoTime ()
        println ("einspeiseleistung: " + (total - begin) / 1e9 + " seconds für " + count + " trafokreise")
    }

    def readFile (session: SparkSession, filename: String): Unit =
    {
        val begin = System.nanoTime ()

        val files = filename.split (",")
        val options = Map [String, String](
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo" -> "true",
            "ch.ninecode.cim.do_topo_islands" -> "true",
            "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
            "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
            "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
            "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
            "ch.ninecode.cim.default_switch_open_state" -> "false")

        val elements = session.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        println (elements.count () + " elements")

        val read = System.nanoTime ()
        println ("read : " + (read - begin) / 1e9 + " seconds")
    }

    def getMaxSimulation (databasePath: String): String =
    {
        val query = "select max(simulation) from results"
        val result = querySQLite (databasePath, query)
        assert (result.next, "no results found")
        result.getString (1)
    }


    def checkResults (result: CachedRowSetImpl, max: Double, reason: String, details: String): Unit =
    {
        val house = result.getString ("House")
        val maximum = result.getDouble ("Maximum")
        near (maximum, max, 1000.0, s"maximum for $house is $maximum instead of $max")
        assert (result.getString ("Reason") == reason, s"reason for $house")
        assert (result.getString ("Details").startsWith (details), s"details for $house")
    }
}
