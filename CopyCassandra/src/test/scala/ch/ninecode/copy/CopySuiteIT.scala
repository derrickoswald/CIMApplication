package ch.ninecode.copy

import java.net.InetSocketAddress
import java.util.Properties

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import com.datastax.oss.driver.api.core.CqlSession

import org.junit.Assume.assumeTrue
import org.junit.Test

import ch.ninecode.copy.Main.main
import ch.ninecode.testutil.TestUtil

class CopySuiteIT extends TestUtil
{
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

    def getSession: CqlSession =
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
        assumeTrue (s"beach:9042 is not listening", serverListening ("beach", 9042))
        assumeTrue (s"localhost:$cassandra_port is not listening", serverListening ("localhost", cassandra_port))
        main (Array ("--unittest",
            "--master", "local[*]",
            "--source_host", "beach",
            "--source_port", "9042",
            "--source_keyspace", "cimapplication",
            "--target_host", "localhost",
            "--target_port", cassandra_port.toString,
            "--target_keyspace", TARGET_KEYSPACE))

        val session = getSession
        val sql = s"select * from system_schema.keyspaces where keyspace_name='$TARGET_KEYSPACE'"
        val records = session.execute (sql).all.asScala
        assert (1 == records.size, s"""keyspace "$TARGET_KEYSPACE" doesn't exist""")
    }
}
