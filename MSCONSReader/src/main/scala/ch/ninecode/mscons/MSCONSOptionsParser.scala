package ch.ninecode.mscons

import scopt.OptionParser

class MSCONSOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[MSCONSOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default: MSCONSOptions = MSCONSOptions ()
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

    opt[Unit]("unittest").
        hidden ().
        action ((_, c) => { unittest = true; c.copy (unittest = true) }).
        text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

    version ("version").
        validate (Unit => { versionout = true; Right (Unit) }).
        text ("Scala: %s, Spark: %s, %s: %s".format (
            APPLICATION_VERSION.split ("-")(0),
            APPLICATION_VERSION.split ("-")(1),
            APPLICATION_NAME,
            APPLICATION_VERSION.split ("-")(2)
        )
        )

    opt[Unit]("verbose").
        action ((_, c) ⇒ c.copy (verbose = true)).
        text ("emit progress messages [%s]".format (default.verbose))

    opt[LogLevels.Value]("log").
        action ((x, c) => c.copy (log_level = x)).
        text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

    opt[String]("output_file").
        action ((x, c) ⇒ c.copy (output_file = x)).
        text ("output file to create, when null use stdout")

    opt[String]("delimiter").
        action ((x, c) ⇒ c.copy (delimiter = x)).
        text ("delimiter between fields in each output record")

    arg[String]("<MSCONS> <MSCONS>...").optional ().unbounded ().
        action ((x, c) ⇒ c.copy (mscons = c.mscons :+ x)).
        text ("MSCONS files to process")

    help ("help").
        hidden ().
        validate (Unit => { helpout = true; Right (Unit) })

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note (
        """
Ingest MSCONS files.

The program reads one or more MSCONS files.
"""
    )
}
