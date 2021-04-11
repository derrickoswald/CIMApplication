package ch.ninecode.copy

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 *
 * Options for copy between Cassandra instances.
 *
 * @param main_options       main() program options
 * @param spark_options      Spark session options
 * @param source_host        Cassandra source connection host.
 * @param source_port        Cassandra source connection port.
 * @param source_keyspace    Cassandra source keyspace.
 * @param target_host        Cassandra destination connection host.
 * @param target_port        Cassandra destination connection port.
 * @param target_keyspace    Cassandra destination keyspace.
 * @param target_replication Cassandra destination keyspace replication factor.
 *
 */
case class CopyOptions (
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    source_host: String = "localhost",
    source_port: Int = 9042,
    source_keyspace: String = "cimapplication",
    target_host: String = "localhost",
    target_port: Int = 9042,
    target_keyspace: String = "cimapplication",
    target_replication: Int = 1
) extends Mainable with JSONAble[CopyOptions] with Sparkable
{
    def toJSON: String = CopyOptions.toJSON(this)

    def fromJSON (text: String): Either[String, CopyOptions] = CopyOptions.fromJSON(text)
}
object CopyOptions extends JSON[CopyOptions]
{
    def schemaResourceName: String = "CopyOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/CopyOptionsSchema.json" -> "resource:CopyOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap
    def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        SparkOptions.customSerializers)
}