package ch.ninecode.mv

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses

class MediumVoltageSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("MediumVoltageSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "4g")
        //configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))
        configuration.set ("spark.ui.showConsoleProgress", "false")

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

        val root = "sample"
        val filename =
            FILE_DEPOT + root + ".rdf"

        val options = MediumVoltageOptions (
            verbose = true,
            cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
            three = false,
            base_temperature = 20.0,
            temperature = 20.0,
            workdir = "./target/",
            files = List (filename)
        )
        val ms = MediumVoltage (session, options)
        val count = ms.run ()

        val total = System.nanoTime ()
        println ("total: " + (total - begin) / 1e9 + " seconds " + count + " UST\n")
    }
}
