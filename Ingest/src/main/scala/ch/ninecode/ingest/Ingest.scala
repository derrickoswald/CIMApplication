package ch.ninecode.ingest

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp
import java.io.File
import java.nio.file.StandardCopyOption

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.sun.jndi.toolkit.url.Uri
import org.apache.log4j.LogManager
import org.apache.log4j.Level
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

    def runJob (job: IngestJob): Unit =
    {
        var jobWithLocalFiles = job.copy()
        val made = time("schema: %s seconds")
        {
            val schema = Schema(session, "/simulation_schema.sql", verbose = options.verbose)
            schema.make(keyspace = jobWithLocalFiles.keyspace, replication = jobWithLocalFiles.replication)
        }
        if (made)
        {
            val mapping_files = time(s"put ${jobWithLocalFiles.mapping}: %s seconds")
            {
                if (jobWithLocalFiles.nocopy)
                    Seq(jobWithLocalFiles.mapping)
                else
                {
                    if (jobWithLocalFiles.mapping.startsWith("s3"))
                    {
                        val uri = new Uri(jobWithLocalFiles.mapping)
                        val bucket = uri.getHost
                        val key = uri.getPath.substring(1)
                        val awsCreds = new BasicAWSCredentials(jobWithLocalFiles.aws_s3a_access_key, jobWithLocalFiles.aws_s3a_secret_key)
                        val s3Client = AmazonS3ClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                            .withRegion("eu-central-1")
                            .build();
                        val fullObject = s3Client.getObject(new GetObjectRequest(bucket, key))

                        val localFile = File.createTempFile("s3_ingest_", key.replace('\\','_').replace('/','_'))
                        val copyResult = Files.copy(fullObject.getObjectContent, localFile.toPath, StandardCopyOption.REPLACE_EXISTING)
                        if (copyResult > 0) {
                            log.info(s"copied ${jobWithLocalFiles.mapping} to ${localFile.toString}")
                        }
                        jobWithLocalFiles = jobWithLocalFiles.copy(mapping = localFile.toString.replace("\\","\\\\"))
                    }
                    putFile(s"${options.workdir}${base_name(jobWithLocalFiles.mapping)}", jobWithLocalFiles.mapping, jobWithLocalFiles.mapping.toLowerCase.endsWith(".zip"))

                }
            }
            mapping_files.headOption match
            {
                case Some(filename) =>
                    time("process: %s seconds")
                    {
                        val processor: IngestProcessor = jobWithLocalFiles.format.toString match
                        {
                            case "Belvis" => IngestBelvis(session, options)
                            case "LPEx" => IngestLPEx(session, options)
                            case "MSCONS" => IngestMSCONS(session, options)
                            case "Custom" => IngestCustom(session, options)
                            case "Parquet" => IngestParquet(session, options)
                            case "Nyquist" => IngestNyquist(session, options)
                        }
                        processor.process(filename, jobWithLocalFiles)
                    }
                    cleanUp(jobWithLocalFiles, filename)
                case None =>
            }
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
