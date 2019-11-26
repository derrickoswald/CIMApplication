package ch.ninecode.sc

import java.io.File
import java.util.HashMap
import java.util.Map

import org.scalatest.BeforeAndAfter

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.Complex

class NonradialSuite extends SparkSuite with BeforeAndAfter
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    val FILE_DEPOT = "data/"

    val FILENAME1 = "DemoData.rdf"
    val FILENAME2 = "three_winding_non-radial.rdf"

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME1).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME1.replace (".rdf", ".zip"), FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME2.replace (".rdf", ".zip"), FILE_DEPOT)
    }

    after
    {
        new File (FILE_DEPOT + FILENAME1).delete
        new File (FILE_DEPOT + FILENAME2).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME1

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String]().asInstanceOf [Map[String, String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

            val tns = session.sparkContext.getPersistentRDDs.filter (_._2.name == "TopologicalNode")
            val ele = if (tns.isEmpty || tns.head._2.isEmpty)
            {
                // identify topological nodes
                val ntp = CIMNetworkTopologyProcessor (session)
                val e = ntp.process (
                    CIMTopologyOptions (
                        identify_islands = true,
                        force_retain_switches = Unforced,
                        force_retain_fuses = ForceTrue,
                        default_switch_open_state = false,
                        debug = true,
                        storage = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
                ).persist (StorageLevel.MEMORY_AND_DISK_SER)
                println (e.count () + " elements")

                val topo = System.nanoTime ()
                println ("topology: " + (topo - read) / 1e9 + " seconds")
            }
            else
                elements

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
                base_temperature = 20.0,
                low_temperature = 20.0,
                workdir = "./results/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            // output SQLite database
            Database.store (sc_options)(results)

            val string = results.sortBy (_.tx).map (_.csv (sc_options.cmin))
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))
    }

    test ("Three Winding Transformer with non-radial network")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME2

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new HashMap[String, String]().asInstanceOf [Map[String, String]]
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

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
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
                base_temperature = 20.0,
                low_temperature = 20.0,
                workdir = "./results/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            // output SQLite database
            Database.store (sc_options)(results)

            val string = results.sortBy (_.tx).map (_.csv (sc_options.cmin))
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0001").first ().low_sk == 0.0, "USER001 power")
            assert (results.filter (_.equipment == "USR0001").first ().errors.contains (ScError (false, true, "%s transformer windings for edge %s".format (3, "TX0001")).toString), "USER001 errors")
            assert (results.filter (_.equipment == "USR0002").first ().low_sk == 0.0, "USER002 power")
            assert (results.filter (_.equipment == "USR0002").first ().errors.contains (ScError (false, true, "%s transformer windings for edge %s".format (3, "TX0001")).toString), "USER002 errors")
            assert (results.filter (_.equipment == "USR0003").first ().low_sk == 0.0, "USER003 power")
            assert (results.filter (_.equipment == "USR0003").first ().errors.contains (ScError (false, true, "%s transformer windings for edge %s".format (3, "TX0001")).toString), "USER003 errors")
    }
}
