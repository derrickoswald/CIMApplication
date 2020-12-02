package ch.ninecode.mfi

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
class MaximumFeedInSuiteIT
{
    import ch.ninecode.mfi.MaximumFeedInSuiteIT._

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
        MaximumFeedIn.main(Array("--unittest", "--help"))
    }

    // results from SQlite
    def getExpectedValue (house: String): Double =
    {
        house match
        {
            case "USR0001" => 61000.0
            case "USR0002" => 61000.0
            case "USR0003" => 61000.0
            case "USR0004" => 61000.0
            case "USR0005" => 61000.0
            case "USR0006" => 61000.0
            case "USR0007" => 61000.0
            case "USR0008" => 46000.0
            case "USR0009" => 46000.0
            case "USR0010" => 46000.0
            case "USR0011" => 46000.0
            case "USR0012" => 46000.0
            case "USR0013" => 46000.0
            case "USR0014" => 61000.0
            case "USR0015" => 61000.0
            case "USR0016" => 46000.0
            case "USR0017" => 46000.0
            case "USR0018" => 46000.0
            case "USR0019" => 117000.0
            case "USR0020" => 117000.0
            case "USR0021" => 46000.0
            case "USR0022" => 46000.0
            case "USR0023" => 46000.0
            case "USR0024" => 80000.0
            case "USR0025" => 80000.0
            case "USR0026" => 79000.0
            case "USR0027" => 80000.0
            case "USR0028" => 80000.0
            case "USR0029" => 80000.0
            case "USR0030" => 80000.0
            case "USR0031" => 117000.0
            case "USR0032" => 46000.0
            case "USR0033" => 46000.0
            case "USR0034" => 46000.0
            case "USR0035" => 46000.0
            case "USR0036" => 61000.0
            case "USR0037" => 61000.0
            case "USR0038" => 61000.0
        }
    }

    @Test def runMaximumFeedInDemoData ()
    {
        MaximumFeedIn.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--id", ID,
//                "--description", "demo data maximum feed in test",
                "--all",
                "--output", "Cassandra",
                "--host", "localhost",
                "--port", cassandra_port,
                "--keyspace", KEYSPACE,
                s"$FILE_DEPOT$FILENAME1.rdf"))
        using(getSession)
        {
            cassandraSession =>
                val sql = s"""select trafo, house, maximum, reason, details from "$KEYSPACE".maximumfeedin where id='$ID' and house like 'USR%'"""
                val results = cassandraSession.execute(sql).all.asScala
                assert(results.size == 38, "number of records")
                for (result <- results)
                {
                    val house = result.getString("house")
                    near(result.getDouble("maximum"), getExpectedValue(house))
                    assert(result.getString("reason") == "current limit", s"reason for $house")
                }
        }
    }
}


object MaximumFeedInSuiteIT extends Unzip with Using
{
    val KEYSPACE = "Test"
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"
    val ID = "MaximumFeedInSuiteIT"

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

