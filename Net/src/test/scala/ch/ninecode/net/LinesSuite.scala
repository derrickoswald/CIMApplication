package ch.ninecode.net

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

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
    override def withFixture (test: OneArgTest): Outcome =
    {
        val SERVER = "sandbox"
        val PORT = 7077
        if (serverListening(SERVER, PORT))
        {
            time("total : %s seconds")
            {
                // create the fixture
                val session = time("setup : %s seconds")
                {
                    // create the configuration
                    val s1 = jarForObject(new DefaultSource())
                    val s2 = jarForObject(ThreePhaseComplexDataElement("", 0L, Complex.j, Complex.j, Complex.j, ""))
                    val s3 = jarForObject(Lines)
                    val configuration = new SparkConf(false)
                        .setAppName(this.getClass.getSimpleName)
                        .setMaster(s"spark://$SERVER:$PORT")
                        .set("spark.driver.memory", "2g")
                        .set("spark.executor.memory", "2g")
                        .set("spark.ui.showConsoleProgress", "false")
                        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                        .setJars(Set(s1, s2, s3).toArray)

                    // register relevant classes
                    registerDependency(configuration)

                    // register GraphX classes
                    GraphXUtils.registerKryoClasses(configuration)

                    // create the fixture
                    val session = SparkSession.builder().config(configuration).getOrCreate() // create the fixture
                    session.sparkContext.setLogLevel("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
                    session
                }
                try
                {
                    withFixture(test.toNoArgTest(session)) // "loan" the fixture to the test
                }
                finally
                {
                    session.stop() // clean up the fixture
                }
            }
        }
        else
            Canceled(new TestCanceledException((_: StackDepthException) => Some("sandbox not reachable"), None, Position("LineSuite.scala", "", 123), None))
    }

    test("basic")
    {
        session: SparkSession =>
            val FILENAME = "hdfs://sandbox:8020/DemoData.rdf"
            readCIMElements(session, FILENAME)
            val lines = Lines(session)
            time("execution time: %s seconds")
            {
                val doit = lines.getLines()
                // grep "ACLineSegment rdf:" DemoData.rdf | wc
                // 47
                assert(doit.count == 47, "expected number of ACLineSegments")
            }
    }
}
