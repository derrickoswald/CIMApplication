package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.json.JsonStructure

import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame

case class QueryFunction (sql: String) extends CIMWebFunction
{
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
        val resultset: Array[Row] = spark.sql (sql).collect
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