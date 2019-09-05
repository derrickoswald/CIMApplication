package ch.ninecode.sim

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses

class SummarySuite extends org.scalatest.fixture.FunSuite
{
    val INPUT_KEYSPACE = "cimapplication"
    val OUTPUT_KEYSPACE = "test"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SimulationSuite")
        configuration.setMaster ("local[*]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.graphx.pregel.checkpointInterval", "8")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
        withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        finally
            session.stop // clean up the fixture
    }

    def last_simulation (spark: SparkSession): String =
    {
        val simulations = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulation", "keyspace" -> OUTPUT_KEYSPACE))
            .load
            .select ("id", "run_time")
        val id = simulations.schema.fieldIndex ("id")
        val time = simulations.schema.fieldIndex ("run_time")
        val work = simulations.rdd.map (row ⇒ (row.getString (id), row.getTimestamp (time)))
        work.sortBy (_._2.getTime, ascending = false).first._1
    }

    test ("events")
    {
        spark ⇒
            val id = last_simulation (spark)
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
            val check = SimulationEvents (STANDARD_TRIGGERS) (spark, options)
            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, id, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
            check.run (access)
    }

    test ("coincidence_factor")
    {
        spark ⇒
            val id = last_simulation (spark)
            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, id, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
            val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
            val options = SimulationOptions (verbose = true, unittest = true)
            val coincidence = SimulationCoincidenceFactor (IGNORED_AGGREGATES) (spark, options)
            coincidence.run (access)
    }

    test ("load_factor")
    {
        spark ⇒
            val id = last_simulation (spark)
            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, id, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
            val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
            val options = SimulationOptions (verbose = true, unittest = true)
            val load = SimulationLoadFactor (IGNORED_AGGREGATES) (spark, options)
            load.run (access)
    }

    test ("responsibility_factor")
    {
        spark ⇒
            val id = last_simulation (spark)
            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, id, INPUT_KEYSPACE, OUTPUT_KEYSPACE, true, true)
            val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
            val options = SimulationOptions (verbose = true, unittest = true)
            val responsibility = SimulationResponsibilityFactor (IGNORED_AGGREGATES) (spark, options)
            responsibility.run (access)
    }
}
