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

