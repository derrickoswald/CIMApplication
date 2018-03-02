package ch.ninecode.sc

import java.io.File
import java.util.HashMap
import java.util.Map

import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMNetworkTopologyProcessor

class PrivateSuite
    extends
        SparkSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"

    test ("Basic")
    {
        session: SparkSession ⇒

            val filename = PRIVATE_FILE_DEPOT + "bkw_cim_export_sias_current_20161220_Haelig_no_EEA7355_or_EEA5287" + ".rdf"

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (false)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (description = "Basic", trafos = PRIVATE_FILE_DEPOT + "trafo.txt")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - topo) / 1e9 + " seconds")

            // output SQLite database
            Database.store (sc_options) (results.collect)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            println ("total: " + (db - start) / 1e9 + " seconds")
    }

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // add EquivalentInjection elements based on the csv file
            val infos = ShortCircuitInfo (session, StorageLevel.MEMORY_AND_DISK_SER)
            val equivalents = infos.getShortCircuitInfo (PRIVATE_FILE_DEPOT + "KS_Leistungen.csv")
            val export = new CIMExport (session)
            export.export (equivalents, PRIVATE_FILE_DEPOT + "KS_Leistungen.rdf", "generated from " + "KS_Leistungen.csv")
            infos.merge (equivalents)

            val inj = System.nanoTime ()
            println ("equivalent injection: " + (inj - topo) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (description = "Extended")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val sc = System.nanoTime ()
            println ("short circuit: " + (sc - inj) / 1e9 + " seconds")

            // output SQLite database
            Database.store (sc_options) (results.collect)

            val db = System.nanoTime ()
            println ("database: " + (db - sc) / 1e9 + " seconds")

            println ("total: " + (db - start) / 1e9 + " seconds")
    }
/*
    test ("CKW")
    {
        session: SparkSession ⇒

            val filename = PRIVATE_FILE_DEPOT + "CIM_Export_CKW_Stripe1.rdf"

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (false)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions ()
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            // write output to file and console
            val output = PRIVATE_FILE_DEPOT + "result"
            val string = results.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
            {
                val h = csv (i)
                println (h)
            }

            // output SQLite database
            Database.store ("test", sc_options) (results.collect)
    }
*/
}