package ch.ninecode.gl

import java.io.File

import scala.io.Source

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.net.LineData
import ch.ninecode.net.LineDetails
import ch.ninecode.net.Lines
import ch.ninecode.net.Net
import ch.ninecode.testutil.TestUtil
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement
import ch.ninecode.util.Util
import org.scalactic.source.Position
import org.scalatest.BeforeAndAfter
import org.scalatest.Canceled
import org.scalatest.Outcome
import org.scalatest.exceptions.StackDepthException
import org.scalatest.exceptions.TestCanceledException

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

class LineSuite extends TestUtil with BeforeAndAfter
{
    override val classesToRegister: Array[Class[_]] = Array.concat (CIMClasses.list, GridLABD.classes, Net.classes, Util.classes)
    val FILE_DEPOT = "data/"
    val FILENAME = "LineTest"

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
                    val s3 = jarForObject (Probe ())
                    val s4 = jarForObject (Lines)
                    val s5 = jarForObject (FlowDirection)
                    val configuration = new SparkConf (false)
                        .setAppName (this.getClass.getSimpleName)
                        .setMaster (s"spark://$SERVER:$PORT")
                        .set ("spark.driver.memory", "2g")
                        .set ("spark.executor.memory", "2g")
                        .set ("spark.sql.warehouse.dir", "file:///tmp/")
                        .set ("spark.ui.showConsoleProgress", "false")
                        .set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                        .setJars (Set (s1, s2, s3, s4, s5).toArray)

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

    test ("static")
    {
        session: SparkSession =>
            val gridlabd = new GridLABD (session, workdir = s"hdfs://sandbox:8020/")
            val source = Source.fromFile (new File (s"$FILE_DEPOT$FILENAME.rdf"), "UTF-8")
            val bytes = source.getLines.mkString ("\n").getBytes ("UTF-8")
            gridlabd.writeInputFile ("/", s"$FILENAME.rdf", bytes)
            source.close

            val filename = s"hdfs://sandbox:8020/$FILENAME.rdf"
            readCIMElements (session, filename, Map[String, String] (
                    "path" -> filename,
                    "ch.ninecode.cim.do_topo" -> "true"
                )
            )

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = true // should be default value anyway

            val l1 = Lines (session).getLines (Probe().acceptAllLines)
            val lines1 = l1.map (Probe().stringify).collect

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = false

            val l2 = Lines (session).getLines (Probe().acceptAllLines)
            val lines2 = l2.map (Probe().stringify).collect

            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = true // reset static var

            def pull (id: String, results: Array[(String, String)]): String =
            {
                results.find (_._1 == id) match
                {
                    case Some (item) => item._2
                    case _ => fail (s"id '$id' not found")
                }
            }
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
