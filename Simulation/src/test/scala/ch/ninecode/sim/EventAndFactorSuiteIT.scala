package ch.ninecode.sim

import java.util.Properties

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import ch.ninecode.cim.CIMClasses

class EventAndFactorSuiteIT
{
    import EventAndFactorSuiteIT._

    val INPUT_KEYSPACE = "test"
    val OUTPUT_KEYSPACE = "test"
    val ID = "Basic" // note: EventAndFactorSuiteIT must run after SimulationSuiteIT

    @Test def events ()
    {
        val options = SimulationOptions (verbose = true, three_phase = true)
        val STANDARD_TRIGGERS: Iterable[Trigger] = List[Trigger] (
            // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
            HighTrigger ("voltage", 1, "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000),
            LowTrigger ("voltage", 1, "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000),
            HighTrigger ("voltage", 2, "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000),
            LowTrigger ("voltage", 2, "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000),

            // current >75% and >14h within 24h = orange
            // current >90% and >3h within 24h = red
            // current >110% for 15 minutes or more = red
            HighTrigger ("current", 1, "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000),
            HighTrigger ("current", 2, "ratedCurrent", 100.0, 0.90,  3 * 60 * 60 * 1000),
            HighTrigger ("current", 2, "ratedCurrent", 100.0, 1.10,      15 * 60 * 1000),

            // power >75% and >14h within 24h = orange
            // power >90% and >3h within 24h = red
            // power >110% for 15 minutes or more = red
            HighTrigger ("power", 1, "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000),
            HighTrigger ("power", 2, "ratedS", 630.0, 0.90,  3 * 60 * 60 * 1000),
            HighTrigger ("power", 2, "ratedS", 630.0, 1.10,      15 * 60 * 1000)
        )
        val check = SimulationEvents (STANDARD_TRIGGERS) (_Spark, options)
        val access = SimulationCassandraAccess  (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
        check.run (access)
    }

    @Test def coincidence_factor ()
    {
        val access = SimulationCassandraAccess  (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
        val options = SimulationOptions (verbose = true, unittest = true, three_phase = true)
        val coincidence = SimulationCoincidenceFactor (IGNORED_AGGREGATES) (_Spark, options)
        coincidence.run (access)
    }

    @Test def load_factor ()
    {
        val access = SimulationCassandraAccess  (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
        val options = SimulationOptions (verbose = true, unittest = true, three_phase = true)
        val load = SimulationLoadFactor (IGNORED_AGGREGATES) (_Spark, options)
        load.run (access)
    }

    @Test def responsibility_factor ()
    {
        val access = SimulationCassandraAccess  (_Spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, ID, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
        val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
        val options = SimulationOptions (verbose = true, unittest = true, three_phase = true)
        val responsibility = SimulationResponsibilityFactor (IGNORED_AGGREGATES) (_Spark, options)
        responsibility.run (access)
    }
}

object EventAndFactorSuiteIT
{
    var _Spark: SparkSession = _

    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/configuration.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val PORT: String = properties.getProperty ("nativeTransportPort", "9042")

    @BeforeClass def before (): Unit =
    {
        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SummarySuiteIT")
        configuration.setMaster ("local[*]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.graphx.pregel.checkpointInterval", "8")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.cassandra.connection.host", "localhost")
        configuration.set ("spark.cassandra.connection.port", PORT)

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the session
        val session = SparkSession.builder.config (configuration).getOrCreate
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        _Spark = session
    }

    @AfterClass def after ()
    {
        _Spark.stop
    }
}