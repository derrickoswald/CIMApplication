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

import ch.ninecode.cim.CIMClasses
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import scala.collection.JavaConverters._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.fixture.FunSuite

class SimulationSuite extends FunSuite with BeforeAndAfterAll
{
    type FixtureParam = SparkSession

    val FILE_DEPOT = "data/"
    val FILENAME = "DemoData"

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

    /**
     * Set SPARK_LOCAL_IP to the IP address in dotted-quad format (e.g. 1.2.3.4) if it isn't set.
     *
     * Avoids "Set SPARK_LOCAL_IP if you need to bind to another address" warning message.
     *
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
                        val newenv = new java.util.HashMap[String, String]()
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
                read = zip.read (bytesIn); read != -1
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

    override def beforeAll ()
    {
        setLocalIP ()
        if (!new File (FILE_DEPOT + FILENAME + ".rdf").exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME + ".zip", FILE_DEPOT)
    }

    override def afterAll (): Unit =
    {
        new File (FILE_DEPOT + FILENAME + ".rdf").delete
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // set the env to add the real local address
        setLocalIP ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SimulationSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.graphx.pregel.checkpointInterval", "8")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        session.sparkContext.setLogLevel ("ERROR") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        finally
            session.stop // clean up the fixture
    }

    test ("Help")
    {
        session ⇒
            Main.main (Array ("--unittest", "--help"))
    }

    test ("DemoData")
    {
        session ⇒
            val json = FILE_DEPOT + "demodata.json"
            using (new PrintWriter (new File (json), "UTF-8"))
            {
                writer =>
                    writer.write (
                        """
                          |{
                          |    "name": "DemoData",
                          |    "description": "simulation with demo data",
                          |    "cim": "data/DemoData.rdf",
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
                          |        "start": "2018-01-01T00:00:00.000+0100",
                          |        "end": "2018-02-01T00:00:00.000+0100"
                          |    },
                          |    "keyspaces": {
                          |        "input": "cimapplication",
                          |        "output": "test"
                          |    },
                          |    "players": [
                          |        {
                          |            "title": "Measured power for all house services",
                          |            "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                          |        }
                          |    ],
                          |    "recorders": [
                          |        {
                          |            "title": "All transformer output power",
                          |            "query": "select    concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,    'power' type,    'power_out' property,    'VA' unit,    n.TopologicalIsland islandfrom    PowerTransformer p,    Terminal t,    TopologicalNode nwhere    t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    t.ACDCTerminal.sequenceNumber > 1 and    t.TopologicalNode = n.IdentifiedObject.mRID",
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
                          |            "query": "select    concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,    'current' type,    'current_out' property,    'Amperes' unit,    n.TopologicalIsland islandfrom    PowerTransformer p,    Terminal t,    TopologicalNode nwhere    t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    t.ACDCTerminal.sequenceNumber > 1 and    t.TopologicalNode = n.IdentifiedObject.mRID",
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
                          |            "query": "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,    'losses' type,    'power_losses' property,    'VA' unit,    n.TopologicalIsland islandfrom    PowerTransformer p,    Terminal t,    TopologicalNode nwhere    t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    t.ACDCTerminal.sequenceNumber > 1 and    t.TopologicalNode = n.IdentifiedObject.mRID",
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
                          |            "title": "All BusbarSection node voltages",
                          |            "query": "select    concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,    b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'voltage' type,    'voltage' property,    'Volts' unit,    n.TopologicalIsland islandfrom    TopologicalNode n,    Terminal t,    BusbarSection bwhere    t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "title": "All BusbarSection output power",
                          |            "query": "select    concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,    b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'power' type,    'power' property,    'VA' unit,    n.TopologicalIsland islandfrom    TopologicalNode n,    Terminal t,    BusbarSection bwhere    t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "title": "All EnergyConsumer node voltages",
                          |            "query": "select    concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,    c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'voltage' type,    'voltage' property,    'Volts' unit,    n.TopologicalIsland islandfrom    TopologicalNode n,    Terminal t,    EnergyConsumer cwhere    t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "title": "All EnergyConsumer output power",
                          |            "query": "select    concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,    c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'power' type,    'power' property,    'VA' unit,    n.TopologicalIsland islandfrom    TopologicalNode n,    Terminal t,    EnergyConsumer cwhere    t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "title": "All cable currents",
                          |            "query": "select    concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name,    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,    'current' type,    'current_in' property,    'Amperes' unit,    n.TopologicalIsland islandfrom    ACLineSegment a,    Terminal t1,    Terminal t2,    TopologicalNode nwhere    (        t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and        t1.ACDCTerminal.sequenceNumber = 1 and        t1.TopologicalNode != n.IdentifiedObject.mRID    )    and    (        t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and        t2.ACDCTerminal.sequenceNumber = 2 and        t2.TopologicalNode = n.IdentifiedObject.mRID    )",
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
                          |            "title": "All cable losses",
                          |            "query": "select    concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name,    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,    'losses' type,    'power_losses' property,    'Wh' unit,    n.TopologicalIsland islandfrom    ACLineSegment a,    Terminal t1,    Terminal t2,    TopologicalNode nwhere    (        t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and        t1.ACDCTerminal.sequenceNumber = 1 and        t1.TopologicalNode != n.IdentifiedObject.mRID    )    and    (        t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and        t2.ACDCTerminal.sequenceNumber = 2 and        t2.TopologicalNode = n.IdentifiedObject.mRID    )",
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
                          |    "transformers": [],
                          |    "extras": [
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
                          |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                          |        }
                          |    ]
                          |}
                          |""".stripMargin
                    )
            }
            val sep = System.getProperty ("file.separator")
            Main.main (Array ("--unittest", "--master", "local[*]", "--verbose", "--keep", "--summarize", "--host", "localhost", "--workdir", new java.io.File (".").getCanonicalPath + sep + "data/", json))
            new File (FILE_DEPOT + "demodata.json").delete
    }

//    test ("events")
//    {
//        spark ⇒
//            val options = SimulationOptions (verbose = true, events = true)
//            val check = SimulationEvents (spark, options)
//            check.run (Array("63b5f4b7-dde8-44de-a06d-6a285a1e2f04"))
//    }

//    test ("power")
//    {
//        spark ⇒
//            val summarize = Summarize (spark, SimulationOptions (verbose = true, unittest = true, summarize = true))
//            summarize.run (Array("bf34b237-c798-485d-90ba-9a990d570f7c"))
//    }
}
