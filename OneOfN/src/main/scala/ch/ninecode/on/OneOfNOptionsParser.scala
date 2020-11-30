package ch.ninecode.on

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class OneOfNOptionsParser (options: OneOfNOptions)
    extends MainOptionsParser[OneOfNOptions](options)
    with SparkOptionsParser[OneOfNOptions]
    with CIMReaderOptionsParser[OneOfNOptions]
{
    val default: OneOfNOptions = OneOfNOptions()

    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${default.verbose}]")

    opt[Unit]("three")
        .action((_, c) => c.copy(three = true))
        .text(s"generate three phase .glm files [${default.three}]")

    opt[Double]("tbase")
        .valueName("<value>")
        .action((x, c) => c.copy(base_temperature = x))
        .text(s"temperature assumed in CIM file (°C) [${default.base_temperature}]")

    opt[Double]("temp")
        .valueName("<value>")
        .action((x, c) => c.copy(temperature = x))
        .text(s"temperature for simulation (°C) [${default.temperature}]")

    opt[String]("workdir")
        .valueName("<dir>")
        .action((x, c) => c.copy(workdir = x))
        .text(s"shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files [${default.workdir}]")

    note("Creates GridLAB-D .glm models for all medium voltage (N5 network) feeder service areas for one-of-N analysis.\n")
}
