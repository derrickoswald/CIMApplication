package ch.ninecode.mfi

import scala.io.Source

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.CassandraOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class EinspeiseleistungOptionsParser (options: EinspeiseleistungOptions)
    extends MainOptionsParser[EinspeiseleistungOptions](options)
    with SparkOptionsParser[EinspeiseleistungOptions]
    with CIMReaderOptionsParser[EinspeiseleistungOptions]
    with CassandraOptionsParser[EinspeiseleistungOptions]
{
    implicit val FormatsRead: scopt.Read[MaximumFeedInOutputType.Value] = scopt.Read.reads(MaximumFeedInOutputType.withName)

    opt[String]("id")
        .valueName("<text>")
        .action((x, c) => c.copy(id = x))
        .text(s"unique id for this analysis (not used for SQLite output) [${options.id}]")

    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"turns on the INFO messages logging [${options.verbose}]")

    opt[Unit]("three")
        .action((_, c) => c.copy(three = true))
        .text(s"use three phase computations [${options.three}]")

    opt[Unit]("precalculation")
        .action((_, c) => c.copy(precalculation = true))
        .text(s"only calculates threshold and EEA existence for all HAS, assuming no EEA [${options.precalculation}]")

    opt[String]("trafos")
        .valueName("<TRA file>")
        .action((x, c) =>
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
        .text("file of transformer names (one per line) to process")

    opt[Unit]("export_only")
        .action((_, c) => c.copy(export_only = true))
        .text(s"generates glm files only - no solve or analyse operations [${options.export_only}]")

    opt[Unit]("all")
        .action((_, c) => c.copy(all = true))
        .text(s"process all transformers (not just those with EEA) [${options.all}]")

    opt[Unit]("erase")
        .action((_, c) => c.copy(erase = true))
        .text(s"clean up (delete) simulation files [${options.erase}]")

    opt[Int]("simulation")
        .valueName("N")
        .action((x, c) => c.copy(simulation = x))
        .text(s"simulation number (precalc) to use for transformer list [${options.simulation}]")

    opt[Int]("reference")
        .valueName("N")
        .action((x, c) => c.copy(reference = x))
        .text(s"simulation number (precalc) to use as reference for transformer list [${options.reference}]")

    opt[Int]("delta")
        .valueName("D")
        .action((x, c) => c.copy(delta = x))
        .text(f"delta power difference threshold for reference comparison [${options.delta}%g]")

    opt[Double]("precalcfactor")
        .valueName("D")
        .action((x, c) => c.copy(precalc_factor = x))
        .text(f"factor to multiply precalculation results for GridLAB-D [${options.precalc_factor}%g]")

    opt[Double]("cosphi")
        .valueName("D")
        .action((x, c) => c.copy(cosphi = x))
        .text(f"power factor for photo-voltaic installations, positive leading, negative lagging [${options.cosphi}%g]")

    opt[Double]("voltage_threshold")
        .valueName("D")
        .action((x, c) => c.copy(voltage_threshold = x))
        .text(f"the voltage threshold for the feeder of the house under test [${options.voltage_threshold}%g%%]")

    opt[Double]("voltage_threshold2")
        .valueName("D")
        .action((x, c) => c.copy(voltage_threshold2 = x))
        .text(f"the voltage threshold for neighboring feeders of the house under test [${options.voltage_threshold2}%g%%]")

    opt[Unit]("ignore_other")
        .action((_, c) => c.copy(ignore_other = true))
        .text(s"ignore cable currents on neighboring feeders [${options.ignore_other}]")

    opt[Double]("cable_impedance_limit").valueName("D")
        .action((x, c) => c.copy(cable_impedance_limit = x))
        .text(f"cables with higher impedances for R1 will not be processed with GridLAB-D [${options.cable_impedance_limit}%gΩ]")

    opt[String]("workdir")
        .valueName("<dir>")
        .action((x, c) => c.copy(workdir = x))
        .text(s"shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files [${options.workdir}]")

    opt[Double]("tbase")
        .valueName("<value>")
        .action((x, c) => c.copy(base_temperature = x))
        .text(s"base temperature for feedin (°C) [${options.base_temperature}]")

    opt[Double]("tsim")
        .valueName("<value>")
        .action((x, c) => c.copy(sim_temperature = x))
        .text(s"simulation temperature for feedin (°C) [${options.sim_temperature}]")

    opt[MaximumFeedInOutputType.Value]("output")
        .action((x, c) => c.copy(output = x))
        .text(s"type of output, one of ${MaximumFeedInOutputType.values.iterator.mkString(",")} [${options.output}]")

    opt[String]("outputfile")
        .valueName("<file>")
        .action((x, c) => c.copy(outputfile = x))
        .text(s"name of the SQLite database results file [${options.outputfile}]")

    opt[String]("keyspace")
        .action((x, c) => c.copy(keyspace = x))
        .text(s"target Cassandra keyspace [${options.keyspace}]")

    opt[Int]("replication")
        .action((x, c) => c.copy(replication = x))
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${options.replication}]")

    note(
        """
Calculate maximum feed-in power without reinforcement or exceeding voltage, current or power constraints.
""")
}