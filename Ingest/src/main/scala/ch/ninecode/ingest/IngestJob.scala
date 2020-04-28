package ch.ninecode.ingest

import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonException
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.JsonValue
import javax.json.JsonWriterFactory
import javax.json.stream.JsonGenerator

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.JavaConversions.mapAsJavaMap

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Formats extends Enumeration
{
    type Formats = Value
    val Belvis, LPEx, MSCONS, Custom = Value
}

object Modes extends Enumeration
{
    type Modes = Value
    val Overwrite, Append = Value
}


/**
 *
 * @param mapping Mapping CSV file name.
 * @param metercol Mapping CSV measurement identifier column name (column containing CH############################### values).
 * @param mridcol Mapping CSV mRID column name (column containing HAS##### values).
 * @param timezone Time zone for the measurement files.
 * @param mintime Minimum time for ingestion timespan.
 *                The default is the minimum representable time in the Linux epoch.
 * @param maxtime Maximum time for ingestion timespan.
 *                The default is the maximum representable time in the Linux epoch.
 * @param format Type of data file, either Belvis, LPEx or MSCONS.
 * @param mode Ingest Mode: 'Overwrite' or 'Append'. Overwrite: overwrites existing values in database. Append: adds values to existing ones.
 * @param nocopy If <code>true</code>, use the file names provided directly, without unzipping or transferring them to HDFS.
 * @param datafiles Source Belvis/LPEx/MSCONS files.
 * @param keyspace Cassandra keyspace.
 * @param replication Cassandra keyspace replication factor only if the keyspace is created.
 */
case class IngestJob
(
    mapping: String = "",
    metercol: String = "Messpunktbezeichnung",
    mridcol: String = "NISNr",
    timezone: String = "Europe/Berlin",
    mintime: Long = 0L, // "1970-01-01 01:00:00"
    maxtime: Long = Int.MaxValue * 1000L, // "2038-01-19 04:14:07"
    format: Formats.Value = Formats.Belvis,
    mode: Modes.Value = Modes.Overwrite,
    nocopy: Boolean = false,
    datafiles: Seq[String] = Seq (),
    keyspace: String = "cimapplication",
    replication: Int = 1
)
{
    protected def getPrettyJsonWriterFactory: JsonWriterFactory =
    {
        import IngestJob.FACTORY_INSTANCE

        FACTORY_INSTANCE match
        {
            case Some (writer) => writer
            case None =>
                val writer = Json.createWriterFactory (Map[String, AnyRef](JsonGenerator.PRETTY_PRINTING -> "true"))
                FACTORY_INSTANCE = Some (writer)
                writer
        }
    }

    def asTimeStamp (t: Long): String =
    {
        val calendar: Calendar = Calendar.getInstance ()
        calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
        val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        iso_date_format.setCalendar (calendar)

        calendar.setTimeInMillis (t)
        iso_date_format.format (calendar)
    }

    def asJson: String =
    {
        val files = Json.createArrayBuilder
        datafiles.foreach (files.add)
        val json = Json.createObjectBuilder
            .add ("mapping", mapping)
            .add ("metercol", metercol)
            .add ("mridcol", mridcol)
            .add ("timezone", timezone)
            .add ("timespan", Json.createObjectBuilder.add ("mintime", mintime).add ("maxtime", maxtime))
            .add ("format", format.toString)
            .add ("mode", mode.toString)
            .add ("nocopy", nocopy)
            .add ("datafiles", files)
            .add ("keyspace", keyspace)
            .add ("replication", replication)
            .build
        val string = new StringWriter
        val writer = getPrettyJsonWriterFactory.createWriter (string)
        writer.write (json)
        writer.close ()
        string.toString
    }
}

object IngestJob
{
    lazy val log: Logger = LoggerFactory.getLogger (getClass)

    type objectParser[T] = (String, JsonObject) => Option[T]

    var FACTORY_INSTANCE: Option[JsonWriterFactory] = None

    def readJSON (json: String): Option[JsonObject] =
    {
        try
        {
            try
            Json.createReader (new StringReader (json)).readObject match
            {
                case obj: JsonObject => Some (obj)
                case _ =>
                    log.error ("not a JsonObject")
                    None
            }
            catch
            {
                case je: JsonException =>
                    log.error (s"""unparseable as JSON (${je.getMessage})""")
                    None
            }
        }
        catch
        {
            case e: Exception =>
                log.error (e.getMessage)
                None
        }
    }

    def typeString (value: JsonValue): String = value.getValueType.toString

