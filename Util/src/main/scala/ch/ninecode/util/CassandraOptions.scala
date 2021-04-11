package ch.ninecode.util

/**
 * Options for Cassandra.
 *
 * @param host    Cassandra connection host (listen_address or seed in cassandra.yaml)
 * @param port    Cassandra connection port
 */
case class CassandraOptions (
    host: String = "localhost",
    port: Int = 9042,
    user: String = "",
    password: String = ""
)
{
    def toJSON: String = CassandraOptions.toJSON(this)
    def fromJSON (text: String): Either[String, CassandraOptions] = CassandraOptions.fromJSON(text)
}
object CassandraOptions extends JSON[CassandraOptions]
{
    def schemaResourceName: String = "CassandraOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/CassandraOptionsSchema.json" -> "resource:CassandraOptionsSchema.json"
    )
    def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}