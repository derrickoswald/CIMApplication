package ch.ninecode.lv

import ch.ninecode.util.CIMReaderOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class LowVoltageOptionsParser (options: LowVoltageOptions) extends CIMReaderOptionsParser[LowVoltageOptions](options)
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${options.verbose}]")

    opt[Unit]("three")
        .action((_, c) => c.copy(three = true))
        .text(s"use three phase computations [${options.three}]")

    opt[String]("trafos").valueName("<TRA file>")
        .action((x, c) => c.copy(trafos = x))
        .text(s"file of transformer names (one per line) to process [${options.trafos}]")

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