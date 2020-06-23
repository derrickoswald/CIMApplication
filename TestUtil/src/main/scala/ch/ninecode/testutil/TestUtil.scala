package ch.ninecode.testutil

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMClasses

import org.scalatest.Outcome
import org.scalatest.fixture

trait TestUtil extends fixture.FunSuite with SQLite with Unzip
{
    type FixtureParam = SparkSession
    val classesToRegister: Array[Class[_]] = CIMClasses.list

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        info (template.format ((t1 - t0) / 1e9), None)
        ret
    }

    def serverListening (host: String, port: Int): Boolean =
    {
        try
        {
            val socket = new scala.tools.nsc.io.Socket (new java.net.Socket (host, port))
            socket.close
            true
        }
        catch
        {
            case _: Exception => false
        }
    }

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = s"/tmp/${Random.nextInt (99999999)}.jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

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
        val _ = configuration.registerKryoClasses (classesToRegister)
        // use the custom registrator
        .set ("spark.kryo.registrator", "ch.ninecode.cim.CIMRegistrator")
    }

    /**
     * Delete files and directories recursively.
     *
     * @param path The starting path.
     */
    def deleteRecursive (path: File): Unit =
    {
        if (path.isDirectory)
            for (subpath <- path.list)
                deleteRecursive (new File (path, subpath))
        val _ = path.delete
    }

    def readCIMElements (session: SparkSession, filename: String): Unit =
    {
        val options = Map[String, String] (
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo_islands" -> "true",
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
            val count = elements.count
            info (s"$count elements", None)
        }
    }

    /**
     * Get the named RDD.
     *
     * @param name The name of the RDD, usually the same as the CIM class.
     * @param spark The Spark session which persisted the named RDD.
     * @tparam T The type of objects contained in the named RDD.
     * @return The typed RDD, e.g. <code>RDD[T]</code>.
     *
     * @example The RDD of all elements is somewhat special,
     * currently it is named Elements (plural), so this method must be used:
     * {{{val elements: RDD[Element] = get[Element]("Elements")}}}.
     *
     */
    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    def get[T : ClassTag](name: String)(implicit spark: SparkSession): RDD[T] =
    {
        spark.sparkContext.getPersistentRDDs.find (_._2.name == name) match
        {
            case Some ((_, rdd: RDD[_])) =>
                rdd.asInstanceOf[RDD[T]]
            case Some (_) | None =>
                spark.sparkContext.emptyRDD[T]
        }
    }

    /**
     * Get the typed RDD.
     *
     * Convenience method where the name of the RDD is the same as the contained
     * class type (the usual case).
     *
     * @param spark The Spark session which persisted the typed RDD.
     * @tparam T The type of the RDD, e.g. <code>RDD[T]</code>.
     * @return The RDD with the given type of objects, e.g. <code>RDD[ACLineSegment]</code>.
     */
    def get[T : ClassTag](implicit spark: SparkSession): RDD[T] =
    {
        val classname = classTag[T].runtimeClass.getName
        val name = classname.substring (classname.lastIndexOf (".") + 1)
        get (name)
    }

    /**
     * Assert the difference between a number and a reference value are less than a threshold.
     *
     * @param number the number to test
     * @param reference the reference value
     * @param epsilon the difference limit
     * @param message a message to display if the assert fails
     */
    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3, message: String = ""): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= epsilon,
            if ("" == message)
                s"""$number vs. reference $reference differs by more than $epsilon ($diff)"""
            else
                message
        )
    }
}
