package ch.ninecode.ts

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipInputStream

import scala.collection.immutable

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.ts.Main.main

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TimeSeriesSuiteIT
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

    @Test def Help ()
    {
        main (Array ("--unittest", "--help"))
    }

    @Test def TimeSeries ()
    {
        main (Array ("--unittest",
            "--master", "local[*]",
            "--logging", "INFO",
            "--host", "localhost",
            "--port", cassandra_port,
            "--keyspace", "test"))
    }
}

object TimeSeriesSuiteIT
{
    val KEYSPACE = "test"
    val FILE_DEPOT = "data/"
    val FILENAME0 = "measurement_data"
    lazy val wd: String = "%s%s".format (new java.io.File (".").getCanonicalPath, System.getProperty ("file.separator"))

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

    /**
     * This utility extracts files and directories of a standard zip file to a destination directory.
     *
     * @author www.codejava.net
     *
     */
    class Unzip
    {
        /**
         * Extracts a zip file specified by the file to a directory.
         *
         * The directory will be created if does not exist.
         *
         * @param file the zip file
         * @param directory the directory to extract it to
         * @throws IOException if there is a problem with the zip extraction
         */
        @throws[IOException]
        def unzip (file: String, directory: String): Unit =
        {
            val dir = new File (directory)
            if (!dir.exists)
                dir.mkdir
            using (new ZipInputStream (new FileInputStream (file)))
            {
                zip =>
                    var entry = zip.getNextEntry
                    // iterates over entries in the zip file
                    while (null != entry)
                    {
                        val path = directory + entry.getName
                        if (!entry.isDirectory)
                        // if the entry is a file, extracts it
                            extractFile (zip, path)
                        else
                        // if the entry is a directory, make the directory
                            new File (path).mkdir
                        zip.closeEntry ()
                        entry = zip.getNextEntry
                    }
            }

        }

        /**
         * Extracts a zip entry (file entry).
         *
         * @param zip  The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            using (new BufferedOutputStream (new FileOutputStream (path)))
            {
                bos =>
                    val bytes = new Array[Byte](4096)
                    var read = -1
                    while ({ read = zip.read (bytes); read != -1 })
                        bos.write (bytes, 0, read)
            }
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

    def populate_measured_data (): Unit =
    {
        println ("creating Spark session")

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SummarySuiteIT")
        configuration.setMaster ("local[*]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.cassandra.connection.host", "localhost")
        configuration.set ("spark.cassandra.connection.port", cassandra_port)

        val session = SparkSession.builder.config (configuration).getOrCreate
        session.sparkContext.setLogLevel ("WARN")

        val measurement_options = immutable.HashMap (
            "header" → "false",
            "ignoreLeadingWhiteSpace" → "false",
            "ignoreTrailingWhiteSpace" → "false",
            "sep" → ",",
            "quote" → "\"",
            "escape" → "\\",
            "encoding" → "UTF-8",
            "comment" → "#",
            "nullValue" → "",
            "nanValue" → "NaN",
            "positiveInf" → "Inf",
            "negativeInf" → "-Inf",
            "dateFormat" → "yyyy-MM-dd",
            "timestampFormat" → "yyyy-MM-dd HH:mm:ss",
            "mode" → "DROPMALFORMED",
            "inferSchema" → "true"
        )
        Schema (session, KEYSPACE, 1, true).make
        println (s"reading $FILE_DEPOT$FILENAME0.csv")
        val df = session.sqlContext.read.format ("csv").options (measurement_options).csv (s"$FILE_DEPOT$FILENAME0.csv")
        val ok = df.rdd.map (row ⇒ (row.getString (0), "energy", row.getTimestamp (1), 900000, row.getDouble (2), 0.0, "Wh"))
        println (s"saving to $KEYSPACE.measured_value")
        ok.saveToCassandra (KEYSPACE, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        println ("stopping Spark session")
        session.stop
    }

    @BeforeClass def before ()
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME0.csv").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME0.zip", FILE_DEPOT)
        println (s"populating $KEYSPACE.measured_value")
        populate_measured_data ()
    }

    @AfterClass def after ()
    {
        // erase the unpacked file
        new File (s"$FILE_DEPOT$FILENAME0.csv").delete
    }
}