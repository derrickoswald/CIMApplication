package ch.ninecode.sc

import java.io.File

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util._

import org.scalatest.BeforeAndAfter

class NonradialSuite extends SCTestBase with BeforeAndAfter
{
    val log: Logger = LoggerFactory.getLogger (getClass)

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
        session: SparkSession =>

            val filename = FILE_DEPOT + FILENAME1


            val customOptions = Map[String, String] (
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
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
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
        session: SparkSession =>

            val filename = FILE_DEPOT + FILENAME2
            val customOptions = Map[String, String] (
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
                default_short_circuit_impedance_min = Complex (0.0, 20.166666666666667), // purely reactive
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

            near (results.filter (_.equipment == "USR0001").first ().low_sk, 2624837.249756107)
            near (results.filter (_.equipment == "USR0002").first ().low_sk, 6196605.977290054)
            near (results.filter (_.equipment == "USR0003").first ().low_sk, 6169913.155864568)
    }
}
