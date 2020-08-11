package ch.ninecode.copy

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 * Options for copy between Cassandra instances.
 *
 * @param valid              False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest           If <code>true</code>, don't call sys.exit().
 * @param log_level          Logging level.
 * @param master             Spark master.
 * @param options            Spark options.
 * @param source_host        Cassandra source connection host.
 * @param source_port        Cassandra source connection port.
 * @param source_keyspace    Cassandra source keyspace.
 * @param target_host        Cassandra destination connection host.
 * @param target_port        Cassandra destination connection port.
 * @param target_keyspace    Cassandra destination keyspace.
 * @param target_replication Cassandra destination keyspace replication factor.
 */
case class CopyOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    log_level: LogLevels.Value = LogLevels.OFF,
    master: String = "",
    options: Map[String, String] = Map (),
    source_host: String = "localhost",
    source_port: Int = 9042,
    source_keyspace: String = "cimapplication",
    target_host: String = "localhost",
    target_port: Int = 9042,
    target_keyspace: String = "cimapplication",
    target_replication: Int = 1
)