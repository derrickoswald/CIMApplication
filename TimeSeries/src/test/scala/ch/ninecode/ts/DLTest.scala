package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import com.intel.analytics.bigdl.utils.Engine
import ch.ninecode.ts.TimeSeries.jarForObject

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DLTest
{
    import ch.ninecode.ts.DLTest._

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        println (template.format ((t1 - t0) / 1e9))
        ret
    }

    def toCalendar (date: String): Calendar =
    {
        val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSSZ")
        val timestamp = date_format.parse (date)
        val calendar = Calendar.getInstance (TimeZone.getTimeZone ("GMT"))
        calendar.setTimeInMillis (timestamp.getTime)
        calendar
    }

    /* @Test */ def makeModel ()
    {
        val KEYSPACE = "test"
        val start = toCalendar ("2017-07-19 00:00:00.000+0000")
        val end = toCalendar ("2018-03-31 23:45:00.000+0000")
        //        select * from cimapplication.measured_value_stats where mrid='HAS149' allow filtering;
        //
        //        mrid   | type   | start                           | average   | count | end                             | maximum | minimum | missing | stddev
        //        --------+--------+---------------------------------+-----------+-------+---------------------------------+---------+---------+---------+-----------
        //        HAS149 | energy | 2017-01-17 05:15:00.000000+0000 | 205.49709 | 43467 | 2018-04-19 03:45:00.000000+0000 |    3024 |       2 |     400 | 255.27808
        val kWh = 205.49709 * 96 * 365.25 / 1000.0

        time ("total execution: %s seconds")
        {
            val model = TimeSeriesModel (session, TimeSeriesOptions (keyspace = KEYSPACE, model_file = "models/myDLModel"))
            time ("modelling time: %s seconds")
            {
                model.makeDLModel ()
            }
            time ("synthesis time: %s seconds")
            {
                model.generateSimpleTimeSeries ("HASFAKE", start, end, 900000, kWh)
            }
        }
    }
}

@SuppressWarnings (Array ("org.wartremover.warts.Null"))
object DLTest
{
    val KEYSPACE = "cimapplication"
    var session: SparkSession = _

    @BeforeClass def before ()
    {
        println ("creating Spark session")

        // create the configuration
        val s1 = jarForObject (ch.ninecode.ts.TimeSeriesOptions ())
        val s2 = jarForObject (com.datastax.spark.connector.SomeColumns ())
        val s3 = jarForObject (new org.apache.spark.mllib.stat.test.BinarySample (true, 1.0))
        val s4 = jarForObject (new com.intel.analytics.bigdl.utils.LayerException (null, null))
        val s5 = jarForObject (com.intel.analytics.bigdl.models.utils.DistriOptimizerPerfParam())
        val configuration = Engine.createSparkConf ()
            .setAppName ("DLSuite")
            .setMaster ("spark://sandbox:7077")
            .set ("spark.driver.memory", "2g")
            .set ("spark.executor.memory", "2g")
            .set ("spark.executor.cores", "1")
            .set ("spark.cores.max", "2")
            .set ("spark.ui.port", "4041")
            .set ("spark.ui.showConsoleProgress", "false")
            .set ("spark.cassandra.connection.host", "beach")
            .set ("spark.cassandra.connection.port", "9042")
            .setJars (Array (s1, s2, s3, s4, s5))

        session = SparkSession.builder.config (configuration).getOrCreate
        session.sparkContext.setLogLevel ("WARN")
        session.sparkContext.setCheckpointDir ("hdfs://sandbox:8020/checkpoint")
        Engine.init
    }

    @AfterClass def after ()
    {
        println ("stopping Spark session")
        session.stop
    }
}