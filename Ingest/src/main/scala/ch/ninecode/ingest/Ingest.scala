package ch.ninecode.ingest

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Schema
import ch.ninecode.util.SparkInitializer
import ch.ninecode.util.SparkOptions

/**
 * Import measured data into Cassandra.
 *
 * Copies files to HDFS, reads them into Spark,
 * executes a join across a CH### to mRID mapping table and stores them in Cassandra.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Spark master, Cassandra host, processing details, etc.
 */
class Ingest (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose)
    {
        LogManager.getLogger(getClass).setLevel(Level.INFO)
        LogManager.getLogger("ch.ninecode.util.Schema").setLevel(Level.INFO)
    }

    case class Reading (mRID: String, time: Timestamp, period: Int, values: Array[Double])

    def readFile (file: String): Array[Byte] =
    {
        try
            Files.readAllBytes(Paths.get(file))
        catch
        {
            case e: Exception =>
                log.error(s"""ingest failed for file "$file"""", e)
                Array()
        }
    }

    def process (filename: String, job: IngestJob): Unit =
    {
        log.error("abstract class Ingest call to process()")
    }

    def setS3Configurations (job: IngestJob): Unit =
    {
        if (job.aws_s3a_access_key.trim.nonEmpty && job.aws_s3a_secret_key.trim.nonEmpty)
        {
            val _ = System.setProperty("com.amazonaws.services.s3.enableV4", "true")
            session.sparkContext.hadoopConfiguration.set("com.amazonaws.services.s3.enableV4", "true")
            session.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", job.aws_s3a_access_key)
            session.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", job.aws_s3a_secret_key)
            session.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", "s3.eu-central-1.amazonaws.com")
            session.sparkContext.hadoopConfiguration.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        }
    }

    def runIngestProcessor(job: IngestJob): Unit = {
        val mapping_files = time(s"put ${job.mapping}: %s seconds")
        {
            if (job.nocopy)
                Seq(job.mapping)
            else
                putFile(s"${options.workdir}${base_name(job.mapping)}", job.mapping, job.mapping.toLowerCase.endsWith(".zip"))
        }
        mapping_files.headOption match
        {
            case Some(filename) =>
                time("process: %s seconds")
                {
                    val processor: IngestProcessor = job.format.toString match
                    {
                        case "Belvis" => IngestBelvis(session, options)
                        case "BelvisPlus" => IngestBelvisPlus(session, options)
                        case "LPEx" => IngestLPEx(session, options)
                        case "MSCONS" => IngestMSCONS(session, options)
                        case "Custom" => IngestCustom(session, options)
                        case "Parquet" => IngestParquet(session, options)
                        case "Nyquist" => IngestNyquist(session, options)
                    }
                    processor.process(filename, job)
                }
                cleanUp(job, filename)
            case None =>
        }
    }

    def runJob (job: IngestJob): Unit =
    {
        setS3Configurations(job)
        val made = time("schema: %s seconds")
        {
            val schema = Schema(session, "/simulation_schema.sql", verbose = options.verbose)
            schema.make(keyspace = job.keyspace, replication = job.replication)
        }
        if (made)
        {
            runIngestProcessor(job)
        }
    }

    def run (): Unit =
    {
        // the ingestion jobs to do
        val jobs: Seq[IngestJob] = IngestJob.getAll(options)
        jobs.foreach(runJob)
    }
}

object Ingest extends SparkInitializer[IngestOptions] with Main
{
    def run (options: IngestOptions): Unit =
    {
        if (options.main_options.valid)
        {
            org.apache.log4j.LogManager.getLogger(getClass).setLevel(org.apache.log4j.Level.INFO)
            val session: SparkSession = createSession(options)
            time("execution: %s seconds")
            {
                new Ingest(session, options).run()
            }
        }
    }

    def main (args: Array[String])
    {
        val have = scala.util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error(s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit(1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set(
                jarForObject(com.datastax.spark.connector.mapper.ColumnMapper),
                jarForObject(IngestOptions())
            ).toArray

            // initialize the default options
            val default = IngestOptions(
                main_options = MainOptions(application_name, application_version),
                spark_options = SparkOptions(jars = jars),
            )

            // parse the command line arguments
            new IngestOptionsParser(default).parse(args, default) match
            {
                case Some(options) =>
                    // execute the main program if everything checks out
                    run(options)
                    if (!options.main_options.unittest)
                        sys.exit(0)
                case None =>
                    sys.exit(1)
            }
        }
    }
}
