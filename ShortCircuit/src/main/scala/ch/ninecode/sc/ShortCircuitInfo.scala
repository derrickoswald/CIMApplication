package ch.ninecode.sc

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel

/**
 * Generate the RDD of available short circuit power and angle at each station.
 * Reads a CSV (in a particular format) to extract the ShortCircuitData information.
 *
 * @param session
 * @param storage_level
 */
class ShortCircuitInfo (session: SparkSession, storage_level: StorageLevel) extends Serializable
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