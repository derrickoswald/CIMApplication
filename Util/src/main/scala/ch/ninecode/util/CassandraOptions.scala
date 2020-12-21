package ch.ninecode.util

/**
 * Options for Cassandra.
 *
 * @param host    Cassandra connection host (listen_address or seed in cassandra.yaml)
 * @param port    Cassandra connection port
 */
case class CassandraOptions (
    host: String = "localhost",
    port: Int = 9042
)
{
    def toJSON: String = CassandraOptions.toJSON(this)
}
object CassandraOptions extends JSON[CassandraOptions]
{
    def schemaResourceName: String = "CassandraOptionsSchema.json"
}