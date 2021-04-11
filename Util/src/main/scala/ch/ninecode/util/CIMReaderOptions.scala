package ch.ninecode.util

import org.apache.spark.storage.StorageLevel
import org.json4s.Formats
import org.json4s.JsonAST.JString

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceFalse
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.State
import ch.ninecode.cim.Unforced

/**
 * Options for the CIMReader.
 *
 * @param topology         <code>true</code> if topology processing is enabled
 * @param topology_options topology processing options
 * @param about            <code>true</code> if rdf:about processing is enabled
 * @param normalize        <code>true</code> if normalization processing is enabled
 * @param dedup            <code>true</code> if deduplication processing is enabled
 * @param edges            <code>true</code> if edge creation processing is enabled
 * @param join             <code>true</code> if ServiceLocation merging is enabled
 * @param debug            <code>true</code> if debug message logging is enabled
 * @param splitsize        the file split size (determined the number of partitions) in bytes
 * @param cache            the cache directory that will be created or used
 * @param storage          the RDD storage level
 * @param files            the CIM RDF files to be read
 */
case class CIMReaderOptions (
    topology: Boolean = false,
    topology_options: CIMTopologyOptions = CIMTopologyOptions(),
    about: Boolean = false,
    normalize: Boolean = false,
    dedup: Boolean = false,
    edges: Boolean = false,
    join: Boolean = false,
    debug: Boolean = false,
    splitsize: Long = 67108864L,
    cache: String = "",
    storage: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER,
    files: Seq[String] = Seq()
)
{
    /**
     * Create an equivalent CIMRelation parameters map from the options.
     *
     * @return A map that can be passed to the CIM DataFrameReader.
     */
    def toMap: Map[String, String] =
    {
        Map(
            "ch.ninecode.cim.do_topo" -> topology.toString,
            "ch.ninecode.cim.do_topo_islands" -> topology_options.identify_islands.toString,
            "ch.ninecode.cim.force_retain_switches" -> topology_options.force_retain_switches.toString,
            "ch.ninecode.cim.force_retain_fuses" -> topology_options.force_retain_fuses.toString,
            "ch.ninecode.cim.force_switch_separate_islands" -> topology_options.force_switch_separate_islands.toString,
            "ch.ninecode.cim.force_fuse_separate_islands" -> topology_options.force_fuse_separate_islands.toString,
            "ch.ninecode.cim.default_switch_open_state" -> topology_options.default_switch_open_state.toString,
            "ch.ninecode.cim.do_about" -> about.toString,
            "ch.ninecode.cim.do_normalize" -> normalize.toString,
            "ch.ninecode.cim.do_deduplication" -> dedup.toString,
            "ch.ninecode.cim.make_edges" -> edges.toString,
            "ch.ninecode.cim.do_join" -> join.toString,
            "ch.ninecode.cim.debug" -> debug.toString,
            "ch.ninecode.cim.split_maxsize" -> splitsize.toString,
            "ch.ninecode.cim.cache" -> cache,
            "StorageLevel" -> CIMReaderOptions.storageAsString (storage),
            "path" -> files.mkString(",")
        )
    }

    def toJSON: String = CIMReaderOptions.toJSON(this)
    def fromJSON (text: String): Either[String, CIMReaderOptions] = CIMReaderOptions.fromJSON(text)
}
object CIMReaderOptions extends JSON[CIMReaderOptions]
{
    def schemaResourceName: String = "CIMReaderOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/CIMReaderOptionsSchema.json" -> "resource:CIMReaderOptionsSchema.json"
    )

    def stateAsString (state: State): String =
        state match
        {
            case ForceTrue => "ForceTrue"
            case ForceFalse => "ForceFalse"
            case Unforced => "Unforced"
        }

    class StateSerializer extends JSONCustomSerializer[State](
        (format: Formats) =>
            (
                {
                    case JString(s) => CIMTopologyOptions.parseState(s)
                },
                {
                    case x: State => JString(stateAsString (x))
                }
            )
    )

    /**
     * Convert storage level to a string.
     *
     * @return a String that would generate level from StorageLevel.fromString
     */
    def storageAsString (storage: StorageLevel): String =
    {
        storage match
        {
            case StorageLevel.NONE => "NONE"
            case StorageLevel.DISK_ONLY => "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 => "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY => "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 => "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER => "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 => "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK => "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 => "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER => "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 => "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP => "OFF_HEAP"
            case _ => ""
        }
    }

    class StorageLevelSerializer extends JSONCustomSerializer[StorageLevel](
        (format: Formats) =>
            (
                {
                    case JString(s) => StorageLevel.fromString (s)
                },
                {
                    case x: StorageLevel => JString(storageAsString (x))
                }
            )
    )

    def customSerializers: Seq[JSONCustomSerializer[_]] = List(new StateSerializer, new StorageLevelSerializer)

    def parseBoolean (text: String, default: Boolean = false): Boolean =
    {
        try
        {
            text.toBoolean
        }
        catch
        {
            case e: IllegalArgumentException =>
                e.printStackTrace()
                default
        }
    }

    def parseLong (text: String, default: Long = 0L): Long =
    {
        try
        {
            text.toLong
        }
        catch
        {
            case e: IllegalArgumentException =>
                e.printStackTrace()
                default
        }
    }

    def parseStorage (text: String, default: StorageLevel = StorageLevel.NONE): StorageLevel =
    {
        try
        {
            StorageLevel.fromString(text)
        }
        catch
        {
            case e: IllegalArgumentException =>
                e.printStackTrace()
                default
        }
    }

    /**
     * Convert an options map into the options case class.
     *
     * @param src      the map to read from
     * @param template the template to use for default values
     * @return the option case class corresponding to the map entries
     */
    def apply (src: Map[String, String], template: Option[CIMReaderOptions]): CIMReaderOptions =
    {
        var hasTopoOption = false // true when a topology entry is encountered
        def asBoolean (flag: Option[String], otherwise: Boolean): Boolean =
        {
            flag match
            {
                case Some(string) =>
                    hasTopoOption = true
                    parseBoolean(string, otherwise)
                case _ => otherwise
            }
        }

        def asState (state: Option[String], otherwise: State): State =
        {
            state match
            {
                case Some(string) =>
                    hasTopoOption = true
                    CIMTopologyOptions.parseState(string)
                case _ => otherwise
            }
        }

        val o = template match
        {
            case Some(options) => options
            case None => CIMReaderOptions()
        }
        val t = o.topology_options

        val topology_options = CIMTopologyOptions(
            identify_islands = asBoolean(src.get("ch.ninecode.cim.do_topo_islands"), t.identify_islands),
            force_retain_switches = asState(src.get("ch.ninecode.cim.force_retain_switches"), t.force_retain_switches),
            force_retain_fuses = asState(src.get("ch.ninecode.cim.force_retain_fuses"), t.force_retain_fuses),
            force_switch_separate_islands = asState(src.get("ch.ninecode.cim.force_switch_separate_islands"), t.force_switch_separate_islands),
            force_fuse_separate_islands = asState(src.get("ch.ninecode.cim.force_fuse_separate_islands"), t.force_fuse_separate_islands),
            default_switch_open_state = asBoolean(src.get("ch.ninecode.cim.default_switch_open_state"), t.default_switch_open_state),
            // these are duplicated in general CIMReader options:
            debug = src.get("ch.ninecode.cim.debug").map(parseBoolean(_, o.debug)).getOrElse(o.debug),
            storage = src.get("StorageLevel").map(parseStorage(_, o.storage)).getOrElse(o.storage)
        )

        CIMReaderOptions(
            topology = hasTopoOption || asBoolean(src.get("ch.ninecode.cim.do_topo"), o.topology),
            topology_options = topology_options,
            about = src.get("ch.ninecode.cim.do_about").map(parseBoolean(_, o.about)).getOrElse(o.about),
            normalize = src.get("ch.ninecode.cim.do_normalize").map(parseBoolean(_, o.normalize)).getOrElse(o.normalize),
            dedup = src.get("ch.ninecode.cim.do_deduplication").map(parseBoolean(_, o.dedup)).getOrElse(o.dedup),
            edges = src.get("ch.ninecode.cim.make_edges").map(parseBoolean(_, o.edges)).getOrElse(o.edges),
            join = src.get("ch.ninecode.cim.do_join").map(parseBoolean(_, o.join)).getOrElse(o.join),
            debug = src.get("ch.ninecode.cim.debug").map(parseBoolean(_, o.debug)).getOrElse(o.debug),
            splitsize = src.get("ch.ninecode.cim.split_maxsize").map(parseLong(_, o.splitsize)).getOrElse(o.splitsize),
            cache = src.getOrElse("ch.ninecode.cim.cache", o.cache),
            storage = src.get("StorageLevel").map(parseStorage(_, o.storage)).getOrElse(o.storage),
            files = src.get("path").map(_.split(",").toSeq).getOrElse(o.files)
        )
    }
}