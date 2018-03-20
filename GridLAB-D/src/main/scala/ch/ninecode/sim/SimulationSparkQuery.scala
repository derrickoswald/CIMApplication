package ch.ninecode.sim

import javax.json.Json
import javax.json.JsonObject

import scala.collection.JavaConversions._

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

case class SimulationSparkQuery (session: SparkSession, sql: String)
{
    def packRow (row: Row): JsonObject =
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
        ret.build
    }

    def execute (): Seq[JsonObject] =
    {
        val df: DataFrame = session.sql (sql)
        df.toLocalIterator.map (row ⇒ packRow (row)).toSeq
    }
}
