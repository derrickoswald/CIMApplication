package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scopt.OptionParser

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

object Operations extends Enumeration
{
    type Operations = Value
    val Statistics, Meta, Model, Synthesize = Value
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

    implicit val OperationsRead: scopt.Read[Operations.Value] = scopt.Read.reads (Operations.withName)

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

    implicit val IntArrayRead: scopt.Read[Array[Int]] = scopt.Read.reads (_.split (",").map (_.toInt))

    implicit val DoubleArrayRead: scopt.Read[Array[Double]] = scopt.Read.reads (_.split (",").map (_.toDouble))

    implicit val CalendarRead: scopt.Read[Calendar] = scopt.Read.calendarRead ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    lazy val formatter: SimpleDateFormat =
    {
        val format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        format.setTimeZone (TimeZone.getTimeZone ("UTC"));
        format
    }

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    version ("version").
        validate (Unit => { versionout = true; Right (Unit) }).
            text ("Scala: %s, Spark: %s, %s: %s".format (
                APPLICATION_VERSION.split ("-")(0),
                APPLICATION_VERSION.split ("-")(1),
                APPLICATION_NAME,
                APPLICATION_VERSION.split ("-")(2)
            )
        )

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
        action ((x, c) => c.copy (storage_level = x)).
        text (s"storage level for RDD serialization [${default.storage_level}]")

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
        text (s"target Cassandra keyspace [${default.keyspace}]")

    opt [String]("model_file").
        action ((x, c) ⇒ c.copy (model_file = x)).
        text (s"model file name [${default.model_file}]")

    cmd (Operations.Statistics.toString)
        .action ((_, c) => c.copy (operation = Operations.Statistics) )
        .text (s"    perform statistical analysis${if (default.operation == Operations.Statistics) " - default" else ""}")

    cmd (Operations.Meta.toString)
        .action ((_, c) => c.copy (operation = Operations.Meta) )
        .text (s"    perform metadata extraction${if (default.operation == Operations.Meta) " - default" else ""}")
        .children (
            opt [String]("meta_file").
                action ((x, c) ⇒ c.copy (meta_file = x)).
                text (s"metadata file name [${default.meta_file}]")

        )

    cmd (Operations.Model.toString)
        .action ((_, c) => c.copy (operation = Operations.Model) )
        .text (s"    create a model${if (default.operation == Operations.Model) " - default" else ""}")
        .children (
            opt [Array[Int]]("tree_depth").
            action ((x, c) => c.copy (tree_depth = x)).
            text (s"decision tree depth, or array for hyperparameter tuning [${default.tree_depth.mkString (",")}]"),

            opt [Array[Int]]("bins").
            action ((x, c) => c.copy (bins = x)).
            text (s"maximum number of bins for discretizing, or array for hyperparameter tuning [${default.bins.mkString (",")}]"),

            opt [Array[Double]]("info").
            action ((x, c) => c.copy (info = x)).
            text (s"minimum information gain for a split, or array for hyperparameter tuning [${default.info.mkString (",")}]"),

            opt [Long]("seed").
            action ((x, c) => c.copy (seed = x)).
            text (s"seed value for random number generation [${default.seed}]")
        )

    cmd (Operations.Synthesize.toString)
        .action ((_, c) => c.copy (operation = Operations.Synthesize) )
        .text (s"    generate a synthetic load profile${if (default.operation == Operations.Synthesize) " - default" else ""}")
        .children (
            opt [String]("synthesis").
                action ((x, c) => c.copy (synthesis = x)).
                text (s"name, id, or mRID for the synthesized time series [${default.synthesis}]"),

            opt [Calendar]("start").
                action ((x, c) => c.copy (start = x)).
                text (s"starting time for the synthesis [${formatter.format (default.start.getTime)}]"),

            opt [Calendar]("end").
                action ((x, c) => c.copy (end = x)).
                text (s"ending time for the synthesis [${formatter.format (default.end.getTime)}]"),

            opt [Int]("period").
                action ((x, c) => c.copy (period = x)).
                text (s"number of milliseconds between synthesized readings [${default.period}]"),

            opt [Double]("yearly_kWh").
                action ((x, c) => c.copy (yearly_kWh = x)).
                text (s"energy used by the synthesized time series per year (kWh) [${default.yearly_kWh}]")
        )

    help ("help").
        hidden ().
        validate (Unit => { helpout = true; Right (Unit) })

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note ("""
Performs three functions:
Analyze smart meter data for min, average, max and standard deviation.
Generate a decision tree regression model.
Synthesize load profiles from the model.
""")

}