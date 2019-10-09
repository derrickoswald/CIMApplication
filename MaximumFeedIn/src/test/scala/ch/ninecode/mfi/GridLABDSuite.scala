package ch.ninecode.mfi

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.sql.DriverManager
import java.util.zip.ZipInputStream

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import org.scalatest._

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

class GridLABDSuite extends fixture.FunSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"
    val FILENAME2 = "parallel_cable_sample"
    val FILENAME3 = "three_winding_transformer"

    type FixtureParam = SparkSession

    def using[T <: AutoCloseable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

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
            using (new ZipInputStream (new FileInputStream (file)))
            {
                zip =>
                    var entry = zip.getNextEntry
                    // iterates over entries in the zip file
                    while (null != entry)
                    {
                        val path = directory + entry.getName
                        if (!entry.isDirectory)
                            // if the entry is a file, extract it
                            extractFile (zip, path)
                        else
                            // if the entry is a directory, make the directory
                            new File (path).mkdir
                        zip.closeEntry ()
                        entry = zip.getNextEntry
                    }
            }
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
            val bytesIn = new Array[Byte](4096)
            using (new BufferedOutputStream (new FileOutputStream (path)))
            {
                bos =>
                    var read = -1
                    while (
                    {
                        read = zip.read (bytesIn)
                        read != -1
                    })
                    bos.write (bytesIn, 0, read)
            }
        }
    }

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
        new File (s"$FILE_DEPOT$FILENAME1.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME2.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME3.rdf").delete
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
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Einspeiseleistung.classes)

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

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                outputfile = "simulation/results.db",
                files = List (filename),
                precalc_factor = 2.5
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection (s"jdbc:sqlite:${options.outputfile}")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where (simulation = (select max(simulation) from results) or simulation = (select max(simulation) from results) - 1) and house like 'USR%' order by house, simulation")
            var records: Int = 0
            while (resultset.next)
            {
                val max_precalc = resultset.getDouble (3)
                assert (resultset.next, "expected pairs of results")
                val max_loadflow = resultset.getDouble (3)
                assert (max_precalc - max_loadflow <= 1500.0, "compare precalc with loadflow")
                records = records + 1
            }
            resultset.close ()
            statement.close ()
            connection.close ()
            assert (records == 38, "number of records")
    }

    test ("Test cable_impedance_limit parameter")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
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
                outputfile = "simulation/results.db",
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
            val connection = DriverManager.getConnection (s"jdbc:sqlite:${options.outputfile}")

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

            val filename = s"$FILE_DEPOT$FILENAME2.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                outputfile = "simulation/results.db",
                files = List (filename),
                precalc_factor = 2.5
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection (s"jdbc:sqlite:${options.outputfile}")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where id = (select max(id) from results)")
            var records: Int = 0
            while (resultset.next)
            {
                assert (resultset.getString (1) == "TX0001", "transformer name")
                assert (resultset.getString (2) == "USR0001", "energy consumer name")
                assert (resultset.getDouble (3) == 95000.0, "maximum")
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

            val filename = s"$FILE_DEPOT$FILENAME3.rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                workdir = "simulation/",
                outputfile = "simulation/results.db",
                files = List (filename),
                precalc_factor = 2.5
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection (s"jdbc:sqlite:${options.outputfile}")

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

    def simulation (outputfile: String = "simulation/results.db"): String =
    {
        Class.forName ("org.sqlite.JDBC") // load the sqlite-JDBC driver using the current class loader
        using (DriverManager.getConnection (s"jdbc:sqlite:${outputfile}"))
        {
            connection =>
                using (connection.createStatement ())
                {
                    statement =>
                        using (statement.executeQuery ("select max(simulation) from results"))
                        {
                            resultset =>
                                resultset.next
                                resultset.getString (1)
                        }
                }
        }
    }

    /**
     * Test for equality of three phase and single phase feed in values.
     */
    test ("Three phase")
    {
        session: SparkSession ⇒

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"

            val options_one_phase = EinspeiseleistungOptions (
                verbose = true,
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                outputfile = "simulation_three_phase/results.db",
                files = List (filename),
                precalc_factor = 2.5
            )
            val eins = Einspeiseleistung (session, options_one_phase)
            eins.run ()
            val one_phase = simulation (options_one_phase.outputfile)

            val options_three_phase = EinspeiseleistungOptions (
                verbose = true,
                three = true,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                cosphi = 1.0,
                voltage_threshold = 3.0,
                voltage_threshold2 = 3.0,
                ignore_other = false,
                outputfile = "simulation_three_phase/results.db",
                files = List (filename),
                precalc_factor = 2.5
            )
            val drei = Einspeiseleistung (session, options_three_phase)
            drei.run ()
            val three_phase = simulation (options_three_phase.outputfile)

            Class.forName ("org.sqlite.JDBC") // load the sqlite-JDBC driver using the current class loader
            using (DriverManager.getConnection (s"jdbc:sqlite:${options_three_phase.outputfile}"))
            {
                connection =>
                    using (connection.createStatement ())
                    {
                        statement =>
                            using (statement.executeQuery (s"select trafo, house, maximum, reason, details from results where simulation='$one_phase'"))
                            {
                                resultset =>
                                    while (resultset.next)
                                    {
                                        val trafo = resultset.getString (1)
                                        val house = resultset.getString (2)
                                        val maximum = resultset.getDouble (3)
                                        val reason = resultset.getString (4)
                                        val details = resultset.getString (5)
                                        using (statement.executeQuery (s"select maximum, reason, details from results where simulation='$three_phase' and trafo='$trafo' and house='$house'"))
                                        {
                                            rs =>
                                                assert (rs.next, s"record for trafo '$trafo' house '$house' not found")
                                                val max = resultset.getDouble (1)
                                                val rea = resultset.getString (2)
                                                val det = resultset.getString (3)
                                                assert (max == maximum, s"maximum $trafo.$house")
                                                assert (rea == reason, s"reason $trafo.$house")
                                                assert (det == details, s"details $trafo.$house")
                                        }
                                    }
                            }
                    }
            }
    }
}
