package ch.ninecode.ts

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MetaTest
{
    import ch.ninecode.ts.MetaTest._

    @Test def makeModel ()
    {
        val KEYSPACE = "subsample"
        val begin = System.nanoTime ()
        val model = Model (session, TimeSeriesOptions (keyspace = KEYSPACE, log_level = LogLevels.INFO, model_file = "hdfs://sandbox:8020/models/myMetaModel16"))
        model.makeMetaDecisionTreeRegressorModel ()
        val finish = System.nanoTime ()
        println ("execution: " + (finish - begin) / 1e9 + " seconds")
    }
}

object MetaTest
{
    val KEYSPACE = "test"
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
        configuration.setJars (Array (
            "/home/derrick/.m2/repository/com/datastax/spark/spark-cassandra-connector_2.11/2.4.1/spark-cassandra-connector_2.11-2.4.1.jar",
            "/home/derrick/code/CIMApplication/TimeSeries/target/TimeSeries-2.11-2.4.3-2.5.0.jar"))

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