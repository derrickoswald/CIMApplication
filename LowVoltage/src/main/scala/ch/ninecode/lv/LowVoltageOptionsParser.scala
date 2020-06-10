package ch.ninecode.lv

import scopt.OptionParser

/**
 * Parser for command line operation.
 *
 * @param APPLICATION_NAME the name of the program
 * @param APPLICATION_VERSION the version of the program.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class LowVoltageOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[LowVoltageOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default: LowVoltageOptions = LowVoltageOptions ()
    var unittest = false
    var helpout = false
    var versionout = false
    val COMMA = ","
    val EQUAL = "="

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String,String]] = scopt.Read.reads (
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

    opt[Unit]("verbose")
        .action ((_, c) => c.copy (verbose = true))
        .text (s"emit progress messages [${default.verbose}]")

    opt[String]("master").valueName ("MASTER_URL")
        .action ((x, c) => c.copy (master = x))
        .text (s"local[*], spark://host:port, mesos://host:port or yarn [${default.master}]")

    opt[Map[String, String]]("sparkopts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (spark_options = x)).
        text (s"Spark options [${default.spark_options.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")

    opt[Map[String, String]]("cimopts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (cim_reader_options = x)).
        text (s"CIMReader options [${default.cim_reader_options.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")

    opt[String]("storage")
        .action ((x, c) => c.copy (storage = x))
        .text (s"storage level for RDD serialization [${default.storage}]")

    opt[LogLevels.Value]("log")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.mkString (COMMA)} [${default.log_level}]")

    opt[Unit]("deduplicate").
        action ((_, c) => c.copy (dedup = true)).
        text (s"de-duplicate input (striped) files [${default.dedup}]")

    opt[Unit]("three")
        .action ((_, c) => c.copy (three = true))
        .text (s"use three phase computations [${default.three}]")

    opt[String]("trafos").valueName ("<TRA file>")
        .action ((x, c) => c.copy (trafos = x))
        .text (s"file of transformer names (one per line) to process [${default.trafos}]")

    opt[String]("workdir")
        .action (
            (x, c) =>
            {
                val sep = System.getProperty ("file.separator")
                c.copy (workdir = if (x.endsWith (sep)) x else s"$x$sep")
            }
        )
        .text (s"working directory for unzip and copy [${default.workdir}]")

    opt[String]("checkpoint").valueName ("<dir>").
        action ((x, c) => c.copy (checkpoint_dir = x)).
        text (s"checkpoint directory on HDFS, e.g. hdfs://... [${default.checkpoint_dir}]")

    arg[String]("<CIM> <CIM> ...").optional ().unbounded ().
        action ((x, c) => c.copy (files = c.files :+ x)).
        text ("CIM rdf files to process")

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
Generate GridLAB-D model files (.glm files) from CIM files.
"""
    )
}