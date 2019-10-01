package ch.ninecode.copy

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scopt.OptionParser

class CopyOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[CopyOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new CopyOptions
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)
    implicit val FormatsRead: scopt.Read[Formats.Value] = scopt.Read.reads (Formats.withName)

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    opt [Unit]("unittest").
        hidden ().
        action ((_, c) => { unittest = true; c.copy (unittest = true) }).
        text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

    opt [LogLevels.Value]("log").
        action ((x, c) => c.copy (log_level = x)).
        text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

    opt [String]("master").valueName ("MASTER_URL").
        action ((x, c) ⇒ c.copy (master = x)).
        text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

    opt [String]("source_host").valueName ("Cassandra").
        action ((x, c) ⇒ c.copy (source_host = x)).
        text ("Cassandra source connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.source_host))

    opt [Int]("source_port").valueName ("<port>").
        action ((x, c) ⇒ c.copy (source_port = x)).
        text ("Cassandra source connection port [%s]".format (default.source_port))

    opt [String]("source_keyspace").valueName ("<name>").
        action ((x, c) ⇒ c.copy (source_keyspace = x)).
        text ("source Cassandra keyspace [%s]".format (default.source_keyspace))

    opt [String]("target_host").valueName ("Cassandra").
        action ((x, c) ⇒ c.copy (target_host = x)).
        text ("Cassandra destination connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.target_host))

    opt [Int]("target_port").valueName ("<port>").
        action ((x, c) ⇒ c.copy (target_port = x)).
        text ("Cassandra destination connection port [%s]".format (default.target_port))

    opt [String]("target_keyspace").valueName ("<name>").
        action ((x, c) ⇒ c.copy (target_keyspace = x)).
        text ("destination Cassandra keyspace [%s]".format (default.target_keyspace))

    opt [Int]("target_replication").valueName ("<#>").
        action ((x, c) ⇒ c.copy (target_replication = x)).
        text ("destination keyspace replication if the Cassandra keyspace needs creation [%s]".format (default.target_replication))

    help ("help").
        hidden ().
        validate (Unit => { helpout = true; Right (Unit) })

    version ("version").
        validate (Unit => { versionout = true; Right (Unit) }).
            text ("Scala: %s, Spark: %s, %s: %s".format (
                APPLICATION_VERSION.split ("-")(0),
                APPLICATION_VERSION.split ("-")(1),
                APPLICATION_NAME,
                APPLICATION_VERSION.split ("-")(2)
            )
        )

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note (
        """
Copies data from one Cassandra instance or keyspace to another through Spark.
"""
    )
}
