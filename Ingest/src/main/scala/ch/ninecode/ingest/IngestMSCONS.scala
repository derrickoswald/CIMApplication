package ch.ninecode.ingest

import java.util.Calendar

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import ch.ninecode.mscons.MSCONSOptions
import ch.ninecode.mscons.MSCONSParser
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement
import com.datastax.spark.connector.rdd.ReadConf

case class IngestMSCONS (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose)
    {
        LogManager.getLogger (getClass).setLevel (Level.INFO)
        LogManager.getLogger ("ch.ninecode.mscons.MSCONSParser").setLevel (Level.INFO)
    }
    implicit val spark: SparkSession = session

    /**
     * Make a three phase data element from record returned by MSCONS parser.
     *
     * @param join_table the mapping from meter id to mRID
     * @param record     a reading from the MSCONS parser
     */
    def to_data_element (join_table: Map[String, String])
        (record: (String, String, Calendar, Int, Double, Double, String)): Option[ThreePhaseComplexDataElement] =
    {
        join_table.get (record._1) match
        {
            case Some (mrid) =>
                Some (ThreePhaseComplexDataElement (mrid, record._3.getTimeInMillis, Complex (record._5, record._6), record._7))
            case None =>
                None
        }
    }

    /**
     * Apply the MSCONS parser to parse a file.
     *
     * @param filename the file to parse
     * @return the sequence of data elements found in the file
     */
    def processOneFile (join_table: Map[String, String], job: IngestJob)(filename: String): Seq[ThreePhaseComplexDataElement] =
    {
        val parser = MSCONSParser (MSCONSOptions ())
        parser.parse (filename)
            .flatMap (to_data_element (join_table))
            .filter (x => (x.millis >= job.mintime) && (x.millis <= job.maxtime))
    }

    /**
     * Sum the elements for an mRID.
     *
     * @param m the measurements for an mRID
     * @return the aggregated (sum) of elements
     */
    def complex2 (m: Iterable[ThreePhaseComplexDataElement]): Option[ThreePhaseComplexDataElement] =
    {
        m.headOption match
        {
            case Some (value) =>
                val sum = Complex (m.map (_.value_a.re).sum, m.map (_.value_a.im).sum)
                Some (ThreePhaseComplexDataElement (value.element, value.millis, sum, value.units))
            case _ =>
                None
        }
    }

    /**
     * Prepare the data element for insertion into Cassandra.
     *
     * @param record the three phase data element
     * @return a tuple suitable for Cassandra:
     *         ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     */
    def split (record: ThreePhaseComplexDataElement): (String, String, Long, Int, Double, Double, String) =
    {
        (record.element, "energy", record.millis, 900000, record.value_a.re, record.value_a.im, record.units)
    }

    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping (session, filename, job)
        val all_files = job.datafiles.flatMap (getFiles (job, options.workdir))
        val some_files = all_files.take (6).mkString (",")
        val more_files = all_files.length > 6
        time (s"processed files [${some_files}${if (more_files) "..." else ""}]: %s seconds")
        {
            val mscons_files = session.sparkContext.parallelize (all_files)

            // read all files into one RDD
            val raw = mscons_files.flatMap (processOneFile (join_table, job))
            // combine real and imaginary parts
            if (job.mode == Modes.Append)
            {
                val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
                implicit val configuration: ReadConf =
                    ReadConf
                        .fromSparkConf (session.sparkContext.getConf)
                        .copy (splitCount = Some (executors))
                val df = session
                    .sparkContext
                    .cassandraTable (job.keyspace, "measured_value")
                    .select ("mrid", "time", "period", "real_a", "imag_a", "units")
                    .map (
                        row =>
                        {
                            ThreePhaseComplexDataElement (
                                row.getString ("mrid"),
                                row.getLong ("time"),
                                Complex (row.getDouble ("real_a"), row.getDouble ("imag_a")),
                                row.getString ("units"))
                        }
                    )
                val unioned = raw.union (df)
                val grouped = unioned.groupBy (x => (x.element, x.millis)).values.flatMap (complex2).map (split)
                grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
            } else
            {
                val grouped = raw.groupBy (x => (x.element, x.millis)).values.flatMap (complex2).map (split)
                grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
            }
            if (!job.nocopy)
                all_files.foreach (x => hdfs.delete (new Path (x), false))

        }
    }
}
