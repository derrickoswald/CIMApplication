package ch.ninecode.ingest

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp

import org.apache.log4j.LogManager
import org.apache.log4j.Level
import org.apache.spark.sql.SparkSession
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
    if (options.verbose)
    {
        LogManager.getLogger (getClass).setLevel (Level.INFO)
        LogManager.getLogger ("ch.ninecode.util.Schema").setLevel (Level.INFO)
    }

    case class Reading (mRID: String, time: Timestamp, period: Int, values: Array[Double])

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

    def process (filename: String, job: IngestJob): Unit =
    {
        log.error ("abstract class Ingest call to process()")
    }

    def runJob (job: IngestJob): Unit =
    {
        val made = time ("schema: %s seconds")
        {
            val schema = Schema (session, "/simulation_schema.sql", verbose = options.verbose)
            schema.make (keyspace = job.keyspace, replication = job.replication)
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
                    time ("process: %s seconds")
                    {
                        val processor: IngestProcessor = job.format.toString match
                        {
                            case "Belvis" => IngestBelvis (session, options)
                            case "LPEx" =>   IngestLPEx   (session, options)
                            case "MSCONS" => IngestMSCONS (session, options)
                            case "Custom" => IngestCustom (session, options)
                            case "Parquet" => IngestParquet (session, options)
                        }
                        processor.process (filename, job)
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
