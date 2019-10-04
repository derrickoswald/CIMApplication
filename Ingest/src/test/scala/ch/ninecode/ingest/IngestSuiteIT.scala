package ch.ninecode.ingest

import java.util.Properties

import scala.collection.JavaConverters._

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Row
import com.datastax.driver.core.Session

import org.junit.Test

import ch.ninecode.ingest.Main.main

class IngestSuiteIT
{
    val KEYSPACE = "delete_me"
    val FILE_DEPOT = "data/"
    val MAPPING_FILE = "mapping.csv"
    val LPEX_FILE1 = "lpex1.txt"
    val LPEX_FILE2 = "lpex2.txt"
    val DAYLIGHT_MAPPING_FILE = "daylight_mapping.csv"
    val DAYLIGHT_START = "daylight_start.txt"
    val DAYLIGHT_END = "daylight_end.txt"

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
            9042
        else
            port.toInt
    }

    def checkCount (session: Session, sql: String, count: Long, tag: String): Unit =
    {
        assert (session.execute (sql).all.asScala.head.getLong ("count") == count, s"number of records for $tag")
    }

    def checkValue (session: Session, sql: String, real: Double, imag: Double, units: String): Unit =
    {
        val values = session.execute (sql).all
        assert (values.size == 1, "exists")
        val row: Row = values.asScala.head
        assert (row.getDouble ("real_a") == real, "real value")
        assert (row.getDouble ("imag_a") == imag, "imaginary value")
        assert (row.getString ("units") == units, "units")
    }

    @Test def Help ()
    {
        main (Array ("--unittest", "--help"))
    }

    @Test def Ingest ()
    {
        main (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", "localhost",
            "--port", cassandra_port.toString,
            "--keyspace", KEYSPACE,
            "--nocopy",
            "--mapping", s"${FILE_DEPOT}${MAPPING_FILE}",
            "--metercol", "meter",
            "--mridcol", "mRID",
            "--format", "LPEx",
            s"${FILE_DEPOT}${LPEX_FILE1}",
            s"${FILE_DEPOT}${LPEX_FILE2}"))

        val session = new Cluster.Builder ().addContactPoints ("localhost").withPort (cassandra_port).build ().connect()

        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12345' and type='power'", 96L, "HAS12345")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12345' and type='power' and time='2019-03-02 23:15:00.000+0000'", 12075.0, 3750.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12346' and type='power'", 96L, "HAS12346")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12346' and type='power' and time='2019-03-02 23:15:00.000+0000'", 75.0, 0.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12347' and type='power'", 96L, "HAS12347")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12347' and type='power' and time='2019-03-02 23:15:00.000+0000'", 17400.0, -750.0, "W")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy'", 2877L, "HAS12348")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy' and time='2017-08-31 22:15:00.000+0000'", 56.0, 0, "Wh")
        checkValue (session, s"select * from $KEYSPACE.measured_value where mrid='HAS12348' and type='energy' and time='2017-09-30 22:00:00.000+0000'", 56.0, 0, "Wh")

        session.execute (s"delete from $KEYSPACE.measured_value where mrid in ('HAS12345', 'HAS12346', 'HAS12347', 'HAS12348') and type in ('power', 'energy')")
        session.close ()
    }

    @Test def DaylightSavings_Time ()
    {
        main (Array ("--unittest", "--verbose",
            "--master", "local[2]",
            "--host", "localhost",
            "--port", cassandra_port.toString,
            "--keyspace", KEYSPACE,
            "--nocopy",
            "--mapping", s"${FILE_DEPOT}${DAYLIGHT_MAPPING_FILE}",
            "--mridcol", "mrid",
            "--metercol", "meter",
            "--format", "LPEx",
            s"${FILE_DEPOT}${DAYLIGHT_START}",
            s"${FILE_DEPOT}${DAYLIGHT_END}"))

        val session = new Cluster.Builder ().addContactPoints ("localhost").withPort (cassandra_port).build ().connect()

        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS42' and type='energy' and time>'2018-10-28 23:45:00.000+0000'", 188L, "daylight savings start")
        checkCount (session, s"select count(*) as count from $KEYSPACE.measured_value where mrid='HAS42' and type='energy' and time<'2018-10-28 23:45:00.000+0000'", 196L, "daylight savings end")

        session.execute (s"delete from $KEYSPACE.measured_value where mrid in ('HAS42', 'HAS43') and type = 'energy'")

        session.close ()
    }
}

