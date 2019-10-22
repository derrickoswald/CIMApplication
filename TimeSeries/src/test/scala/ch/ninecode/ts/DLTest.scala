package ch.ninecode.ts

import java.io.Closeable
import java.text.SimpleDateFormat
import java.util.Properties

import ch.ninecode.ts.TimeSeries.main
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import com.intel.analytics.bigdl.utils.Engine

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DLTest
{
    import ch.ninecode.ts.DLTest._

    /* @Test */ def makeModel ()
    {
        val KEYSPACE = "test"
        val begin = System.nanoTime ()
        val model = Model (session, TimeSeriesOptions (keyspace = KEYSPACE, model_file = "models/myDLModel"))
        model.makeDLModel ()

//        val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSSZ")
//        val start = toCalendar (date_format.parse ("2017-07-19 00:00:00.000+0000"))
//        val end = toCalendar (date_format.parse ("2018-03-31 23:45:00.000+0000"))
//        //        select * from cimapplication.measured_value_stats where mrid='HAS149' allow filtering;
//        //
//        //        mrid   | type   | start                           | average   | count | end                             | maximum | minimum | missing | stddev
//        //        --------+--------+---------------------------------+-----------+-------+---------------------------------+---------+---------+---------+-----------
//        //        HAS149 | energy | 2017-01-17 05:15:00.000000+0000 | 205.49709 | 43467 | 2018-04-19 03:45:00.000000+0000 |    3024 |       2 |     400 | 255.27808
//        val kWh = 205.49709 * 96 * 365.25 / 1000.0
//        model.generateTimeSeries ("HASFAKE", start, end, 900000, kWh)
//
//        //        select synthesis, type, min(period) as period, min(time) as min, max(time) as max, count(synthesis) as count, min(real_a) as min, avg(real_a) as avg, max(real_a) as max, cimapplication.standard_deviation (real_a) as standard_deviation from cimapplication.synthesized_value where real_a > 0.0 and type = 'energy' group by synthesis, type, period allow filtering;
//        //
//        //        synthesis | type   | period | min           | max           | count | min       | avg       | max       | standard_deviation
//        //        -----------+--------+--------+---------------+---------------+-------+-----------+-----------+-----------+--------------------
//        //        HASFAKE | energy | 900000 | 118.510638298 | 546.730769231 | 24575 | 118.51064 | 257.89588 | 546.73077 |           77.92206
//
//        //        select avg(average) as average from cimapplication.measured_value_stats;
//        //
//        //        average
//        //        -----------
//        //        591.61085
//
        val finish = System.nanoTime ()
        println ("execution: " + (finish - begin) / 1e9 + " seconds")
    }
}

object DLTest
{
    val KEYSPACE = "cimapplication"
    var session: SparkSession = _

    @BeforeClass def before ()
    {
        println ("creating Spark session")

        // create the configuration
        val configuration = Engine.createSparkConf ()
        configuration.setAppName ("DLSuite")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.executor.cores", "1")
        configuration.set ("spark.cores.max", "2")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.cassandra.connection.host", "beach")
        configuration.set ("spark.cassandra.connection.port", "9042")
        configuration.setJars (Array (
            "/home/derrick/.m2/repository/com/datastax/spark/spark-cassandra-connector_2.11/2.4.1/spark-cassandra-connector_2.11-2.4.1.jar",
            "/home/derrick/.m2/repository/com/intel/analytics/bigdl/bigdl-SPARK_2.4/0.9.1/bigdl-SPARK_2.4-0.9.1.jar",
            "/home/derrick/.m2/repository/com/intel/analytics/bigdl/spark-version/2.0-SPARK_2.4/0.9.1/2.0-SPARK_2.4-0.9.1.jar",
            "/home/derrick/.m2/repository/com/intel/analytics/bigdl/core/dist/all/0.9.1/all-0.9.1.jar",
            "/home/derrick/.m2/repository/org/apache/spark/spark-mllib_2.11/2.4.3/spark-mllib_2.11-2.4.3.jar",
            "/home/derrick/code/CIMApplication/TimeSeries/TimeSeries-2.11-2.4.3-2.5.0.jar"))

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