package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.json4s.JLong
import org.json4s.JsonAST.JString

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

object Formats extends Enumeration
{
    type Formats = Value
    val Belvis, LPEx, MSCONS, Custom, Parquet, Nyquist = Value
}

object Modes extends Enumeration
{
    type Modes = Value
    val Overwrite, Append = Value
}


/**
 *
 * @param mapping     Mapping CSV file or RDF file name.
 * @param metercol    Mapping CSV measurement identifier column name (column containing CH############################### values).
 * @param mridcol     Mapping CSV mRID column name (column containing HAS##### values).
 * @param timezone    Time zone for the measurement files.
 * @param mintime     Minimum time for ingestion timespan.
 *                    The default is the minimum representable time in the Linux epoch.
 * @param maxtime     Maximum time for ingestion timespan.
 *                    The default is the maximum representable time in the Linux epoch.
 * @param format      Type of data file, either Belvis, LPEx, MSCONS, Custom, Parquet, Nyquist.
 * @param mode        Ingest Mode: 'Overwrite' or 'Append'. Overwrite: overwrites existing values in database. Append: adds values to existing ones.
 * @param nocopy      If <code>true</code>, use the file names provided directly, without unzipping or transferring them to HDFS.
 * @param datafiles   Source Belvis/LPEx/MSCONS files.
 * @param keyspace    Cassandra keyspace.
 * @param replication Cassandra keyspace replication factor only if the keyspace is created.
 * @param aws_s3a_access_key access key used to execute Amazon Web Services
 * @param aws_s3a_secret_key secret access key used to execute Amazon Web Services
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
    datafiles: Seq[String] = Seq(),
    keyspace: String = "cimapplication",
    replication: Int = 1,
    aws_s3a_access_key: String = "",
    aws_s3a_secret_key: String = ""
) extends JSONAble[IngestJob]
{
    def toJSON: String = IngestJob.toJSON(this)

    def fromJSON (text: String): Either[String, IngestJob] = IngestJob.fromJSON(text)
}

object IngestJob extends JSON[IngestJob]
{
    def schemaResourceName: String = "IngestJobSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/IngestJobSchema.json" -> "resource:IngestJobSchema.json"
    )
    lazy val calendar0: Calendar =
    {
        val c = Calendar.getInstance()
        c.setTimeZone(TimeZone.getTimeZone("GMT"))
        c.setTimeInMillis(0L)
        c
    }

    lazy val iso_date_format: SimpleDateFormat =
    {
        val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        format.setCalendar(calendar0)
        format
    }

    def iso_parse (s: String): Calendar =
    {
        val ret = Calendar.getInstance()
        ret.setTime(iso_date_format.parse(s))
        ret
    }

    class TimeStampSerializer extends JSONCustomSerializer[Long](
        (format: org.json4s.Formats) =>
            (
                {
                    case JString(s) => iso_parse(s).getTimeInMillis
                    case JLong(l) => l
                },
                {
                    case x: Long => JLong(x)
                }
            )
    )

    class FormatSerializer extends JSONCustomSerializer[Formats.Value](
        (format: org.json4s.Formats) =>
            (
                {
                    case JString(s) => Formats.withName(s)
                },
                {
                    case x: Formats.Value => JString(x.toString)
                }
            )
    )

    class ModeSerializer extends JSONCustomSerializer[Modes.Value](
        (format: org.json4s.Formats) =>
            (
                {
                    case JString(s) => Modes.withName(s)
                },
                {
                    case x: Modes.Value => JString(x.toString)
                }
            )
    )

    def customSerializers: Seq[JSONCustomSerializer[_]] = List(
        new TimeStampSerializer,
        new FormatSerializer,
        new ModeSerializer)
}
