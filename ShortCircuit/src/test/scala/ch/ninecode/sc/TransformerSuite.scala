package ch.ninecode.sc

import java.util.HashMap
import java.util.Map
import java.io.File

import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.BeforeAndAfter

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl.Complex

class TransformerSuite
extends
    SparkSuite
with
    BeforeAndAfter
{
    val FILE_DEPOT = "data/"

    val FILENAME1 = "voltage_regulator.rdf"
    val FILENAME2 = "three_winding_transformer.rdf"

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME1).exists)
            new Unzip ().unzip (FILE_DEPOT + "voltage_regulator.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + "three_winding_transformer.zip", FILE_DEPOT)
    }

    test ("Voltage Regulator")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME1

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
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
            results.cache ()

            val string = results.sortBy (_.tx).map (_.csv)
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0002").first ().errors.forall (!_.startsWith ("INVALID")), "USR0002 should be valid")
            assert (results.filter (_.equipment == "USR0003").first ().errors.forall (!_.startsWith ("INVALID")), "USR0003 should be valid")
            assert (results.filter (_.equipment == "USR0004").first ().errors.exists (_.startsWith ("INVALID")), "USR0004 should be invalid")
    }

    test ("Three Winding Transformer")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME2

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
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
            results.cache ()

            val string = results.sortBy (_.tx).map (_.csv)
            val csv = string.collect
            println ("results: " + csv.length)
            println (ScResult.csv_header)
            for (i <- csv.indices)
                println (csv (i))

            assert (results.filter (_.equipment == "USR0001").first ().errors.forall (!_.startsWith ("INVALID")), "USR0001 should be valid")
            assert (results.filter (_.equipment == "USR0002").first ().errors.forall (!_.startsWith ("INVALID")), "USR0002 should be valid")
            assert (results.filter (_.equipment == "USR0003").first ().errors.forall (!_.startsWith ("INVALID")), "USR0003 should be valid")
            assert (results.filter (_.equipment == "USR0004").first ().errors.exists (_.startsWith ("INVALID")), "USR0004 should be invalid")
            assert (results.filter (_.equipment == "USR0005").first ().errors.exists (_.startsWith ("INVALID")), "USR0005 should be invalid")
    }
}
