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
    val Statistics, Meta, Model, MetaModel, SimpleMetaModel, Synthesize = Value
}

@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class TimeSeriesOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[TimeSeriesOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new  TimeSeriesOptions
    var unittest = false
    var helpout = false
    var versionout = false
    val COMMA = ","
    val EQUAL = "="

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val OperationsRead: scopt.Read[Operations.Value] = scopt.Read.reads (Operations.withName)

    implicit val string_string_mapRead: scopt.Read[Map[String,String]] = scopt.Read.reads (
        s =>
        {
            val pairs = for (p <- s.split (COMMA); kv = p.split (EQUAL))
                yield
                {
                    if (2 == kv.length)
                        Some ((kv(0), kv(1)))
                    else
                    {
                        reportError (s"unrecognized key=value pair '$p'")
                        helpout = true
                        None
                    }
                }
            pairs.flatten.toMap
        }
    )

    implicit val string_int_mapRead: scopt.Read[Map[String,Int]] = scopt.Read.reads (
        s =>
        {
            def toInt (s: String): Option[Int] =
            {
                try
                {
                    Some(s.toInt)
                }
                catch
                {
                    case _: Exception => None
                }
            }
            val pairs = for (p <- s.split (COMMA); kv = p.split (EQUAL))
                yield
                    {
                        if (2 == kv.length)
                            toInt (kv(1)) match
                            {
                                case Some (i) => Some ((kv(0), i))
                                case _ =>
                                    reportError (s"unrecognized integer '${kv(1)}'")
                                    helpout = true
                                    None
                            }
                        else
                        {
                            reportError (s"unrecognized key=value pair '$p'")
                            helpout = true
                            None
                        }
                    }
            pairs.flatten.toMap
        }
    )

    implicit val IntArrayRead: scopt.Read[Array[Int]] = scopt.Read.reads (_.split (COMMA).map (_.toInt))

    implicit val DoubleArrayRead: scopt.Read[Array[Double]] = scopt.Read.reads (_.split (COMMA).map (_.toDouble))

    implicit val CalendarRead: scopt.Read[Calendar] = scopt.Read.calendarRead ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    lazy val formatter: SimpleDateFormat =
    {
        val format = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        format.setTimeZone (TimeZone.getTimeZone ("UTC"))
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

    def perYear (average_Wh: Double): Double =
    {
        val periods = 24 * 60 * 60 * 1000 / default.period
        average_Wh * periods * 365.25 / 1000.0
    }

    def perPeriod (yearly_kWh: Double): Double =
    {
        val periods = 24 * 60 * 60 * 1000 / default.period
        yearly_kWh * 1000.0 / 365.25 / periods
    }

    version ("version")
        .validate (Unit => { versionout = true; Right (Unit) })
        .text (
            {
                val version = APPLICATION_VERSION.split ("-")
                s"Scala: ${version(0)}, Spark: ${version(1)}, $APPLICATION_NAME: ${version(2)}"
            }
        )

    opt[Unit]("unittest")
        .hidden ()
        .action ((_, c) => { unittest = true; c.copy (unittest = true) })
        .text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    opt[String]("master").valueName ("MASTER_URL")
        .action ((x, c) => c.copy (master = x))
        .text (s"spark://host:port, mesos://host:port, yarn, or local[*] [${default.master}]")

    opt[Map[String, String]]("opts").valueName ("k1=v1,k2=v2")
        .action ((x, c) => c.copy (spark_options = c.spark_options ++ x))
        .text (s"Spark options [${default.spark_options.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")

    opt[String]("storage_level")
        .action ((x, c) => c.copy (storage_level = x))
        .text (s"storage level for RDD serialization [${default.storage_level}]")

    opt[LogLevels.Value]("logging")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.iterator.mkString (COMMA)} [${default.log_level}]")

    opt[String]("host").valueName ("<cassandra>")
        .action ((x, c) => c.copy (host = x))
        .text (s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${default.host}]")

    opt[Int]("port").valueName ("<port_number>")
        .action ((x, c) => c.copy (port = x))
        .text (s"Cassandra connection port [${default.port}]")

    opt[String]("keyspace")
        .action ((x, c) => c.copy (keyspace = x))
        .text (s"target Cassandra keyspace [${default.keyspace}]")

    opt[String]("model_file")
        .action ((x, c) => c.copy (model_file = x))
        .text (s"model file name [${default.model_file}]")

    cmd (Operations.Statistics.toString)
        .action ((_, c) => c.copy (operation = Operations.Statistics) )
        .text (s"    perform statistical analysis${if (default.operation == Operations.Statistics) " - default" else ""}")

    cmd (Operations.Meta.toString)
        .action ((_, c) => c.copy (operation = Operations.Meta) )
        .text (s"    perform metadata extraction${if (default.operation == Operations.Meta) " - default" else ""}")
        .children (
            opt[String]("meta_file")
                 .action ((x, c) => c.copy (meta_file = x))
                .text (s"metadata file name [${default.meta_file}]")

        )

    cmd (Operations.Model.toString)
        .action ((_, c) => c.copy (operation = Operations.Model) )
        .text (s"    create a model${if (default.operation == Operations.Model) " - default" else ""}")
        .children (
            opt[Array[Int]]("tree_depth")
            .action ((x, c) => c.copy (tree_depth = x))
            .text (s"decision tree depth, or array for hyperparameter tuning [${default.tree_depth.mkString (COMMA)}]"),

            opt[Array[Int]]("bins")
            .action ((x, c) => c.copy (bins = x))
            .text (s"maximum number of bins for discretizing, or array for hyperparameter tuning [${default.bins.mkString (COMMA)}]"),

            opt[Array[Double]]("info")
            .action ((x, c) => c.copy (info = x))
            .text (s"minimum information gain for a split, or array for hyperparameter tuning [${default.info.mkString (COMMA)}]"),

            opt[Long]("seed")
            .action ((x, c) => c.copy (seed = x))
            .text (s"seed value for random number generation [${default.seed}]")
        )

    cmd (Operations.MetaModel.toString)
        .action ((_, c) => c.copy (operation = Operations.MetaModel) )
        .text (s"    create a meta model${if (default.operation == Operations.MetaModel) " - default" else ""}")
        .children (
            opt[Array[Int]]("tree_depth")
                .action ((x, c) => c.copy (tree_depth = x))
                .text (s"decision tree depth, or array for hyperparameter tuning [${default.tree_depth.mkString (COMMA)}]"),

            opt[Array[Int]]("bins")
                .action ((x, c) => c.copy (bins = x))
                .text (s"maximum number of bins for discretizing, or array for hyperparameter tuning [${default.bins.mkString (COMMA)}]"),

            opt[Array[Double]]("info")
                .action ((x, c) => c.copy (info = x))
                .text (s"minimum information gain for a split, or array for hyperparameter tuning [${default.info.mkString (COMMA)}]"),

            opt[Long]("seed")
                .action ((x, c) => c.copy (seed = x))
                .text (s"seed value for random number generation [${default.seed}]")
        )

    cmd (Operations.SimpleMetaModel.toString)
        .action ((_, c) => c.copy (operation = Operations.SimpleMetaModel) )
        .text (s"    create a meta model for each separate class${if (default.operation == Operations.SimpleMetaModel) " - default" else ""}")
        .children (
            opt[Array[Int]]("tree_depth")
                .action ((x, c) => c.copy (tree_depth = x))
                .text (s"decision tree depth, or array for hyperparameter tuning [${default.tree_depth.mkString (COMMA)}]"),

            opt[Array[Int]]("bins")
                .action ((x, c) => c.copy (bins = x))
                .text (s"maximum number of bins for discretizing, or array for hyperparameter tuning [${default.bins.mkString (COMMA)}]"),

            opt[Array[Double]]("info")
                .action ((x, c) => c.copy (info = x))
                .text (s"minimum information gain for a split, or array for hyperparameter tuning [${default.info.mkString (COMMA)}]"),

            opt[Long]("seed")
                .action ((x, c) => c.copy (seed = x))
                .text (s"seed value for random number generation [${default.seed}]")
        )

    cmd (Operations.Synthesize.toString)
        .action ((_, c) => c.copy (operation = Operations.Synthesize) )
        .text (s"    generate a synthetic load profile${if (default.operation == Operations.Synthesize) " - default" else ""}")
        .children (
            opt[String]("synthesis")
                .action ((x, c) => c.copy (synthesis = x))
                .text (s"name, id, or mRID for the synthesized time series [${default.synthesis}]"),

            opt[Calendar]("start")
                .action ((x, c) => c.copy (start = x))
                .text (s"starting time for the synthesis [${formatter.format (default.start.getTime)}]"),

            opt[Calendar]("end")
                .action ((x, c) => c.copy (end = x))
                .text (s"ending time for the synthesis [${formatter.format (default.end.getTime)}]"),

            opt[Int]("period")
                .action ((x, c) => c.copy (period = x))
                .text (s"number of milliseconds between synthesized readings [${default.period}]"),

            opt[Double]("yearly_kWh")
                .action ((x, c) => c.copy (yearly_kWh = x))
                .text (s"energy used by the synthesized time series per year (kWh) [${default.yearly_kWh}]"),

            opt[Double]("average_Wh")
                .action ((x, c) => c.copy (yearly_kWh = perYear (x)))
                .text (s"energy used by the synthesized time series per period (Wh), alternative form for yearly_kWh [${perPeriod (default.yearly_kWh)}]"),

            opt[Map[String, Int]]("classes").valueName ("cls1=#,cls2=#")
                .action ((x, c) => c.copy (classes = c.classes ++ x))
                .text (s"meta class & count, one or more of ${TimeSeriesMeta.classes.mkString (COMMA)} with instance count [${default.classes.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")
        )

    help ("help")
        .hidden ()
        .validate (Unit => { helpout = true; Right (Unit) })

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note ("""
Performs four functions:
Analyze smart meter data for min, average, max and standard deviation.
Extract meta data.
Generate a decision tree regression model.
Synthesize load profiles from the model.
""")

}