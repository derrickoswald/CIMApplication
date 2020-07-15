package ch.ninecode.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.Outcome
import org.scalatest.fixture

import scala.collection.JavaConverters._


trait TestUtil extends fixture.FunSuite with SQLite with Unzip
{
    type FixtureParam = SparkSession
    val classesToRegister: Array[Array[Class[_]]]

    def withFixture (test: OneArgTest): Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

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

        // register relevant classes
        registerDependency (configuration)

        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally
        {
            session.stop () // clean up the fixture
            val total = System.nanoTime ()
            println ("total : " + (total - start) / 1e9 + " seconds")
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
        val start = System.nanoTime
        var thisFiles = files
        var thisOptions = options
        if (thisFiles == null)
        {
            thisFiles = filename.split (",")
        }
        if (thisOptions == null)
        {
            thisOptions = Map [String, String](
                "path" -> filename,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "Unforced",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.debug" -> "true",
                "ch.ninecode.cim.do_deduplication" -> "true"
            )
        }
        val elements = session.sqlContext.read.format ("ch.ninecode.cim")
            .options (thisOptions)
            .load (thisFiles: _*)
            .persist (StorageLevel.MEMORY_AND_DISK_SER)
        println (elements.count + " elements")
        val read = System.nanoTime
        println ("read: " + (read - start) / 1e9 + " seconds")
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



