package ch.ninecode.mfi

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.sql.DriverManager
import java.util
import java.util.zip.ZipInputStream

import scala.collection.JavaConverters._
import org.scalatest.fixture.FunSuite
import org.scalatest.BeforeAndAfter

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

class GridLABDSuite extends FunSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData.rdf"
    val FILENAME2 = "parallel_cable_sample.rdf"
    val FILENAME3 = "three_winding_transformer.rdf"

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
         * @param file      The Zip file.
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
         * @param zip  The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            val bos = new BufferedOutputStream (new FileOutputStream (path))
            val bytesIn = new Array[Byte](4096)
            var read = -1
            while (
            {
                read = zip.read (bytesIn);
                read != -1
            })
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
                    val map = obj.asInstanceOf [java.util.Map[String, String]]
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
     *
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
                        val newenv = new java.util.HashMap[String, String]()
                        newenv.put ("SPARK_LOCAL_IP", ip.getHostAddress)
                        newenv.put ("SPARK_HOME", "/home/derrick/spark/spark-2.3.2-bin-hadoop2.7")
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
            new Unzip ().unzip (FILE_DEPOT + FILENAME1.replace (".rdf", ".zip"), FILE_DEPOT)
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME2).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME2.replace (".rdf", ".zip"), FILE_DEPOT)
        // unpack the zip files
        if (!new File (FILE_DEPOT + FILENAME3).exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME3.replace (".rdf", ".zip"), FILE_DEPOT)
    }

    after
    {
        new File (FILE_DEPOT + FILENAME1).delete
        new File (FILE_DEPOT + FILENAME2).delete
        new File (FILE_DEPOT + FILENAME3).delete
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Einspeiseleistung.classes)
        configuration.set ("spark.ui.showConsoleProgress", "false")

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    /**
     * Test for equality of precalculation and load-flow feed in values.
     */
    test ("Basic")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = FILE_DEPOT + FILENAME1

            val options = EinspeiseleistungOptions (
                verbose = true,
                // cim_reader_options = mutable.HashMap[String, String] ("ch.ninecode.cim.cache" → "cache/parallel_cable_sample_cache"),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 2.5,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                files = List (filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where (simulation = (select max(simulation) from results) or simulation = (select max(simulation) from results) - 1) and house like 'USR%' order by house, simulation")
            var records: Int = 0
            while (resultset.next)
            {
                val max_precalc = resultset.getDouble (3)
                assert (resultset.next, "expected pairs of results")
                val max_loadflow = resultset.getDouble (3)
                assert (Math.abs (max_precalc - max_loadflow) <= 1000.0, "compare precalc with loadflow")
                records = records + 1
            }
            resultset.close ()
            statement.close ()
            connection.close ()
            assert (records == 38, "number of records")
    }

    test ("test cable_impedance_limit paramter")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = FILE_DEPOT + FILENAME1

            val options = EinspeiseleistungOptions (
                verbose = true,
                // cim_reader_options = mutable.HashMap[String, String] ("ch.ninecode.cim.cache" → "cache/parallel_cable_sample_cache"),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 2.5,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                files = List (filename),
                cable_impedance_limit = 0.14
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where (simulation = (select max(simulation) from results) or simulation = (select max(simulation) from results) - 1) and house like 'USR%' and trafo like 'TX0002'")
            var records: Int = 0
            while (resultset.next)
            {
                assert (resultset.getObject ("maximum") == null, "has no max because of cable impedance (invalid element)")
                assert (resultset.getString ("reason") == "no results", "has no result because of cable impedance (invalid element)")
                assert (resultset.getString ("details") == "invalid element (CAB0014 r=0.14600148356433446)", "has no details because of cable impedance (invalid element)")
                records = records + 1

            }
            resultset.close ()
            statement.close ()
            connection.close ()
            assert (records == 28, "number of records")
    }

    /**
     * Test for the correct current limit on a parallel set of cables.
     */
    test ("Parallel")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = FILE_DEPOT + FILENAME2

            val options = EinspeiseleistungOptions (
                verbose = true,
                // cim_reader_options = mutable.HashMap[String, String] ("ch.ninecode.cim.cache" → "cache/parallel_cable_sample_cache"),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 2.5,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                files = List (filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where id = (select max(id) from results)")
            var records: Int = 0
            while (resultset.next)
            {
                assert (resultset.getString (1) == "TX0001", "transformer name")
                assert (resultset.getString (2) == "USR0001", "energy consumer name")
                assert (resultset.getDouble (3) == 96000.0, "maximum")
                assert (resultset.getString (4) == "current limit", "reason")
                assert (resultset.getString (5) == "CAB0001 > 134.0 Amps", "details")
                records = records + 1
            }
            resultset.close ()
            statement.close ()
            connection.close ()
            assert (records == 1, "number of records")
    }

    /**
     * Test for the correct handling of special transformers.
     */
    test ("Special transformer")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = FILE_DEPOT + FILENAME3

            val options = EinspeiseleistungOptions (
                verbose = true,
                // cim_reader_options = mutable.HashMap[String, String] ("ch.ninecode.cim.cache" → "cache/special_transformer_cache"),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 2.5,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                files = List (filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val countset = statement.executeQuery ("select count() from results where simulation = (select max(simulation) from results)")
            while (countset.next)
            {
                assert (countset.getInt (1) == 12, "should have 12 results")
            }
            countset.close ()

            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where simulation = (select max(simulation) from results)")
            while (resultset.next)
            {
                if (resultset.getString (1) == "TX0003")
                {
                    assert (resultset.getObject (3) == null, "maximum")
                    assert (resultset.getString (4) == "no results", "special transformer")
                    assert (resultset.getString (5) == "low voltage (1000.0V:400.0V) subtransmission edge TX0003")
                }
                else
                    if (resultset.getString (1) == "TX0002")
                    {
                        assert (resultset.getObject (3) == null, "all results on TX00002 have no value for maximum")
                        assert (resultset.getString (4) == "no results", "special transformer")
                        assert (resultset.getString (5) == "3 transformer windings for edge TX0002", "three winding transformer")
                    }
                    else
                    {
                        assert (resultset.getString (4) == "current limit", "normal transformer")
                    }
            }
            resultset.close ()
            statement.close ()
            connection.close ()
    }
}
