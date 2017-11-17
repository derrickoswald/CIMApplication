package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession

import scala.collection.mutable.HashMap

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
            val reader_options = new HashMap[String, String] ()
            if (null != options)
                reader_options ++= options
            else
            {
                reader_options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
                reader_options.put ("ch.ninecode.cim.make_edges", "false")
                reader_options.put ("ch.ninecode.cim.do_join", "false")
                reader_options.put ("ch.ninecode.cim.do_topo", "false")
                reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
                reader_options.put ("ch.ninecode.cim.do_deduplication", if (1 < files.length) "true" else "false")
            }
            val opts = Json.createObjectBuilder
            for (pair <- reader_options)
                opts.add (pair._1, pair._2)
            response.add ("options", opts)
            reader_options.put ("path", files.mkString (",")) // ToDo: why is this still needed?
            val elements = spark.read.format ("ch.ninecode.cim").options (reader_options).load (files:_*)
            val count = elements.count
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
