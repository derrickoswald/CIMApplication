package ch.ninecode.ingest

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp

import org.apache.log4j.LogManager
import org.apache.log4j.Level
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataType

import ch.ninecode.util.Schema

/**
 * Import measured data into Cassandra.
 *
 * Copies files to HDFS, reads them into Spark, executes a join across a CH### to mRID mapping table and stores them in Cassandra.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Spark master, Cassandra host, processing details, etc.
 */
class Ingest (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)

    case class Reading (mRID: String, time: Timestamp, period: Int, values: Array[Double])

    def map_csv_options: Map[String, String] =
    {
        Map[String, String](
            "header" -> "true",
            "ignoreLeadingWhiteSpace" -> "false",
            "ignoreTrailingWhiteSpace" -> "false",
            "sep" -> ";",
            "quote" -> "\"",
            "escape" -> "\\",
            "encoding" -> "UTF-8",
            "comment" -> "#",
            "nullValue" -> "",
            "nanValue" -> "NaN",
            "positiveInf" -> "Inf",
            "negativeInf" -> "-Inf",
            "dateFormat" -> "yyyy-MM-dd",
            "timestampFormat" -> "dd.MM.yyyy HH:mm",
            "mode" -> "PERMISSIVE",
            "inferSchema" -> "true")
    }

    def readFile (file: String): Array[Byte] =
    {
        try
            Files.readAllBytes (Paths.get (file))
        catch
        {
            case e: Exception =>
                log.error (s"""ingest failed for file "$file"""", e)
                Array ()
        }
    }

    def extractor (datatype: DataType): (Row, Int) => String =
    {
        datatype.simpleString match
        {
            case "decimal" | "double" | "float" =>
                (row: Row, column: Int) => row.getDouble (column).toString
            case "string" =>
                (row: Row, column: Int) => row.getString (column)
            case "integer" | "int" | "short" | "smallint" =>
                (row: Row, column: Int) => row.getInt (column).toString
            case "long" =>
                (row: Row, column: Int) => row.getLong (column).toString
            case _ =>
                (_: Row, _: Int) => s"unsupported datatype ${datatype.toString}"
        }
    }

    def process (join_table: Map[String, String], job: IngestJob): Unit =
    {
        log.error ("abstract class Ingest call to process()")
    }

    def runJob (job: IngestJob): Unit =
    {
        val made = time ("schema: %s seconds")
        {
            val schema = Schema (session, "/simulation_schema.sql", job.keyspace, job.replication, verbose = options.verbose)
            schema.make ()
        }
        if (made)
        {
            val mapping_files = time (s"put ${job.mapping}: %s seconds")
            {
                if (job.nocopy)
                    Seq (job.mapping)
                else
                    putFile (s"${options.workdir}${base_name (job.mapping)}", job.mapping, job.mapping.toLowerCase.endsWith (".zip"))
            }
            mapping_files.headOption match
            {
                case Some (filename) =>
                    val dataframe = time (s"read $filename: %s seconds")
                    {
                        session.sqlContext.read.format ("csv").options (map_csv_options).csv (filename)
                    }
                    val join_table = time ("map: %s seconds")
                    {
                        val ch_number = dataframe.schema.fieldIndex (job.metercol)
                        val nis_number = dataframe.schema.fieldIndex (job.mridcol)
                        val extract = extractor (dataframe.schema.fields (ch_number).dataType)
                        dataframe.rdd.map (row => (extract (row, ch_number), row.getString (nis_number))).filter (_._2 != null).collect.toMap
                    }
                    time ("process: %s seconds")
                    {
                        val processor: IngestProcessor = job.format.toString match
                        {
                            case "Belvis" => IngestBelvis (session, options)
                            case "LPEx" =>   IngestLPEx   (session, options)
                            case "MSCONS" => IngestMSCONS (session, options)
                            case "Custom" => IngestCustom (session, options)
                        }
                        processor.process (join_table, job)
                    }
                    cleanUp (job, filename)
                case None =>
            }
        }
    }

    def run (): Unit =
    {
        // the ingestion jobs to do
        val jobs: Seq[IngestJob] = IngestJob.getAll (options)
        jobs.foreach (runJob)
    }
}
