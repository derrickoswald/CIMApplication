package ch.ninecode.sim

import javax.json.Json
import javax.json.JsonObject

import scala.collection.JavaConversions._

import com.datastax.driver.core.DataType
import com.datastax.driver.core.DataType.Name.ASCII
import com.datastax.driver.core.DataType.Name.BIGINT
import com.datastax.driver.core.DataType.Name.BLOB
import com.datastax.driver.core.DataType.Name.BOOLEAN
import com.datastax.driver.core.DataType.Name.COUNTER
import com.datastax.driver.core.DataType.Name.CUSTOM
import com.datastax.driver.core.DataType.Name.DATE
import com.datastax.driver.core.DataType.Name.DECIMAL
import com.datastax.driver.core.DataType.Name.DOUBLE
import com.datastax.driver.core.DataType.Name.FLOAT
import com.datastax.driver.core.DataType.Name.INET
import com.datastax.driver.core.DataType.Name.INT
import com.datastax.driver.core.DataType.Name.LIST
import com.datastax.driver.core.DataType.Name.MAP
import com.datastax.driver.core.DataType.Name.SET
import com.datastax.driver.core.DataType.Name.SMALLINT
import com.datastax.driver.core.DataType.Name.TEXT
import com.datastax.driver.core.DataType.Name.TIME
import com.datastax.driver.core.DataType.Name.TIMESTAMP
import com.datastax.driver.core.DataType.Name.TIMEUUID
import com.datastax.driver.core.DataType.Name.TINYINT
import com.datastax.driver.core.DataType.Name.TUPLE
import com.datastax.driver.core.DataType.Name.UDT
import com.datastax.driver.core.DataType.Name.UUID
import com.datastax.driver.core.DataType.Name.VARCHAR
import com.datastax.driver.core.DataType.Name.VARINT
import org.apache.spark.sql.SparkSession
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector

case class SimulationCassandraQuery (session: SparkSession, sql: String)
{
    def packRow (row: com.datastax.driver.core.Row): JsonObject =
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
                case ASCII ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index))
                case BIGINT ⇒ if (!row.isNull(index)) ret.add (name, row.getLong (index))
                case BLOB ⇒ if (!row.isNull(index)) ret.add (name, row.getBytes (index).toString) // ToDo: handle blob
                case BOOLEAN ⇒ if (!row.isNull(index)) ret.add (name, row.getBool (index))
                case COUNTER ⇒ if (!row.isNull(index)) ret.add (name, row.getLong (index)) // ToDo: counter?
                case DECIMAL ⇒ if (!row.isNull(index)) ret.add (name, row.getDouble (index))
                case DOUBLE ⇒ if (!row.isNull(index)) ret.add (name, row.getDouble (index))
                case FLOAT ⇒ if (!row.isNull(index)) ret.add (name, row.getDouble (index))
                case INET ⇒ if (!row.isNull(index)) ret.add (name, row.getInet (index).toString) // ToDo: internet address?
                case INT ⇒ if (!row.isNull(index)) ret.add (name, row.getInt (index))
                case TEXT ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index))
                case TIMESTAMP ⇒ if (!row.isNull(index)) ret.add (name, row.getTimestamp (index).getTime)
                case UUID ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index))
                case VARCHAR ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index))
                case VARINT ⇒ if (!row.isNull(index)) ret.add (name, row.getInt (index)) // ToDo: varying int?
                case TIMEUUID ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index))
                case LIST ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: list of what?
                case SET ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: set?
                case MAP ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: map?
                case CUSTOM ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: custom?
                case UDT ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: udt?
                case TUPLE ⇒ if (!row.isNull(index)) ret.add (name, row.getString (index)) // ToDo: tuple?
                case SMALLINT ⇒ if (!row.isNull(index)) ret.add (name, row.getInt (index))
                case TINYINT ⇒ if (!row.isNull(index)) ret.add (name, row.getInt (index))
                case DATE ⇒ if (!row.isNull(index)) ret.add (name, row.getDate (index).toString)
                case TIME ⇒ if (!row.isNull(index)) ret.add (name, row.getTime (index).toString)
            }
        }
        ret.build
    }

    def execute (): Seq[JsonObject] =
    {
        CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session =>
                val resultset: ResultSet = session.execute (sql)
                resultset.iterator.map (packRow).toSeq
        }
    }
}
