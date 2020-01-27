package ch.ninecode.sc

import java.io.File

import org.scalatest.BeforeAndAfter

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.Complex

class TransformerSuite extends SparkSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"

    val FILENAME1 = "voltage_regulator.rdf"
    val FILENAME2 = "three_winding_transformer.rdf"
    val FILENAME3 = "subtransmission.rdf"

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME1).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME1.replace (".rdf", ".zip"), FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME2.replace (".rdf", ".zip"), FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME3).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME3.replace (".rdf", ".zip"), FILE_DEPOT)
    }

    after
    {
        new File (FILE_DEPOT + FILENAME1).delete
        new File (FILE_DEPOT + FILENAME2).delete
        new File (FILE_DEPOT + FILENAME3).delete
    }

    test ("Voltage Regulator")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME1

            val start = System.nanoTime
            val files = filename.split (",")
            val options = Map[String, String] (
                "path" -> filename)

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

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

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
                base_temperature = 20.0,
                low_temperature = 20.0)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val string = results.sortBy (_.tx).map (_.csv (sc_options.cmin))
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0002").first ().errors.exists (_.startsWith ("INVALID")), "USR0002 should be invalid")
            assert (results.filter (_.equipment == "USR0003").first ().errors.exists (_.startsWith ("INVALID")), "USR0003 should be invalid")
            assert (results.filter (_.equipment == "USR0004").first ().errors.exists (_.startsWith ("INVALID")), "USR0004 should be invalid")
    }

    test ("Three Winding Transformer")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME2

            val start = System.nanoTime
            val files = filename.split (",")
            val options = Map[String, String] (
                "path" -> filename)

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

            val string = results.sortBy (_.tx).map (_.csv (sc_options.cmin))
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0001").first ().errors.isEmpty, "USR0001 should be valid (TX0001)")
            assert (results.filter (_.equipment == "USR0002").first ().errors.isEmpty, "USR0002 should be valid (TX0001)")
            assert (results.filter (_.equipment == "USR0003").first ().errors.forall (_.startsWith ("computed by load-flow")), "USR0003 should be valid")
            assert (results.filter (_.equipment == "USR0004").first ().errors.exists (_.startsWith ("computed by load-flow")), "USR0004 should be valid")
            assert (results.filter (_.equipment == "USR0005").first ().errors.exists (_.startsWith ("computed by load-flow")), "USR0005 should be valid")
    }

    test ("Subtransmission")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME3

            val start = System.nanoTime
            val files = filename.split (",")
            val options = Map[String, String] (
                "path" -> filename)

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

            val string = results.sortBy (_.tx).map (_.csv (sc_options.cmin))
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (near (results.filter (_.equipment == "USR0001").first ().high_ik, 200.528465600727))
            assert (near (results.filter (_.equipment == "USR0002").first ().high_ik, 170.281577472793))
    }
}
