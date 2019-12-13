package ch.ninecode.sim

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.util.Properties
import java.util.zip.ZipInputStream

import scala.collection._
import scala.collection.JavaConverters._

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Row
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimulationSuiteIT
{
    import SimulationSuiteIT._

    @Test def Help ()
    {
        Main.main (Array ("--unittest", "--help"))
    }

    @Test def SimulationDemoData ()
    {
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    s"""
                    |{
                    |    "id": "Basic",
                    |    "name": "DemoData",
                    |    "description": "simulation with demo data",
                    |    "cim": "$FILE_DEPOT$FILENAME1.rdf",
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
                    |        "end": "2018-02-01T00:00:00.000+0100",
                    |        "buffer": 3600000
                    |    },
                    |    "temperatures": {
                    |        "cim_temperature": 20.0,
                    |        "simulation_temperature": 20.0
                    |    },
                    |    "swing": "hi",
                    |    "swing_voltage_factor": 1.0,
                    |    "keyspaces": {
                    |        "input": "$KEYSPACE",
                    |        "output": "$KEYSPACE",
                    |        "replication": 1
                    |    },
                    |    "players": [
                    |        {
                    |            "title": "Measured power for all PSRType_HouseService house services",
                    |            "query": "select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                    |        }
                    |    ],
                    |    "recorders": [
                    |        {
                    |            "title": "All PowerTransformer input power",
                    |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_in' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                    |            "query": "select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                    |            "query": "select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'power' type, 'measured_power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                    |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                    |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent, 'power' type, 'power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                    |            "query": "select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_in' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                    |            "query": "select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'Wh' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                    |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.EnergyConnection.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                    |        },
                    |        {
                    |            "title": "substation",
                    |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                    |        },
                    |        {
                    |            "title": "contains",
                    |            "query": "select first_value (contains.station) key, concat_ws (',', collect_list(contains.name)) value from ( select name_node_station.station station, name_node_station.name name from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.station) station from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalNode n, Terminal t, BusbarSection b, Substation s where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode and s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID ) busbars group by node ) name_node_station ) contains group by station"
                    |        },
                    |        {
                    |            "title": "stationratedS",
                    |            "query": "select first_value (c.substation) key, cast (sum(e.ratedS) as string) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, ( select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u ) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                    |        }
                    |    ],
                    |    "postprocessing": []
                    |}
                    |""".stripMargin
                )
        }
        Main.main (
            Array (
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--host", "localhost",
                "--port", cassandra_port,
                "--workdir", s"$wd$FILE_DEPOT",
                json))
        new File (json).delete
    }

    @Test def SimulationDemoDataReinforced ()
    {
        val json = s"$FILE_DEPOT$FILENAME2.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    s"""
                      |{
                      |    "id": "Reinforced",
                      |    "name": "Reinforced Simulation Test",
                      |    "description": "simulation with demo data and reinforcement",
                      |    "cim": "$FILE_DEPOT$FILENAME2.rdf",
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
                      |        "end": "2018-02-01T00:00:00.000+0100",
                      |        "buffer": 3600000
                      |    },
                      |    "temperatures": {
                      |        "cim_temperature": 20.0,
                      |        "simulation_temperature": 20.0
                      |    },
                      |    "swing": "hi",
                      |    "swing_voltage_factor": 1.0,
                      |    "keyspaces": {
                      |        "input": "$KEYSPACE",
                      |        "output": "$KEYSPACE",
                      |        "replication": 1
                      |    },
                      |    "players": [
                      |        {
                      |            "title": "Measured power for all house services",
                      |            "query": "select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                      |        }
                      |    ],
                      |    "recorders": [
                      |        {
                      |            "title": "All PowerTransformer input power",
                      |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_in' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                      |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode",
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
                      |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent, 'power' type, 'power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode",
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
                      |            "query": "select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_in' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                      |            "query": "select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'Wh' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                      |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.EnergyConnection.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                      |        },
                      |        {
                      |            "title": "substation",
                      |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                      |        },
                      |        {
                      |            "title": "contains",
                      |            "query": "select first_value (contains.station) key, concat_ws (',', collect_list(contains.name)) value from ( select name_node_station.station station, name_node_station.name name from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.station) station from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalNode n, Terminal t, BusbarSection b, Substation s where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode and s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID ) busbars group by node ) name_node_station ) contains group by station"
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
        Main.main (
            Array (
                "--unittest",
                "--master", "local[2]",
                "--opts", "spark.driver.memory=2g,spark.executor.memory=2g",
                "--verbose",
                "--keep",
                "--host", "localhost",
                "--port", cassandra_port,
                "--workdir", s"$wd$FILE_DEPOT",
                json
            )
        )
        new File (json).delete
    }

    @Test def ThreePhase ()
    {
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    s"""
                       |{
                       |    "id": "ThreePhase",
                       |    "name": "Three Phase Simulation Test",
                       |    "description": "three phase simulation with demo data",
                       |    "cim": "$FILE_DEPOT$FILENAME1.rdf",
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
                       |        "end": "2018-02-01T00:00:00.000+0100",
                       |        "buffer": 3600000
                       |    },
                       |    "temperatures": {
                       |        "cim_temperature": 20.0,
                       |        "simulation_temperature": 20.0
                       |    },
                       |    "swing": "hi",
                       |    "swing_voltage_factor": 1.0,
                       |    "keyspaces": {
                       |        "input": "$KEYSPACE",
                       |        "output": "$KEYSPACE",
                       |        "replication": 1
                       |    },
                       |    "players": [
                       |        {
                       |            "title": "Measured power for all PSRType_HouseService house services",
                       |            "query": "select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                       |        }
                       |    ],
                       |    "recorders": [
                       |        {
                       |            "title": "All PowerTransformer input power",
                       |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_in' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                       |            "title": "All BusbarSection node voltages",
                       |            "query": "select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |            "query": "select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'power' type, 'measured_power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |            "query": "select concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name, c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat (c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent, 'power' type, 'power' property, 'VA' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |            "query": "select concat (name_island.name, '_current_recorder') name, name_island.name mrid, name_island.name parent, 'current' type, 'current_in' property, 'Amperes' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                       |            "query": "select concat (name_island.name, '_losses_recorder') name, name_island.name mrid, name_island.name parent, 'losses' type, 'power_losses' property, 'Wh' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (wires.mrid))) name, first_value (wires.island) island from ( select distinct a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes, n.TopologicalIsland island from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.TopologicalNode != t2.TopologicalNode and t1.TopologicalNode = n.IdentifiedObject.mRID ) wires group by nodes ) name_island ",
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
                       |            "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.EnergyConnection.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
                       |        },
                       |        {
                       |            "title": "substation",
                       |            "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                       |        },
                       |        {
                       |            "title": "contains",
                       |            "query": "select first_value (contains.station) key, concat_ws (',', collect_list(contains.name)) value from ( select name_node_station.station station, name_node_station.name name from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.station) station from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalNode n, Terminal t, BusbarSection b, Substation s where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode and s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID ) busbars group by node ) name_node_station ) contains group by station"
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
        Main.main (
            Array (
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--three",
                "--fake",
                "--host", "localhost",
                "--port", cassandra_port,
                "--workdir", s"$wd$FILE_DEPOT",
                json
            )
        )
        new File (json).delete
    }

    @Test def VoltageFactor ()
    {
        val ID_SIMULATION = "VoltageFactor"
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using (new PrintWriter (new File (json), "UTF-8"))
        {
            writer =>
                writer.write (
                    s"""
                       |{
                       |    "id": "$ID_SIMULATION",
                       |    "name": "Voltage Factor Simulation Test",
                       |    "description": "simulation with 103% slack voltage",
                       |    "cim": "$FILE_DEPOT$FILENAME1.rdf",
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
                       |        "end": "2018-02-01T00:00:00.000+0100",
                       |        "buffer": 3600000
                       |    },
                       |    "temperatures": {
                       |        "cim_temperature": 20.0,
                       |        "simulation_temperature": 20.0
                       |    },
                       |    "swing": "hi",
                       |    "swing_voltage_factor": 1.03,
                       |    "keyspaces": {
                       |        "input": "$KEYSPACE",
                       |        "output": "$KEYSPACE",
                       |        "replication": 1
                       |    },
                       |    "players": [
                       |        {
                       |            "title": "Measured power for all PSRType_HouseService house services",
                       |            "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID "
                       |        }
                       |    ],
                       |    "recorders": [
                       |        {
                       |            "title": "All PowerTransformer input power",
                       |            "query": "select concat (name_island.name, '_power_recorder') name, name_island.name mrid, name_island.name parent, 'power' type, 'power_in' property, 'VA' unit, name_island.island from ( select concat_ws ('_', sort_array (collect_set (trafos.mrid))) name, first_value (trafos.island) island from ( select distinct t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t1.TopologicalNode node, n.TopologicalIsland island from PowerTransformer t, Terminal t1, Terminal t2, TopologicalNode n where t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 2 and t2.ACDCTerminal.sequenceNumber = 2 and t1.TopologicalNode = n.IdentifiedObject.mRID and t2.TopologicalNode = n.IdentifiedObject.mRID ) trafos group by node ) name_island",
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
                       |            "query": "select concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |            "query": "select concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name, c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, n.IdentifiedObject.mRID parent, 'voltage' type, 'voltage' property, 'Volts' unit, n.TopologicalIsland island from TopologicalNode n, Terminal t, EnergyConsumer c where t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ",
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
                       |        }
                       |    ],
                       |    "postprocessing": []
                       |}
                       |""".stripMargin
                )
        }
        Main.main (
            Array (
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--host", "localhost",
                "--port", cassandra_port,
                "--workdir", s"$wd$FILE_DEPOT",
                json))
        using (Cluster.builder.addContactPoint ("localhost").withPort (cassandra_port.toInt).build.connect)
        {
            cassandraSession =>
                val sql = s"select * from $KEYSPACE.simulated_value where simulation='$ID_SIMULATION' and type='voltage' and period=900000 allow filtering"
                def mag (row: Row): Double =
                {
                    val r = row.getDouble ("real_a")
                    val i = row.getDouble ("imag_a")
                    Math.sqrt (r * r + i * i)
                }
                assert (cassandraSession.execute (sql).all.asScala.forall (row => mag (row) > 409.5)) // min is 409.92237
        }
        new File (json).delete
    }
}

object SimulationSuiteIT
{
    val KEYSPACE = "test"
    val FILE_DEPOT = "data/"
    val FILENAME0 = "measurement_data"
    val FILENAME1 = "DemoData"
    val FILENAME2 = "DemoDataReinforced"
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
        val p = properties.getProperty ("nativeTransportPort", "9042")
        if ("" == p)
            "9042"
        else
            p
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
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File (s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        println (s"populating $KEYSPACE.measured_value")
        populate_measured_data ()
    }

    @AfterClass def after ()
    {
        // erase the unpacked file
        new File (s"$FILE_DEPOT$FILENAME0.csv").delete
        new File (s"$FILE_DEPOT$FILENAME1.rdf").delete
        new File (s"$FILE_DEPOT$FILENAME2.rdf").delete
    }
}
