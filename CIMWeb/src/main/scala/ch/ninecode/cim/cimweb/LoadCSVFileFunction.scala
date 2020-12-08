package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructField

case class LoadCSVFileFunction (paths: Array[String], options: Iterable[(String, String)] = Iterable()) extends CIMWebFunction
{
    // load the file
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response =
            try
            {
                // read the file(s)
                val prefix = hdfs.getUri.toString
                val files = paths.map(s =>
                {
                    val file = if (s.startsWith("/")) s else s"/$s";
                    new Path(prefix, file).toString
                })
                val filelist = files.foldLeft(Json.createArrayBuilder)((b, f) => b.add(f))
                val reader_options = Map[String, String](
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
                ) ++ options
                val opts = reader_options.foldLeft(Json.createObjectBuilder)((b, p) => b.add(p._1, p._2))
                val id = reader_options.getOrElse("id", "Load")
                val tables = Json.createArrayBuilder
                files.foreach(filename =>
                {
                    val from = if (-1 == filename.lastIndexOf("/")) 0 else filename.lastIndexOf("/") + 1
                    val to = if (-1 == filename.lastIndexOf(".")) filename.length else filename.lastIndexOf(".")
                    val tablename = filename.substring(from, to)
                    spark.sparkContext.setJobGroup(id, s"load CSV $tablename")
                    val df: DataFrame = spark.sqlContext.read.format("csv").options(reader_options).csv(filename)
                    val schema = Json.createObjectBuilder
                    val fields: Array[StructField] = df.schema.fields
                    for (column <- fields.indices)
                    {
                        val field = fields(column)
                        schema.add(field.name, field.dataType.json.stripPrefix("\"").stripSuffix("\""))
                    }
                    df.createOrReplaceTempView(s"`$tablename`")
                    spark.sparkContext.setJobGroup(null, null)
                    val table = Json.createObjectBuilder
                        .add("tablename", tablename)
                        .add("schema", schema)
                    tables.add(table)
                }
                )
                Json.createObjectBuilder.add("files", filelist).add("options", opts).add("tables", tables)
            }
            catch
            {
                case e: Exception =>
                    Json.createObjectBuilder.add("error", e.getMessage)
            }
        response.build
    }

    override def toString: String = s"${super.toString} (paths=${paths.mkString(",")}, options=${if (null != options) options.toString else "null"})"
}
