package ch.ninecode.sc

import java.io.BufferedOutputStream
import java.util.HashMap
import java.util.Map
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util
import java.util.zip.ZipInputStream

import scala.collection.JavaConverters._

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMNetworkTopologyProcessor


class ShortCircuitSuite
    extends
        FunSuite
    with
        BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val PRIVATE_FILE_DEPOT = "private_data/"

    val FILENAME1 = "Beispiel zur Ermittlung der Kurzschlussleistung.rdf"
    val FILENAME2 = "Beispiel zur Ermittlung der Kurzschlussleistung mit EquivalentInjection.rdf"
    val FILENAME3 = "sak_sample.rdf"
    val FILENAME4 = "sak_sample_ganged.rdf"
    val FILENAME5 = "sak_sample_parallel.rdf"
    val FILENAME6 = "sak_sample_complex_parallel.rdf"
    val FILENAME7 = "ibw_cim_export.rdf"

    type FixtureParam = SparkSession

    /**
     * This utility extracts files and directories of a standard zip file to
     * a destination directory.
     *
     * @author www.codejava.net
     *
     */
    class Unzip
    {
        /**
         * Extracts a zip file specified by the file to a directory.
         *
         * The directory will be created if does not exist.
         *
         * @param file The Zip file.
         * @param directory The directory to extract it to
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        def unzip (file: String, directory: String): Unit =
        {
            val dir = new File (directory)
            if (!dir.exists)
                dir.mkdir
            val zip = new ZipInputStream (new FileInputStream (file))
            var entry = zip.getNextEntry
            // iterates over entries in the zip file
            while (null != entry)
            {
                val path = directory + entry.getName
                if (!entry.isDirectory)
                // if the entry is a file, extracts it
                    extractFile (zip, path)
                else
                // if the entry is a directory, make the directory
                    new File (path).mkdir
                zip.closeEntry ()
                entry = zip.getNextEntry
            }
            zip.close ()
        }

        /**
         * Extracts a zip entry (file entry).
         *
         * @param zip The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            val bos = new BufferedOutputStream (new FileOutputStream (path))
            val bytesIn = new Array[Byte](4096)
            var read = -1
            while ({ read = zip.read (bytesIn); read != -1 })
                bos.write (bytesIn, 0, read)
            bos.close ()
        }
    }


    /**
     * Add to the process environment.
     *
     * @see https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
     * @param newenv The list of key value pairs to add.
     */
    protected def setEnv (newenv: java.util.HashMap[String, String]): Unit =
    {
        try
        {
            val env: util.Map[String, String] = System.getenv
            for (cl <- Class.forName ("java.util.Collections").getDeclaredClasses)
            {
                if ("java.util.Collections$UnmodifiableMap" == cl.getName)
                {
                    val field = cl.getDeclaredField ("m")
                    field.setAccessible (true)
                    val obj = field.get (env)
                    val map = obj.asInstanceOf[java.util.Map[String, String]]
                    map.putAll (newenv)
                }
            }
        }
        catch
        {
            case e: Exception =>
                e.printStackTrace ()
        }
    }

    /**
     * Set SPARK_LOCAL_IP to the IP address in dotted-quad format (e.g. 1.2.3.4) if it isn't set.
     *
     * Avoids "Set SPARK_LOCAL_IP if you need to bind to another address" warning message.
     * @see findLocalInetAddress: https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/util/Utils.scala
     */
    def setLocalIP (): Unit =
    {
        if (null == System.getenv ("SPARK_LOCAL_IP"))
        {
            val address = InetAddress.getLocalHost
            if (address.isLoopbackAddress)
            {
                // Address resolves to something like 127.0.1.1, which happens on Debian; try to find
                // a better address using the local network interfaces
                // getNetworkInterfaces returns ifs in reverse order compared to ifconfig output order
                // on unix-like system. On windows, it returns in index order.
                // It's more proper to pick ip address following system output order.
                val interfaces = NetworkInterface.getNetworkInterfaces.asScala.toSeq
                val windows = System.getProperty ("os.name").startsWith ("Windows") // underlying operating system is Windows

                for (ni <- if (windows) interfaces else interfaces.reverse)
                {
                    val addresses = ni.getInetAddresses.asScala.filterNot (addr => addr.isLinkLocalAddress || addr.isLoopbackAddress).toSeq
                    if (addresses.nonEmpty)
                    {
                        val addr = addresses.find (_.isInstanceOf [Inet4Address]).getOrElse (addresses.head)
                        // because of Inet6Address.toHostName may add interface at the end if it knows about it
                        val ip = InetAddress.getByAddress (addr.getAddress)
                        // We've found an address that looks reasonable!
                        val newenv = new java.util.HashMap[String, String] ()
                        newenv.put ("SPARK_LOCAL_IP", ip.getHostAddress)
                        newenv.put ("SPARK_HOME", "/home/derrick/spark/spark-2.2.0-bin-hadoop2.7")
                        setEnv (newenv)
                    }
                }
            }
        }
    }

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
            new Unzip ().unzip (FILE_DEPOT + "ibw_cim_export.zip", FILE_DEPOT)
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // set the env to add the real local address
        setLocalIP ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuitSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register ShortCircuit analysis classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.sc.Complex],
            classOf[ch.ninecode.sc.Graphable],
            classOf[ch.ninecode.sc.HouseConnection],
            classOf[ch.ninecode.sc.Impedanzen],
            classOf[ch.ninecode.sc.ScEdge],
            classOf[ch.ninecode.sc.ScNode],
            classOf[ch.ninecode.sc.ShortCircuit],
            classOf[ch.ninecode.sc.ShortCircuitInfo],
            classOf[ch.ninecode.sc.ShortCircuitOptions],
            classOf[ch.ninecode.sc.StartingTrafos],
            classOf[ch.ninecode.sc.TData],
            classOf[ch.ninecode.sc.Transformers],
            classOf[ch.ninecode.sc.TransformerSet]))
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        session.sparkContext.setLogLevel ("INFO") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop // clean up the fixture
    }

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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
            val ele = ntp.process (false)
            println (ele.count () + " elements")

            val topo = System.nanoTime ()
            println ("topology: " + (topo - read) / 1e9 + " seconds")

            // add EquivalentInjection elements based on the csv file
            val infos = ShortCircuitInfo (session, StorageLevel.MEMORY_AND_DISK_SER)
            val equivalents = infos.getShortCircuitInfo (PRIVATE_FILE_DEPOT + "KS_Leistungen.csv")
            val export = new CIMExport (session)
            export.export (equivalents, PRIVATE_FILE_DEPOT + "KS_Leistungen.rdf", "generated from " + "KS_Leistungen.csv")
            infos.merge (equivalents)

            // short circuit calculations
            val sc_options = ShortCircuitOptions (trafos = PRIVATE_FILE_DEPOT + "trafo.txt")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val house_connection = shortcircuit.run ()

            // write output to file and console
            val output = PRIVATE_FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            // output SQLite database
            Database.store ("test", sc_options) (house_connection.collect)
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
                default_supply_network_short_circuit_angle = 90.0,
                trafos = FILE_DEPOT + "Beispiel zur Ermittlung der Kurzschlussleistung.transformers")
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "L2_node_2_topo")
            assert (0 < consumer.count (), "L2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = house_connection.filter (_.node == "L2_node_1_topo")
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
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "L2_node_2_topo")
            assert (0 < consumer.count (), "L2_node_2 not found")
            val data = consumer.first ()
            assert (Math.abs (data.sk - 2.13e6) < 5e3, "expected 2.13MVA")

            val busbar = house_connection.filter (_.node == "L2_node_1_topo")
            assert (0 < busbar.count (), "L2_node_1 not found")
            val sc = busbar.first ()
            assert (Math.abs (sc.sk - 8.98e6) < 5e3, "expected 8.98MVA")
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
            val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"), true, true)
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

            // short circuit calculations
            val sc_options = ShortCircuitOptions ()
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val house_connection = shortcircuit.run ()

            // write output to file and console
            val output = PRIVATE_FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            // output SQLite database
            Database.store ("test", sc_options) (house_connection.collect)
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
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "Line2_node_2_topo")
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
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "Line2_node_2_topo")
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
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "Line2_node_2_topo")
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
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer = house_connection.filter (_.node == "Line2_node_2_topo")
            assert (0 < consumer.count (), "Line2_node_2 not found")
            val data = consumer.first ()
            assert (null != data.errors)
            assert (data.errors.contains (ScError (true, "non-radial network detected through Line2")))
    }

    test ("IBW")
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
                cmax = 1.0,
                cmin = 1.0,
                cosphi = 1.0,
                starting_ratio = 1.0)
            val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
            val house_connection = shortcircuit.run ()
            house_connection.cache ()

            // write output to file and console
            val output = FILE_DEPOT + "/result"
            val string = house_connection.sortBy (_.tx).map (_.csv)

            val path = new File (output)
            FileUtils.deleteQuietly (path)
            string.saveAsTextFile (output)

            val results = string.collect
            println ("results: " + results.length)
            println (HouseConnection.csv_header)
            for (i <- results.indices)
            {
                val h = results (i)
                println (h)
            }

            val consumer1 = house_connection.filter (_.node == "HAS9754_topo")
            assert (0 < consumer1.count (), "HAS9754_topo not found")
            val data1 = consumer1.first ()
            assert (Math.abs (data1.ik - 755) < 0.6, "expected ik1polig=755A")
            assert (Math.abs (data1.ik3pol - 1455) < 0.5, "expected ik3polig=1455A")
            assert (Math.abs (data1.sk - 1.008e6) < 5e3, "expected sk=1.008MVA")
            assert (Math.abs (data1.motor_3ph_max_med / 400.0 - 48.86) < 0.5, "expected maxanlaufstrom=49A")

            val consumer2 = house_connection.filter (_.node == "HAS9753_topo")
            assert (0 < consumer2.count (), "HAS9753_topo not found")
            val data2 = consumer2.first ()
            assert (Math.abs (data2.motor_3ph_max_med / 400.0 - 272.8) < 0.5, "expected maxanlaufstrom=273A")
    }
}
