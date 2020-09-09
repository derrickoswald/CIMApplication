package ch.ninecode.ingest

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuite

class IngestCustomSuite extends FixtureAnyFunSuite with BeforeAndAfterAll
{
    case class FixtureParam(ingestCustom: IngestCustom, session: SparkSession)

    override def withFixture (test: OneArgTest): Outcome =
    {
        val session = createSparkSession ()
        val ingestCustom = IngestCustom (session, new IngestOptions ())
        val fixtureParam = FixtureParam(ingestCustom, session)
        try {
            withFixture(test.toNoArgTest(fixtureParam))
        } finally
        {
            session.stop ()
        }
    }

    private def createSparkContext (conf: Option[SparkConf] = None): SparkContext =
    {
        new SparkContext (conf.getOrElse (
            new SparkConf ()
                .setMaster ("local[*]")
                .setAppName ("unit test")))
    }

    private def createSparkSession (): SparkSession =
    {
        val sc = createSparkContext ()
        val session = SparkSession.builder.config (sc.getConf).getOrCreate
        session.sparkContext.setLogLevel ("WARN")
        session
    }

    test ("check period calculation from custom ingest format")
    {
        fixture: FixtureParam =>
            val joinTable: Map[String, String] = Map ("719929" -> "HAK123")
            val lineStart: String = "719929;;Wirkenergie A+ 15;1-1:1.8.0*255;15;kWh;01.07.2020;"
            val lineValues: String = "00:00:00;00:15:00;1.275;00:15:00;23:45:00;1.35;23:45:00;00:00:00;4.2"
            val line: String = lineStart + lineValues

            val measuredValue = fixture.ingestCustom.line_custom (joinTable, new IngestJob())(line)
            assert (measuredValue.length == 3, "should have 3 values")

            val periodFirst: Int = measuredValue.head._4
            assert (periodFirst == 900000, "period of first value should be 90000 (15 minutes)")

            val periodLast: Int = measuredValue.last._4
            assert (periodLast == 900000, "period of last value should be 90000 (15 minutes)")
    }
}
