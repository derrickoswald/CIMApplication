package ch.ninecode.ts

import java.util.Properties

import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.ts.TimeSeries.main

@FixMethodOrder (MethodSorters.NAME_ASCENDING)
class ModelSuiteIT
{
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
        val port = properties.getProperty ("nativeTransportPort", "9042")
        if ("" == port)
            DEFAULT_CASSANDRA_PORT
        else
            port.toInt
    }

    def time[R] (template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        println (template.format ((t1 - t0) / 1e9))
        ret
    }

    @Test def makeModel ()
    {
        val KEYSPACE = "test"
        val kWh = 205.49709 * 96 * 365.25 / 1000.0

        time ("total execution: %s seconds")
        {
            time ("modelling time: %s seconds")
            {
                main (Array (
                    "Model", "--unittest",
                    "--master", "local[*]",
                    "--log", "INFO",
                    "--host", "localhost",
                    "--port", cassandra_port.toString,
                    "--keyspace", KEYSPACE,
                    "--tree_depth", "8", // it's just quicker this way
                    "--model_file", "target/models/myDecisionTreeRegressorModel"))
            }

            time ("synthesis time: %s seconds")
            {
                main (Array (
                    "Synthesize", "--unittest",
                    "--master", "local[*]",
                    "--log", "INFO",
                    "--host", "localhost",
                    "--port", cassandra_port.toString,
                    "--keyspace", KEYSPACE,
                    "--model_file", "target/models/myDecisionTreeRegressorModel",
                    "--start", "2017-07-19T00:00:00.000+0000",
                    "--end", "2018-03-31T23:45:00.000+0000",
                    "--yearly_kWh", kWh.toString))
            }
        }
    }
}

