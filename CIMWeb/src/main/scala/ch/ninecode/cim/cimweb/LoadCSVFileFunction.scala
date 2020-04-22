package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import scala.collection.mutable

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructField

final case class LoadCSVFileFunction (paths: Array[String], options: Iterable[(String, String)] = null) extends CIMWebFunction
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
            val files = paths.map (s => { val file = if (s.startsWith ("/")) s else s"/$s"; new Path (prefix, file).toString })
            val ff = Json.createArrayBuilder
            for (f <- files)
                ff.add (f)
            response.add ("files", ff)
            val reader_options = mutable.Map[String, String] (
                "header" -> "false",
                "ignoreLeadingWhiteSpace" -> "false",
                "ignoreTrailingWhiteSpace" -> "false",
                "sep" -> ",",
                "quote" -> "\"",
                "escape" -> "\\",
                "mode" -> "PERMISSIVE",
                "encoding" -> "UTF-8",
                "delimiter" -> ",",
                "comment" -> "#",
                "nullValue" -> "",
                "nanValue" -> "NaN",
                "positiveInf" -> "Inf",
                "negativeInf" -> "-Inf",
                "dateFormat" -> "yyyy-MM-dd",
                "timestampFormat" -> "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "inferSchema" -> "true"
            )
            if (null != options)
                reader_options ++= options
            val opts = Json.createObjectBuilder
            for (pair <- reader_options)
                opts.add (pair._1, pair._2)
            response.add ("options", opts)
            val tables = Json.createArrayBuilder
            files.map (filename =>
                {
                    val from = if (-1 == filename.lastIndexOf ("/")) 0 else filename.lastIndexOf ("/") + 1
                    val to = if (-1 == filename.lastIndexOf (".")) filename.length else filename.lastIndexOf (".")
                    val tablename = filename.substring (from, to)
                    val df: DataFrame = spark.sqlContext.read.format ("csv").options (reader_options).csv (filename)
                    val schema = Json.createObjectBuilder
                    val fields: Array[StructField] = df.schema.fields
                    for (column <- fields.indices)
                    {
                        val field = fields(column)
                        schema.add (field.name, field.dataType.json.stripPrefix("\"").stripSuffix("\""))
                    }
                    df.createOrReplaceTempView (s"`$tablename`")
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

    override def toString: String = s"${super.toString} (paths=${paths.mkString (",")}, options=${if (null != options) options.toString else "null"})"
}
