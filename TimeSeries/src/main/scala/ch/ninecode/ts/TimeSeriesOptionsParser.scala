package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.storage.StorageLevel

import ch.ninecode.util.SparkOptionsParser

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

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class TimeSeriesOptionsParser (options: TimeSeriesOptions) extends SparkOptionsParser[TimeSeriesOptions](options)
{
    implicit val storageRead: scopt.Read[StorageLevel] = scopt.Read.reads(
        s =>
        {
            try
            {
                StorageLevel.fromString(s)
            }
            catch
            {
                case exception: IllegalArgumentException =>
                    reportError(s"unrecognized storage level '$s', ${exception.getLocalizedMessage}")
                    options.storage
            }
        }
    )

    /**
     * @see https://spark.apache.org/docs/latest/api/scala/org/apache/spark/storage/StorageLevel$$.html
     */
    lazy val storageLevels = List(
        "NONE",
        "DISK_ONLY",
        "DISK_ONLY_2",
        "MEMORY_ONLY",
        "MEMORY_ONLY_2",
        "MEMORY_ONLY_SER",
        "MEMORY_ONLY_SER_2",
        "MEMORY_AND_DISK",
        "MEMORY_AND_DISK_2",
        "MEMORY_AND_DISK_SER",
        "MEMORY_AND_DISK_SER_2",
        "OFF_HEAP"
    )

    implicit val OperationsRead: scopt.Read[Operations.Value] = scopt.Read.reads(Operations.withName)

    implicit val string_int_mapRead: scopt.Read[Map[String, Int]] = scopt.Read.reads(
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

            val pairs = for (p <- s.split(COMMA); kv = p.split(EQUAL))
                yield
                    {
                        if (2 == kv.length)
                            toInt(kv(1)) match
                            {
                                case Some(i) => Some((kv(0), i))
                                case _ =>
                                    reportError(s"unrecognized integer '${kv(1)}'")
                                    helpout = true
                                    None
                            }
                        else
                        {
                            reportError(s"unrecognized key=value pair '$p'")
                            helpout = true
                            None
                        }
                    }
            pairs.flatten.toMap
        }
    )

    implicit val IntArrayRead: scopt.Read[Array[Int]] = scopt.Read.reads(_.split(COMMA).map(_.toInt))

    implicit val DoubleArrayRead: scopt.Read[Array[Double]] = scopt.Read.reads(_.split(COMMA).map(_.toDouble))

    implicit val CalendarRead: scopt.Read[Calendar] = scopt.Read.calendarRead("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    lazy val formatter: SimpleDateFormat =
    {
        val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        format
    }

    def perYear (average_Wh: Double): Double =
    {
        val periods = 24 * 60 * 60 * 1000 / options.period
        average_Wh * periods * 365.25 / 1000.0
    }

    def perPeriod (yearly_kWh: Double): Double =
    {
        val periods = 24 * 60 * 60 * 1000 / options.period
        yearly_kWh * 1000.0 / 365.25 / periods
    }

    opt[StorageLevel]("storage")
        .valueName("<enum>")
        .action((x, c) => c.copy(storage = x))
        .text(s"storage level for RDD serialization, one of ${storageLevels.mkString(",")} [${options.storageAsString}]")

    opt[String]("host")
        .valueName("<cassandra>")
        .action(
            (x, c) =>
            {
                c.copy(
                    host = x,
                    spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.host" -> x))
                )
            }
        )
        .text(s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${options.host}]")

    opt[Int]("port")
        .valueName("<port_number>")
        .action(
            (x, c) =>
            {
                c.copy(
                    port = x,
                    spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.port" -> x.toString))
                )
            }
        )
        .text(s"Cassandra connection port [${options.port}]")

    opt[String]("keyspace")
        .action((x, c) => c.copy(keyspace = x))
        .text(s"target Cassandra keyspace [${options.keyspace}]")

    opt[Int]("replication")
        .action((x, c) => c.copy(replication = x))
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${options.replication}]")

    opt[String]("model_file")
        .action((x, c) => c.copy(model_file = x))
        .text(s"model file name [${options.model_file}]")

    cmd(Operations.Statistics.toString)
        .action((_, c) => c.copy(operation = Operations.Statistics))
        .text(s"    perform statistical analysis${if (options.operation == Operations.Statistics) " - options" else ""}")

    cmd(Operations.Meta.toString)
        .action((_, c) => c.copy(operation = Operations.Meta))
        .text(s"    perform metadata extraction${if (options.operation == Operations.Meta) " - options" else ""}")
        .children(
            opt[String]("meta_file")
                .action((x, c) => c.copy(meta_file = x))
                .text(s"metadata file name [${options.meta_file}]")

        )

    cmd(Operations.Model.toString)
        .action((_, c) => c.copy(operation = Operations.Model))
        .text(s"    create a model${if (options.operation == Operations.Model) " - options" else ""}")
        .children(
            opt[Array[Int]]("tree_depth")
                .action((x, c) => c.copy(tree_depth = x))
                .text(s"decision tree depth, or array for hyperparameter tuning [${options.tree_depth.mkString(COMMA)}]"),

            opt[Array[Int]]("bins")
                .action((x, c) => c.copy(bins = x))
                .text(s"maximum number of bins for discretizing, or array for hyperparameter tuning [${options.bins.mkString(COMMA)}]"),

            opt[Array[Double]]("info")
                .action((x, c) => c.copy(info = x))
                .text(s"minimum information gain for a split, or array for hyperparameter tuning [${options.info.mkString(COMMA)}]"),

            opt[Long]("seed")
                .action((x, c) => c.copy(seed = x))
                .text(s"seed value for random number generation [${options.seed}]")
        )

    cmd(Operations.MetaModel.toString)
        .action((_, c) => c.copy(operation = Operations.MetaModel))
        .text(s"    create a meta model${if (options.operation == Operations.MetaModel) " - options" else ""}")
        .children(
            opt[Array[Int]]("tree_depth")
                .action((x, c) => c.copy(tree_depth = x))
                .text(s"decision tree depth, or array for hyperparameter tuning [${options.tree_depth.mkString(COMMA)}]"),

            opt[Array[Int]]("bins")
                .action((x, c) => c.copy(bins = x))
                .text(s"maximum number of bins for discretizing, or array for hyperparameter tuning [${options.bins.mkString(COMMA)}]"),

            opt[Array[Double]]("info")
                .action((x, c) => c.copy(info = x))
                .text(s"minimum information gain for a split, or array for hyperparameter tuning [${options.info.mkString(COMMA)}]"),

            opt[Long]("seed")
                .action((x, c) => c.copy(seed = x))
                .text(s"seed value for random number generation [${options.seed}]")
        )

    cmd(Operations.SimpleMetaModel.toString)
        .action((_, c) => c.copy(operation = Operations.SimpleMetaModel))
        .text(s"    create a meta model for each separate class${if (options.operation == Operations.SimpleMetaModel) " - options" else ""}")
        .children(
            opt[Array[Int]]("tree_depth")
                .action((x, c) => c.copy(tree_depth = x))
                .text(s"decision tree depth, or array for hyperparameter tuning [${options.tree_depth.mkString(COMMA)}]"),

            opt[Array[Int]]("bins")
                .action((x, c) => c.copy(bins = x))
                .text(s"maximum number of bins for discretizing, or array for hyperparameter tuning [${options.bins.mkString(COMMA)}]"),

            opt[Array[Double]]("info")
                .action((x, c) => c.copy(info = x))
                .text(s"minimum information gain for a split, or array for hyperparameter tuning [${options.info.mkString(COMMA)}]"),

            opt[Long]("seed")
                .action((x, c) => c.copy(seed = x))
                .text(s"seed value for random number generation [${options.seed}]")
        )

    cmd(Operations.Synthesize.toString)
        .action((_, c) => c.copy(operation = Operations.Synthesize))
        .text(s"    generate a synthetic load profile${if (options.operation == Operations.Synthesize) " - options" else ""}")
        .children(
            opt[String]("synthesis")
                .action((x, c) => c.copy(synthesis = x))
                .text(s"name, id, or mRID for the synthesized time series [${options.synthesis}]"),

            opt[Calendar]("start")
                .action((x, c) => c.copy(start = x))
                .text(s"starting time for the synthesis [${formatter.format(options.start.getTime)}]"),

            opt[Calendar]("end")
                .action((x, c) => c.copy(end = x))
                .text(s"ending time for the synthesis [${formatter.format(options.end.getTime)}]"),

            opt[Int]("period")
                .action((x, c) => c.copy(period = x))
                .text(s"number of milliseconds between synthesized readings [${options.period}]"),

            opt[Double]("yearly_kWh")
                .action((x, c) => c.copy(yearly_kWh = x))
                .text(s"energy used by the synthesized time series per year (kWh) [${options.yearly_kWh}]"),

            opt[Double]("average_Wh")
                .action((x, c) => c.copy(yearly_kWh = perYear(x)))
                .text(s"energy used by the synthesized time series per period (Wh), alternative form for yearly_kWh [${perPeriod(options.yearly_kWh)}]"),

            opt[Map[String, Int]]("classes").valueName("cls1=#,cls2=#")
                .action((x, c) => c.copy(classes = c.classes ++ x))
                .text(s"meta class & count, one or more of ${TimeSeriesMeta.classes.mkString(COMMA)} with instance count [${options.classes.map(x => s"${x._1}$EQUAL${x._2}").mkString(COMMA)}]")
        )

    note(
        """
Performs four functions:
Analyze smart meter data for min, average, max and standard deviation.
Extract meta data.
Generate a decision tree regression model.
Synthesize load profiles from the model.
""")
}