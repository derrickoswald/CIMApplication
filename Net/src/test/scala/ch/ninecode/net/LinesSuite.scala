package ch.ninecode.net

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement

import org.scalatest.Canceled
import org.scalatest.Outcome
import org.scalatest.exceptions.StackDepthException
import org.scalatest.exceptions.TestCanceledException
import org.scalactic.source.Position

class LinesSuite extends TestUtil
{
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

    override val classesToRegister: Array[Array[Class[_]]] = Array(CIMClasses.list)

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

    override def withFixture (test: OneArgTest): Outcome =
    {
        val SERVER = "sandbox"
        val PORT = 7077
        if (serverListening (SERVER, PORT))
        {
            time ("total : %s seconds")
            {
                // create the fixture
                val session = time ("setup : %s seconds")
                {
                    // create the configuration
                    val s1 = jarForObject (new DefaultSource ())
                    val s2 = jarForObject (ThreePhaseComplexDataElement ("", 0L, Complex.j, Complex.j, Complex.j, ""))
                    val s3 = jarForObject (Lines)
                    val configuration = new SparkConf (false)
                        .setAppName (this.getClass.getSimpleName)
                        .setMaster (s"spark://$SERVER:$PORT")
                        .set ("spark.driver.memory", "2g")
                        .set ("spark.executor.memory", "2g")
                        .set ("spark.sql.warehouse.dir", "file:///tmp/")
                        .set ("spark.ui.showConsoleProgress", "false")
                        .set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                        .setJars (Set (s1, s2, s3).toArray)

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
        else
            Canceled (new TestCanceledException ((_: StackDepthException) => Some ("sandbox not reachable"), None, Position ("LineSuite.scala", "", 123), None))
    }

    test ("basic")
    {
        session: SparkSession =>
            val FILENAME = "hdfs://sandbox:8020/DemoData.rdf"
            readCIMElements (session, FILENAME)
            val lines = Lines (session)
            time ("execution time: %s seconds")
            {
                val doit = lines.getLines ()
                // grep "ACLineSegment rdf:" DemoData.rdf | wc
                // 47
                assert (doit.count == 47, "expected number of ACLineSegments")
            }
    }
}
