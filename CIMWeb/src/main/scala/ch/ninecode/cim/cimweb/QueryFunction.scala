package ch.ninecode.cim.cimweb

import java.nio.charset.StandardCharsets
import java.sql.Date
import java.sql.Time
import java.util.Base64

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.json.JsonStructure
import scala.collection.JavaConverters._

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.DataTypes.ASCII
import com.datastax.oss.driver.api.core.`type`.DataTypes.BIGINT
import com.datastax.oss.driver.api.core.`type`.DataTypes.BLOB
import com.datastax.oss.driver.api.core.`type`.DataTypes.BOOLEAN
import com.datastax.oss.driver.api.core.`type`.DataTypes.COUNTER
import com.datastax.oss.driver.api.core.`type`.CustomType
import com.datastax.oss.driver.api.core.`type`.DataTypes.DATE
import com.datastax.oss.driver.api.core.`type`.DataTypes.DECIMAL
import com.datastax.oss.driver.api.core.`type`.DataTypes.DOUBLE
import com.datastax.oss.driver.api.core.`type`.DataTypes.FLOAT
import com.datastax.oss.driver.api.core.`type`.DataTypes.INET
import com.datastax.oss.driver.api.core.`type`.DataTypes.INT
import com.datastax.oss.driver.api.core.`type`.ListType
import com.datastax.oss.driver.api.core.`type`.MapType
import com.datastax.oss.driver.api.core.`type`.SetType
import com.datastax.oss.driver.api.core.`type`.DataTypes.SMALLINT
import com.datastax.oss.driver.api.core.`type`.DataTypes.TEXT
import com.datastax.oss.driver.api.core.`type`.DataTypes.TIME
import com.datastax.oss.driver.api.core.`type`.DataTypes.TIMESTAMP
import com.datastax.oss.driver.api.core.`type`.DataTypes.TIMEUUID
import com.datastax.oss.driver.api.core.`type`.DataTypes.TINYINT
import com.datastax.oss.driver.api.core.`type`.TupleType
import com.datastax.oss.driver.api.core.`type`.DataTypes.VARINT
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.DataTypes.UUID
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.spark.connector._

