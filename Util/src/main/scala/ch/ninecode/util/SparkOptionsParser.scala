package ch.ninecode.util

import org.apache.log4j.Level

/**
 * Parser for command line operation of programs using Spark.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class SparkOptionsParser[T <: Mainable with Sparkable] (default: T) extends MainOptionsParser[T](default)
{
    val COMMA = ","
    val EQUAL = "="

    implicit val logRead: scopt.Read[Level] = scopt.Read.reads (Level.toLevel (_, default.spark_options.log))

    val logLevels = List (
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE",
        "ALL"
    )

    implicit val arrayRead: scopt.Read[Array[String]] = scopt.Read.reads (_.split (COMMA))

    opt [String]("master")
        .valueName ("<master_url>")
        .action ((x, c) =>
        {
            c.spark_options = c.spark_options.copy (master = x);
            c
        })
        .text (s"local[*], spark://host:port/, mesos://host:port or yarn [${default.spark_options.master}]")

    opt [Map[String, String]]("spark_options")
        .valueName ("<map>")
        .action ((x, c) =>
        {
            c.spark_options = c.spark_options.copy (options = x);
            c
        })
        .text (s"Spark options [${default.spark_options.options.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")

    opt [Level]("log")
        .valueName ("<enum>")
        .action ((x, c) =>
        {
            c.spark_options = c.spark_options.copy (log = x);
            c
        })
        .text (s"log level, one of ${logLevels.mkString (",")} [${default.spark_options.log}]")

    opt [Array[String]]("jars")
        .valueName ("<list>")
        .action ((x, c) =>
        {
            c.spark_options = c.spark_options.copy (jars = x);
            c
        })
        .text (s"names of jars to send to Spark [${default.spark_options.jars.mkString (COMMA)}]")

    opt [String]("checkpoint")
        .valueName ("<dir>")
        .action ((x, c) =>
        {
            c.spark_options = c.spark_options.copy (checkpoint = x);
            c
        })
        .text (s"checkpoint directory on HDFS, e.g. hdfs://... [${default.spark_options.checkpoint}]")
}
