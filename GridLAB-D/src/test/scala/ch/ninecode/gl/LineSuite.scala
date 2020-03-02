package ch.ninecode.gl

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.io.Source
import scala.util.Random
import scala.tools.nsc.io.Jar

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.testutil.Unzip
import ch.ninecode.util._

import org.scalactic.source.Position
import org.scalatest.BeforeAndAfter
import org.scalatest.Canceled
import org.scalatest.Outcome
import org.scalatest.exceptions.StackDepthException
import org.scalatest.exceptions.TestCanceledException
import org.scalatest.fixture

case class Probe ()
{
    def acceptAllLines (line_filter: LineData): Boolean = true

    def stringify (line: LineData): (String, String) =
    {
        val id = line.lines.map (_.line.id).toArray.sortWith (_ < _).mkString ("_")
        val z = line.lines.map (_.impedance.toString).mkString("_")
        (id, z)
    }
}

class LineSuite extends fixture.FunSuite with Unzip with BeforeAndAfter
{
    type FixtureParam = SparkSession
    val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list, GridLABD.classes, Util.classes)
    val FILE_DEPOT = "data/"
    val FILENAME = "LineTest"

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

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    after
    {
        new File (s"$FILE_DEPOT$FILENAME.rdf").delete
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
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    def withFixture (test: OneArgTest): Outcome =
    {
        if (serverListening ("sandbox", 7077))
        {
            // create the fixture
            val start = System.nanoTime ()

            // create the configuration
            val configuration = new SparkConf (false)
            configuration.setAppName (this.getClass.getSimpleName)
            configuration.setMaster ("spark://sandbox:7077")
            configuration.set ("spark.driver.memory", "2g")
            configuration.set ("spark.executor.memory", "2g")
            configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
            configuration.set ("spark.ui.showConsoleProgress", "false")
            configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

            val s1 = jarForObject (new DefaultSource ())
            val s2 = jarForObject (ThreePhaseComplexDataElement (null, 0L, null, null, null, null))
            val s3 = jarForObject (Probe ())
            val s4 = jarForObject (Lines)
            configuration.setJars (Array (s1, s2, s3, s4))

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
        else
            Canceled (new TestCanceledException ((_: StackDepthException) => Some ("sandbox not reachable"), None, Position ("LineSuite.scala", "", 123), None))
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
                "StorageLevel" -> "MEMORY_AND_DISK_SER"
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

    test ("static")
    {
        session: SparkSession =>
            val gridlabd = new GridLABD (session, workdir = s"hdfs://sandbox:8020/")
            val source = Source.fromFile (new File (s"$FILE_DEPOT$FILENAME.rdf"), "UTF-8")
            val bytes = source.getLines.mkString ("\n").getBytes ("UTF-8")
            gridlabd.writeInputFile ("/", s"$FILENAME.rdf", bytes)
            source.close

            val filename = s"hdfs://sandbox:8020/$FILENAME.rdf"
            readCIMElements (session, filename)

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = true // should be default value anyway

            val l1 = Lines (session).getLines (Probe().acceptAllLines)
            val lines1 = l1.map (Probe().stringify).collect

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = false

            val l2 = Lines (session).getLines (Probe().acceptAllLines)
            val lines2 = l2.map (Probe().stringify).collect

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = true // reset static var

            def pull (id: String, results: Array[(String, String)]): String = results.find (_._1 == id).get._2
            // cab0001 has a per unit impedance overriding the r,x
            // 85.24045203099396m × (0.842e-3+0.075e-3j), 85.24045203099396m × (2.84e-3+0.3e-3j)
            assert (pull ("CAB0001", lines1) == "(z1=0.071772+0.006393j,z0=0.242083+0.025572j)")
            // cab0002 doesn't have a per unit impedance, and r,x are the impedance per kilometer
            assert (pull ("CAB0001", lines1) == pull ("CAB0002", lines1)) // use ERRONEOUS fallback
            assert (pull ("CAB0001", lines1) == pull ("CAB0001", lines2)) // for correctly specified cables, ERRONEOUS doesn't matter
            assert (pull ("CAB0001", lines2) != pull ("CAB0002", lines2)) // setting ERRONEOUS wrong

            gridlabd.eraseInputFile (s"$FILENAME.rdf")
    }
}