    def parseTimespan (json: JsonObject): (Calendar, Calendar) =
    {
        val MEMBERNAME = "timespan"
        val calendar0: Calendar = Calendar.getInstance ()
        calendar0.setTimeZone (TimeZone.getTimeZone ("GMT"))
        calendar0.setTimeInMillis (0L)
        var mintime: Calendar = calendar0
        val calendar1: Calendar = Calendar.getInstance ()
        calendar1.setTimeZone (TimeZone.getTimeZone ("GMT"))
        calendar1.setTimeInMillis (Long.MaxValue)
        var maxtime: Calendar = calendar1

        val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        iso_date_format.setCalendar (calendar0)

        def iso_parse (s: String): Calendar =
        {
            val ret = Calendar.getInstance ()
            ret.setTime (iso_date_format.parse (s))
            ret
        }

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val interval = obj.asScala
                    interval.foreach
                    {
                        case ("mintime", v: JsonString) => mintime = iso_parse (v.getString)
                        case ("mintime", v: JsonNumber) => mintime.setTimeInMillis (v.longValue)
                        case ("maxtime", v: JsonString) => maxtime = iso_parse (v.getString)
                        case ("maxtime", v: JsonNumber) => maxtime.setTimeInMillis (v.longValue)
                        case (k: String, v: JsonValue) =>
                            log.warn (s"""unexpected JSON member or type: $MEMBERNAME["$k"] of type "${typeString (v)}"""")
                    }
                case _ =>
                    log.warn (s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString (value)}")""")
            }
        }
        else
            log.warn (s"""JSON member "$MEMBERNAME" not found, using defaults""")

        (mintime, maxtime)
    }

    def parseFormat (json: JsonObject): Formats.Value =
    {
        val MEMBERNAME = "format"

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            value match
            {
                case string: JsonString =>
                    Formats.withName (string.getString)
                case _ =>
                    log.warn (s"""JSON member "$MEMBERNAME" is not a JSON string (type "${typeString (value)}")""")
                    Formats.Belvis
            }
        }
        else
            Formats.Belvis
    }

    def parseMode (json: JsonObject): Modes.Value =
    {
        val MEMBERNAME = "mode"

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            value match
            {
                case string: JsonString =>
                    Modes.withName (string.getString)
                case _ =>
                    log.warn (s"""JSON member "$MEMBERNAME" is not a JSON string (type "${typeString (value)}")""")
                    Modes.Overwrite
            }
        }
        else
            Modes.Overwrite
    }

    def parseArrayOfString (member: String, json: JsonObject): Seq[String] =
    {
        if (json.containsKey (member))
        {
            val value = json.get (member)
            if (value.getValueType == JsonValue.ValueType.ARRAY)
            {
                value
                    .asJsonArray
                    .asScala
                    .flatMap (
                        item =>
                            item match
                            {
                                case string: JsonString => Some (string.getString)
                                case _ =>
                                    log.error (s"""unexpected JSON type for $member element ("${typeString (item)}")""")
                                    None
                            }
                    )
            }
            else
            {
                log.error (s"""unexpected JSON type for $member ("${typeString (value)}")""")
                Seq()
            }
        }
        else
            Seq()
    }

    def parseJob (options: IngestOptions) (json: JsonObject): Option[IngestJob] =
    {
        val mapping = json.getString ("mapping", "")
        if ("" == mapping)
        {
            log.error (s"""job does not specify a mapping file""")
            None
        }
        else
        {
            val metercol = json.getString ("metercol", "Messpunktbezeichnung")
            val mridcol = json.getString ("mridcol", "NISNr")
            val timezone = json.getString ("timezone", "Europe/Berlin")
            val (mintime, maxtime) = parseTimespan (json)
            val format = parseFormat (json)
            val mode = parseMode (json)
            val nocopy = json.getBoolean ("nocopy", false)
            val datafiles = parseArrayOfString ("datafiles", json)
            val keyspace = json.getString ("keyspace", "cimapplication")
            val replication = json.getInt ("replication", 1)

            Some (IngestJob (
                mapping = mapping,
                metercol = metercol,
                mridcol = mridcol,
                timezone = timezone,
                mintime = mintime.getTimeInMillis,
                maxtime = maxtime.getTimeInMillis,
                format = format,
                mode = mode,
                nocopy = nocopy,
                datafiles = datafiles,
                keyspace = keyspace,
                replication = replication
                )
            )
        }
    }

    def getAll (options: IngestOptions): Seq[IngestJob] =
    {
        if (options.verbose)
            LogManager.getLogger (getClass).setLevel (Level.INFO)
        lazy val log = LoggerFactory.getLogger (getClass)
        val jsons = options.ingestions.map (readJSON)
        val ingestions = jsons.flatten
        if (jsons.length != ingestions.length)
            log.warn ("not all ingestions will be processed")
        val jobs = ingestions.flatMap (parseJob (options))
        if (ingestions.length != jobs.length)
            log.warn ("some ingestion JSON files have errors")
        jobs
    }
}
