package ch.ninecode.copy

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

object Formats extends Enumeration
{
    type Formats = Value
    val Belvis, LPEx = Value
}

case class CopyOptions
(
    /**
     * False if either help or version requested (i.e. don't proceed with execution).
     */
    var valid: Boolean = true,

    /**
     * If <code>true</code>, don't call sys.exit().
     */
    unittest: Boolean = false,

    /**
     * Logging level.
     */
    log_level: LogLevels.Value = LogLevels.OFF,

    /**
     * Spark master.
     */
    master: String = "",

    /**
     * Spark options.
     */
    options: Map[String, String] = Map (),

    /**
     * Cassandra source connection host.
     */
    source_host: String = "localhost",

    /**
     * Cassandra source connection port.
     */
    source_port: Int = 9042,

    /**
     * Cassandra source keyspace.
     */
    source_keyspace: String = "cimapplication",

    /**
     * Cassandra destination connection host.
     */
    target_host: String = "localhost",

    /**
     * Cassandra destination connection port.
     */
    target_port: Int = 9042,

    /**
     * Cassandra destination keyspace.
     */
    target_keyspace: String = "cimapplication",

    /**
     * Cassandra destination keyspace replication factor.
     */
    target_replication: Int = 1
)