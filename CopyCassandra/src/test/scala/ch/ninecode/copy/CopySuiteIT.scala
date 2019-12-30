package ch.ninecode.copy

import java.util.Properties

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

    /* @Test */ def Copy ()
    {
        main (Array ("--unittest",
            "--master", "local[*]",
            "--source_host", "beach",
            "--source_port", "9042",
            "--source_keyspace", "subsample",
            "--target_host", "localhost",
            "--target_port", cassandra_port.toString,
            "--target_keyspace", TARGET_KEYSPACE))
    }
}
