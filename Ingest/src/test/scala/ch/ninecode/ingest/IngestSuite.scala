package ch.ninecode.ingest

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util
import java.util.zip.ZipInputStream

import scala.collection.JavaConverters._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import ch.ninecode.ingest.Main.main

class IngestSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "data/"
    val MAPPING_FILE = "mapping.csv"
    val LPEX_FILE1 = "lpex1.txt"
    val LPEX_FILE2 = "lpex2.txt"

    /**
     * Add to the process environment.
     *
     * @see https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
     * @param newenv The list of key value pairs to add.
     */
    protected def setEnv (newenv: java.util.HashMap[String, String]): Unit =
    {
        try
        {
            val env: util.Map[String, String] = System.getenv
            for (cl <- Class.forName ("java.util.Collections").getDeclaredClasses)
            {
                if ("java.util.Collections$UnmodifiableMap" == cl.getName)
                {
                    val field = cl.getDeclaredField ("m")
                    field.setAccessible (true)
                    val obj = field.get (env)
                    val map = obj.asInstanceOf [java.util.Map[String, String]]
                    map.putAll (newenv)
                }
            }
        }
        catch
        {
            case e: Exception =>
                e.printStackTrace ()
        }
    }

    def setHadoopConfigurationDirectory (path: String): Unit =
    {
        if (null == System.getenv ("HADOOP_CONF_DIR"))
        {
            val newenv = new java.util.HashMap[String, String]()
            newenv.put ("HADOOP_CONF_DIR", path)
            setEnv (newenv)
        }
    }

    /**
     * This utility extracts files and directories of a standard zip file to
     * a destination directory.
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
         * @param file      The Zip file.
         * @param directory The directory to extract it to
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        def unzip (file: String, directory: String): Unit =
        {
            val dir = new File (directory)
            if (!dir.exists)
                dir.mkdir
            val zip = new ZipInputStream (new FileInputStream (file))
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
            zip.close ()
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
            val bos = new BufferedOutputStream (new FileOutputStream (path))
            val bytesIn = new Array[Byte](4096)
            var read = -1
            while (
            {
                read = zip.read (bytesIn)
                read != -1
            })
                bos.write (bytesIn, 0, read)
            bos.close ()
        }
    }

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

    override def beforeAll (): Unit =
    {
        setHadoopConfigurationDirectory ("/home/derrick/spark/hadoop-2.7.6/etc/hadoop")
    }

    test ("Help")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Ingest")
    {
        // to reset schema use:
        // delete from cimapplication.measured_value where mrid in ('HAS12345', 'HAS12346', 'HAS12347', 'HAS12348') and type in ('power', 'energy');
        main (Array ("--unittest", "--verbose",
            "--master", "local[*]",
            "--host", "beach",
            "--mapping", FILE_DEPOT + MAPPING_FILE,
            "--metercol", "meter",
            "--mridcol", "mRID",
            "--format", "LPEx",
            FILE_DEPOT + LPEX_FILE1,
            FILE_DEPOT + LPEX_FILE2))
    }
}