case class QueryFunction (sql: String, cassandra: Boolean, table_name: String = "", cassandra_table_name: String = "") extends CIMWebFunction
{
    jars = Array (
        jarForObject (this),
        jarForObject (com.datastax.oss.driver.api.core.ConsistencyLevel.ANY)) // spark-cassandra-connector.jar

    def packRow (row: Row): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        for (column <- row.schema.fields.indices)
        {
            val name = row.schema (column).name
            row.schema (column).dataType.typeName match
            {
                case "boolean" => if (!row.isNullAt (column)) ret.add (name, row.getBoolean (column))
                case "byte" => if (!row.isNullAt (column)) ret.add (name, row.getByte (column))
                case "calendarinterval" => if (!row.isNullAt (column)) ret.add (name, row.get (column).toString)
                case "decimal" => if (!row.isNullAt (column)) ret.add (name, row.getDouble (column))
                case "double" => if (!row.isNullAt (column)) ret.add (name, row.getDouble (column))
                case "float" => if (!row.isNullAt (column)) ret.add (name, row.getFloat (column))
                case "integer" => if (!row.isNullAt (column)) ret.add (name, row.getInt (column))
                case "long" => if (!row.isNullAt (column)) ret.add (name, row.getLong (column))
                case "short" => if (!row.isNullAt (column)) ret.add (name, row.getShort (column))
                case "string" => if (!row.isNullAt (column)) ret.add (name, row.getString (column))
                case "struct" => if (!row.isNullAt (column)) ret.add (name, packRow (row.getAs[Row](column)))
                case "timestamp" => if (!row.isNullAt (column)) ret.add (name, row.getTimestamp (column).getTime)
                case _ => if (!row.isNullAt (column)) ret.add (name, row.get (column).toString)
            }
        }

        ret
    }

    def getDataTypeClass (datatype: DataType): Class[_] =
    {
        datatype match
        {
            case ASCII => classOf [String]
            case BIGINT => classOf [Long]
            //            case BLOB =>
            case BOOLEAN => classOf [Boolean]
            case COUNTER => classOf [Long]
            case DECIMAL => classOf [Double]
            case DOUBLE => classOf [Double]
            case FLOAT => classOf [Double]
            //            case INET =>
            case INT => classOf [Integer]
            case TEXT => classOf [String]
            case TIMESTAMP => classOf [Date]
            //            case UUID => if (!row.isNull (index)) ret.add (name, row.getString (index))
            case VARINT => classOf [Integer]
            //            case TIMEUUID =>
            //            case LIST =>
            //            case SET =>
            //            case MAP =>
            //            case CUSTOM =>
            //            case UDT =>
            //            case TUPLE =>
            case SMALLINT => classOf [Integer]
            case TINYINT => classOf [Integer]
            case DATE => classOf [Date]
            case TIME => classOf [Time]
            case _ => classOf [String] // BLOB, CUSTOM, INET, LIST, MAP, SET, TIMEUUID, TUPLE, UDT, UUID
        }
    }

    def packRow2 (row: com.datastax.oss.driver.api.core.cql.Row): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        val definitions = row.getColumnDefinitions
        for (index <- 0 until definitions.size)
        {
            //column: ColumnDefinitions.Definition
            val definition = definitions.get (index)
            val name: String = definition.getName.toString // column.getName
            val typ: DataType = definition.getType // column.getType
            typ match
            {
                case ASCII => if (!row.isNull (index)) ret.add (name, row.getString (index))
                case BIGINT => if (!row.isNull (index)) ret.add (name, row.getLong (index))
                case BLOB =>
                    if (!row.isNull (index))
                    {
                        val bytes = row.getByteBuffer (index)
                        val encoded = Base64.getEncoder.encode (bytes)
                        val string = new String (encoded.array, StandardCharsets.US_ASCII)
                        ret.add (name, string)
                    }
                case BOOLEAN => if (!row.isNull (index)) ret.add (name, row.getBoolean (index))
                case COUNTER => if (!row.isNull (index)) ret.add (name, row.getLong (index)) // ToDo: counter?
                case DECIMAL => if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case DOUBLE => if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case FLOAT => if (!row.isNull (index)) ret.add (name, row.getDouble (index))
                case INET => if (!row.isNull (index)) ret.add (name, row.getInetAddress (index).toString) // ToDo: internet address?
                case INT => if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case TEXT => if (!row.isNull (index)) ret.add (name, row.getString (index))
                case TIMESTAMP => if (!row.isNull (index)) ret.add (name, row.getInstant (index).toEpochMilli)
                case UUID => if (!row.isNull (index)) ret.add (name, row.getString (index))
                case VARINT => if (!row.isNull (index)) ret.add (name, row.getInt (index)) // ToDo: varying int?
                case TIMEUUID => if (!row.isNull (index)) ret.add (name, row.getString (index))
                case lt: ListType => if (!row.isNull (index))
                {
                    val c1 = lt.getElementType
                    val list = row.getList (name, getDataTypeClass (c1))
                    val array = Json.createArrayBuilder
                    list.asScala.foreach (x => array.add (x.toString))
                    ret.add (name, array)
                }
                case _: SetType => if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: set?
                case mt: MapType => if (!row.isNull (index))
                {
                    val c1 = mt.getKeyType
                    val c2 = mt.getValueType
                    val map = row.getMap (index, getDataTypeClass (c1), getDataTypeClass (c2))
                    val obj = map.entrySet.asScala.foldLeft (Json.createObjectBuilder)((b, x) => b.add (x.getKey.toString, x.getValue.toString))
                    ret.add (name, obj)
                }
                case _: CustomType => if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: custom?
                case _: UserDefinedType => if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: udt?
                case _: TupleType => if (!row.isNull (index)) ret.add (name, row.getString (index)) // ToDo: tuple?
                case SMALLINT => if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case TINYINT => if (!row.isNull (index)) ret.add (name, row.getInt (index))
                case DATE => if (!row.isNull (index)) ret.add (name, new Date (row.getInstant (index).toEpochMilli).toString)
                case TIME => if (!row.isNull (index)) ret.add (name, new Time (row.getInstant (index).toEpochMilli).toString)
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
                    for (row: com.datastax.oss.driver.api.core.cql.Row <- resultset.iterator.asScala)
                        response.add (packRow2 (row))
            }
        else
        {
            val df: DataFrame = spark.sql (sql)
            if ("" != table_name)
            {
                val _ = df
                    .cache ()
                    .createOrReplaceTempView (table_name)
            }
            if ("" != cassandra_table_name)
            {
                val rows: RDD[Row] = df.rdd
                val len = rows.first.length
                if ((len == 7) || (len == 11))
                {
                    if (len == 7)
                        rows.map (row => (row.getString (row.fieldIndex ("mrid")), row.getString (row.fieldIndex ("type")), row.getString (row.fieldIndex ("time")), row.getInt (row.fieldIndex ("interval")), row.getDouble (row.fieldIndex ("real_a")), row.getDouble (row.fieldIndex ("imag_a")), row.getString (row.fieldIndex ("units")))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "units"))
                    else
                        if (len == 11)
                            rows.map (row => (row.getString (row.fieldIndex ("mrid")), row.getString (row.fieldIndex ("type")), row.getString (row.fieldIndex ("time")), row.getInt (row.fieldIndex ("interval")), row.getDouble (row.fieldIndex ("real_a")), row.getDouble (row.fieldIndex ("imag_a")), row.getDouble (row.fieldIndex ("real_b")), row.getDouble (row.fieldIndex ("imag_b")), row.getDouble (row.fieldIndex ("real_c")), row.getDouble (row.fieldIndex ("imag_c")), row.getString (row.fieldIndex ("units")))).saveToCassandra ("cimapplication", cassandra_table_name, SomeColumns ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c", "units"))
                }
                else
                // ToDo: need an error mechanism
                    println ("""Cassandra format error: RDD has rows of %d columns, not 7, or 11 ("mrid", "type", "time", "period", "real_a", "imag_a", ..., "units")""".format (rows.first.length))
            }
            val results = df.collectAsList
            for (row <- results.asScala)
                response.add (packRow (row))
        }
        response.build
    }

    override def toString: String = s"${super.toString} (sql=$sql)"
}