package ch.ninecode.testutil

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.Outcome
import org.scalatest.fixture

trait TestUtil extends fixture.FunSuite with SQLite with Unzip
{
    type FixtureParam = SparkSession
    val classesToRegister: Array[Array[Class[_]]]

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        info (template.format ((t1 - t0) / 1e9), None)
        ret
    }

    def withFixture (test: OneArgTest): Outcome =
    {
        time ("total : %s seconds")
        {
            // create the fixture
            val session = time ("setup : %s seconds")
            {
                // create the configuration
                val configuration = new SparkConf (false)
                    .setAppName (this.getClass.getSimpleName)
                    .setMaster ("local[2]")
                    .set ("spark.driver.memory", "2g")
                    .set ("spark.executor.memory", "2g")
                    .set ("spark.sql.warehouse.dir", "file:///tmp/")
                    .set ("spark.ui.showConsoleProgress", "false")
                    .set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                    .set ("spark.graphx.pregel.checkpointInterval", "8")
                    .set ("spark.sql.warehouse.dir", System.getProperty ("java.io.tmpdir", "/tmp/"))

                // register relevant classes
                registerDependency (configuration)

                // register GraphX classes
                GraphXUtils.registerKryoClasses (configuration)

                // create the fixture
                val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
                session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
                session
            }
            try
            {
                withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
            }
            finally
            {
                session.stop () // clean up the fixture
            }
        }
    }

    def registerDependency (configuration: SparkConf): Unit =
    {
        val length = classesToRegister.length
        for (i <- Range (0, length))
        {
            val classToRegister: Array[Class[_]] = classesToRegister(i)
            configuration.registerKryoClasses (classToRegister)
        }
    }

    def readCIMElements (session: SparkSession, filename: String): Unit =
    {
        val options = Map[String, String] (
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo" -> "true",
            "ch.ninecode.cim.force_retain_switches" -> "Unforced",
            "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
            "ch.ninecode.cim.debug" -> "true",
            "ch.ninecode.cim.do_deduplication" -> "true"
        )
        readCIMElements (session, filename, options)
    }

    def readCIMElements (session: SparkSession,
                         filename: String,
                         options: Map[String, String])
    {
        time ("read: %s seconds")
        {
            val files = filename.split (",")
            val thisOptions =
                if (options.contains ("path"))
                    options
                else
                    options + ("path" -> filename)
            val elements = session.sqlContext.read.format ("ch.ninecode.cim")
                .options (thisOptions)
                .load (files: _*)
                .persist (StorageLevel.MEMORY_AND_DISK_SER)
            info (s"${elements.count} elements", None)
        }
    }

    def near (number: Double, reference: Double): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= 1.0e-3,
            s"""$number vs. reference $reference differs by more than 0.001 ($diff)""")
    }

    def near (number: Double, reference: Double, epsilon: Double): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= epsilon,
                s"""$number vs. reference $reference differs by more than $epsilon ($diff)""")
    }

    def near (number: Double, reference: Double, epsilon: Double, message: String): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= epsilon, message)
    }
}
