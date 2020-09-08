package ch.ninecode.ingest

import java.net.InetSocketAddress
import java.util.Properties

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import com.datastax.oss.driver.api.core.CqlSession

import org.junit.Test

import Ingest.main

class IngestSuiteIT
{
    val LOCALHOST = "localhost"
    val DEFAULT_CASSANDRA_PORT = 9042
    val KEYSPACE = "delete_me"
    val FILE_DEPOT = "data/"
    val MAPPING_FILE = "mapping.csv"
    val LPEX_FILE1 = "lpex1.txt"
    val LPEX_FILE2 = "lpex2.txt"
    val DAYLIGHT_MAPPING_FILE = "daylight_mapping.csv"
    val DAYLIGHT_START = "daylight_start.txt"
    val DAYLIGHT_END = "daylight_end.txt"
    val HDFS_HOST = "sandbox"

    def getSession: CqlSession =
    {
        val session: CqlSession = CqlSession
            .builder ()
            .withLocalDatacenter ("datacenter1")
            .addContactPoint (new InetSocketAddress (LOCALHOST, cassandra_port))
            .build ()
        session
    }

    def cassandra_port: Int =
    {
        val properties: Properties =
        {
            val in = this.getClass.getResourceAsStream ("/configuration.properties")
            val p = new Properties ()
            p.load (in)
            in.close ()
            p
        }
        val port = properties.getProperty ("nativeTransportPort", "9042")
        if ("" == port)
            DEFAULT_CASSANDRA_PORT
        else
            port.toInt
    }

    def checkCount (session: CqlSession, sql: String, count: Long, tag: String): Unit =
    {
        val records = session.execute (sql).all.asScala
        assert (1 == records.size, "record doesn't exist")
        records.headOption match
        {
            case Some (record) =>
                assert (record.getLong ("count") == count, s"number of records for $tag")
            case _ =>
        }
    }

    def checkValue (session: CqlSession, sql: String, real: Double, imag: Double, units: String): Unit =
    {
        val records = session.execute (sql).all.asScala
        assert (1 == records.size, "record doesn't exist")
        records.headOption match
        {
            case Some (row) =>
                assert (row.getDouble ("real_a") == real, "real value")
                assert (row.getDouble ("imag_a") == imag, "imaginary value")
                assert (row.getString ("units") == units, "units")
            case _ =>
        }
    }

    @Test def doHelp ()
    {
        main (Array ("--unittest", "--help"))
    }

    @Test def doIngest ()
    {
        val port = cassandra_port.toString

        main (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", LOCALHOST,
            "--port", port,
            "--keyspace", KEYSPACE,
            "--nocopy",
            "--mapping", s"$FILE_DEPOT$MAPPING_FILE",
            "--metercol", "meter",
            "--mridcol", "mRID",
            "--format", "LPEx",
            s"$FILE_DEPOT$LPEX_FILE1",
            s"$FILE_DEPOT$LPEX_FILE2"))

        val session = getSession

        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12345' and type='power'", 96L, "HAS12345")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12345' and type='power' and time='2019-03-02 23:15:00.000+0000'", 12075.0, 3750.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12346' and type='power'", 96L, "HAS12346")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12346' and type='power' and time='2019-03-02 23:15:00.000+0000'", 75.0, 0.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12347' and type='power'", 96L, "HAS12347")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12347' and type='power' and time='2019-03-02 23:15:00.000+0000'", 17400.0, -750.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy'", 2877L, "HAS12348")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy' and time='2017-08-31 22:15:00.000+0000'", 56.0, 0, "Wh")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy' and time='2017-09-30 22:00:00.000+0000'", 56.0, 0, "Wh")

        val _ = session.execute (s"delete from $KEYSPACE.measured_value where mrid in ('HAS12345', 'HAS12346', 'HAS12347', 'HAS12348') and type in ('power', 'energy')")
        session.close ()
    }

    @Test def useDaylightSavings_Time ()
    {
        main (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", LOCALHOST,
            "--port", cassandra_port.toString,
            "--keyspace", KEYSPACE,
            "--nocopy",
            "--mapping", s"$FILE_DEPOT$DAYLIGHT_MAPPING_FILE",
            "--mridcol", "mrid",
            "--metercol", "meter",
            "--format", "LPEx",
            s"$FILE_DEPOT$DAYLIGHT_START",
            s"$FILE_DEPOT$DAYLIGHT_END"))

        val session = getSession

        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS42' and type='energy' and time>'2018-10-28 23:45:00.000+0000'", 188L, "daylight savings start")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS42' and type='energy' and time<'2018-10-28 23:45:00.000+0000'", 196L, "daylight savings end")

        val _ = session.execute (s"delete from $KEYSPACE.measured_value where mrid in ('HAS42', 'HAS43') and type = 'energy'")

        session.close ()
    }

    @Test def importMSCONS ()
    {
        val FILE_DEPOT = "../MSCONSReader/data/"
        val MAPPING_FILE = s"${FILE_DEPOT}mapping.csv"
        val FILES = Array (
            s"${FILE_DEPOT}20191215_045127_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572840_-566879393.txt",
            s"${FILE_DEPOT}20191215_045127_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572841_-1811850990.txt",
            s"${FILE_DEPOT}20191215_045128_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572842_-1470816376.txt",
            s"${FILE_DEPOT}20191215_045128_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572843_-1813073308.txt",
            s"${FILE_DEPOT}20191215_045129_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572844_-1411967842.txt",
            s"${FILE_DEPOT}20191215_045129_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572845_1003992095.txt"
        )

        main (Array.concat (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", LOCALHOST,
            "--port", cassandra_port.toString,
            "--keyspace", KEYSPACE,
            "--nocopy",
            "--mapping", MAPPING_FILE,
            "--format", "MSCONS"), FILES))

        val session = getSession
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='USR0001' and type='energy'", 96L, "merged records")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='USR0001' and type='energy' and time = '2019-12-13 23:15:00.000+0000'", 36300, 10800, "Wh")
        val _ = session.execute (s"delete from $KEYSPACE.measured_value where mrid='USR0001' and type = 'energy'")
        session.close ()
    }

    @Test def importMSCONS_Zip ()
    {
        val FILE_DEPOT = "data" + System.getProperty ("file.separator")
        val MAPPING_FILE = s"${FILE_DEPOT}sample.csv"
        val FILE = s"${FILE_DEPOT}sample.zip"

        main (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", LOCALHOST,
            "--port", cassandra_port.toString,
            "--keyspace", KEYSPACE,
            "--mapping", MAPPING_FILE,
            "--format", "MSCONS",
            FILE))

        val session = getSession
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='USR0001' and type='energy'", 96L, "merged records")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='USR0001' and type='energy' and time = '2019-12-13 23:15:00.000+0000'", 36300, 10800, "Wh")
        val _ = session.execute (s"delete from $KEYSPACE.measured_value where mrid='USR0001' and type = 'energy'")
        session.close ()
    }
}
