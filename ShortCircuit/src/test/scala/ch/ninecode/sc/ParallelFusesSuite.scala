package ch.ninecode.sc

import java.util.HashMap
import java.util.Map

import org.scalatest.BeforeAndAfter

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.spark.rdd.RDD

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced

class ParallelFusesSuite extends SparkSuite with BeforeAndAfter
{
    val FILE_DEPOT = "private_data/"

    val FILENAME1 = "ekz/EKZ_Testcase1_STA866.rdf"
    val FILENAME2 = "ekz/EKZ_Testcase2_STA654.rdf"
    val FILENAME3 = "ekz/EKZ_Testcase3_STA5573.rdf"
    val FILENAME4 = "ekz/EKZ_Testcase4_STA333.rdf"
    val FILENAME5 = "ekz/EKZ_Testcase5_STA1854.rdf"
    val FILENAME6 = "ekz/EKZ_Testcase6_STA4597.rdf"
    val FILENAME7 = "ekz/EKZ_Testcase7_STA883.rdf"
    val FILENAME8 = "ekz/EKZ_Testcase8_STA5804.rdf"
    val FILENAME9 = "ekz/EKZ_Testcase9_STA5813.rdf"
    val FILENAME10 = "ekz/EKZ_Testcase10_STA3310.rdf"
    val FILENAME11 = "ekz/EKZ_Testcase11_STA5934.rdf"
    val FILENAME12 = "ekz/EKZ_Testcase12_STA875.rdf"


    def processFile(session: SparkSession, filename: String): RDD[ScResult] = {
        val start = System.nanoTime
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")

        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*).persist (StorageLevel.MEMORY_AND_DISK_SER)
        println (elements.count + " elements")
        val read = System.nanoTime
        println ("read: " + (read - start) /  1e9 + " seconds")

        // identify topological nodes
        val ntp = CIMNetworkTopologyProcessor (session)
        val ele = ntp.process (
            CIMTopologyOptions (
                identify_islands = true,
                force_retain_switches = Unforced,
                force_retain_fuses = ForceTrue,
                default_switch_open_state = false,
                debug = true,
                storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
        )
        println (ele.count () + " elements")

        val topo = System.nanoTime ()
        println ("topology: " + (topo - read) / 1e9 + " seconds")

        // short circuit calculations
        val sc_options = ShortCircuitOptions (
            default_short_circuit_power_max = 600.0e6,
            base_temperature = 20.0,
            low_temperature = 60.0,
            high_temperature = 60.0,
            cmin = 0.95,
            cmax = 1.0,
            cosphi = 0.65,
            workdir="simulation/")
        val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
        val results = shortcircuit.run ().cache ()

        Database.store (sc_options) (results)
        results
    }

    def filterResults(results: RDD[ScResult], trafo: String): RDD[ScResult] = {
        results.filter(r => {r.fuses != null && r.fuses.nonEmpty && r.tx == trafo && r.equipment.startsWith("HAS") && r.fuseString.contains("+")})
    }

    def checkParallelFuse (fuses: RDD[ScResult], node: String, fuseString: String): Unit = {
        val filtered = fuses.filter(_.equipment == node)
        assert(filtered.count() == 1, "expected 1 element for: " + node)
        val value = filtered.first.fuseString
        assert(value == fuseString, "parallel fuse expected for " + node + ": " + fuseString)
    }

    test ("Testcase2")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME2
            val results = processFile(session, filename)
            val filtered_results = filterResults(results, "TRA532_TRA533")

            val ddd = results.filter (_.fuses  != null)

            assert(filtered_results.count == 58, "58 HAS with parallel fuses expected")
    }

    test ("Testcase6")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME6
            val results = processFile(session, filename)
            val filtered_results = filterResults(results, "TRA6864")

            assert(filtered_results.count == 5, "5 HAS with parallel fuses expected")
            assert(FData.hasMissingValues(filtered_results.filter (_.equipment == "HAS112021").first.fuses), "has missing fuse value (-1)")
            checkParallelFuse(filtered_results, "HAS112021", "630.0+630.0+-1.0")
    }

    test ("Testcase11")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME11
            val results = processFile(session, filename)
            val filtered_results = filterResults(results, "TRA8419")

            assert(filtered_results.count == 3, "3 HAS with parallel fuses expected")

            checkParallelFuse(filtered_results, "HAS69774", "315.0+315.0+250.0")
            checkParallelFuse(filtered_results, "HAS69773", "315.0+315.0+100.0")
            checkParallelFuse(filtered_results, "HAS106736", "315.0+315.0+40.0")
    }

    test ("Testcase12")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME12
            val results = processFile(session, filename)
            val filtered_results = filterResults(results, "TRA403")

            assert(filtered_results.count == 24, "24 HAS with parallel fuses expected")

            checkParallelFuse(filtered_results, "HAS17937", "630.0+630.0,400.0")
            checkParallelFuse(filtered_results, "HAS13669", "630.0+630.0,400.0+400.0,160.0")
            checkParallelFuse(filtered_results, "HAS13533", "630.0+630.0,400.0")
            checkParallelFuse(filtered_results, "HAS13523", "630.0+630.0,400.0+400.0,160.0")
            checkParallelFuse(filtered_results, "HAS13522", "630.0+630.0,400.0+400.0,250.0")
            checkParallelFuse(filtered_results, "HAS13521", "630.0+630.0,400.0+400.0,250.0")
            checkParallelFuse(filtered_results, "HAS13520", "630.0+630.0,400.0+400.0,250.0")
            checkParallelFuse(filtered_results, "HAS13518", "630.0+630.0,400.0")
            checkParallelFuse(filtered_results, "HAS13517", "630.0+630.0,400.0")
            checkParallelFuse(filtered_results, "HAS118740", "630.0+630.0,400.0")
            checkParallelFuse(filtered_results, "HAS115599", "630.0+630.0,250.0")
    }
}
