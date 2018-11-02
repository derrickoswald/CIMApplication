package ch.ninecode.on

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture.FunSuite
import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

class OneOfNSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("OneOfNSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.graphx.pregel.checkpointInterval", "8")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register OneOfN classes
        configuration.registerKryoClasses (OneOfN.classes)

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    /**
     * Generate a sample .glm file.
     *
     * Note that with the number of output files requested, you will likely bump into the error:
     *     recorder file output_data/KLE111341_current.csv: Too many open files
     * To fix this, first check your own user hard file limit:
     *     $ ulimit -Ha
     *     ...
     *     open files                      (-n) 4096
     *     ...
     * and then check your soft limit:
     *     $ ulimit -Sa
     *     ...
     *     open files                      (-n) 1024
     *     ...
     * If there is a difference you can bump up the soft limit yourself to the hard limit
     *     $ ulimit -Sn 4096
     * and check your soft limit again.
     * Otherwise you will need to do extraordinary work as root to get the limit extended.
     */
    test ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = "bkw_cim_export_azimi"
        val filename =
            FILE_DEPOT + root + ".rdf"

        val options = OneOfNOptions (
            verbose = true,
            cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
            three = false,
            base_temperature = 20.0,
            temperature = 20.0,
            workdir = "./target/",
            files = List (filename)
        )
        val on = OneOfN (session, options)
        val count = on.run ()

        val total = System.nanoTime ()
        println ("total: " + (total - begin) / 1e9 + " seconds " + count + " feeders\n")
    }
}
