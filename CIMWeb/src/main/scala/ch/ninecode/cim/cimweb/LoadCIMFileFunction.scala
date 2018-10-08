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

case class LoadCIMFileFunction (paths: Array[String], options: Iterable[(String, String)] = null) extends CIMWebFunction
{
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
                    ("ch.ninecode.cim.split_maxsize", "67108864")
                )
            }
            else
                options

            // echo settings to the response
            val opts = Json.createObjectBuilder
            for (pair <- op)
                opts.add (pair._1, pair._2)
            response.add ("options", opts)

            // there is a problem (infinite loop) if post processing is done in the CIMReader
            // so we extract out topo, edge, and join processing
            var topo = false
            var isld = false
            var join = false
            var edge = false
            val reader_options = new HashMap[String, String] ()
            for (option ← op)
                option._1 match
                {
                    case "ch.ninecode.cim.do_topo" ⇒
                        topo = isld || (try { option._2.toBoolean } catch { case _: Throwable => false })
                    case "ch.ninecode.cim.do_topo_islands" ⇒
                        isld = try { option._2.toBoolean } catch { case _: Throwable => false }
                        topo = topo || isld
                    case "ch.ninecode.cim.do_join" ⇒
                        join = try { option._2.toBoolean } catch { case _: Throwable => false }
                    case "ch.ninecode.cim.make_edges" ⇒
                        edge = try { option._2.toBoolean } catch { case _: Throwable => false }
                    case _ ⇒
                        reader_options.put (option._1, option._2)
                }
            reader_options.put ("path", files.mkString (","))

            val elements = spark.read.format ("ch.ninecode.cim").options (reader_options).load (files:_*)
            var count = elements.count
            if (topo)
            {
                val ntp = CIMNetworkTopologyProcessor (spark)
                val elements2 = ntp.process (
                    CIMTopologyOptions (
                        identify_islands = isld,
                        storage = org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER))
                count = elements2.count
            }
            if (join)
            {
                val join = new CIMJoin (spark, StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
                val elements3 = join.do_join ()
                count = elements3.count
            }
            if (edge)
            {
                val edges = new CIMEdges (spark, StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
                val elements4 = edges.make_edges (topo)
                count = elements4.count
            }
            response.add ("elements", count)
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
