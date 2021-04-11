package ch.ninecode.sim

import org.slf4j.LoggerFactory

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.CassandraOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class SimulationOptionsParser (default: SimulationOptions)
    extends MainOptionsParser[SimulationOptions](default)
    with SparkOptionsParser[SimulationOptions]
    with CIMReaderOptionsParser[SimulationOptions]
    with CassandraOptionsParser[SimulationOptions]
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${default.verbose}]")

    opt[String]("workdir").valueName("<dir>")
        .action(
            (x, c) =>
            {
                val sep = System.getProperty("file.separator")
                c.copy(workdir = if (x.endsWith(sep)) x else s"$x$sep")
            }
        )
        .text(s"directory for work files on each executor [${default.workdir}]")

    opt[Unit]("three")
        .action((_, c) => c.copy(three_phase = true))
        .text(s"perform simulation using three phase load-flow [${default.three_phase}]")

    opt[Unit]("fake")
        .action((_, c) => c.copy(fake_three_phase = true))
        .text(s"convert single phase measurements into three phase [${default.fake_three_phase}]")

    opt[Unit]("keep")
        .action((_, c) => c.copy(keep = true))
        .text(s"keep intermediate glm and input/output files in workdir [${default.keep}]")

    opt[Unit]("simulationonly")
        .action((_, c) => c.copy(simulationonly = true))
        .text(s"perform simulation operations only [${default.simulationonly}]")

    opt[Unit]("postprocessonly")
        .action((_, c) => c.copy(postprocessonly = true))
        .text(s"perform postprocessing operations only [${default.postprocessonly}]")

    opt[Double]("cable_impedance_limit")
        .action((x, c) => c.copy(cable_impedance_limit = x))
        .text("cables with higher impedances for R1 will not be processed with gridlabd [%g]".format(default.cable_impedance_limit))

    // turn off <CIM> files and use <JSON> instead
    options.remove(options.indexOf(cim_files))
    arg[String]("<JSON> <JSON>...")
        .optional()
        .unbounded()
        .action(
            (x, c) =>
            {
                try
                {
                    val sep = System.getProperty("file.separator")
                    val file = if (x.startsWith(sep)) x else s"${new java.io.File(".").getCanonicalPath}$sep$x"
                    val source = scala.io.Source.fromFile(file, "UTF-8")
                    val text = source.mkString
                    source.close
                    SimulationJob.fromJSON(text) match {
                        case Right(job) => c.copy(simulation = c.simulation :+ job)
                        case Left(message) =>
                            val log = LoggerFactory.getLogger(getClass.getName)
                            log.error(message)
                            c
                    }

                }
                catch
                {
                    case e: Exception =>
                        val log = LoggerFactory.getLogger(getClass.getName)
                        log.error("bad input file name", e)
                        helpout = true
                        c
                }
            }
        )
        .text("simulation files to process")

    note(
        """
Simulates networks based on a CIM model and smart meter measurements.

The program uses one or more JSON files to specify the simulation parameters.
One or more CIM files specified by the JSON are read in and a topological analysis
is performed to produce a number of transformer service areas.
For each area it generates a GridLAB-D model file and input player
files based on the smart meter data in Cassandra.
It then execute load-flow analysis for each area, and the output recorder
files are saved in Cassandra.
There are a number of optional postprocessing steps to summarize the results.
"""
    )
}
