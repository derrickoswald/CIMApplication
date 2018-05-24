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

import scala.collection.JavaConverters._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import ch.ninecode.sim.Main.main

class SimulationSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "TRA2755_terminal_2_island"
    val FILENAME2 = "SAK_sta117_sta206"

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
        if (!new File (FILE_DEPOT + FILENAME2 + ".rdf").exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME2 + ".zip", FILE_DEPOT)
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
                    |    "name": "Basic",
                    |    "description": "basic simulation file for illustrative purposes",
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
                    |         "start": "2018-01-30T00:00:00.000+0100",
                    |         "end": "2018-03-01T00:00:00.000+0100"
                    |    },
                    |    "transformers": [
                    |        "TRA2755"
                    |    ],
                    |    "players": [
                    |         {
                    |             "title": "house services",
                    |             "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |             "cassandraquery": "select cimapplication.subtract_offset (time, period) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value where mrid='%s' and type='%s'",
                    |             "bind": [
                    |                 "mrid",
                    |                 "type"
                    |             ]
                    |         }
                    |    ],
                    |    "recorders": [
                    |        {
                    |            "title": "cable currents",
                    |            "query": "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID  parent, 'current' type, 'current_in' property, 'Amperes' unit, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where (t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 1 and t1.TopologicalNode != n.IdentifiedObject.mRID) and  (t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ACDCTerminal.sequenceNumber = 2 and t2.TopologicalNode = n.IdentifiedObject.mRID)",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": 1800
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": 3600
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": 7200
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        }
                    |    ],
                    |    "extra": [
                    |        {
                    |            "title": "ratedCurrent",
                    |            "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
                    |        },
                    |        {
                    |            "title": "ratedS",
                    |            "query": "select p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (e.ratedS as string) value from PowerTransformer p, PowerTransformerEnd e where e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and e.TransformerEnd.endNumber = 1"
                    |        },
                    |        {
                    |            "title": "nominalVoltage",
                    |            "query": "select e.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (v.nominalVoltage * 1000.0 as string) value from EnergyConsumer e, BaseVoltage v where e.ConductingEquipment.BaseVoltage = v.IdentifiedObject.mRID"
                    |        }
                    |    ]
                    |}
                    """.stripMargin
                )
        }
        val sep = System.getProperty ("file.separator")
        main (Array ("--unittest", "--verbose", "--keep", "--host", "sandbox", "--workdir", new java.io.File(".").getCanonicalPath + sep + "data/", json))
    }

    test ("Transformers")
    {
        val json = FILE_DEPOT + "transformers.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    """
                    |{
                    |    "name": "Transformers",
                    |    "description": "test transformer players",
                    |    "cim": "data/SAK_sta117_sta206.rdf",
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
                    |         "start": "2017-07-18T00:00:00.000+0100",
                    |         "end": "2017-07-19T00:00:00.000+0100"
                    |    },
                    |    "transformers": [
                    |         "TRA2755"
                    |    ],
                    |    "players": [
                    |         {
                    |             "title": "house services",
                    |             "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |             "cassandraquery": "select cimapplication.subtract_offset (time, period) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value where mrid='%s' and type='%s'",
                    |             "bind": [
                    |                 "mrid",
                    |                 "type"
                    |             ]
                    |         }
                    |    ],
                    |    "recorders": [
                    |        {
                    |            "title": "All transformer power flows",
                    |            "query": "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID  parent, 'power' type, 'power_out' property, 'Volt-Amperes' unit, n.TopologicalIsland island from PowerTransformer p, Terminal t, TopologicalNode n where t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t.ACDCTerminal.sequenceNumber > 1 and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": 1800
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": 3600
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": 7200
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        },
                    |        {
                    |            "title": "All transformer output currents",
                    |            "query": "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current' type, 'current_out' property, 'Amperes' unit, n.TopologicalIsland island from PowerTransformer p, Terminal t, TopologicalNode n where t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t.ACDCTerminal.sequenceNumber > 1 and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": 1800
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": 3600
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": 7200
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        },
                    |        {
                    |            "title": "All transformer power losses",
                    |            "query": "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'energy' type, 'power_losses' property, 'Volt-Amperes' unit, n.TopologicalIsland island from PowerTransformer p, Terminal t, TopologicalNode n where t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t.ACDCTerminal.sequenceNumber > 1 and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": 1800
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": 3600
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": 7200
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        }
                    |    ],
                    |    "extra": [
                    |        {
                    |            "title": "ratedS",
                    |            "query": "select p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (e.ratedS as string) value from PowerTransformer p, PowerTransformerEnd e where e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and e.TransformerEnd.endNumber = 1"
                    |        }
                    |    ]
                    |}
                    """.stripMargin
                )
        }
        val sep = System.getProperty ("file.separator")
        main (Array ("--unittest", "--verbose", "--keep", "--host", "sandbox", "--workdir", new java.io.File(".").getCanonicalPath + sep + "data/", json))
    }

    test ("Typical")
    {
        val json = FILE_DEPOT + "typical.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    """
                    |{
                    |    "name": "Typical",
                    |    "description": "typical simulation file for illustrative purposes",
                    |    "cim": "data/SAK_sta117_sta206.rdf",
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
                    |        "start": "2018-01-30T00:00:00.000+0000",
                    |        "end": "2018-03-01T00:00:00.000+0000"
                    |    },
                    |    "transformers": [
                    |        "TRA2755",
                    |        "TRA2769"
                    |    ],
                    |    "players": [
                    |         {
                    |             "title": "house services",
                    |             "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID",
                    |             "cassandraquery": "select cimapplication.subtract_offset (time, period) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value where mrid='%s' and type='%s'",
                    |             "bind": [
                    |                 "mrid",
                    |                 "type"
                    |             ]
                    |         }
                    |    ],
                    |    "recorders": [
                    |        {
                    |            "title": "cable currents",
                    |            "query": "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current' type, 'current_in' property, 'Amperes' unit, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where (t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 1 and t1.TopologicalNode != n.IdentifiedObject.mRID) and  (t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ACDCTerminal.sequenceNumber = 2 and t2.TopologicalNode = n.IdentifiedObject.mRID)",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        },
                    |        {
                    |            "title": "All node voltages",
                    |            "query": "select concat (n.IdentifiedObject.mRID, '_voltage_recorder') name, ifnull (t.ConductingEquipment, n.IdentifiedObject.mRID) mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n left outer join ( select distinct (t1.TopologicalNode) TopologicalNode, first (t1.ConductingEquipment) ConductingEquipment from Terminal t1 where t1.ConductingEquipment not in ( select t2.ConductingEquipment from Terminal t2 where ACDCTerminal.sequenceNumber > 1 ) group by t1.TopologicalNode ) t on n.IdentifiedObject.mRID = t.TopologicalNode",
                    |            "interval": 900,
                    |            "aggregations": [
                    |                {
                    |                    "intervals": 1,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 4,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 12,
                    |                    "ttl": null
                    |                },
                    |                {
                    |                    "intervals": 96,
                    |                    "ttl": null
                    |                }
                    |            ]
                    |        }
                    |    ],
                    |    "extra": [
                    |        {
                    |            "title": "ratedCurrent",
                    |            "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
                    |        },
                    |        {
                    |            "title": "ratedS",
                    |            "query": "select p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (e.ratedS as string) value from PowerTransformer p, PowerTransformerEnd e where e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and e.TransformerEnd.endNumber = 1"
                    |        },
                    |        {
                    |            "title": "nominalVoltage",
                    |            "query": "select e.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (v.nominalVoltage * 1000.0 as string) value from EnergyConsumer e, BaseVoltage v where e.ConductingEquipment.BaseVoltage = v.IdentifiedObject.mRID"
                    |        }
                    |    ]
                    |}
                    """.stripMargin
                )
        }
        val sep = System.getProperty ("file.separator")
        main (Array ("--unittest", "--verbose", "--host", "sandbox", json))
    }

//    test ("Summarize")
//    {
//        main (Array ("--unittest", "--verbose", "--host", "sandbox", "--summarize"))
//    }
//
//    test ("DemoData")
//    {
//        val json = FILE_DEPOT + "basic.json"
//        using (new PrintWriter (new File (json), "UTF-8"))
//        {
//            writer =>
//                writer.write (
//                    """
//                    |{
//                    |    "name": "Demo",
//                    |    "description": "demo data simulation",
//                    |    "cim": "data/DemoData.rdf",
//                    |    "cimreaderoptions": {
//                    |        "ch.ninecode.cim.do_about": false,
//                    |        "ch.ninecode.cim.do_normalize": false,
//                    |        "ch.ninecode.cim.do_deduplication": false,
//                    |        "ch.ninecode.cim.make_edges": false,
//                    |        "ch.ninecode.cim.do_join": false,
//                    |        "ch.ninecode.cim.do_topo_islands": false,
//                    |        "ch.ninecode.cim.do_topo": false,
//                    |        "ch.ninecode.cim.split_maxsize": 67108864
//                    |    },
//                    |    "interval": {
//                    |         "start": "2017-07-18T00:00:00.000+0100",
//                    |         "end": "2017-07-19T00:00:00.000+0100"
//                    |    },
//                    |    "transformers": [
//                    |    ],
//                    |    "players": [
//                    |         {
//                    |             "title": "house services",
//                    |             "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID",
//                    |             "cassandraquery": "select cimapplication.subtract_offset (time, period) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value where mrid='%s' and type='%s'",
//                    |             "bind": [
//                    |                 "mrid",
//                    |                 "type"
//                    |             ]
//                    |         }
//                    |    ],
//                    |    "recorders": [
//                    |        {
//                    |            "title": "cable currents",
//                    |            "query": "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current' type, 'current_in' property, 'Amperes' unit, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where (t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 1 and t1.TopologicalNode != n.IdentifiedObject.mRID) and  (t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ACDCTerminal.sequenceNumber = 2 and t2.TopologicalNode = n.IdentifiedObject.mRID)",
//                    |            "interval": 900,
//                    |            "aggregations": [
//                    |                {
//                    |                    "intervals": 1,
//                    |                    "ttl": 1800
//                    |                },
//                    |                {
//                    |                    "intervals": 4,
//                    |                    "ttl": 3600
//                    |                },
//                    |                {
//                    |                    "intervals": 12,
//                    |                    "ttl": 7200
//                    |                },
//                    |                {
//                    |                    "intervals": 96,
//                    |                    "ttl": null
//                    |                }
//                    |            ]
//                    |        }
//                    |    ],
//                    |    "extra": [
//                    |        {
//                    |            "title": "ratedCurrent",
//                    |            "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
//                    |        },
//                    |        {
//                    |            "title": "ratedS",
//                    |            "query": "select p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (e.ratedS as string) value from PowerTransformer p, PowerTransformerEnd e where e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and e.TransformerEnd.endNumber = 1"
//                    |        },
//                    |        {
//                    |            "title": "nominalVoltage",
//                    |            "query": "select e.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (v.nominalVoltage * 1000.0 as string) value from EnergyConsumer e, BaseVoltage v where e.ConductingEquipment.BaseVoltage = v.IdentifiedObject.mRID"
//                    |        }
//                    |    ]
//                    |}
//                    """.stripMargin
//                )
//        }
//        val sep = System.getProperty ("file.separator")
//        main (Array ("--unittest", "--verbose", "--keep", "--host", "sandbox", "--workdir", new java.io.File(".").getCanonicalPath + sep + "data/", json))
//    }
}
