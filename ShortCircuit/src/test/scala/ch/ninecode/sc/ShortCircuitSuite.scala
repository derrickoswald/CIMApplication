package ch.ninecode.sc

import java.util.HashMap
import java.util.Map
import java.io.File

import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.BeforeAndAfter

import ch.ninecode.cim.CIMNetworkTopologyProcessor

class ShortCircuitSuite
    extends
        SparkSuite
    with
        BeforeAndAfter
{
    val FILE_DEPOT = "data/"

    val FILENAME1 = "Beispiel zur Ermittlung der Kurzschlussleistung.rdf"
    val FILENAME2 = "Beispiel zur Ermittlung der Kurzschlussleistung mit EquivalentInjection.rdf"
    val FILENAME3 = "sak_sample.rdf"
    val FILENAME4 = "sak_sample_ganged.rdf"
    val FILENAME5 = "sak_sample_parallel.rdf"
    val FILENAME6 = "sak_sample_complex_parallel.rdf"
    val FILENAME7 = "sak_sample_complex2_parallel.rdf"
    val FILENAME8 = "ibw_cim_export.rdf"
    val FILENAME9 = "fuse_no_sample.rdf"
    val FILENAME10 = "fuse_nc_sample.rdf"

    before
    {
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME1).exists)
            new Unzip ().unzip (FILE_DEPOT + "Beispiel zur Ermittlung der Kurzschlussleistung.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + "Beispiel zur Ermittlung der Kurzschlussleistung mit EquivalentInjection.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME3).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME4).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample_ganged.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME5).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample_parallel.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME6).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample_complex_parallel.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME7).exists)
            new Unzip ().unzip (FILE_DEPOT + "sak_sample_complex2_parallel.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME8).exists)
            new Unzip ().unzip (FILE_DEPOT + "ibw_cim_export.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME9).exists)
            new Unzip ().unzip (FILE_DEPOT + "fuse_no_sample.zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME10).exists)
            new Unzip ().unzip (FILE_DEPOT + "fuse_nc_sample.zip", FILE_DEPOT)
    }

    test ("DACHCZ")
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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                default_supply_network_short_circuit_power = 600.0e6,
                default_supply_network_short_circuit_impedance = Complex (0.0, 20.166666666666667), // purely reactive
                trafos = FILE_DEPOT + "Beispiel zur Ermittlung der Kurzschlussleistung.transformers")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "L2_node_2_topo")
            assert (0 < consumer.count (), "L2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = results.filter (_.node == "L2_node_1_topo")
            assert (0 < busbar.count (), "L2_node_1 not found")
            val sc = busbar.first ()
            assert (Math.abs (sc.sk - 8.98e6) < 5e3, "expected 8.98MVA")
    }

    test ("DACHCZ with EquivalentInjection")
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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "Beispiel zur Ermittlung der Kurzschlussleistung.transformers")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "L2_node_2_topo")
            assert (0 < consumer.count (), "L2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = results.filter (_.node == "L2_node_1_topo")
            assert (0 < busbar.count (), "L2_node_1 not found")
            val sc = busbar.first ()
            assert (Math.abs (sc.sk - 8.98e6) < 5e3, "expected 8.98MVA")
    }

    test ("SAK Spreadsheet")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME3

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "sak_sample.transformers",
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.r - 0.19521016) < 0.0005, "expected r=195mΩ")
            assert (Math.abs (data.x - 0.05195) < 0.0005, "expected x=52mΩ")
            assert (Math.abs (data.ik - 595) < 0.5, "expected ik1polig=595A")
            assert (Math.abs (data.ik3pol - 1086) < 0.5, "expected ik3polig=1086A")
            // I'm not sure why SAK uses ik3pol (which is scaled bx cmax) to calculate Sk
            assert (Math.abs (data.sk * sc_options.cmax - 0.752e6) < 5e3, "expected 0.752MVA")
    }

    test ("SAK Spreadsheet Ganged")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME4

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "sak_sample_ganged.transformers",
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.r - 193.36016e-3) < 0.0005, "expected r=193mΩ")
            assert (Math.abs (data.x - 46.45e-3) < 0.0005, "expected x=46mΩ")
            assert (Math.abs (data.ik - 601) < 0.5, "expected ik1polig=601A")
            assert (Math.abs (data.ik3pol - 1103) < 0.5, "expected ik3polig=1103A")
            // I'm not sure why SAK uses ik3pol (which is scaled bx cmax) to calculate Sk
            assert (Math.abs (data.sk * sc_options.cmax - 0.764e6) < 5e3, "expected 0.764MVA")
    }

    test ("SAK Spreadsheet Parallel")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME5

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "sak_sample.transformers",
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.r - 162.55141e-3) < 0.0005, "expected r=163mΩ")
            assert (Math.abs (data.x - 37.1e-3) < 0.0005, "expected x=37mΩ")
            assert (Math.abs (data.ik - 746) < 0.5, "expected ik1polig=746A")
            assert (Math.abs (data.ik3pol - 1316) < 0.5, "expected ik3polig=1316A")
            // I'm not sure why SAK uses ik3pol (which is scaled bx cmax) to calculate Sk
            assert (Math.abs (data.sk * sc_options.cmax - 0.912e6) < 5e3, "expected 0.912MVA")
    }

    test ("Complex Parallel")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME6

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "sak_sample.transformers",
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (null != data.errors)
            assert (data.errors.contains (ScError (true, "non-radial network detected through Line2").toString))
    }

    test ("Complex 2 Parallel")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME7

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                trafos = FILE_DEPOT + "sak_sample.transformers",
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (null != data.errors)
            assert (data.errors.contains (ScError (true, "non-radial network detected from Line1_node_2_topo to Line_A_node_2_topo").toString))
    }

    test ("IBW")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME8

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                cmax = 1.0,
                cmin = 1.0,
                cosphi = 1.0,
                starting_ratio = 1.0)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer1 = results.filter (_.node == "HAS9754_topo")
            assert (0 < consumer1.count (), "HAS9754_topo not found")
            val data1 = consumer1.first ()
            assert (Math.abs (data1.ik - 740) < 0.5, "expected ik1polig=740A")
            assert (Math.abs (data1.ik3pol - 1400) < 0.5, "expected ik3polig=1400A")
            assert (Math.abs (data1.sk - 0.970e6) < 5e3, "expected sk=0.970MVA")
            assert (Math.abs (data1.motor_3ph_max_med / 400.0 - 48.39) < 0.5, "expected maxanlaufstrom=48A")

            val consumer2 = results.filter (_.node == "HAS9753_topo")
            assert (0 < consumer2.count (), "HAS9753_topo not found")
            val data2 = consumer2.first ()
            assert (Math.abs (data2.motor_3ph_max_med / 400.0 - 258.5) < 0.5, "expected maxanlaufstrom=259A")
    }

    test ("normalOpen=true Fuse")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME9

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line_3_node_2_topo")
            assert (0 < consumer.count (), "Line_3_node_2_topo not found")
            val data = consumer.first ()
            assert (Math.abs (data.ik - 812) < 0.5, "expected ik1polig=812A")
            assert (Math.abs (data.ik3pol - 1465) < 0.5, "expected ik3polig=1465A")
            // I'm not sure why SAK uses ik3pol (which is scaled bx cmax) to calculate Sk
            assert (Math.abs (data.sk * sc_options.cmax - 1.015e6) < 5e3, "expected sk=1.015MVA")
            assert (0 == results.filter (!_.errors.isEmpty).count, "expected no errors")
    }

    test ("normalOpen=false open=true Fuse")
    {
        session: SparkSession ⇒

            val filename = FILE_DEPOT + FILENAME10

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false).persist (StorageLevel.MEMORY_AND_DISK_SER)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // short circuit calculations
            val sc_options = ShortCircuitOptions (
                cmax = 0.95,
                cmin = 0.95)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val results = shortcircuit.run ()
            results.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "result"
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

            val consumer = results.filter (_.node == "Line_3_node_2_topo")
            assert (0 < consumer.count (), "Line_3_node_2_topo not found")
            val data = consumer.first ()
            assert (Math.abs (data.ik - 812) < 0.5, "expected ik1polig=812A")
            assert (Math.abs (data.ik3pol - 1465) < 0.5, "expected ik3polig=1465A")
            // I'm not sure why SAK uses ik3pol (which is scaled bx cmax) to calculate Sk
            assert (Math.abs (data.sk * sc_options.cmax - 1.015e6) < 5e3, "expected sk=1.015MVA")
            assert (0 == results.filter (!_.errors.isEmpty).count, "expected no errors")
            // if the transformer impedances are removed from the sample file, this command yields the same results:
            // spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/ShortCircuit-2.11-2.2.0-2.4.0-jar-with-dependencies.jar --logging "INFO" --netz "0.0 + 0.0j" --trafoz "0.01375 + 0.05312j" --cmax 0.95 --cmin 0.95 "hdfs://sandbox:8020/fuse_nc_sample.rdf"
            // except there is a warning "NONFATAL: transformer has no impedance value, using default 0.01375+0.05312j"
    }
}
