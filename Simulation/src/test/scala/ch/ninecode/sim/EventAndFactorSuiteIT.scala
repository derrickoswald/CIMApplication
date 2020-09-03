package ch.ninecode.sim

import java.util.Properties

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import ch.ninecode.cim.CIMClasses
import ch.ninecode.util.MainOptions

class EventAndFactorSuiteIT
{

    import EventAndFactorSuiteIT._

    val INPUT_KEYSPACE = "Test"
    val OUTPUT_KEYSPACE = "Test"
    val ID = "Basic" // note: EventAndFactorSuiteIT must run after SimulationSuiteIT

    @Test def events ()
    {
        val options = SimulationOptions (verbose = true, three_phase = true)
        val STANDARD_TRIGGERS: Iterable[Trigger] = List [Trigger](
            // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
            HighTrigger ("voltage", 1, "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000),
            LowTrigger ("voltage", 1, "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000),
            HighTrigger ("voltage", 2, "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000),
            LowTrigger ("voltage", 2, "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000),

            // current >75% and >14h within 24h = orange
            // current >90% and >3h within 24h = red
            // current >110% for 15 minutes or more = red
            HighTrigger ("current", 1, "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000),
            HighTrigger ("current", 2, "ratedCurrent", 100.0, 0.90, 3 * 60 * 60 * 1000),
            HighTrigger ("current", 2, "ratedCurrent", 100.0, 1.10, 15 * 60 * 1000),

            // power >75% and >14h within 24h = orange
            // power >90% and >3h within 24h = red
            // power >110% for 15 minutes or more = red
            HighTrigger ("power", 1, "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000),
            HighTrigger ("power", 2, "ratedS", 630.0, 0.90, 3 * 60 * 60 * 1000),
            HighTrigger ("power", 2, "ratedS", 630.0, 1.10, 15 * 60 * 1000)
        )
        val check = SimulationEvents (STANDARD_TRIGGERS)(_Spark, options)
        val access = SimulationCassandraAccess (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, verbose = true)
        check.run (access)
    }

    @Test def coincidence_factor ()
    {
        val access = SimulationCassandraAccess (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, verbose = true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List [SimulationAggregate]()
        val options = SimulationOptions (verbose = true, main_options = MainOptions (unittest = true), three_phase = true)
        val coincidence = SimulationCoincidenceFactor (IGNORED_AGGREGATES)(_Spark, options)
        coincidence.run (access)
    }

    @Test def load_factor ()
    {
        val access = SimulationCassandraAccess (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, verbose = true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List [SimulationAggregate]()
        val options = SimulationOptions (verbose = true, main_options = MainOptions (unittest = true), three_phase = true)
        val load = SimulationLoadFactor (IGNORED_AGGREGATES)(_Spark, options)
        load.run (access)
    }

    @Test def responsibility_factor ()
    {
        val access = SimulationCassandraAccess (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, verbose = true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List [SimulationAggregate]()
        val options = SimulationOptions (verbose = true, main_options = MainOptions (unittest = true), three_phase = true)
        val responsibility = SimulationResponsibilityFactor (IGNORED_AGGREGATES)(_Spark, options)
        responsibility.run (access)
    }
}

object EventAndFactorSuiteIT
{
    val DEFAULT_CASSANDRA_PORT = "9042"

    lazy val _Spark: SparkSession =
    {
        // create the configuration
        val configuration = new SparkConf (false)
            .setAppName ("SummarySuiteIT")
            .setMaster ("local[*]")
            .set ("spark.driver.memory", "2g")
            .set ("spark.executor.memory", "2g")
            .set ("spark.ui.port", "4041")
            .set ("spark.graphx.pregel.checkpointInterval", "8")
            .set ("spark.ui.showConsoleProgress", "false")
            .set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .set ("spark.cassandra.connection.host", "localhost")
            .set ("spark.cassandra.connection.port", PORT)
            // register CIMReader classes
            .registerKryoClasses (CIMClasses.list)
            // use the custom registrator
            .set ("spark.kryo.registrator", "ch.ninecode.cim.CIMRegistrator")
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the session
        SparkSession.builder.config (configuration).getOrCreate
    }

    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/configuration.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val PORT: String =
    {
        val port = properties.getProperty ("nativeTransportPort", "9042")
        if ("" == port)
            DEFAULT_CASSANDRA_PORT
        else
            port
    }

    @BeforeClass def before (): Unit =
    {
        _Spark.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
    }

    @AfterClass def after ()
    {
        _Spark.stop
    }
}