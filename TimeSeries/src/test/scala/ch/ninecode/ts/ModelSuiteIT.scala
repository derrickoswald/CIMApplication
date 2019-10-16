package ch.ninecode.ts

import java.io.Closeable
import java.util.Properties

import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.ts.Main.main

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ModelSuiteIT
{
    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    def cassandra_port: String =
    {
        val properties: Properties =
        {
            val in = this.getClass.getResourceAsStream ("/configuration.properties")
            val p = new Properties ()
            p.load (in)
            in.close ()
            p
        }
        properties.getProperty ("nativeTransportPort", "9042")
    }

    @Test def makeModel ()
    {
        val KEYSPACE = "test"

        val begin = System.nanoTime ()
        main (Array (
            "Model", "--unittest",
            "--master", "local[*]",
            "--logging", "INFO",
            "--host", "localhost",
            "--port", cassandra_port,
            "--keyspace", KEYSPACE,
            "--tree_depth", "8", // it's just quicker this way
            "--model_file", "target/models/myDecisionTreeRegressorModel"))

        val modeled = System.nanoTime ()
        println ("modelling time: " + (modeled - begin) / 1e9 + " seconds")

        val kWh = 205.49709 * 96 * 365.25 / 1000.0
        main (Array (
            "Synthesize", "--unittest",
            "--master", "local[*]",
            "--logging", "INFO",
            "--host", "localhost",
            "--port", cassandra_port,
            "--keyspace", KEYSPACE,
            "--model_file", "target/models/myDecisionTreeRegressorModel",
            "--start", "2017-07-19T00:00:00.000+0000",
            "--end", "2018-03-31T23:45:00.000+0000",
            "--yearly_kWh", kWh.toString))

        val finish = System.nanoTime ()
        println ("synthesis time: " + (finish - modeled) / 1e9 + " seconds")
        println ("total execution: " + (finish - begin) / 1e9 + " seconds")
    }
}

