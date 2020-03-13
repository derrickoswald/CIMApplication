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
        info (template.format ((t1 - t0) / 1e9))
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
                configuration.setAppName (this.getClass.getSimpleName)
                configuration.setMaster ("local[2]")
                configuration.set ("spark.driver.memory", "2g")
                configuration.set ("spark.executor.memory", "2g")
                configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
                configuration.set ("spark.ui.showConsoleProgress", "false")
                configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

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
        classesToRegister.foreach (classToRegister =>
        {
            configuration.registerKryoClasses (classToRegister)
        })
    }

    def readCIMElements (session: SparkSession,
                         filename: String,
                         options: Map[String, String] = null,
                         files: Array[String] = null)
    {
        time ("read: %s seconds")
        {
            val thisFiles = if (files == null)
                filename.split (",")
            else
                files

            val thisOptions = if (options == null)
            {
                Map[String, String](
                    "path" -> filename,
                    "StorageLevel" -> "MEMORY_AND_DISK_SER",
                    "ch.ninecode.cim.do_topo" -> "true",
                    "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                    "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                    "ch.ninecode.cim.debug" -> "true",
                    "ch.ninecode.cim.do_deduplication" -> "true"
                )
            }
            else
                if (options.contains ("path"))
                    options
                else
                    options + ("path" -> filename)
            val elements = session.sqlContext.read.format ("ch.ninecode.cim")
                .options (thisOptions)
                .load (thisFiles: _*)
                .persist (StorageLevel.MEMORY_AND_DISK_SER)
            info (s"${elements.count} elements")
        }
    }

    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3, message: String = null): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= epsilon,
            if (null == message)
                s"""$number vs. reference $reference differs by more than $epsilon ($diff)"""
            else
                message)
    }
}



