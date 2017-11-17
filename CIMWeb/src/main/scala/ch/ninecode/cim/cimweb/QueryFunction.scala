package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.json.JsonStructure

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import com.datastax.spark.connector._

case class QueryFunction (sql: String, table_name: String = "", cassandra_table_name: String = "") extends CIMWebFunction
{
    jars = Array (jarForObject (this), jarForObject (SomeColumns ("mrid")), jarForObject (new com.twitter.jsr166e.LongAdder ()))

    def packRow (row: Row): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        for (column ← row.schema.fields.indices)
        {
            val name = row.schema(column).name
            row.schema(column).dataType.typeName match
            {
                case "boolean" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getBoolean (column))
                case "byte" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getByte (column))
                case "calendarinterval" ⇒ if (!row.isNullAt (column)) ret.add (name, row.get (column).toString)
                case "decimal" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getDouble (column))
                case "double" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getDouble (column))
                case "float" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getFloat (column))
                case "integer" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getInt (column))
                case "long" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getLong (column))
                case "short" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getShort (column))
                case "string" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getString (column))
                case "struct" ⇒ if (!row.isNullAt (column)) ret.add (name, packRow (row.get (column).asInstanceOf[Row]))
                case "timestamp" ⇒ if (!row.isNullAt (column)) ret.add (name, row.getTimestamp (column).getTime)
                case _ ⇒ if (!row.isNullAt (column)) ret.add (name, row.get (column).toString)
            }
        }

        ret
    }

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val df: DataFrame = spark.sql (sql)
        if ("" != table_name)
        {
            df.cache ()
            df.createOrReplaceTempView (table_name)
        }
        if ("" != cassandra_table_name)
        {
            val rows: RDD[Row] = df.rdd
            val len = rows.first.length
            if ((len == 6) || (len == 8) || (len == 10))
            {
                if (len == 6)
                    rows.map (row ⇒ (row.getString (0), row.getString (1), row.getString (2).substring (0, 10), row.getString (2), row.getDouble (3), row.getDouble (4), row.getString (5))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "real_a", "imag_a", "units"))
                else if (len == 8)
                    rows.map (row ⇒ (row.getString (0), row.getString (1), row.getString (2).substring (0, 10), row.getString (2), row.getDouble (3), row.getDouble (4), row.getDouble (5), row.getDouble (6), row.getString (7))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "real_a", "imag_a", "real_b", "imag_b", "units"))
                else if (len == 10)
                    rows.map (row ⇒ (row.getString (0), row.getString (1), row.getString (2).substring (0, 10), row.getString (2), row.getDouble (3), row.getDouble (4), row.getDouble (5), row.getDouble (6), row.getString (7), row.getDouble (8), row.getString (9))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c", "units"))
            }
            else
                // ToDo: need an error mechanism
                println ("Cassandra format error: RDD has rows of %d columns, not 6, 8, or 10 (\"mrid\", \"type\", \"time\", \"real_a\", \"imag_a\", ... \"units\")".format (rows.first.length))
        }
        val resultset: Array[Row] = df.collect
        val response = Json.createArrayBuilder
        resultset.map (packRow).map (response.add)
        response.build
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" (sql=")
        sb.append (sql)
        sb.append (")")
        sb.toString
    }
}