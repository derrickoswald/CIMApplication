package ch.ninecode.copy

import scopt.OptionParser

@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class CopyOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[CopyOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new CopyOptions
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    opt[Unit]("unittest")
        .hidden ()
        .action ((_, c) => { unittest = true; c.copy (unittest = true) })
        .text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    opt[LogLevels.Value]("log")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.mkString (",")} [${default.log_level}]")

    opt[String]("master").valueName ("MASTER_URL")
        .action ((x, c) ⇒ c.copy (master = x))
        .text (s"local[*], spark://host:port, mesos://host:port or yarn [${default.master}]")

    opt[String]("source_host").valueName ("Cassandra")
        .action ((x, c) ⇒ c.copy (source_host = x))
        .text (s"Cassandra source connection host (listen_address or seed in cassandra.yaml) [${default.source_host}]")

    opt[Int]("source_port").valueName ("<port>")
        .action ((x, c) ⇒ c.copy (source_port = x))
        .text (s"Cassandra source connection port [${default.source_port}]")

    opt[String]("source_keyspace").valueName ("<name>")
        .action ((x, c) ⇒ c.copy (source_keyspace = x))
        .text (s"source Cassandra keyspace [${default.source_keyspace}]")

    opt[String]("target_host").valueName ("Cassandra")
        .action ((x, c) ⇒ c.copy (target_host = x))
        .text (s"Cassandra destination connection host (listen_address or seed in cassandra.yaml) [${default.target_host}]")

    opt[Int]("target_port").valueName ("<port>")
        .action ((x, c) ⇒ c.copy (target_port = x))
        .text (s"Cassandra destination connection port [${default.target_port}]")

    opt[String]("target_keyspace").valueName ("<name>")
        .action ((x, c) ⇒ c.copy (target_keyspace = x))
        .text (s"destination Cassandra keyspace [${default.target_keyspace}]")

    opt[Int]("target_replication").valueName ("<#>")
        .action ((x, c) ⇒ c.copy (target_replication = x))
        .text (s"destination keyspace replication if the Cassandra keyspace needs creation [${default.target_replication}]")

    help ("help")
        .hidden ()
        .validate (Unit => { helpout = true; Right (Unit) })

    version ("version")
        .validate (Unit => { versionout = true; Right (Unit) })
        .text (
            {
                val version = APPLICATION_VERSION.split ("-")
                s"Scala: ${version(0)}, Spark: ${version(1)}, $APPLICATION_NAME: ${version(2)}"
            }
        )

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note (
        """
Copies data from one Cassandra instance or keyspace to another through Spark.
"""
    )
}
