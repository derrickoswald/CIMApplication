package ch.ninecode.sc

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.Complex

/**
 * Parser for command line operation.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class ShortCircuitOptionsParser (options: ShortCircuitOptions) extends CIMReaderOptionsParser[ShortCircuitOptions](options)
{
    implicit val complexRead: scopt.Read[Complex] = scopt.Read.reads (
        s => Complex.fromString (s)
    )

    opt[Unit]("verbose").
        action ((_, c) => c.copy (verbose = true)).
        text ("log informational messages [false]")

    opt[String]("description").valueName ("<text>").
        action ((x, c) => c.copy (description = x)).
        text ("text describing this program execution for SQLite run table")

    opt[Double]("netp_max").valueName ("<Sk_max>").
        action ((x, c) => c.copy (default_short_circuit_power_max = x)).
        text (s"maximum network power if not in CIM, VA [${options.default_short_circuit_power_max}]")

    opt[Complex]("netz_max").valueName ("<r + xj>").
        action ((x, c) => c.copy (default_short_circuit_impedance_max = x)).
        text (s"network impedance at maximum power if not in CIM, Ω [${options.default_short_circuit_impedance_max}]")

    opt[Double]("neta_max").valueName ("<angle>").
        action ((x, c) => c.copy (default_short_circuit_angle_max = x)).
        text (s"network power factor angle at maximum power if not in CIM, overrides impedance, ° [${options.default_short_circuit_angle_max}]")

    opt[Double]("netp_min").valueName ("<Sk_min>").
        action ((x, c) => c.copy (default_short_circuit_power_min = x)).
        text (s"minimum network power if not in CIM, VA [${options.default_short_circuit_power_min}]")

    opt[Complex]("netz_min").valueName ("<r + xj>").
        action ((x, c) => c.copy (default_short_circuit_impedance_min = x)).
        text (s"network impedance at minumum power if not in CIM, Ω [${options.default_short_circuit_impedance_min}]")

    opt[Double]("neta_min").valueName ("<angle>").
        action ((x, c) => c.copy (default_short_circuit_angle_min = x)).
        text (s"network power factor angle at minimum power if not in CIM, overrides impedance, ° [${options.default_short_circuit_angle_min}]")

    opt[Double]("tbase").valueName ("<value>").
        action ((x, c) => c.copy (base_temperature = x)).
        text (s"temperature assumed in CIM file (°C) [${options.base_temperature}]")

    opt[Double]("tlow").valueName ("<value>").
        action ((x, c) => c.copy (low_temperature = x)).
        text (s"low temperature for maximum fault (°C) [${options.low_temperature}]")

    opt[Double]("thigh").valueName ("<value>").
        action ((x, c) => c.copy (high_temperature = x)).
        text (s"high temperature for minimum fault (°C) [${options.high_temperature}]")

    opt[String]("trafos").valueName ("<TRA file>").
        action ((x, c) => c.copy (trafos = x)).
        text ("file of transformer names (one per line) to process")

    opt[Double]("trafop").valueName ("<ratedS>").
        action ((x, c) => c.copy (default_transformer_power_rating = x)).
        text (s"transformer power if not in CIM, VA [${options.default_transformer_power_rating}]")

    opt[Complex]("trafoz").valueName ("<r + xj>").
        action ((x, c) => c.copy (default_transformer_impedance = x)).
        text (s"transformer impedance if not in CIM, Ω [${options.default_transformer_impedance}]")

    opt[Double]("cmax").
        action ((x, c) => c.copy (cmax = x)).
        text (s"voltage factor for maximum fault level, used for rating equipment [${options.cmax}]")

    opt[Double]("cmin").
        action ((x, c) => c.copy (cmin = x)).
        text (s"voltage factor for minimum fault level, used for protections settings [${options.cmin}]")

    opt[Double]("cosphi").
        action ((x, c) => c.copy (cosphi = x, worstcasepf = false)).
        text ("load power factor, used for maximum inrush current [worst case]")

    opt[Int]("fuse_table").
        action ((x, c) => c.copy (fuse_table = x)).
        text (s"recommended fuse sizing table, #1 from 65A⇒25 to 2400A⇒630, #2 from 28A⇒10 to 2500A⇒630, #3 DIN as #1, SEV 200A⇒60 to 1150A⇒400, #4 0⇒6,65A⇒25 to 2400A⇒500 [${options.fuse_table}]")

    opt[Int]("messagemax").
        action ((x, c) => c.copy (messagemax = x)).
        text (s"maximum number of warning and error messages per node [${options.messagemax}]")

    opt[Long]("batchsize").
        action ((x, c) => c.copy (batchsize = x)).
        text (s"size of result collections for driver database writes [${options.batchsize}]")

    opt[Double]("cable_impedance_limit").valueName ("<value>").
        action ((x, c) => c.copy (cable_impedance_limit = x)).
        text (s"cables with higher impedances for R1 will not be processed with gridlabd [${options.cable_impedance_limit}]")

    opt[Boolean]("calculate_public_lighting").
        action ((x, c) => c.copy (calculate_public_lighting = x)).
        text ("calculate public lighting [%s]".format (options.calculate_public_lighting))

    opt[String]("workdir").valueName ("<dir>").
        action ((x, c) => c.copy (workdir = x)).
        text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")
}
