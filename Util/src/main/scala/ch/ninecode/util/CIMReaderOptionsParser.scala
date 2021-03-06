package ch.ninecode.util

import org.apache.spark.storage.StorageLevel

import scopt.OptionDef

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.State

/**
 * Parser for command line operation of programs using CIMReader and Spark.
 *
 * @tparam T class type required for parsed values
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
trait CIMReaderOptionsParser[T <: CIMAble with Mainable] extends MainOptionsParser[T]
{
    lazy val stateStrings = List("ForceTrue", "ForceFalse", "Unforced")
    implicit val stateRead: scopt.Read[State] = scopt.Read.reads(CIMTopologyOptions.parseState)

    implicit val storageRead: scopt.Read[StorageLevel] = scopt.Read.reads(
        s =>
        {
            try
            {
                StorageLevel.fromString(s)
            }
            catch
            {
                case exception: IllegalArgumentException =>
                    reportError(s"unrecognized storage level '$s', ${exception.getLocalizedMessage}")
                    getDefault.cim_options.storage
            }
        }
    )

    /**
     * @see https://spark.apache.org/docs/latest/api/scala/org/apache/spark/storage/StorageLevel$$.html
     */
    lazy val storageLevels = List(
        "NONE",
        "DISK_ONLY",
        "DISK_ONLY_2",
        "MEMORY_ONLY",
        "MEMORY_ONLY_2",
        "MEMORY_ONLY_SER",
        "MEMORY_ONLY_SER_2",
        "MEMORY_AND_DISK",
        "MEMORY_AND_DISK_2",
        "MEMORY_AND_DISK_SER",
        "MEMORY_AND_DISK_SER_2",
        "OFF_HEAP"
    )

    val children = List(
        opt[Unit]("identify_islands")
            .action((_, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(identify_islands = true))
                c
            })
            .text(s"perform island topological processing [${getDefault.cim_options.topology_options.identify_islands}]"),

        opt[State]("retain_switch")
            .valueName("<state>")
            .action((s, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(force_retain_switches = s))
                c
            })
            .text(s"attribute 'retain' for all switches except Fuse types, one of ${stateStrings.mkString(",")} [${getDefault.cim_options.topology_options.force_retain_switches.toString}]"),

        opt[State]("retain_fuse")
            .valueName("<state>")
            .action((s, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(force_retain_fuses = s))
                c
            })
            .text(s"attribute 'retain' for all fuses, one of ${stateStrings.mkString(",")} [${getDefault.cim_options.topology_options.force_retain_fuses.toString}]"),

        opt[State]("switch_island")
            .valueName("<state>")
            .action((s, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(force_switch_separate_islands = s))
                c
            })
            .text(s"switches (except Fuse) separate topological islands, one of ${stateStrings.mkString(",")} [${getDefault.cim_options.topology_options.force_switch_separate_islands.toString}]"),

        opt[State]("fuse_island")
            .valueName("<state>")
            .action((s, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(force_fuse_separate_islands = s))
                c
            })
            .text(s"fuses separate topological islands, one of ${stateStrings.mkString(",")} [${getDefault.cim_options.topology_options.force_fuse_separate_islands.toString}]"),

        opt[Unit]("default_open")
            .action((s, c) =>
            {
                c.cim_options = c.cim_options.copy(topology_options = c.cim_options.topology_options.copy(default_switch_open_state = true))
                c
            })
            .text(s"default switch open/normalOpen value if not specified [${getDefault.cim_options.topology_options.default_switch_open_state.toString}]")
    )

    opt[Unit]("topology")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(topology = true)
            c
        })
        .text(s"do topology processing (enables the following ${children.length} options) [${getDefault.cim_options.topology}]")
        .children(
            children: _*
        )

    opt[Unit]("about")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(about = true)
            c
        })
        .text(s"do about processing [${getDefault.cim_options.about}]")

    opt[Unit]("normalize")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(normalize = true)
            c
        })
        .text(s"do normalization processing [${getDefault.cim_options.normalize}]")

    opt[Unit]("dedup")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(dedup = true)
            c
        })
        .text(s"do deduplication processing [${getDefault.cim_options.dedup}]")

    opt[Unit]("edges")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(edges = true)
            c
        })
        .text(s"do edge processing [${getDefault.cim_options.edges}]")

    opt[Unit]("join")
        .action((_, c) =>
        {
            c.cim_options = c.cim_options.copy(join = true)
            c
        })
        .text(s"do asset join processing [${getDefault.cim_options.join}]")

    opt[Unit]("debug")
        .action(
            (_, c) =>
            {
                val t = c.cim_options.topology_options.copy(debug = true)
                c.cim_options = c.cim_options.copy(debug = true, topology_options = t)
                c
            }
        )
        .text(s"enable debug messages [${getDefault.cim_options.debug}]")

    opt[Long]("splitsize")
        .action((l, c) =>
        {
            c.cim_options = c.cim_options.copy(splitsize = l)
            c
        })
        .text(s"file read split size [${getDefault.cim_options.splitsize}]")

    opt[String]("cache")
        .valueName("<dir>")
        .action((s, c) =>
        {
            c.cim_options = c.cim_options.copy(cache = s)
            c
        })
        .text(s"CIM cache location [${getDefault.cim_options.cache}]")

    opt[StorageLevel]("storage")
        .valueName("<enum>")
        .action(
            (x, c) =>
            {
                c.cim_options = c.cim_options.copy(storage = x, topology_options = c.cim_options.topology_options.copy(storage = x))
                c
            }
        )
        .text(s"storage level for RDD serialization, one of ${storageLevels.mkString(",")} [${CIMReaderOptions.storageAsString(getDefault.cim_options.storage)}]")

    val cim_files: OptionDef[String, T] = arg[String]("<CIM> <CIM> ...")
        .optional()
        .unbounded()
        .action((x, c) =>
        {
            c.cim_options = c.cim_options.copy(files = c.cim_options.files :+ x)
            c
        })
        .text("CIM rdf files to process")
}