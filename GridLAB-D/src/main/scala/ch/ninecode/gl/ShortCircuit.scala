package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel

/**
 * Station short circuit power availability (pre-computed).
 * The available power and phase from the mid-voltage network if the primary is shorted.
 * @param mRID CIM MRID of the station.
 * @param Sk Available short circuit power available at the primary of the transformer (MW).
 * @param Ikw Short circuit phase angle at the primary of the transformer (degrees).
 * @param valid Indicates if the station was found in the pre-computed list, default values otherwise (and thus not valid).
 */
case class ShortCircuitData (
    mRID: String,
    Sk: Double,
    Ikw: Double,
    valid: Boolean)

class ShortCircuit (session: SparkSession, storage_level: StorageLevel) extends Serializable
{
    def read_csv (csv: String): RDD[ShortCircuitData] =
    {
        val customSchema = StructType (
            Array
            (
                StructField ("id", StringType),
                StructField ("Fehlerort", StringType),
                StructField ("Un", DoubleType),
                StructField ("Ikw...RST.", DoubleType),
                StructField ("Sk..RST.", DoubleType),
                StructField ("Beschreibung..SAP.Nr..", StringType),
                StructField ("Abgang", StringType),
                StructField ("NIS.ID", StringType),
                StructField ("NIS.Name", StringType)
            )
        )

        val df = session.sqlContext.read
            .format ("csv")
            .option ("header", "true")
            .schema (customSchema)
            .csv (csv)

        import session.sqlContext.implicits._
        val sc = df.map ( r => ShortCircuitData (r.getString (7), r.getDouble (4), r.getDouble (3), valid = true) ).rdd

        sc.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) => sc.checkpoint ()
            case None =>
        }

        sc
    }
}