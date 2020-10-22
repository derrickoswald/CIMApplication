package ch.ninecode.ts

import java.io.File
import java.util.Properties

import scala.collection.immutable

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.ts.TimeSeries.main
import ch.ninecode.testutil.Using
import ch.ninecode.testutil.Unzip
import ch.ninecode.util.Schema

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TimeSeriesStatsSuiteIT
{
    def cassandra_port: String =
    {
        val properties: Properties =
        {
            val in = this.getClass.getResourceAsStream("/configuration.properties")
            val p = new Properties()
            p.load(in)
            in.close()
            p
        }
        properties.getProperty("nativeTransportPort", "9042")
    }

    @Test def Help ()
    {
        main(Array("--unittest", "--help"))
    }

    @Test def TimeSeriesStats ()
    {
        main(Array(
            "Statistics", "--unittest",
            "--master", "local[*]",
            "--log", "INFO",
            "--host", "localhost",
            "--port", cassandra_port,
            "--keyspace", "test"))
    }
}

object TimeSeriesStatsSuiteIT extends Unzip with Using
{
    val KEYSPACE = "test"
    val FILE_DEPOT = "data/"
    val FILENAME0 = "measurement_data"

    def cassandra_port: String =
    {
        val properties: Properties =
        {
            val in = this.getClass.getResourceAsStream("/configuration.properties")
            val p = new Properties()
            p.load(in)
            in.close()
            p
        }
        properties.getProperty("nativeTransportPort", "9042")
    }

    def populate_measured_data (): Unit =
    {
        println("creating Spark session")

        // create the configuration
        val configuration = new SparkConf(false)
            .setAppName("TimeSeriesStatsSuiteIT")
            .setMaster("local[*]")
            .set("spark.driver.memory", "2g")
            .set("spark.executor.memory", "2g")
            .set("spark.ui.port", "4041")
            .set("spark.ui.showConsoleProgress", "false")
            .set("spark.cassandra.connection.host", "localhost")
            .set("spark.cassandra.connection.port", cassandra_port)

        val session = SparkSession.builder.config(configuration).getOrCreate
        session.sparkContext.setLogLevel("WARN")

        val measurement_options = immutable.HashMap(
            "header" -> "false",
            "ignoreLeadingWhiteSpace" -> "false",
            "ignoreTrailingWhiteSpace" -> "false",
            "sep" -> ",",
            "quote" -> "\"",
            "escape" -> "\\",
            "encoding" -> "UTF-8",
            "comment" -> "#",
            "nullValue" -> "",
            "nanValue" -> "NaN",
            "positiveInf" -> "Inf",
            "negativeInf" -> "-Inf",
            "dateFormat" -> "yyyy-MM-dd",
            "timestampFormat" -> "yyyy-MM-dd HH:mm:ss",
            "mode" -> "DROPMALFORMED",
            "inferSchema" -> "true"
        )
        Schema(session, "/simulation_schema.sql", true).make(keyspace = KEYSPACE, replication = 1)
        println(s"reading $FILE_DEPOT$FILENAME0.csv")
        val df = session.sqlContext.read.format("csv").options(measurement_options).csv(s"$FILE_DEPOT$FILENAME0.csv")
        val ok = df.rdd.map(row => (row.getString(0), "energy", row.getTimestamp(1), 900000, row.getDouble(2), 0.0, "Wh"))
        println(s"saving to $KEYSPACE.measured_value")
        ok.saveToCassandra(KEYSPACE, "measured_value", SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        println("stopping Spark session")
        session.stop
    }

    @BeforeClass def before ()
    {
        // unpack the zip files
        if (!new File(s"$FILE_DEPOT$FILENAME0.csv").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME0.zip", FILE_DEPOT)
        println(s"populating $KEYSPACE.measured_value")
        populate_measured_data()
    }

    @AfterClass def after ()
    {
        // erase the unpacked file
        val _ = new File(s"$FILE_DEPOT$FILENAME0.csv").delete
    }
}