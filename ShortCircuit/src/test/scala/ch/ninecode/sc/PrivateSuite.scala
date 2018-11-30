package ch.ninecode.sc

import java.util.HashMap
import java.util.Map

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced

class PrivateSuite extends SparkSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"

    test ("Extended")
    {
        session: SparkSession ⇒

            val filename = PRIVATE_FILE_DEPOT + "bkw_cim_export_schopfen_all" + ".rdf"

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.make_edges", "false")
            options.put ("ch.ninecode.cim.do_join", "false")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
            elements.persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) /  1e9 + " seconds")

            // identify topological nodes
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = false,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = true,
                    storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

// TBD: how to execute this ahead of time once and apply it to the test
//            // add EquivalentInjection elements based on the csv file
//            val infos = ShortCircuitInfo (session, StorageLevel.MEMORY_AND_DISK_SER)
//            val equivalents = infos.getShortCircuitInfo (PRIVATE_FILE_DEPOT + "KS_Leistungen.csv")
//            val export = new CIMExport (session)
//            export.export (equivalents, PRIVATE_FILE_DEPOT + "KS_Leistungen.rdf", "generated from " + "KS_Leistungen.csv")
//            infos.merge (equivalents)

            val inj = System.nanoTime ()
            println ("equivalent injection: " + (inj - topo) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (description = "Extended")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - inj) / 1e9 + " seconds")

            // output SQLite database
            Database.store (sc_options) (results)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            println ("total: " + (db - start) / 1e9 + " seconds")
    }

    test ("CKW")
    {
        session: SparkSession ⇒

            val filename = PRIVATE_FILE_DEPOT + "CIM_Export_CKW_EMM-Rueeggisignen.rdf"

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.make_edges", "false")
            options.put ("ch.ninecode.cim.do_join", "false")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
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
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (workdir = "./target")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - topo) / 1e9 + " seconds")

            // output SQLite database
            Database.store (sc_options) (results)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            println ("total: " + (db - start) / 1e9 + " seconds")
    }

    test ("avoid invalid elements because of bad cable values in 230 voltage level")
    {
        session: SparkSession ⇒

            val filename = PRIVATE_FILE_DEPOT + "CIM_Export_CKW_HOH-Sonderschule.rdf"

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.make_edges", "false")
            options.put ("ch.ninecode.cim.do_join", "false")
            options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
            options.put ("ch.ninecode.cim.do_topo_islands", "false")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
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
            ).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (workdir = "./target")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - topo) / 1e9 + " seconds")

            val invalidElements = results.filter(sc => sc.errors.exists(_.contains("invalid element")))
            assert(invalidElements.count == 0, "result should not have 'invalid element'")

            val end = System.nanoTime ()
            println ("total: " + (end - start) / 1e9 + " seconds")
    }
}