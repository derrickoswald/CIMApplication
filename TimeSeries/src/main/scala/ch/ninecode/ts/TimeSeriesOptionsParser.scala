package ch.ninecode.ts

import scopt.OptionParser

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

class TimeSeriesOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[TimeSeriesOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new  TimeSeriesOptions
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String,String]] = scopt.Read.reads (
        s =>
        {
            var ret = Map[String, String] ()
            val ss = s.split (",")
            for (p <- ss)
            {
                val kv = p.split ("=")
                ret = ret + ((kv(0), kv(1)))
            }
            ret
        }
    )

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
        text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    opt [String]("master").valueName ("MASTER_URL").
        action ((x, c) => c.copy (master = x)).
        text (s"spark://host:port, mesos://host:port, yarn, or local[*] [${default.master}]")

    opt [Map[String, String]]("opts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (spark_options = c.spark_options ++ x)).
        text (s"Spark options [${default.spark_options.map (x ⇒ x._1 + "=" + x._2).mkString (",")}]")

    opt [String]("storage_level").
        action ((x, c) => c.copy (storage = x)).
        text (s"storage level for RDD serialization [${default.storage}]")

    opt [LogLevels.Value]("logging").
        action ((x, c) => c.copy (log_level = x)).
        text (s"log level, one of ${LogLevels.values.iterator.mkString (",")} [${default.log_level}]")

    opt [String]("host").valueName ("<cassandra>").
        action ((x, c) ⇒ c.copy (host = x)).
        text (s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${default.host}]")

    opt [Int]("port").valueName ("<port_number>").
        action ((x, c) ⇒ c.copy (port = x)).
        text (s"Cassandra connection port [${default.port}]")

    opt [String]("keyspace").
        action ((x, c) ⇒ c.copy (keyspace = x)).
        text (s"target Cassandra keyspace [${default.keyspace}}]")

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

    note ("""
Analyze smart meter data for min, average, max and standard deviation.
""")

}