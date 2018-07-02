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
import Array._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import ch.ninecode.ingest.Main.main

class IngestSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "private_data/"
    val FILENAME1 = "Stoerung_Messstellen2.csv"
    val FILENAME2 = "20180412_080258_Belvis_manuell_TS Amalerven.zip"
    val FILENAME3 = "20180412_122100_Belvis_manuell_TS Ara_BadRagaz .zip"
    val FILENAME4 = "20180412_150702_Belvis_manuell_TS BahnhofWienacht.zip"
    val FILENAME5 = "20180412_150901_Belvis_manuell_TS Bahnhofplatz.zip"
    val FILENAME6 = "20180412_122400_Belvis_manuell_TSAuenVaettis.zip"
    val FILENAME7 = "20180419_111102_Belvis_manuell_Bad_Ragaz1.zip"
    val FILENAME8 = "20180412_122600_Belvis_manuell_TS Bach.zip"
    val FILENAME9 = "20180419_115102_Belvis_manuell_Bad_Ragaz2.zip"
    val FILENAME10 = "20180412_123800_Belvis_manuell_TS Badrieb.zip"
    val FILENAME11 = "20180412_124500_Belvis_manuell_TS Bahnhof Sargans.zip"

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
                    val map = obj.asInstanceOf[java.util.Map[String, String]]
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

    /**
     * Set SPARK_LOCAL_IP to the IP address in dotted-quad format (e.g. 1.2.3.4) if it isn't set.
     *
     * Avoids "Set SPARK_LOCAL_IP if you need to bind to another address" warning message.
     * @see findLocalInetAddress: https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/util/Utils.scala
     */
    def setLocalIP (): Unit =
    {
        if (null == System.getenv ("SPARK_LOCAL_IP"))
        {
            val address = InetAddress.getLocalHost
            if (address.isLoopbackAddress)
            {
                // Address resolves to something like 127.0.1.1, which happens on Debian; try to find
                // a better address using the local network interfaces
                // getNetworkInterfaces returns ifs in reverse order compared to ifconfig output order
                // on unix-like system. On windows, it returns in index order.
                // It's more proper to pick ip address following system output order.
                val interfaces = NetworkInterface.getNetworkInterfaces.asScala.toSeq
                val windows = System.getProperty ("os.name").startsWith ("Windows") // underlying operating system is Windows

                for (ni <- if (windows) interfaces else interfaces.reverse)
                {
                    val addresses = ni.getInetAddresses.asScala.filterNot (addr => addr.isLinkLocalAddress || addr.isLoopbackAddress).toSeq
                    if (addresses.nonEmpty)
                    {
                        val addr = addresses.find (_.isInstanceOf [Inet4Address]).getOrElse (addresses.head)
                        // because of Inet6Address.toHostName may add interface at the end if it knows about it
                        val ip = InetAddress.getByAddress (addr.getAddress)
                        // We've found an address that looks reasonable!
                        val newenv = new java.util.HashMap[String, String] ()
                        newenv.put ("SPARK_LOCAL_IP", ip.getHostAddress)
                        setEnv (newenv)
                    }
                }
            }
        }
    }

    def setHadoopConfigurationDirectory (path: String): Unit =
    {
        if (null == System.getenv ("HADOOP_CONF_DIR"))
        {
            val newenv = new java.util.HashMap[String, String] ()
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
         * @param file The Zip file.
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
         * @param zip The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            val bos = new BufferedOutputStream (new FileOutputStream (path))
            val bytesIn = new Array[Byte](4096)
            var read = -1
            while ({ read = zip.read (bytesIn); read != -1 })
                bos.write (bytesIn, 0, read)
            bos.close ()
        }
    }

    def using[T <: Closeable, R](resource: T)(block: T => R): R =
    {
        try { block (resource) }
        finally { resource.close () }
    }

    override def beforeAll ()
    {
        setLocalIP ()
        setHadoopConfigurationDirectory ("/home/derrick/spark/hadoop-2.7.3/etc/hadoop")
    }

    test ("Help")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Ingest")
    {
        main (Array ("--unittest", "--verbose",
            "--master", "spark://sandbox:7077",
            "--host", "sandbox",
            "--mapping", FILE_DEPOT + FILENAME1,
            "--mintime", "2017-10-22 00:00:00", "--maxtime", "2018-02-14 00:00:00",
            FILE_DEPOT + FILENAME2,
            FILE_DEPOT + FILENAME3,
            FILE_DEPOT + FILENAME4,
            FILE_DEPOT + FILENAME5,
            FILE_DEPOT + FILENAME6,
            FILE_DEPOT + FILENAME7,
            FILE_DEPOT + FILENAME8,
            FILE_DEPOT + FILENAME9,
            FILE_DEPOT + FILENAME10,
            FILE_DEPOT + FILENAME11))
    }

    test ("Full")
    {
        val prefix = "/home/derrick/trial/aws/"
        val root = prefix + "Messdaten_20180424/"
        val dir = new File (root)
        val files = dir.list.filter (_.endsWith (".zip")).map (root + _)
        val args = concat (Array (
            "--unittest", "--verbose",
            "--master", "spark://sandbox:7077",
            "--host", "sandbox",
            "--mapping", prefix + "Messdaten_20180424/Steuerung_Messstellen2.csv"),
            files)
        main (args)
    }
}
