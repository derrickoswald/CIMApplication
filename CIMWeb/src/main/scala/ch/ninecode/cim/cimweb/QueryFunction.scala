package ch.ninecode.cim.cimweb

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.json.JsonStructure

import scala.collection.JavaConversions._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import com.datastax.driver.core.DataType
import com.datastax.driver.core.ResultSet
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.driver.core.DataType.Name._

case class QueryFunction (sql: String, cassandra: Boolean, table_name: String = "", cassandra_table_name: String = "") extends CIMWebFunction
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
    
    def packRow2 (row: com.datastax.driver.core.Row): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        val definitions = row.getColumnDefinitions
        for (index ← 0 until definitions.size)
        {
            //column: ColumnDefinitions.Definition
            val name: String = definitions.getName (index) // column.getName
            val typ: DataType = definitions.getType (index) // column.getType
            typ.getName match
            {
                case ASCII ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index))
                case BIGINT ⇒ if (!row.isNull (index)) ret.add (name, row.getLong (index))
                case BLOB ⇒
                    if (!row.isNull (index))
                    {
                        val bytes = row.getBytes (index).asReadOnlyBuffer
                        val encoded = Base64.getEncoder.encode (bytes)
                        val string = new String (encoded.array, StandardCharsets.UTF_8)
                        ret.add (name, string)
                    }
                case BOOLEAN ⇒ if (!row.isNull (index)) ret.add (name, row.getBool (index))
                case COUNTER ⇒ if (!row.isNull (index)) ret.add (name, row.getLong (index)) // ToDo: counter?
                case DECIMAL ⇒ if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case DOUBLE ⇒ if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case FLOAT ⇒ if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case INET ⇒ if (!row.isNull (index)) ret.add (name, row.getInet (index).toString) // ToDo: internet address?
                case INT ⇒ if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case TEXT ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index))
                case TIMESTAMP ⇒ if (!row.isNull (index)) ret.add (name, row.getTimestamp (index).getTime)
                case UUID ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index))
                case VARCHAR ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index))
                case VARINT ⇒ if (!row.isNull (index)) ret.add (name, row.getInt (index)) // ToDo: varying int?
                case TIMEUUID ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index))
                case LIST ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: list of what?
                case SET ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: set?
                case MAP ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: map?
                case CUSTOM ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: custom?
                case UDT ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: udt?
                case TUPLE ⇒ if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: tuple?
                case SMALLINT ⇒ if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case TINYINT ⇒ if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case DATE ⇒ if (!row.isNull (index)) ret.add (name, row.getDate (index).toString)
                case TIME ⇒ if (!row.isNull (index)) ret.add (name, row.getTime (index).toString)
            }
        }
        ret
    }

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val response = Json.createArrayBuilder
        if (cassandra)
            CassandraConnector (spark.sparkContext.getConf).withSessionDo
            {
                session =>
                val resultset: ResultSet = session.execute (sql)
                for (row: com.datastax.driver.core.Row ← resultset.iterator)
                    response.add (packRow2 (row))
            }
        else
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
                if ((len == 7) || (len == 11))
                {
                    if (len == 7)
                        rows.map (row ⇒ (row.getString (row.fieldIndex ("mrid")), row.getString (row.fieldIndex ("type")), row.getString (row.fieldIndex ("time")), row.getInt (row.fieldIndex ("interval")), row.getDouble (row.fieldIndex ("real_a")), row.getDouble (row.fieldIndex ("imag_a")), row.getString (row.fieldIndex ("units")))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "units"))
                    else if (len == 11)
                        rows.map (row ⇒ (row.getString (row.fieldIndex ("mrid")), row.getString (row.fieldIndex ("type")), row.getString (row.fieldIndex ("time")), row.getInt (row.fieldIndex ("interval")), row.getDouble (row.fieldIndex ("real_a")), row.getDouble (row.fieldIndex ("imag_a")), row.getDouble (row.fieldIndex ("real_b")), row.getDouble (row.fieldIndex ("imag_b")), row.getDouble (row.fieldIndex ("real_c")), row.getDouble (row.fieldIndex ("imag_c")), row.getString (row.fieldIndex ("units")))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c", "units"))
                }
                else
                    // ToDo: need an error mechanism
                    println ("""Cassandra format error: RDD has rows of %d columns, not 7, or 11 ("mrid", "type", "time", "period", "real_a", "imag_a", ..., "units")""".format (rows.first.length))
            }
            val results = df.collectAsList
            for (row ← results)
                response.add (packRow (row))
        }
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