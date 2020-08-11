package ch.ninecode.mscons

import ch.ninecode.util.MainOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class MSCONSOptionsParser (options: MSCONSOptions) extends MainOptionsParser[MSCONSOptions](options)
{
    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    opt [Unit]("verbose")
        .action ((_, c) => c.copy (verbose = true))
        .text (s"emit progress messages [${options.verbose}]")

    opt [LogLevels.Value]("log")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.iterator.mkString (",")} [${options.log_level}]")

    opt [String]("output_file")
        .action ((x, c) => c.copy (output_file = x))
        .text ("output file to create, when null use stdout")

    opt [String]("delimiter")
        .action ((x, c) => c.copy (delimiter = x))
        .text (s"delimiter between fields in each output record [${options.delimiter}]")

    arg [String]("<MSCONS> <MSCONS>...")
        .optional ()
        .unbounded ()
        .action ((x, c) => c.copy (mscons = c.mscons :+ x))
        .text ("MSCONS files to process")

    note (
        """
Ingest MSCONS files.

The program reads one or more MSCONS files.
"""
    )
}
