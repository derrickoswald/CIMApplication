package ch.ninecode.esl

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
        configuration.set ("spark.executor.memory", "4g")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")

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

    test ("Basic")
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
    test ("Parallel")
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
}
