package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructField

import scala.collection.mutable.HashMap

case class LoadCSVFileFunction (paths: Array[String], options: Iterable[(String, String)] = null) extends CIMWebFunction
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
                reader_options.put ("header", "false")
                reader_options.put ("ignoreLeadingWhiteSpace", "false")
                reader_options.put ("ignoreTrailingWhiteSpace", "false")
                reader_options.put ("sep", ",")
                reader_options.put ("quote", "\"")
                reader_options.put ("escape", "\\")
                reader_options.put ("mode", "PERMISSIVE")
                reader_options.put ("encoding", "UTF-8")
                reader_options.put ("delimiter", ",")
                reader_options.put ("comment", "#")
                reader_options.put ("nullValue", "")
                reader_options.put ("nanValue", "NaN")
                reader_options.put ("positiveInf", "Inf")
                reader_options.put ("negativeInf", "-Inf")
                reader_options.put ("dateFormat", "yyyy-MM-dd")
                reader_options.put ("timestampFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                reader_options.put ("inferSchema", "true")
            }
            val opts = Json.createObjectBuilder
            for (pair <- reader_options)
                opts.add (pair._1, pair._2)
            response.add ("options", opts)
            val tables = Json.createArrayBuilder
            files.map (filename ⇒
                {
                    val from = if (-1 == filename.lastIndexOf ("/")) 0 else filename.lastIndexOf ("/") + 1
                    val to = if (-1 == filename.lastIndexOf (".")) filename.length else filename.lastIndexOf (".")
                    val tablename = filename.substring (from, to)
                    val df: DataFrame = spark.sqlContext.read.format ("csv").options (reader_options).csv (filename)
                    val schema = Json.createObjectBuilder
                    val fields: Array[StructField] = df.schema.fields
                    for (column ← fields.indices)
                    {
                        val field = fields(column)
                        schema.add (field.name, field.dataType.json.stripPrefix("\"").stripSuffix("\""))
                    }
                    df.createOrReplaceTempView ("`" + tablename + "`")
                    val table = Json.createObjectBuilder
                    table.add ("tablename", tablename)
                    table.add ("schema", schema)
                    tables.add (table)
                }
            )
            response.add ("tables", tables)
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
