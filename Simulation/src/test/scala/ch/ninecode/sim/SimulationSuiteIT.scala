package ch.ninecode.sim

import java.io.File
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.util.Properties

import scala.collection.JavaConverters.asScalaBufferConverter

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.WriteConf

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import ch.ninecode.util.Schema
import ch.ninecode.testutil.Unzip
import ch.ninecode.testutil.Using

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimulationSuiteIT
{

    import SimulationSuiteIT.FILE_DEPOT
    import SimulationSuiteIT.FILENAME1
    import SimulationSuiteIT.FILENAME2
    import SimulationSuiteIT.KEYSPACE
    import SimulationSuiteIT.cassandra_port
    import SimulationSuiteIT.delete
    import SimulationSuiteIT.using
    val curDir = new java.io.File(".").getCanonicalPath

    def getSession: CqlSession =
    {
        val session: CqlSession = CqlSession
            .builder()
            .withLocalDatacenter("datacenter1")
            .addContactPoint(new InetSocketAddress("localhost", cassandra_port.toInt))
            .build()
        session
    }

    @Test def Help ()
    {
        Simulation.main(Array("--unittest", "--help"))
    }

    @Test def SimulationDemoData ()
    {
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using(new PrintWriter(new File(json), "UTF-8"))
        {
            writer =>
                writer.write(
                    s"""
                       |{
                       |    "id": "Basic",
                       |    "name": "DemoData",
                       |    "description": "simulation with demo data",
                       |    "cim": "$FILE_DEPOT$FILENAME1.zip",
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
                       |            "query": "select concat (name_node_island.name, '_voltage_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'voltage' type, 'voltage' property, 'Volts' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |            "query": "select concat (name_node_island.name, '_power_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'power' type, 'measured_power' property, 'VA' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |            "query": "select first_value (c.substation) key, cast (sum(e.ratedS) as string) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, ( select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u ) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.TransformerEnd.BaseVoltage = 'BaseVoltage_400' and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
                       |        }
                       |    ],
                       |    "postprocessing": []
                       |}
                       |""".stripMargin
                )
        }
        Simulation.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--workdir", curDir,
                "--host", "localhost",
                "--port", cassandra_port,
                json))
        delete(json)
    }

    @Test def SimulationDemoDataReinforced ()
    {
        val ID_SIMULATION = "Reinforced"
        val json = s"$FILE_DEPOT$FILENAME2.json"
        using(new PrintWriter(new File(json), "UTF-8"))
        {
            writer =>
                writer.write(
                    s"""
                       |{
                       |    "id": "$ID_SIMULATION",
                       |    "name": "Reinforced Simulation Test",
                       |    "description": "simulation with demo data and reinforcement",
                       |    "cim": "$FILE_DEPOT$FILENAME2.zip",
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
                       |            "query": "select c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit, n.TopologicalIsland island from EnergyConsumer c, Terminal t, TopologicalNode n where c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID ",
                       |            "transform": "new MeasurementTransform{val MILLISECONDS_PER_MINUTE: Int = 60 * 1000;override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] ={data.map (reading =>reading.`type` match{case \\"energy\\" =>val factor = MILLISECONDS_PER_HOUR / reading.period;val t = reading.time - (reading.period - MILLISECONDS_PER_MINUTE);reading.copy (time = t, readings = reading.readings.map (_ * factor), units = \\"VA\\") case _ =>reading})}}"
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
                       |            "title": "All BusbarSection node voltages",
                       |            "query": "select concat (name_node_island.name, '_voltage_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'voltage' type, 'voltage' property, 'Volts' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |        }
                       |    ],
                       |    "postprocessing": []
                       |}
                       |""".stripMargin
                )
        }
        Simulation.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--spark_options", "spark.driver.memory=2g,spark.executor.memory=2g,spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.sql.catalog.casscatalog=com.datastax.spark.connector.datasource.CassandraCatalog,spark.kryo.registrator=ch.ninecode.cim.CIMRegistrator,spark.graphx.pregel.checkpointInterval=8,spark.ui.showConsoleProgress=false,spark.sql.debug.maxToStringFields=250",
                "--verbose",
                "--keep",
                "--workdir", curDir,
                "--host", "localhost",
                "--port", cassandra_port,
                json
            )
        )
        using(getSession)
        {
            cassandraSession =>
                val sql1 = s"""select * from "$KEYSPACE".measured_value where mrid='USR0001' and type='energy' and time='2017-12-31 23:00:00.000+0000'"""
                val sql2 = s"""select * from "$KEYSPACE".simulated_value where simulation='$ID_SIMULATION' and mrid='USR0001' and type='power' and period=900000 and time='2017-12-31 23:00:00.000+0000'"""
                assert(cassandraSession.execute(sql1).all.asScala.headOption.fold(Double.NaN)(_.getDouble("real_a")) * 4 ==
                    cassandraSession.execute(sql2).all.asScala.headOption.fold(Double.NaN)(_.getDouble("real_a")))
        }
        delete(json)
    }

    @Test def ThreePhase ()
    {
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using(new PrintWriter(new File(json), "UTF-8"))
        {
            writer =>
                writer.write(
                    s"""
                       |{
                       |    "id": "ThreePhase",
                       |    "name": "Three Phase Simulation Test",
                       |    "description": "three phase simulation with demo data",
                       |    "cim": "$FILE_DEPOT$FILENAME1.zip",
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
                       |            "query": "select concat (name_node_island.name, '_voltage_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'voltage' type, 'voltage' property, 'Volts' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |            "query": "select concat (name_node_island.name, '_power_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'power' type, 'measured_power' property, 'VA' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |            "query": "select first_value (c.substation) key, cast (sum(e.ratedS) as string) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, ( select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u ) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.TransformerEnd.BaseVoltage = 'BaseVoltage_400' and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
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
        Simulation.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--three",
                "--fake",
                "--workdir",curDir,
                "--host", "localhost",
                "--port", cassandra_port,
                json
            )
        )
        delete(json)
    }

    @Test def VoltageFactor ()
    {
        val ID_SIMULATION = "VoltageFactor"
        val json = s"$FILE_DEPOT$FILENAME1.json"
        using(new PrintWriter(new File(json), "UTF-8"))
        {
            writer =>
                writer.write(
                    s"""
                       |{
                       |    "id": "$ID_SIMULATION",
                       |    "name": "Voltage Factor Simulation Test",
                       |    "description": "simulation with 103% slack voltage",
                       |    "cim": "$FILE_DEPOT$FILENAME1.zip",
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
                       |            "title": "All BusbarSection node voltages",
                       |            "query": "select concat (name_node_island.name, '_voltage_recorder') name, name_node_island.name mrid, name_node_island.node parent, 'voltage' type, 'voltage' property, 'Volts' unit, name_node_island.island from ( select concat_ws ('_', sort_array (collect_set (busbars.mrid))) name, first_value (busbars.node) node, first_value (busbars.island) island from ( select distinct b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, t.TopologicalNode node, n.TopologicalIsland island from TopologicalNode n, Terminal t, BusbarSection b where t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and n.IdentifiedObject.mRID = t.TopologicalNode ) busbars group by node ) name_node_island",
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
                       |        }
                       |    ],
                       |    "postprocessing": []
                       |}
                       |""".stripMargin
                )
        }
        Simulation.main(
            Array(
                "--unittest",
                "--master", "local[2]",
                "--verbose",
                "--keep",
                "--workdir",curDir,
                "--host", "localhost",
                "--port", cassandra_port,
                json))
        using(getSession)
        {
            cassandraSession =>
                val sql = s"""select * from "$KEYSPACE".simulated_value where simulation='$ID_SIMULATION' and type='voltage' and period=900000 allow filtering"""

                def mag (row: Row): Double =
                {
                    val r = row.getDouble("real_a")
                    val i = row.getDouble("imag_a")
                    Math.sqrt(r * r + i * i)
                }

                assert(cassandraSession.execute(sql).all.asScala.forall(row => mag(row) > 403.0)) // min is 403.97855
        }
        delete(json)
    }
}

object SimulationSuiteIT extends Unzip with Using
{
    val KEYSPACE = "Test"
    val FILE_DEPOT = "data/"
    val FILENAME0 = "measurement_data"
    val FILENAME1 = "DemoData"
    val FILENAME2 = "DemoDataReinforced"
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

    def populate_measured_data (): Unit =
    {
        println("creating Spark session")

        // create the configuration
        val configuration = new SparkConf(false)
            .setAppName("SummarySuiteIT")
            .setMaster("local[*]")
            .set("spark.driver.memory", "2g")
            .set("spark.executor.memory", "2g")
            .set("spark.ui.port", "4041")
            .set("spark.ui.showConsoleProgress", "false")
            .set("spark.cassandra.connection.host", "localhost")
            .set("spark.cassandra.connection.port", cassandra_port)

        val session = SparkSession.builder.config(configuration).getOrCreate
        session.sparkContext.setLogLevel("WARN")

        val measurement_options = Map[String, String](
            "header" -> "false",
            "ignoreLeadingWhiteSpace" -> "false",
            "ignoreTrailingWhiteSpace" -> "false",
            "sep" -> ",",
            "quote" -> "\"",
            "escape" -> "\\",
            "encoding" -> "UTF-8",
            "comment" -> "#",
            "nullValue" -> "",
            "nanValue" -> "NaN",
            "positiveInf" -> "Inf",
            "negativeInf" -> "-Inf",
            "dateFormat" -> "yyyy-MM-dd",
            "timestampFormat" -> "yyyy-MM-dd HH:mm:ss",
            "mode" -> "DROPMALFORMED",
            "inferSchema" -> "true"
        )
        Schema(session, "/test_simulation_schema.sql", verbose = true).make(keyspace = KEYSPACE, replication = 1)
        println(s"reading $FILE_DEPOT$FILENAME0.csv")
        val df = session.sqlContext.read.format("csv").options(measurement_options).csv(s"$FILE_DEPOT$FILENAME0.csv")
        val ok = df.rdd.map(row => (row.getString(0), "energy", row.getTimestamp(1), 900000, row.getDouble(2), 0.0, "Wh"))
        println(s"saving to $KEYSPACE.measured_value")
        val conf = WriteConf.fromSparkConf(session.sparkContext.getConf).copy(consistencyLevel = ConsistencyLevel.ANY)
        ok.saveToCassandra(KEYSPACE, "measured_value", SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"), conf)
        println("stopping Spark session")
        session.stop
    }

    @BeforeClass def before ()
    {
        // unpack the zip files
        if (!new File(s"$FILE_DEPOT$FILENAME0.csv").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME0.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
        if (!new File(s"$FILE_DEPOT$FILENAME2.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME2.zip", FILE_DEPOT)
        println(s"populating $KEYSPACE.measured_value")
        populate_measured_data()
    }

    @AfterClass def after ()
    {
        // erase the unpacked file
        delete(s"$FILE_DEPOT$FILENAME0.csv")
        delete(s"$FILE_DEPOT$FILENAME1.rdf")
        delete(s"$FILE_DEPOT$FILENAME2.rdf")
    }
}
