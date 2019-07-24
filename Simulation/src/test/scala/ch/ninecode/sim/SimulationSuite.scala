package ch.ninecode.sim

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.util.zip.ZipInputStream

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses

class SimulationSuite extends FunSuite with BeforeAndAfterAll
{
    type FixtureParam = SparkSession

    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"
    val FILENAME2 = "DemoDataReinforced"

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
        if (!new File (FILE_DEPOT + FILENAME1 + ".rdf").exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME1 + ".zip", FILE_DEPOT)
        if (!new File (FILE_DEPOT + FILENAME2 + ".rdf").exists)
            new Unzip ().unzip (FILE_DEPOT + FILENAME2 + ".zip", FILE_DEPOT)
    }

    override def afterAll (): Unit =
    {
        new File (FILE_DEPOT + FILENAME1 + ".rdf").delete
        new File (FILE_DEPOT + FILENAME2 + ".rdf").delete
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SimulationSuite")
        configuration.setMaster ("local[*]")
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
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
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
                        |        "output": "test",
                        |        "replication": 2
                        |    },
                        |    "players": [
                        |        {
                        |            "title": "Measured power for all PSRType_HouseService house services",
                        |            "query": " select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                        |        }
                        |    ],
                        |    "recorders": [
                        |        {
                        |            "title": "All PowerTransformer output power",
                        |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_out' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                        |            "title": "All PowerTransformer output currents",
                        |            "query": "select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_out' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                        |            "title": "All PowerTransformer power losses",
                        |            "query": "select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                        |            "title": "All BusbarSection node voltages",
                        |            "query": " select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                        |            "title": "All BusbarSection output power",
                        |            "query": " select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'power' type, 'measured_power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                        |            "title": "All EnergyConsumer node voltages",
                        |            "query": " select concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                        |            "title": "All EnergyConsumer output power",
                        |            "query": " select concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent, 'power' type, 'power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                        |            "title": "All ACLineSegment currents",
                        |            "query": " select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_in' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                        |            "title": "All ACLineSegment losses",
                        |            "query": " select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'Wh' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                        |    "transformers": [],
                        |    "extras": [
                        |        {
                        |            "title": "ratedCurrent",
                        |            "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
                        |        },
                        |        {
                        |            "title": "ratedS",
                        |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, cast (sum (e.ratedS) as string) value from Terminal t, PowerTransformerEnd e where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 group by t.TopologicalNode"
                        |        },
                        |        {
                        |            "title": "nominalVoltage",
                        |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                        |        },
                        |        {
                        |            "title": "substation",
                        |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                        |        },
                        |        {
                        |            "title": "contains",
                        |            "query": "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID key, concat_ws (',', collect_list(b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID)) value from Substation s, BusbarSection b where s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID group by s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID"
                        |        },
                        |        {
                        |            "title": "stationratedS",
                        |            "query": "select first_value (c.substation) key, cast (sum(e.ratedS) as string) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, ( select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u ) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                        |        }
                        |    ],
                        |    "postprocessing":
                        |    [
                        |        {
                        |            "class": "event",
                        |            "thresholds":
                        |            [
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "voltage",
                        |                    "severity": 1,
                        |                    "reference": "ratedVoltage",
                        |                    "default": 400.0,
                        |                    "ratio": 1.10,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "low",
                        |                    "type": "voltage",
                        |                    "severity": 1,
                        |                    "reference": "ratedVoltage",
                        |                    "default": 400.0,
                        |                    "ratio": 0.90,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "voltage",
                        |                    "severity": 2,
                        |                    "reference": "ratedVoltage",
                        |                    "default": 400.0,
                        |                    "ratio": 1.06,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "low",
                        |                    "type": "voltage",
                        |                    "severity": 2,
                        |                    "reference": "ratedVoltage",
                        |                    "default": 400.0,
                        |                    "ratio": 0.94,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "current",
                        |                    "severity": 1,
                        |                    "reference": "ratedCurrent",
                        |                    "default": 100.0,
                        |                    "ratio": 1.10,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "current",
                        |                    "severity": 1,
                        |                    "reference": "ratedCurrent",
                        |                    "default": 100.0,
                        |                    "ratio": 0.90,
                        |                    "duration": 10800000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "current",
                        |                    "severity": 2,
                        |                    "reference": "ratedCurrent",
                        |                    "default": 100.0,
                        |                    "ratio": 0.75,
                        |                    "duration": 50400000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "power",
                        |                    "severity": 1,
                        |                    "reference": "ratedS",
                        |                    "default": 630000,
                        |                    "ratio": 1.10,
                        |                    "duration": 900000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "power",
                        |                    "severity": 1,
                        |                    "reference": "ratedS",
                        |                    "default": 630000,
                        |                    "ratio": 0.90,
                        |                    "duration": 10800000
                        |                },
                        |                {
                        |                    "trigger": "high",
                        |                    "type": "power",
                        |                    "severity": 2,
                        |                    "reference": "ratedS",
                        |                    "default": 630000,
                        |                    "ratio": 0.75,
                        |                    "duration": 50400000
                        |                }
                        |            ]
                        |        },
                        |        {
                        |            "class": "coincidence_factor",
                        |            "aggregates":
                        |            [
                        |                {
                        |                    "intervals": 96,
                        |                    "ttl": null
                        |                }
                        |            ]
                        |        },
                        |        {
                        |            "class": "load_factor",
                        |            "aggregates":
                        |            [
                        |                {
                        |                    "intervals": 96,
                        |                    "ttl": null
                        |                }
                        |            ]
                        |        },
                        |        {
                        |            "class": "responsibility_factor",
                        |            "aggregates":
                        |            [
                        |                {
                        |                    "intervals": 96,
                        |                    "ttl": null
                        |                }
                        |            ]
                        |        }
                        |    ]
                        |}
                        |""".stripMargin
                    )
            }
            val sep = System.getProperty ("file.separator")
            Main.main (Array ("--unittest", "--master", "local[2]", "--verbose", "--keep", "--host", "localhost", "--workdir", new java.io.File (".").getCanonicalPath + sep + "data/", json))
            new File (FILE_DEPOT + json).delete
    }

    test ("DemoDataReinforced")
    {
        session ⇒
            val json = FILE_DEPOT + "demodatareinforced.json"
            using (new PrintWriter (new File (json), "UTF-8"))
            {
                writer =>
                    writer.write (
                        """
                          |{
                          |    "name": "DemoDataReinforced",
                          |    "description": "simulation with demo data and reinforcement",
                          |    "cim": "data/DemoDataReinforced.rdf",
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
                          |        "output": "test",
                          |        "replication": 2
                          |    },
                          |    "players": [
                          |        {
                          |            "title": "Measured power for all house services",
                          |            "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                          |        }
                          |    ],
                          |    "recorders": [
                          |        {
                          |            "title": "All PowerTransformer output power",
                          |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_out' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                          |            "title": "All PowerTransformer output currents",
                          |            "query": "select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_out' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                          |            "title": "All PowerTransformer power losses",
                          |            "query": "select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                          |            "query": "select    concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,    b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'voltage' type,    'voltage' property,    'Volts' unit,    n.TopologicalIsland island from    TopologicalNode n,    Terminal t,    BusbarSection b where    t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "query": "select    concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,    b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'power' type,    'measured_power' property,    'VA' unit,    n.TopologicalIsland island from    TopologicalNode n,    Terminal t,    BusbarSection b where    t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "query": "select    concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,    c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    n.IdentifiedObject.mRID parent,    'voltage' type,    'voltage' property,    'Volts' unit,    n.TopologicalIsland island from    TopologicalNode n,    Terminal t,    EnergyConsumer c where    t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "query": "select    concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,    c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,    concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent,    'power' type,    'power' property,    'VA' unit,    n.TopologicalIsland island from    TopologicalNode n,    Terminal t,    EnergyConsumer c where    t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and    n.IdentifiedObject.mRID = t.TopologicalNode",
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
                          |            "title": "All ACLineSegment currents",
                          |            "query": " select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_in' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                          |            "title": "All ACLineSegment losses",
                          |            "query": " select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'Wh' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                          |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, cast (sum (e.ratedS) as string) value from Terminal t, PowerTransformerEnd e where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 group by t.TopologicalNode"
                          |        },
                          |        {
                          |            "title": "nominalVoltage",
                          |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                          |        },
                          |        {
                          |            "title": "substation",
                          |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                          |        },
                          |        {
                          |            "title": "contains",
                          |            "query": "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID key, concat_ws (',', collect_list(b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID)) value from Substation s, BusbarSection b where s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID group by s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID"
                          |        },
                          |        {
                          |            "title": "stationratedS",
                          |            "query": "select first_value (c.substation) key, cast (sum(e.ratedS) as string) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, ( select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u ) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                          |        }
                          |    ],
                          |    "postprocessing":
                          |    [
                          |        {
                          |            "class": "event",
                          |            "thresholds":
                          |            [
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "voltage",
                          |                    "severity": 1,
                          |                    "reference": "ratedVoltage",
                          |                    "default": 400.0,
                          |                    "ratio": 1.10,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "low",
                          |                    "type": "voltage",
                          |                    "severity": 1,
                          |                    "reference": "ratedVoltage",
                          |                    "default": 400.0,
                          |                    "ratio": 0.90,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "voltage",
                          |                    "severity": 2,
                          |                    "reference": "ratedVoltage",
                          |                    "default": 400.0,
                          |                    "ratio": 1.06,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "low",
                          |                    "type": "voltage",
                          |                    "severity": 2,
                          |                    "reference": "ratedVoltage",
                          |                    "default": 400.0,
                          |                    "ratio": 0.94,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "current",
                          |                    "severity": 1,
                          |                    "reference": "ratedCurrent",
                          |                    "default": 100.0,
                          |                    "ratio": 1.10,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "current",
                          |                    "severity": 1,
                          |                    "reference": "ratedCurrent",
                          |                    "default": 100.0,
                          |                    "ratio": 0.90,
                          |                    "duration": 10800000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "current",
                          |                    "severity": 2,
                          |                    "reference": "ratedCurrent",
                          |                    "default": 100.0,
                          |                    "ratio": 0.75,
                          |                    "duration": 50400000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "power",
                          |                    "severity": 1,
                          |                    "reference": "ratedS",
                          |                    "default": 630000,
                          |                    "ratio": 1.10,
                          |                    "duration": 900000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "power",
                          |                    "severity": 1,
                          |                    "reference": "ratedS",
                          |                    "default": 630000,
                          |                    "ratio": 0.90,
                          |                    "duration": 10800000
                          |                },
                          |                {
                          |                    "trigger": "high",
                          |                    "type": "power",
                          |                    "severity": 2,
                          |                    "reference": "ratedS",
                          |                    "default": 630000,
                          |                    "ratio": 0.75,
                          |                    "duration": 50400000
                          |                }
                          |            ]
                          |        },
                          |        {
                          |            "class": "coincidence_factor",
                          |            "aggregates":
                          |            [
                          |                {
                          |                    "intervals": 96,
                          |                    "ttl": null
                          |                }
                          |            ]
                          |        },
                          |        {
                          |            "class": "load_factor",
                          |            "aggregates":
                          |            [
                          |                {
                          |                    "intervals": 96,
                          |                    "ttl": null
                          |                }
                          |            ]
                          |        },
                          |        {
                          |            "class": "responsibility_factor",
                          |            "aggregates":
                          |            [
                          |                {
                          |                    "intervals": 96,
                          |                    "ttl": null
                          |                }
                          |            ]
                          |        }
                          |    ]
                          |}
                          |""".stripMargin
                    )
            }
            val sep = System.getProperty ("file.separator")
            Main.main (Array ("--unittest", "--master", "local[2]", "--opts", "spark.driver.memory=2g,spark.executor.memory=2g", "--verbose", "--keep", "--host", "localhost", "--workdir", new java.io.File (".").getCanonicalPath + sep + "data/", json))
            new File (FILE_DEPOT + json).delete
    }

//    test ("events")
//    {
//        spark ⇒
//            val options = SimulationOptions (verbose = true)
//            val check = SimulationEvents (List()) (spark, options)
//            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, "90738a6a-e775-4514-8b81-d29a9a85a9f9", "cimapplication", "test", true, true)
//            check.run (access)
//    }
//    test ("events")
//    {
//        spark ⇒
//            val options = SimulationOptions (verbose = true)
//            val STANDARD_TRIGGERS: Iterable[Trigger] = List[Trigger] (
//                // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
//                HighTrigger ("voltage", 1, "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000),
//                LowTrigger ("voltage", 1, "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000),
//                HighTrigger ("voltage", 2, "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000),
//                LowTrigger ("voltage", 2, "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000),
//
//                // current >75% and >14h within 24h = orange
//                // current >90% and >3h within 24h = red
//                // current >110% for 15 minutes or more = red
//                HighTrigger ("current", 1, "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000),
//                HighTrigger ("current", 2, "ratedCurrent", 100.0, 0.90,  3 * 60 * 60 * 1000),
//                HighTrigger ("current", 2, "ratedCurrent", 100.0, 1.10,      15 * 60 * 1000),
//
//                // power >75% and >14h within 24h = orange
//                // power >90% and >3h within 24h = red
//                // power >110% for 15 minutes or more = red
//                HighTrigger ("power", 1, "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000),
//                HighTrigger ("power", 2, "ratedS", 630.0, 0.90,  3 * 60 * 60 * 1000),
//                HighTrigger ("power", 2, "ratedS", 630.0, 1.10,      15 * 60 * 1000)
//            )
//            val check = SimulationEvents (STANDARD_TRIGGERS) (spark, options)
//            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, "c4171b8e-4795-435d-9cfe-ea28ad71f91b", "baseline", "baseline", true, true)
//            check.run (access)
//    }
//
//    test ("coincidence_factor")
//    {
//        spark ⇒
//            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, "81e914ea-28b5-46e2-9259-8ce6ce84b5c2", "cimapplication", "test", true, true)
//            val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
//            val options = SimulationOptions (verbose = true, unittest = true)
//            val coincidence = SimulationCoincidenceFactor(IGNORED_AGGREGATES) (spark, options)
//            coincidence.run (access)
//    }
//
//    test ("load_factor")
//    {
//        spark ⇒
//            val access = SimulationCassandraAccess  (spark, org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER, "81e914ea-28b5-46e2-9259-8ce6ce84b5c2", "cimapplication", "test", true, true)
//            val IGNORED_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] ()
//            val options = SimulationOptions (verbose = true, unittest = true)
//            val coincidence = SimulationLoadFactor(IGNORED_AGGREGATES) (spark, options)
//            coincidence.run (access)
//    }
//
//    test ("Ganged")
//    {
//        session ⇒
//            val sep = System.getProperty ("file.separator")
//            Main.main (Array ("--unittest", "--master", "local[*]", "--verbose", "--keep", "--host", "localhost", "--workdir", new java.io.File (".").getCanonicalPath + sep + "data/", "data/tra161_tra162.json"))
//    }
}
