package ch.ninecode.sim

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract case class SimulationAccess (
    spark: SparkSession,
    storage_level: StorageLevel,
    simulation: String,
    input_keyspace: String,
    output_keyspace: String,
    verbose: Boolean = false)
{
    def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame

    if (verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)

    type Trafo = String
    type Type = String
    type Mrid = String

    // ToDo: how can we not hard-code this period?
    val PERIOD: Int = 900000

    def getPeriod: Int = PERIOD

    def key_value (reference: String): DataFrame

    def mrids_for_recorders (typ: String): Array[(Trafo, Iterable[Mrid])]

    def mrid_raw_values (typ: String, mrids: Iterable[String], to_drop: Seq[String], period: Int = PERIOD): DataFrame

    //def events: DataFrame

    def recorders: DataFrame
}
