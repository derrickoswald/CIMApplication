package ch.ninecode.ingest

import ch.ninecode.util.CassandraOptions
import ch.ninecode.util.Cassandraable
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Ingest meter readings options.
 *
 * @param main_options      main() program options
 * @param spark_options     Spark session options
 * @param cassandra_options Cassandra options
 * @param verbose           If <code>true</code>, emit progress messages.
 * @param workdir           Working directory for unzipping and copying if nocopy is <code>false</code>.
 * @param ingestions        Ingest jobs.
 */
case class IngestOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cassandra_options: CassandraOptions = CassandraOptions(),
    verbose: Boolean = false,
    workdir: String = s"${IngestOptions.cwd}/work/",
    ingestions: Seq[IngestJob] = Seq()
) extends Cassandraable with Sparkable with Mainable with JSONAble[IngestOptions]
{
    def toJSON: String = IngestOptions.toJSON(this)

    def fromJSON (text: String): Either[String, IngestOptions] = IngestOptions.fromJSON(text)
}
object IngestOptions extends JSON[IngestOptions]
{
    def schemaResourceName: String = "IngestOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/IngestOptionsSchema.json" -> "resource:IngestOptionsSchema.json"
    ) ++ IngestJob.schemaUriMap ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CassandraOptions.schemaUriMap
    def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        SparkOptions.customSerializers,
        CassandraOptions.customSerializers,
        IngestJob.customSerializers)

    def cwd: String =
    {
        val pwd = new java.io.File(".").getCanonicalPath
        if (pwd.endsWith("."))
            pwd.substring(0, pwd.length - 1)
        else
            pwd
    }
}
