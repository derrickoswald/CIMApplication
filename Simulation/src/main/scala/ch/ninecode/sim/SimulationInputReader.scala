package ch.ninecode.sim

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.util.HDFS

case class SimulationInputReader ( session: SparkSession, options: SimulationOptions ) extends HDFS
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    def readCIM (rdf: String, reader_options: Map[String, String] = Map()): Unit =
    {
        log.info(s"""reading "$rdf"""")
        val start = System.nanoTime()

        val storage_level: StorageLevel = options.cim_options.storage

        if (rdf.startsWith("s3") &&
            options.aws_s3a_access_key.trim.nonEmpty &&
            options.aws_s3a_secret_key.trim.nonEmpty)
            setAwsS3Options()

        val elements = session.read.format("ch.ninecode.cim").options(reader_options).load(rdf)
        log.info(s"${elements.count()} elements")
        val read = System.nanoTime()
        log.info(s"read: ${(read - start) / 1e9} seconds")
        session.sparkContext.getPersistentRDDs.find(_._2.name == "TopologicalIsland") match
        {
            case Some(_) =>
                log.info("topology exists")
            case None =>
                log.info("generating topology")
                generateTopology(storage_level, read)
        }
    }



    private def setAwsS3Options(): Unit =
    {
        val _ = System.setProperty("com.amazonaws.services.s3.enableV4", "true")
        session.sparkContext.hadoopConfiguration.set("com.amazonaws.services.s3.enableV4", "true")
        session.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", options.aws_s3a_access_key)
        session.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", options.aws_s3a_secret_key)
        session.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", "s3.eu-central-1.amazonaws.com")
        session.sparkContext.hadoopConfiguration.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")

    }

    private def generateTopology (storage_level: StorageLevel, read: Long): Unit =
    {
        val ntp = CIMNetworkTopologyProcessor(
            session,
            CIMTopologyOptions(
                identify_islands = true,
                storage = storage_level))
        val ele = ntp.process
        log.info(s"${ele.count()} elements after topology creation")
        val topology = System.nanoTime()
        log.info(s"topology: ${(topology - read) / 1e9} seconds")
    }

    def read_house_trafo_csv (house_trafo_mappings: String): Map[String, String] =
    {
        if (house_trafo_mappings.startsWith("s3") &&
            options.aws_s3a_access_key.trim.nonEmpty &&
            options.aws_s3a_secret_key.trim.nonEmpty)
            setAwsS3Options()

        val mappingDf = if (house_trafo_mappings.eq(""))
        {
            spark.emptyDataFrame
        }
        else
        {
            spark.read.format("csv").option("header", "true").option("delimiter", "\t").load(house_trafo_mappings)
        }

        import spark.implicits._
        mappingDf.map(row => (row.getString(1), row.getString(0))).collect().toMap
    }
}
