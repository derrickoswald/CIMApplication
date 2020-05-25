package ch.ninecode.copy

import java.util.Properties

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import com.datastax.driver.core.Cluster
import org.junit.Assume.assumeTrue
import org.junit.Test

import ch.ninecode.cim.CIMClasses
import ch.ninecode.copy.Main.main
import ch.ninecode.testutil.TestUtil

class CopySuiteIT extends TestUtil
{
    val TARGET_KEYSPACE = "delete_me"
    val DEFAULT_CASSANDRA_PORT = 9042

    override val classesToRegister: Array[Array[Class[_]]] = Array(CIMClasses.list)

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

        val session = new Cluster.Builder ().addContactPoints ("localhost").withPort (cassandra_port).build ().connect()
        val sql = s"select * from system_schema.keyspaces where keyspace_name='$TARGET_KEYSPACE'"
        val records = session.execute (sql).all.asScala
        assert (1 == records.size, s"""keyspace "$TARGET_KEYSPACE" doesn't exist""")
    }
}
