package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.ts.TimeSeries.jarForObject

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleMetaTest
{
    import ch.ninecode.ts.SimpleMetaTest._

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        println (template.format ((t1 - t0) / 1e9))
        ret
    }

    @Test def makeModel ()
    {
        val kWh = 894.40999 * 96 * 365.25 / 1000.0
        val format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        format.setTimeZone (TimeZone.getTimeZone ("UTC"))
        val start = Calendar.getInstance (TimeZone.getTimeZone ("GMT"))
        start.setTimeInMillis (format.parse ("2017-10-22T00:00:00.000+0000").getTime)
        val end = Calendar.getInstance (TimeZone.getTimeZone ("GMT"))
        end.setTimeInMillis (format.parse ("2017-10-25T00:00:00.000+0000").getTime)

        time ("total execution: %s seconds")
        {
            val model = TimeSeriesModel (session, TimeSeriesOptions (keyspace = KEYSPACE, log_level = LogLevels.INFO, tree_depth=Array(8), model_file = "hdfs://sandbox:8020/models/myMetaModel16"))
            time ("modelling time: %s seconds")
            {
                model.makeSingleMetaDecisionTreeRegressorModel ()
            }
            time ("synthesis time: %s seconds")
            {
                model.generateSingleMetaTimeSeries ("HAS7165", start, end, 900000, kWh, Map[String, Int] ("Apartment" -> 12, "General" -> 1))
            }
        }
    }
}

object SimpleMetaTest
{
    val KEYSPACE = "meterdata"
    var session: SparkSession = _

    @BeforeClass def before ()
    {
        println ("creating Spark session")

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("MetaSuite")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.cassandra.connection.host", "beach")
        configuration.set ("spark.cassandra.connection.port", "9042")
        val s1 = jarForObject (ch.ninecode.ts.TimeSeriesOptions ())
        val s2 = jarForObject (com.datastax.spark.connector.SomeColumns ())
        val s3 = jarForObject (new com.twitter.jsr166e.LongAdder ())
        configuration.setJars (Array (
            s1,
            s2,
            s3))

        session = SparkSession.builder.config (configuration).getOrCreate
        session.sparkContext.setLogLevel ("WARN")
        session.sparkContext.setCheckpointDir ("hdfs://sandbox:8020/checkpoint")
    }

    @AfterClass def after ()
    {
        println ("stopping Spark session")
        session.stop
    }
}