package ch.ninecode.sc

import java.io.File

import org.scalatest.BeforeAndAfter
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.util.Complex

class TransformerSuite extends SCTestBase with BeforeAndAfter
{
    val FILENAME1 = "voltage_regulator"
    val FILENAME2 = "three_winding_transformer"
    val FILENAME3 = "subtransmission"

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME3.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME3.zip", FILE_DEPOT)
    }

    after
    {
        deleteRecursive (new File (s"$FILE_DEPOT$FILENAME1.rdf"))
        deleteRecursive (new File (s"$FILE_DEPOT$FILENAME2.rdf"))
        deleteRecursive (new File (s"$FILE_DEPOT$FILENAME3.rdf"))
    }

    test ("Voltage Regulator")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            readCIMElements (session, filename)

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667) // purely reactive
                ,
                low_temperature = 20.0)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val string = results.sortBy (_.tx).map (_.csv (sc_options))
            val csv = string.collect
            println (s"results: ${csv.length}")
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0002").first ().errors.exists (_.startsWith ("INVALID")),
                "USR0002 should be invalid")
            assert (results.filter (_.equipment == "USR0003").first ().errors.exists (_.startsWith ("INVALID")),
                "USR0003 should be invalid")
            assert (results.filter (_.equipment == "USR0004").first ().errors.exists (_.startsWith ("INVALID")),
                "USR0004 should be invalid")
    }

    test ("Three Winding Transformer")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME2.rdf"

            val customOptions = Map [String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
            readCIMElements (session, filename, options = customOptions)


            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667) // purely reactive
                ,
                low_temperature = 20.0,
                workdir = "./results/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val string = results.sortBy (_.tx).map (_.csv (sc_options))
            val csv = string.collect
            println (s"results: ${csv.length}")
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0001")
                .first ().errors.isEmpty, "USR0001 should be valid (TX0001)")
            assert (results.filter (_.equipment == "USR0002")
                .first ().errors.isEmpty, "USR0002 should be valid (TX0001)")
            assert (results.filter (_.equipment == "USR0003")
                .first ().errors.forall (_.startsWith ("computed by load-flow")), "USR0003 should be valid")
            assert (results.filter (_.equipment == "USR0004")
                .first ().errors.exists (_.startsWith ("computed by load-flow")), "USR0004 should be valid")
            assert (results.filter (_.equipment == "USR0005")
                .first ().errors.exists (_.startsWith ("computed by load-flow")), "USR0005 should be valid")
    }

    test ("Subtransmission")
    {
        session: SparkSession =>

            val filename = s"$FILE_DEPOT$FILENAME3.rdf"
            val customOptions = Map [String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
            readCIMElements (session, filename, options = customOptions)

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_short_circuit_power_max = 600.0e6,
                default_short_circuit_impedance_max = Complex (0.0, 20.166666666666667), // purely reactive
                default_short_circuit_power_min = 600.0e6,
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667) // purely reactive
                ,
                low_temperature = 20.0,
                workdir = "./results/")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()

            val string = results.sortBy (_.tx).map (_.csv (sc_options))
            val csv = string.collect
            println (s"results: ${csv.length}")
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            near (results.filter (_.equipment == "USR0001").first ().high_ik, 213.83700143897838)
            near (results.filter (_.equipment == "USR0002").first ().high_ik, 181.53370928588038)
    }
}
