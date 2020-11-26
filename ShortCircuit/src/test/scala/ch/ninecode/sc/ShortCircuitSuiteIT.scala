package ch.ninecode.sc

import java.io.File
import java.net.InetSocketAddress
import java.util.Properties

import scala.collection.JavaConverters.asScalaBufferConverter

import com.datastax.oss.driver.api.core.CqlSession
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.testutil.Unzip
import ch.ninecode.testutil.Using

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ShortCircuitSuiteIT
{

    import ch.ninecode.sc.ShortCircuitSuiteIT._

    def getSession: CqlSession =
    {
        val session: CqlSession = CqlSession
            .builder()
            .withLocalDatacenter("datacenter1")
            .addContactPoint(new InetSocketAddress("localhost", cassandra_port.toInt))
            .build()
        session
    }

    /**
     * Assert the difference between a number and a reference value are less than a threshold.
     *
     * @param number    the number to test
     * @param reference the reference value
     * @param epsilon   the difference limit
     * @param message   a message to display if the assert fails
     */
    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3, message: String = ""): Unit =
    {
        val diff = number - reference
        assert(Math.abs(diff) <= epsilon,
            if ("" == message)
                s"""$number vs. reference $reference differs by more than $epsilon ($diff)"""
            else
                message
        )
    }

    @Test def showHelp ()
    {
        ShortCircuit.main(Array("--unittest", "--help"))
    }

    @Test def runShortCircuitDemoData ()
    {
        ShortCircuit.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--id", ID,
                "--description", "demo data short circuit test",
                "--output", "Cassandra",
                "--host", "localhost",
                "--port", cassandra_port,
                "--keyspace", KEYSPACE,
                s"$FILE_DEPOT$FILENAME1.rdf"))
        using(getSession)
        {
            cassandraSession =>
                val sql = s"""select * from "$KEYSPACE".shortcircuit where id='$ID' and node='USR0023_topo' and equipment='USR0023' and terminal=1"""
                cassandraSession.execute(sql).all.asScala.headOption match
                {
                    case Some(row) =>
                        assert(row.getString("errors") == "", "no errors")
                        near(row.getDouble("low_ik"), 7689.81243)
                        near(row.getDouble("low_r"), 0.013776)
                        near(row.getDouble("low_x"), 0.016641)
                        near(row.getDouble("high_ik"), 7443.77019)
                        near(row.getDouble("high_r"), 0.015006)
                        near(row.getDouble("high_x"), 0.015618)
                        assert(row.getString("fuses") == "(125,100)", "fuseString")
                        assert(row.getBoolean("fuseok"), "fuseOK")
                        assert(row.getString("fusemax") == "630", "fuseMax")
                    case None =>
                        assert(false, "no result record")
                }
        }
    }
}

object ShortCircuitSuiteIT extends Unzip with Using
{
    val KEYSPACE = "Test"
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"
    val ID = "ShortCircuitSuiteIT"

    lazy val wd: String = "%s%s".format(new java.io.File(".").getCanonicalPath, System.getProperty("file.separator"))

    def delete (filename: String): Unit =
    {
        val _ = new File(filename).delete
    }

    def cassandra_port: String =
    {
        val properties: Properties =
        {
            val in = this.getClass.getResourceAsStream("/configuration.properties")
            val p = new Properties()
            p.load(in)
            in.close()
            p
        }
        val p = properties.getProperty("nativeTransportPort", "9042")
        if ("" == p)
            "9042"
        else
            p
    }

    @BeforeClass def before ()
    {
        // unpack the zip files
        if (!new File(s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
    }

    @AfterClass def after ()
    {
        // erase the unpacked file
        delete(s"$FILE_DEPOT$FILENAME1.rdf")
    }
}
