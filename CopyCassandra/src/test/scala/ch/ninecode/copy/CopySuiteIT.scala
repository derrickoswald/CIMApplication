package ch.ninecode.copy

import java.net.InetSocketAddress
import java.util.Properties

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import com.datastax.oss.driver.api.core.CqlSession
import org.junit.Assume.assumeTrue
import org.junit.Test

import Copy.main
import ch.ninecode.testutil.TestUtil

class CopySuiteIT extends TestUtil
{
    val SOURCE_HOST = "beach"
    val SOURCE_KEYSPACE = "cimapplication"
    val TARGET_KEYSPACE = "delete_me"
    val DEFAULT_CASSANDRA_PORT = 9042

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
        val port = properties.getProperty ("nativeTransportPort")
        if ("" == port)
            DEFAULT_CASSANDRA_PORT
        else
            port.toInt
    }

    def getSourceSession: CqlSession =
    {
        val session: CqlSession = CqlSession
            .builder ()
            .withLocalDatacenter ("datacenter1")
            .addContactPoint (new InetSocketAddress (SOURCE_HOST, DEFAULT_CASSANDRA_PORT))
            .build ()
        session
    }

    def getTargetSession: CqlSession =
    {
        val session: CqlSession = CqlSession
            .builder ()
            .withLocalDatacenter ("datacenter1")
            .addContactPoint (new InetSocketAddress ("localhost", cassandra_port))
            .build ()
        session
    }

    @Test def help ()
    {
        main (Array ("--unittest", "--help"))
    }

    @Test def copy ()
    {
        assumeTrue (s"$SOURCE_HOST:9042 is not listening", serverListening (SOURCE_HOST, 9042))
        assumeTrue (s"localhost:$cassandra_port is not listening", serverListening ("localhost", cassandra_port))

        val source_session = getSourceSession
        val target_session = getTargetSession

        val exists = source_session.execute (s"select * from system_schema.keyspaces where keyspace_name='$SOURCE_KEYSPACE'").all.asScala
        assumeTrue (s"""keyspace "$SOURCE_KEYSPACE" doesn't exist in $SOURCE_HOST:9042""", 1 == exists.size)

        main (Array ("--unittest",
            "--master", "local[*]",
            "--source_host", SOURCE_HOST,
            "--source_port", "9042",
            "--source_keyspace", SOURCE_KEYSPACE,
            "--target_host", "localhost",
            "--target_port", cassandra_port.toString,
            "--target_keyspace", TARGET_KEYSPACE))

        val records = target_session.execute (s"select * from system_schema.keyspaces where keyspace_name='$TARGET_KEYSPACE'").all.asScala
        assert (1 == records.size, s"""keyspace "$TARGET_KEYSPACE" doesn't exist""")

        val tables = target_session.execute (s"select table_name from system_schema.tables where keyspace_name='$TARGET_KEYSPACE'").all.asScala.map (_.getString (0))
        assert (0 != tables.size, s"""tables in "$TARGET_KEYSPACE" don't exist""")

        val skip = Array ("version", "measured_value", "simulated_value") // avoid read timeout and extra version
        for (table <- tables if !skip.contains (table))
        {
            target_session.execute (s"""select count(*) from "$TARGET_KEYSPACE".$table""").all.asScala.toList match
            {
                case target :: Nil =>
                    source_session.execute (s"""select count(*) from "$SOURCE_KEYSPACE".$table""").all.asScala.toList match
                    {
                        case source :: Nil =>
                            val target_count = target.getLong (0)
                            val source_count = source.getLong (0)
                            assert (target_count == source_count, s"target count ($target_count) does not match source count ($source_count) for table $table")
                        case _ => fail (s"""failed to count "$SOURCE_KEYSPACE".$table""")
                    }
                case _ => fail (s"""failed to count "$TARGET_KEYSPACE".$table""")
            }
        }
    }
}
