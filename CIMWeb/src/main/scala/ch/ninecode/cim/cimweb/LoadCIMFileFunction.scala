package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import scala.collection.mutable.HashMap

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMEdges
import ch.ninecode.cim.CIMJoin
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceFalse
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.State
import ch.ninecode.cim.Unforced

case class LoadCIMFileFunction (paths: Array[String], options: Iterable[(String, String)] = null) extends CIMWebFunction
{
    def asBoolean (string: String): Boolean = try { string.toBoolean } catch { case _: Throwable => false }

    def parseState (text: String): State =
        text match
        {
            case "ForceTrue" ⇒ ForceTrue
            case "ForceFalse" ⇒ ForceFalse
            case _ ⇒ Unforced
        }

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
        {
            case StorageLevel.NONE ⇒ "NONE"
            case StorageLevel.DISK_ONLY ⇒ "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 ⇒ "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY ⇒ "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 ⇒ "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER ⇒ "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 ⇒ "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK ⇒ "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 ⇒ "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER ⇒ "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 ⇒ "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP ⇒ "OFF_HEAP"
            case _ ⇒ ""
        }
    }

    // load the file
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder
        try
        {
            // read the file(s)
            val prefix = hdfs.getUri.toString
            val files = paths.map (s ⇒ { val file = if (s.startsWith ("/")) s else "/" + s; new Path (prefix, file).toString })
            val ff = Json.createArrayBuilder
            for (f <- files)
                ff.add (f)
            response.add ("files", ff)

            // establish default options if needed
            val op = if (null == options)
            {
                List (
                    ("StorageLevel", "MEMORY_AND_DISK_SER"),
                    ("ch.ninecode.cim.do_about", "false"),
                    ("ch.ninecode.cim.do_normalize", "false"),
                    ("ch.ninecode.cim.do_deduplication", if (1 < files.length) "true" else "false"),
                    ("ch.ninecode.cim.make_edges", "false"),
                    ("ch.ninecode.cim.do_join", "false"),
                    ("ch.ninecode.cim.do_topo_islands", "false"),
                    ("ch.ninecode.cim.do_topo", "false"),
                    ("ch.ninecode.cim.force_retain_switches", "Unforced"),
                    ("ch.ninecode.cim.force_retain_fuses", "Unforced"),
                    ("ch.ninecode.cim.force_switch_separate_islands", "Unforced"),
                    ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced"),
                    ("ch.ninecode.cim.default_switch_open_state", "false"),
                    ("ch.ninecode.cim.debug", "false"),
                    ("ch.ninecode.cim.split_maxsize", "67108864"),
                    ("ch.ninecode.cim.cache", "")
                )
            }
            else
                options

            val reader_options = new HashMap[String, String] ()
            for (option ← op)
                reader_options.put (option._1, option._2)
            reader_options.put ("path", files.mkString (","))
            val elements = spark.read.format ("ch.ninecode.cim").options (reader_options).load (files:_*)
            var count = elements.count
            val storage = StorageLevel.fromString (reader_options.getOrElse ("StorageLevel", "MEMORY_AND_DISK_SER"))
            response.add ("elements", count)

            // echo options to the response
            val opts = Json.createObjectBuilder
            opts.add ("StorageLevel", storage_level_tostring (storage))
            opts.add ("ch.ninecode.cim.do_about", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_about", "false")))
            opts.add ("ch.ninecode.cim.do_normalize", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_normalize", "false")))
            opts.add ("ch.ninecode.cim.do_deduplication", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_deduplication", "false")))
            opts.add ("ch.ninecode.cim.make_edges", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.make_edges", "false")))
            opts.add ("ch.ninecode.cim.do_join", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_join", "false")))
            opts.add ("ch.ninecode.cim.do_topo_islands", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_topo_islands", "false")))
            opts.add ("ch.ninecode.cim.do_topo", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_topo", "false")))
            opts.add ("ch.ninecode.cim.force_retain_switches", reader_options.getOrElse ("ch.ninecode.cim.force_retain_switches", "Unforced"))
            opts.add ("ch.ninecode.cim.force_retain_fuses", reader_options.getOrElse ("ch.ninecode.cim.force_retain_fuses", "Unforced"))
            opts.add ("ch.ninecode.cim.force_switch_separate_islands", reader_options.getOrElse ("ch.ninecode.cim.force_switch_separate_islands", "Unforced"))
            opts.add ("ch.ninecode.cim.force_fuse_separate_islands", reader_options.getOrElse ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced"))
            opts.add ("ch.ninecode.cim.default_switch_open_state", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.default_switch_open_state", "false")))
            opts.add ("ch.ninecode.cim.debug", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.debug", "false")))
            opts.add ("ch.ninecode.cim.split_maxsize", reader_options.getOrElse ("ch.ninecode.cim.split_maxsize", "67108864").toInt)
            opts.add ("ch.ninecode.cim.cache", reader_options.getOrElse ("ch.ninecode.cim.cache", ""))
            response.add ("options", opts)
        }
        catch
        {
            case e: Exception =>
                response.add ("error", e.getMessage )
        }
        response.build
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (paths=")
        sb.append (paths.mkString (","))
        sb.append (", options=")
        sb.append (if (null != options) options.toString else "null")
        sb.append (")")
        sb.toString
    }
}
