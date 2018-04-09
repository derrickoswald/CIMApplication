package ch.ninecode.sim

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util
import java.util.zip.ZipInputStream

import ch.ninecode.sim.Main.main
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import scala.collection.JavaConverters._

class SimulationSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "TRA2755_terminal_2_island"

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
        if (!new File (FILE_DEPOT + FILENAME1 + ".rdf").exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME1 + ".zip", FILE_DEPOT)
    }

    test ("Help")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Basic")
    {
        val json = FILE_DEPOT + "basic.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    """
                    |{
                    |    "name": "Sample",
                    |    "description": "sample simulation file for illustrative purposes",
                    |    "cim": "data/TRA2755_terminal_2_island.rdf",
                    |    "cimreaderoptions": {
                    |        "ch.ninecode.cim.do_about": false,
                    |        "ch.ninecode.cim.do_normalize": false,
                    |        "ch.ninecode.cim.do_deduplication": false,
                    |        "ch.ninecode.cim.make_edges": false,
                    |        "ch.ninecode.cim.do_join": false,
                    |        "ch.ninecode.cim.do_topo_islands": false,
                    |        "ch.ninecode.cim.do_topo": false,
                    |        "ch.ninecode.cim.split_maxsize": 67108864
                    |    },
                    |    "interval": {
                    |         "start": "2017-07-18T00:00:00.000+0000",
                    |         "end": "2017-07-19T00:00:00.000+0000"
                    |    },
                    |    "transformers": [
                    |         "TRA2755"
                    |    ],
                    |    "players": [
                    |         {
                    |             "title": "house services",
                    |             "rdfquery": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID and n.TopologicalIsland = '%s'",
                    |             "cassandraquery": "select cimapplication.subtract_offset (time, interval) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value_by_day where mrid='%s' and type='%s'",
                    |             "bind": [
                    |                 "mrid",
                    |                 "type"
                    |             ]
                    |         }
                    |    ],
                    |    "recorders": [
                    |        {
                    |            "title": "cable currents",
                    |            "query": "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current' type, 'current_in' property, 'Amperes' unit from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where Conductor.len != 0 and (t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 1 and t1.TopologicalNode != n.IdentifiedObject.mRID and n.TopologicalIsland = '%s') and (t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ACDCTerminal.sequenceNumber = 2 and t2.TopologicalNode = n.IdentifiedObject.mRID and n.TopologicalIsland = '%s')",
                    |            "interval": 900
                    |        }
                    |    ]
                    |}
                    """.stripMargin
                )
        }
        val sep = System.getProperty ("file.separator")
        main (Array ("--unittest", "--verbose", "--host", "sandbox", "--workdir", new java.io.File(".").getCanonicalPath + sep + "data/", json))
    }
}
