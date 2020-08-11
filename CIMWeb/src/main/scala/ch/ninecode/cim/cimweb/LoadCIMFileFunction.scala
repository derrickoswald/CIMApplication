package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.ForceFalse
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.State
import ch.ninecode.cim.Unforced

case class LoadCIMFileFunction (paths: Array[String], options: Option[Iterable[(String, String)]]) extends CIMWebFunction
{
    def asBoolean (string: String): Boolean =
    {
        string match
        {
            case "true" => true
            case "false" => false
            case _ => false
        }
    }

    def parseState (text: String): State =
        text match
        {
            case "ForceTrue" => ForceTrue
            case "ForceFalse" => ForceFalse
            case _ => Unforced
        }

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
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

    // load the file
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response =
            try
            {
                // read the file(s)
                val prefix = hdfs.getUri.toString
                val files = paths.map (s =>
                {
                    val file = if (s.startsWith ("/")) s else s"/$s"; new Path (prefix, file).toString
                })
                val filelist = files.foldLeft (Json.createArrayBuilder)((b, f) => b.add (f))

                // establish default options if needed
                val op = options.fold (
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
                    ).toIterable
                )(identity)

                val reader_options = Map [String, String]("path" -> files.mkString (",")) ++ op
                val elements = spark.read.format ("ch.ninecode.cim").options (reader_options).load (files: _*)
                val count = elements.count

                // echo options to the response
                val opts = Json.createObjectBuilder
                    .add ("StorageLevel", reader_options.getOrElse ("StorageLevel", "MEMORY_AND_DISK_SER"))
                    .add ("ch.ninecode.cim.do_about", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_about", "false")))
                    .add ("ch.ninecode.cim.do_normalize", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_normalize", "false")))
                    .add ("ch.ninecode.cim.do_deduplication", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_deduplication", "false")))
                    .add ("ch.ninecode.cim.make_edges", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.make_edges", "false")))
                    .add ("ch.ninecode.cim.do_join", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_join", "false")))
                    .add ("ch.ninecode.cim.do_topo_islands", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_topo_islands", "false")))
                    .add ("ch.ninecode.cim.do_topo", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.do_topo", "false")))
                    .add ("ch.ninecode.cim.force_retain_switches", reader_options.getOrElse ("ch.ninecode.cim.force_retain_switches", "Unforced"))
                    .add ("ch.ninecode.cim.force_retain_fuses", reader_options.getOrElse ("ch.ninecode.cim.force_retain_fuses", "Unforced"))
                    .add ("ch.ninecode.cim.force_switch_separate_islands", reader_options.getOrElse ("ch.ninecode.cim.force_switch_separate_islands", "Unforced"))
                    .add ("ch.ninecode.cim.force_fuse_separate_islands", reader_options.getOrElse ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced"))
                    .add ("ch.ninecode.cim.default_switch_open_state", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.default_switch_open_state", "false")))
                    .add ("ch.ninecode.cim.debug", asBoolean (reader_options.getOrElse ("ch.ninecode.cim.debug", "false")))
                    .add ("ch.ninecode.cim.split_maxsize", reader_options.getOrElse ("ch.ninecode.cim.split_maxsize", "67108864").toInt)
                    .add ("ch.ninecode.cim.cache", reader_options.getOrElse ("ch.ninecode.cim.cache", ""))

                Json.createObjectBuilder.add ("files", filelist).add ("elements", count).add ("options", opts)
            }
            catch
            {
                case e: Exception =>
                    Json.createObjectBuilder.add ("error", e.getMessage)
            }
        response.build
    }

    override def toString: String = s"${super.toString} (paths=${paths.mkString (",")}, options=${if (null != options) options.toString else "null"})"
}
