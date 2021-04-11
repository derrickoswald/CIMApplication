package ch.ninecode.lv

import scala.io.Source

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser
import ch.ninecode.util.Using

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class LowVoltageOptionsParser (options: LowVoltageOptions)
    extends MainOptionsParser[LowVoltageOptions](options)
    with SparkOptionsParser[LowVoltageOptions]
    with CIMReaderOptionsParser[LowVoltageOptions]
    with Using
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${options.verbose}]")

    opt[Unit]("three")
        .action((_, c) => c.copy(three = true))
        .text(s"use three phase computations [${options.three}]")

    opt[String]("trafos")
        .valueName("<TRA file>")
        .action(
            (x, c) =>
            {
                // do all transformers listed in the file
                using (Source.fromFile(x, "UTF-8"))(
                    source =>
                    {
                        val lines = source.getLines().filter(_ != "").toArray
                        if (0 == lines.length)
                            throw new Exception("no transformers to process") // sadly, scopt only understands exceptions
                        c.copy(trafos = lines)
                    }
                )
            }
        )
        .text(s"file of transformer names (one per line) to process [${options.trafos.mkString("\n")}]")

    opt[String]("workdir")
        .action(
            (x, c) =>
            {
                val sep = System.getProperty("file.separator")
                c.copy(workdir = if (x.endsWith(sep)) x else s"$x$sep")
            }
        )
        .text(s"working directory for .glm data [${options.getWorkDir}]")

    note(
        """
Generate GridLAB-D model files (.glm files) from CIM files.
"""
    )
}