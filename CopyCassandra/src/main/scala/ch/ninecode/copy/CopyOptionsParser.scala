package ch.ninecode.copy

import ch.ninecode.util.SparkOptionsParser

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class CopyOptionsParser (options: CopyOptions) extends SparkOptionsParser[CopyOptions](options)
{
    opt[String]("source_host").valueName("Cassandra")
        .action((x, c) => c.copy(source_host = x))
        .text(s"Cassandra source connection host (listen_address or seed in cassandra.yaml) [${options.source_host}]")

    opt[Int]("source_port").valueName("<port>")
        .action((x, c) => c.copy(source_port = x))
        .text(s"Cassandra source connection port[${options.source_port}]")

    opt[String]("source_keyspace").valueName("<name>")
        .action((x, c) => c.copy(source_keyspace = x))
        .text(s"source Cassandra keyspace [${options.source_keyspace}]")

    opt[String]("target_host").valueName("Cassandra")
        .action((x, c) => c.copy(target_host = x))
        .text(s"Cassandra destination connection host (listen_address or seed in cassandra.yaml) [${options.target_host}]")

    opt[Int]("target_port").valueName("<port>")
        .action((x, c) => c.copy(target_port = x))
        .text(s"Cassandra destination connection port[${options.target_port}]")

    opt[String]("target_keyspace").valueName("<name>")
        .action((x, c) => c.copy(target_keyspace = x))
        .text(s"destination Cassandra keyspace [${options.target_keyspace}]")

    opt[Int]("target_replication").valueName("<#>")
        .action((x, c) => c.copy(target_replication = x))
        .text(s"destination keyspace replication if the Cassandra keyspace needs creation [${options.target_replication}]")

    note(
        """
Copies data from one Cassandra instance or keyspace to another through Spark.
"""
    )
}
