package ch.ninecode.ingest

import java.text.SimpleDateFormat

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.Name
import ch.ninecode.model.ServiceLocation
import ch.ninecode.model.StringQuantity
import ch.ninecode.model.UserAttribute
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import scala.collection.Iterable

case class IngestParquet (session: SparkSession, options: IngestOptions) extends IngestProcessor with CIMRDD {
    if (options.verbose) LogManager.getLogger(getClass).setLevel(Level.INFO)

    implicit val spark: SparkSession = session

    def process (join_table: Map[String, String], job: IngestJob): Unit = {
        readCIM(job)
        val synthLoadProfile: RDD[MeasuredValue] = import_parquet(job)
        val mapping: RDD[(String, String)] = getMappingAoHas()
        val joinedData: RDD[MeasuredValue] = synthLoadProfile.keyBy(_._1).join(mapping).values.map(v => v._1.copy(_1 = v._2))

        def aggregateData(data: Iterable[MeasuredValue]): MeasuredValue = {
            val real_a = data.map(_._5).sum
            val imag_a = data.map(_._6).sum
            data.head.copy(_5 = real_a, _6 = imag_a)
        }

        val aggregatedData: RDD[MeasuredValue] = joinedData.groupBy(k => (k._1, k._3)).values.map(aggregateData)
        aggregatedData.saveToCassandra(job.keyspace, "measured_value", SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
    }

    def getMappingAoHas(): RDD[(String, String)] = {
        val name: RDD[Name] = getOrElse[Name]
        val serviceLocation: RDD[ServiceLocation] = getOrElse[ServiceLocation]
        val userAttribute: RDD[UserAttribute] = getOrElse[UserAttribute]
        val stringQuantity: RDD[StringQuantity] = getOrElse[StringQuantity]

        val MstHasMapping: RDD[(String, String)] = userAttribute.keyBy(_.value).join(stringQuantity.keyBy(_.id)).values.map(x => (x._1.name, x._2.value))
        val MstAoMapping: RDD[(String, String)] = serviceLocation.keyBy(_.id).join(name.keyBy(_.IdentifiedObject)).values.map(x => (x._1.sup.sup.sup.name, x._2.name))
        MstAoMapping.join(MstHasMapping).values
    }

    def import_parquet(job: IngestJob): RDD[MeasuredValue] = {
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX")

        def parquetMapping(row: Row): MeasuredValue = {
            val ao_id = row.getLong(0).toString
            val timestamp = dateFormat.parse(row.getString(1)).getTime
            val real_a = row.getDouble(2)
            val imag_a = row.getDouble(3)
            (ao_id, "energy", timestamp, 900000, real_a, imag_a, "Wh")
        }

        val parquetFileDF = session.read.parquet(job.datafiles: _*)
        parquetFileDF.rdd.map(parquetMapping)
    }

    def readCIM(job: IngestJob): Unit = {
        val start = System.nanoTime
        val thisFiles = job.mapping.split(",")
        val readOptions = Map[String, String](
            "path" -> job.mapping,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo_islands" -> "false",
            "ch.ninecode.cim.debug" -> "true",
            "ch.ninecode.cim.do_deduplication" -> "true"
        )
        val elements = session.sqlContext.read.format("ch.ninecode.cim")
            .options(readOptions)
            .load(thisFiles: _*)
            .persist(StorageLevel.MEMORY_AND_DISK_SER)
        println(elements.count + " elements")
        val read = System.nanoTime
        println("read: " + (read - start) / 1e9 + " seconds")
    }
}
