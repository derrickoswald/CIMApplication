package ch.ninecode.mfi

import java.sql.DriverManager

import org.scalatest.fixture.FunSuite
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

class GridLABDSuite extends FunSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"
    val FILE_DEPOT = "data/"

    type FixtureParam = SparkSession

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

    ignore ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = if (false)
            "bkw_cim_export_sias_current_20161220_Haelig"
        else
            "bkw_cim_export_sias_current_20161220_Haelig_no_EEA7355"
        val filename =
            PRIVATE_FILE_DEPOT + root + ".rdf"

        val options = EinspeiseleistungOptions (
            verbose = true,
            cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
            three = false,
            precalculation = false,
            trafos = "",
            export_only = false,
            all = true,
            erase = false,
            simulation = -1,
            reference = -1,
            delta = 1e-6,
            precalc_factor = 1.5,
            workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
            files = List(filename)
        )
        val eins = Einspeiseleistung (session, options)
        val count = eins.run ()

        val total = System.nanoTime ()
        println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")
    }

    /**
     * Test for the correct current limit on a parallel set of cables.
     */
    ignore ("Parallel")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val root = "parallel cable sample"
            val filename = FILE_DEPOT + root + ".rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 1.5,
                workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
                files = List(filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")

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
                assert (records == 1, "number of records")
            }
            resultset.close ()
            statement.close ()
            connection.close ()
    }


    /**
     * Test for the correct current limit on a parallel set of cables.
     */
    ignore ("Three windng transformer")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val root = "three_winding_transformer"
            val filename = FILE_DEPOT + root + ".rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = false,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 1.5,
                workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
                files = List(filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where id = (select max(id) from results)")
            var records: Int = 0
            while (resultset.next)
            {
                if (resultset.getString (2) == "USR0004" || resultset.getString (2) == "USR0005")
                {
                    assert (resultset.getString (1) == "TX0003", "transformer name")
                    assert (resultset.getDouble (3) == 0.0, "maximum")
                    assert (resultset.wasNull, "maximum should be null")
                    assert (resultset.getString (4) == "low voltage (1000.0V:400.0V) subtransmission edge TX0003")
                }
            }
            resultset.close ()
            statement.close ()
            connection.close ()
    }

    ignore ("Too many open files")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val root = "EKZ_Testcase4_STA333"
            val filename = PRIVATE_FILE_DEPOT + root + ".rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 1.5,
                workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
                files = List(filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where id = (select max(id) from results) and trafo = 'TRA8208'")
            var records: Int = 0
            while (resultset.next)
            {
//                 Einspeiseleistung|TRA8208|HAS2760|81000.0|current limit|KLE12754 > 115.0 Amps|1541685757548
                if (resultset.getString (2) == "HAS2760")
                    assert (resultset.getDouble (3) == 81000.0, "maximum")

//                Einspeiseleistung|TRA8208|HAS2807|48000.0|current limit|KLE13149 > 68.0 Amps|1541685757548
                if (resultset.getString (2) == "HAS2807")
                    assert (resultset.getDouble (3) == 48000.0, "maximum")
            }
            resultset.close ()
            statement.close ()
            connection.close ()
    }

    test ("Verstärkern")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val root = "EKZ_Testcase1_STA866"
            val filename = PRIVATE_FILE_DEPOT + root + ".rdf"

            val options = EinspeiseleistungOptions (
                verbose = true,
                cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
                three = false,
                precalculation = false,
                trafos = "",
                export_only = false,
                all = true,
                erase = false,
                simulation = -1,
                reference = -1,
                delta = 1e-6,
                precalc_factor = 1.5,
                workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
                files = List(filename)
            )
            val eins = Einspeiseleistung (session, options)
            val count = eins.run ()

            val total = System.nanoTime ()
            println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")

            // load the sqlite-JDBC driver using the current class loader
            Class.forName ("org.sqlite.JDBC")
            // create a database connection
            val connection = DriverManager.getConnection ("jdbc:sqlite:simulation/results.db")

            val statement = connection.createStatement ()
            val resultset = statement.executeQuery ("select trafo, house, maximum, reason, details from results where simulation = (select max(simulation) from results) and trafo = 'TRA5036'")
            var records: Int = 0
            var HAS108891 = false
            var HAS14977 = false
            var HAS108891_power: Option[Double] = None
            var HAS14977_power: Option[Double] = None
            while (resultset.next)
            {
                if (resultset.getString (2) == "HAS108891")
                {
                    HAS108891 = true
                    HAS108891_power = Some (resultset.getDouble (3))
                }

                if (resultset.getString (2) == "HAS14977")
                {
                    HAS14977 = true
                    HAS14977_power = Some (resultset.getDouble (3))
                }
            }
            resultset.close ()
            statement.close ()
            connection.close ()

            assert (HAS108891, "HAS108891")
            assert (HAS14977, "HAS14977")
            assert (HAS108891_power == HAS14977_power, "maximum")
    }
}
