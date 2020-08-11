package test.scala.ch.ninecode.copy

import java.util.Properties

import scala.collection.JavaConverters._

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Row
import com.datastax.driver.core.Session

import org.junit.Test

import ch.ninecode.copy.Main.main

class CopySuiteIT
{
    val TARGET_KEYSPACE = "delete_me"

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
            9042
        else
            port.toInt
    }

    @Test def Help ()
    {
        main (Array ("--unittest", "--help"))
    }

    @Test def Copy ()
    {
        main (Array ("--unittest",
            "--master", "local[*]",
            "--source_host", "beach",
            "--source_port", "9042",
            "--source_keyspace", "test",
            "--target_host", "localhost",
            "--target_port", cassandra_port.toString,
            "--target_keyspace", TARGET_KEYSPACE))

        //        System.in.read
        //        val session = new Cluster.Builder ().addContactPoints ("localhost").withPort (cassandra_port).build ().connect()
        //
        //        session.close ()
    }
}
