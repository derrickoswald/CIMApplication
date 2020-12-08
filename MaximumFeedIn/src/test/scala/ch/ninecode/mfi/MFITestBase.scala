package ch.ninecode.mfi

import com.sun.rowset.CachedRowSetImpl
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.Util

class MFITestBase extends TestUtil
{
    override val classesToRegister: Array[Class[_]] = Array.concat(
        CIMClasses.list,
        Einspeiseleistung.classes,
        GridLABD.classes,
        Net.classes,
        Util.classes)
    val FILE_DEPOT = "data/"

    def setFile (filename: String): CIMReaderOptions =
    {
        val default = EinspeiseleistungOptions().cim_options
        default.copy(files = Seq(filename))
    }

    def setFiles (filenames: Seq[String], dedup: Boolean = false): CIMReaderOptions =
    {
        val default = EinspeiseleistungOptions().cim_options
        default.copy(dedup = dedup, files = filenames)
    }

    def runMFI (session: SparkSession, options: EinspeiseleistungOptions): Unit =
    {
        // read the file
        val start = System.nanoTime()
        val elements = session
            .read
            .format("ch.ninecode.cim")
            .options(options.cim_options.toMap)
            .load(options.cim_options.files: _*)
            .count
        info(s"$elements elements")
        val read = System.nanoTime()
        info(s"read: ${(read - start) / 1e9} seconds")
        val begin = System.nanoTime()
        val eins = Einspeiseleistung(session, options)
        val count = eins.run()
        val total = System.nanoTime()
        info(s"einspeiseleistung: ${(total - begin) / 1e9} seconds for $count trafokreise")
    }

    def readFile (session: SparkSession, filename: String): Unit =
    {
        time("read : %s seconds")
        {
            val files = filename.split(",")
            val options = Map[String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
                "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
                "ch.ninecode.cim.default_switch_open_state" -> "false")

            val elements = session.read.format("ch.ninecode.cim").options(options).load(files: _*)
            info(s"${elements.count()} elements")
        }
    }

    def getMaxSimulation (databasePath: String): String =
    {
        val query = "select max(simulation) from results"
        val result = querySQLite(databasePath, query)
        assert(result.next, "no results found")
        result.getString(1)
    }


    def checkResults (result: CachedRowSetImpl, max: Double, reason: String, details: String): Unit =
    {
        val house = result.getString("House")
        val maximum = result.getDouble("Maximum")
        near(maximum, max, 1000.0, s"maximum for $house is $maximum instead of $max")
        assert(result.getString("Reason") == reason, s"reason for $house")
        assert(result.getString("Details").startsWith(details), s"details for $house")
    }
}
