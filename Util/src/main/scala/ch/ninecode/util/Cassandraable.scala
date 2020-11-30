package ch.ninecode.util

/**
 * Options when using Cassandra.
 */
trait Cassandraable
{
    /**
     * Contains Cassandra specific options.
     */
    var cassandra_options: CassandraOptions
}
