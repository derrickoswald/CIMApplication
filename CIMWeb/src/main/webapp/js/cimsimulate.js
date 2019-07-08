/**
 * @fileOverview Simulate with gridlabd.
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimmap", "cimquery", "cim", "cimapp", "chooser", "themes/simulation_theme", "moment", "daterangepicker"],
    /**
     * @summary Functions to simulate using CIM files and measurement time series in Cassandra.
     * Clean up with script:
truncate table cimapplication.simulated_value;
truncate table cimapplication.simulation;
truncate table cimapplication.geojson_points;
truncate table cimapplication.geojson_lines;
truncate table cimapplication.geojson_polygons;
truncate table cimapplication.key_value;
truncate table cimapplication.simulation_event;
truncate table cimapplication.load_factor_by_day;
truncate table cimapplication.coincidence_factor_by_day;
truncate table cimapplication.responsibility_by_day;
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimmap, cimquery, cim, cimapp, Chooser, SimulationTheme, moment, DateRangePicker)
    {
        // The simulation details.
        let TheSimulation =
            {
                // the name of the simulation and this JSON file
                name: "DemoData",
                // textual description suitable for GUI display
                description: "simulation with demo data",
                // CIM RDF file, e.g. hdfs://sandbox:8020/some.rdf
                cim: "hdfs://sandbox:8020/DemoData.rdf",
                // CIM read options
                cimreaderoptions: {
                    "ch.ninecode.cim.do_about": false,
                    "ch.ninecode.cim.do_normalize": false,
                    "ch.ninecode.cim.do_deduplication": false,
                    "ch.ninecode.cim.make_edges": false,
                    "ch.ninecode.cim.do_join": false,
                    "ch.ninecode.cim.do_topo_islands": true,
                    "ch.ninecode.cim.do_topo": false,
                    "ch.ninecode.cim.split_maxsize": 67108864
                },
                // time range over which to simulate
                interval: {
                    "start": "2017-07-18T00:00:00.000+0100",
                    "end": "2017-07-19T00:00:00.000+0100"
                },
                // Cassandra keyspaces
                keyspaces: {
                    // the Cassandra keyspace where measurement data is read from for player files
                    "input": "cimapplication",
                    // the Cassandra keyspace where simulation results are stored - recorder files, summaries, simulations
                    "output": "cimapplication",
                    // the replication factor - in the case that the schema will be created
                    "replication": 2
                },
                // player queries, with title and query SQL
                players: [],
                // recorder queries, with title, query SQL, interval and aggregations
                recorders: [],
                // transformer mRID (or ganged name)
                transformers: [],
                // extra data queries for GeoJSON tables
                extras: []
            };
        //        "transformers": [
        //            "TRA2755"
        //        ],
        //        "players": [
        //            {
        //                "title": "house services",
        //                "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID and n.TopologicalIsland = '%s'"
        //            }
        //        ],
        //        "recorders": [
        //            {
        //                "title": "cable currents",
        //                "query": "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current' type, 'current_in' property, 'Amperes' unit from ACLineSegment a, Terminal t1, Terminal t2, TopologicalNode n where Conductor.len != 0 and (t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t1.ACDCTerminal.sequenceNumber = 1 and t1.TopologicalNode != n.IdentifiedObject.mRID and n.TopologicalIsland = '%s') and (t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and t2.ACDCTerminal.sequenceNumber = 2 and t2.TopologicalNode = n.IdentifiedObject.mRID and n.TopologicalIsland = '%s')",
        //                "interval": 900,
        //                "aggregations": [
        //                    {
        //                        "intervals": 1,
        //                        "ttl": 1800
        //                    },
        //                    {
        //                        "intervals": 4,
        //                        "ttl": 3600
        //                    },
        //                    {
        //                        "intervals": 12,
        //                        "ttl": 7200
        //                    },
        //                    {
        //                        "intervals": 96,
        //                        "ttl": null
        //                    }
        //                ]
        //            }
        //        ],
        //        "extras": [
        //            {
        //                "title": "nominalVoltage",
        //                "query": "select e.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (v.nominalVoltage * 1000.0 as string) value from EnergyConsumer e, BaseVoltage v where e.ConductingEquipment.BaseVoltage = v.IdentifiedObject.mRID"
        //            }
        //        ]
        //    }

        let TransformerChooser;

        // User specified player object queries
        let PlayerChooser;
        const PlayerChoices = [
            {
                "title": "Measured power for all PSRType_HouseService house services",
                "query":
                    `
                    select
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        'energy' type,
                        concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
                        t.TopologicalNode parent,
                        'constant_power' property,
                        'Watt' unit,
                        n.TopologicalIsland island
                    from
                        EnergyConsumer c,
                        Terminal t,
                        TopologicalNode n
                    where
                        c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `
            },
            {
                "title": "110% of measured power for all house services",
                "query":
                    `
                    select
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        'energy' type,
                        concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
                        t.TopologicalNode parent,
                        'constant_power' property,
                        'Watt' unit,
                        n.TopologicalIsland island
                    from
                        EnergyConsumer c,
                        Terminal t,
                        TopologicalNode n
                    where
                        c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "transform": "new MeasurementTransform { override def transform (real: Double, imag: Double): (Double, Double) = { val input = Complex (real, imag); val output = input * 1.1; (output.re, output.im) } }"
            },
            {
                "title": "Measured power at power factor 0.9 for all house services",
                "query":
                    `
                    select
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        'energy' type,
                        concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
                        t.TopologicalNode parent,
                        'constant_power' property,
                        'Watt' unit,
                        n.TopologicalIsland island
                    from
                        EnergyConsumer c,
                        Terminal t,
                        TopologicalNode n
                    where
                        c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "transform": "new MeasurementTransform { override def transform (real: Double, imag: Double): (Double, Double) = { val cosphi = Complex (0.9, Math.sqrt (1.0 - 0.9 * 0.9)); val input = Complex (real, imag); val output = input * cosphi; (output.re, output.im) } }"
            },
            {
                "title": "Measured power at random cosϕ between 75° and 90° for all house services",
                "query":
                    `
                    select
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        'energy' type,
                        concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
                        t.TopologicalNode parent,
                        'constant_power' property,
                        'Watt' unit,
                        n.TopologicalIsland island
                    from
                        EnergyConsumer c,
                        Terminal t,
                        TopologicalNode n
                    where
                        c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "transform": "new MeasurementTransform { override def transform (real: Double, imag: Double): (Double, Double) = { val angle = (Math.random () * (90.0 - 75.0) + 75.0) * Math.PI / 180.0; cosphi = Complex (Math.cos (angle), Math.sin (angle)); val input = Complex (real, imag); val output = input * cosphi; (output.re, output.im) } }"
            },
            {
                "title": "Synthesized power for all PSRType_newHouseService house services",
                "query":
                    `
                    select
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        l.LoadDynamics.IdentifiedObject.name synthesis,
                        'energy' type,
                        concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
                        t.TopologicalNode parent,
                        'constant_power' property,
                        'Watt' unit,
                        n.TopologicalIsland island
                    from
                        EnergyConsumer c,
                        LoadUserDefined l,
                        Terminal t,
                        TopologicalNode n
                    where
                        c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_newHouseService' and
                        c.LoadDynamics = l.LoadDynamics.IdentifiedObject.mRID and
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `
            }
        ];

        // User specified recorder object queries
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Node_Parameters
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Link_Parameters
        let RecorderChooser;
        const RecorderChoices = [
            {
                "title": "All PowerTransformer output power",
                "query":
                    `
                    select
                        concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'power' type,
                        'power_out' property,
                        'VA' unit,
                        n.TopologicalIsland island
                    from
                        PowerTransformer p,
                        Terminal t,
                        TopologicalNode n
                    where
                        t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        t.ACDCTerminal.sequenceNumber > 1 and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All PowerTransformer output currents",
                "query":
                    `
                    select
                        concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'current' type,
                        'current_out' property,
                        'Amperes' unit,
                        n.TopologicalIsland island
                    from
                        PowerTransformer p,
                        Terminal t,
                        TopologicalNode n
                    where
                        t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        t.ACDCTerminal.sequenceNumber > 1 and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All PowerTransformer power losses",
                "query":
                    `
                    select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'losses' type,
                        'power_losses' property,
                        'VA' unit,
                        n.TopologicalIsland island
                    from
                        PowerTransformer p,
                        Terminal t,
                        TopologicalNode n
                    where
                        t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        t.ACDCTerminal.sequenceNumber > 1 and
                        t.TopologicalNode = n.IdentifiedObject.mRID
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All BusbarSection node voltages",
                "query":
                    `
                    select
                        concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,
                        b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        n.IdentifiedObject.mRID parent,
                        'voltage' type,
                        'voltage' property,
                        'Volts' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n,
                        Terminal t,
                        BusbarSection b
                    where
                        t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        n.IdentifiedObject.mRID = t.TopologicalNode
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All BusbarSection output power",
                "query":
                    `
                    select
                        concat (b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,
                        b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        n.IdentifiedObject.mRID parent,
                        'power' type,
                        'measured_power' property,
                        'VA' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n,
                        Terminal t,
                        BusbarSection b
                    where
                        t.ConductingEquipment = b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        n.IdentifiedObject.mRID = t.TopologicalNode
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All EnergyConsumer node voltages",
                "query":
                    `
                    select
                        concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_voltage_recorder') name,
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        n.IdentifiedObject.mRID parent,
                        'voltage' type,
                        'voltage' property,
                        'Volts' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n,
                        Terminal t,
                        EnergyConsumer c
                    where
                        t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        n.IdentifiedObject.mRID = t.TopologicalNode
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All EnergyConsumer output power",
                "query":
                    `
                    select
                        concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_power_recorder') name,
                        c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        concat (c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load_object') parent,
                        'power' type,
                        'power' property,
                        'VA' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n,
                        Terminal t,
                        EnergyConsumer c
                    where
                        t.ConductingEquipment = c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                        n.IdentifiedObject.mRID = t.TopologicalNode
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All ACLineSegment currents",
                "query":
                    `
                    select
                        concat (name_island.name, '_current_recorder') name,
                        name_island.name mrid,
                        name_island.name parent,
                        'current' type,
                        'current_in' property,
                        'Amperes' unit,
                        name_island.island
                    from
                    (
                        select
                            concat_ws ('_', sort_array (collect_set (wires.mrid))) name,
                            first_value (wires.island) island
                        from
                            (
                                select distinct
                                    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                                    concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes,
                                    n.TopologicalIsland island
                                from
                                    ACLineSegment a,
                                    Terminal t1,
                                    Terminal t2,
                                    TopologicalNode n
                                where
                                    t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                                    t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                                    t1.TopologicalNode != t2.TopologicalNode and
                                    t1.TopologicalNode = n.IdentifiedObject.mRID
                            )
                            wires
                        group by
                            nodes
                    )
                    name_island
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            },
            {
                "title": "All ACLineSegment losses",
                "query":
                    `
                    select
                        concat (name_island.name, '_losses_recorder') name,
                        name_island.name mrid,
                        name_island.name parent,
                        'losses' type,
                        'power_losses' property,
                        'Wh' unit,
                        name_island.island
                    from
                    (
                        select
                            concat_ws ('_', sort_array (collect_set (wires.mrid))) name,
                            first_value (wires.island) island
                        from
                            (
                                select distinct
                                    a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                                    concat_ws ('_', sort_array (array (t1.TopologicalNode, t2.TopologicalNode))) nodes,
                                    n.TopologicalIsland island
                                from
                                    ACLineSegment a,
                                    Terminal t1,
                                    Terminal t2,
                                    TopologicalNode n
                                where
                                    t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                                    t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                                    t1.TopologicalNode != t2.TopologicalNode and
                                    t1.TopologicalNode = n.IdentifiedObject.mRID
                            )
                            wires
                        group by
                            nodes
                    )
                    name_island
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": null
                    },
                    {
                        "intervals": 4,
                        "ttl": null
                    },
                    {
                        "intervals": 12,
                        "ttl": null
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            }
        ];

        // User specified extra queries - to attach rdf data to JSON objects
        let ExtraChooser;
        const ExtraChoices = [
            {
                "title": "ratedCurrent",
                "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
            },
            {
                "title": "ratedS",
                "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, cast (sum (e.ratedS) as string) value from Terminal t, PowerTransformerEnd e where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 group by t.TopologicalNode"
            },
            {
                "title": "nominalVoltage",
                "query": "select e.mrid key, cast (v.nominalVoltage * 1000.0 as string) value from (select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, c.ConductingEquipment.BaseVoltage voltage from EnergyConsumer c union select b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, b.Connector.ConductingEquipment.BaseVoltage voltage from BusbarSection b) e, BaseVoltage v where e.voltage = v.IdentifiedObject.mRID"
            },
            {
                "title": "substation",
                "query": "select concat_ws ('_', sort_array (collect_set (e.PowerTransformer))) key, first_value (c.substation) value from Terminal t, PowerTransformerEnd e, PowerTransformer p, (select b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, b.Substation substation from Bay b union select u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID mrid, u.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID substation from Substation u) c where t.ACDCTerminal.IdentifiedObject.mRID = e.TransformerEnd.Terminal and e.TransformerEnd.endNumber = 2 and e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = c.mrid group by t.TopologicalNode"
            },
            {
                "title": "contains",
                "query": "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID key, concat_ws (',', collect_list(b.Connector.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID)) value from Substation s, BusbarSection b where s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType in ('PSRType_Substation', 'PSRType_TransformerStation', 'PSRType_DistributionBox') and b.Connector.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID group by s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID"
            }
        ];

        function set_input_keyspace (event)
        {
            TheSimulation.keyspaces.input = event.target.value;
            getDateRange ();
        }

        function set_output_keyspace (event)
        {
            TheSimulation.keyspaces.output = event.target.value;
            getSimulationNames ();
        }

        // accessors
        function getName ()
        {
            return ((null != TheSimulation) ? TheSimulation.name : "");
        }

        function getDescription ()
        {
            return ((null != TheSimulation) ? TheSimulation.description : "");
        }

        function collect_it_all ()
        {
            let items = RecorderChooser.context.items.filter (item => item.value !== "");
            RecorderChoices.forEach (
                choice =>
                    {
                        if (!items.find (item => choice.title === item.value))
                            items.push ({ value: choice.title });
                    }
                );
            RecorderChooser.context.items = items;
            RecorderChooser.render ();
            items = ExtraChooser.context.items.filter (item => item.value !== "");
            ExtraChoices.forEach (
                choice =>
                    {
                        if (!items.find (item => choice.title === item.value))
                            items.push ({ value: choice.title });
                    }
                );
            ExtraChooser.context.items = items;
            ExtraChooser.render ();
        }

        /**
         * @summary Set up the simulation JSON.
         * @description Build the JSON file from user input.
         * @function do_json
         */
        function do_json ()
        {
            const name = document.getElementById ("simulation_name").value;
            const description = document.getElementById ("simulation_description").value;
            if (name !== "")
                TheSimulation.name = name;
            if (description !== "")
                TheSimulation.description = description;
            TheSimulation.cim = document.getElementById ("cim_file").value;
            TheSimulation.keyspaces.input = document.getElementById ("input_keyspace").value;
            TheSimulation.keyspaces.output = document.getElementById ("output_keyspace").value;
            TheSimulation.transformers = query_transformers ();
            TheSimulation.players = query_players ();
            TheSimulation.recorders = query_recorders ();
            TheSimulation.extras = query_extras ();
            TheSimulation.postprocessing = [];
            if (document.getElementById ("events").checked)
                TheSimulation.postprocessing.push (
                    {
                        "class": "event",
                        "thresholds":
                            [
                                {
                                    "trigger": "high",
                                    "type": "voltage",
                                    "severity": 1,
                                    "reference": "ratedVoltage",
                                    "default": 400.0,
                                    "ratio": 1.10,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "low",
                                    "type": "voltage",
                                    "severity": 1,
                                    "reference": "ratedVoltage",
                                    "default": 400.0,
                                    "ratio": 0.90,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "high",
                                    "type": "voltage",
                                    "severity": 2,
                                    "reference": "ratedVoltage",
                                    "default": 400.0,
                                    "ratio": 1.06,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "low",
                                    "type": "voltage",
                                    "severity": 2,
                                    "reference": "ratedVoltage",
                                    "default": 400.0,
                                    "ratio": 0.94,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "high",
                                    "type": "current",
                                    "severity": 1,
                                    "reference": "ratedCurrent",
                                    "default": 100.0,
                                    "ratio": 1.10,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "high",
                                    "type": "current",
                                    "severity": 1,
                                    "reference": "ratedCurrent",
                                    "default": 100.0,
                                    "ratio": 0.90,
                                    "duration": 10800000
                                },
                                {
                                    "trigger": "high",
                                    "type": "current",
                                    "severity": 2,
                                    "reference": "ratedCurrent",
                                    "default": 100.0,
                                    "ratio": 0.75,
                                    "duration": 50400000
                                },
                                {
                                    "trigger": "high",
                                    "type": "power",
                                    "severity": 1,
                                    "reference": "ratedS",
                                    "default": 630000,
                                    "ratio": 1.10,
                                    "duration": 900000
                                },
                                {
                                    "trigger": "high",
                                    "type": "power",
                                    "severity": 1,
                                    "reference": "ratedS",
                                    "default": 630000,
                                    "ratio": 0.90,
                                    "duration": 10800000
                                },
                                {
                                    "trigger": "high",
                                    "type": "power",
                                    "severity": 2,
                                    "reference": "ratedS",
                                    "default": 630000,
                                    "ratio": 0.75,
                                    "duration": 50400000
                                }
                            ]
                    }
                );
            if (document.getElementById ("summarize").checked)
            {
                TheSimulation.postprocessing.push (
                    {
                        "class": "coincidence_factor",
                        "aggregates":
                            [
                                {
                                    "intervals": 96,
                                    "ttl": null
                                }
                            ]
                    }
                );
                TheSimulation.postprocessing.push (
                    {
                        "class": "load_factor",
                        "aggregates":
                            [
                                {
                                    "intervals": 96,
                                    "ttl": null
                                }
                            ]
                    }
                );
                TheSimulation.postprocessing.push (
                    {
                        "class": "responsibility_factor",
                        "aggregates":
                            [
                                {
                                    "intervals": 96,
                                    "ttl": null
                                }
                            ]
                    }
                );
            }

            return (TheSimulation);
        }

        function download_json ()
        {
            const json = do_json ();
            const a = document.createElement ("a");
            a.setAttribute ("href", "data:application/json;base64," + btoa (jsonify (json)));
            a.setAttribute ("download", json.name + ".json");
            a.setAttribute ("type", "application/json");
            a.setAttribute ("style", "display: none;");
            a.setAttribute ("target", "_blank");
            document.body.appendChild (a);
            a.click ();
            document.body.removeChild (a);
        }

        /**
         * @summary Run the simulation using GridLAB-D to populate simmulated_value_by_day Cassandra table.
         * @description Execute gridlabd for the simulation parameters provided by the user.
         * Typical command line call: wget --output-document=simulation.log --post-file=sak.json "http://localhost:9080/cimweb/cim/estimation;verbose=true;keep=true"
         * @function do_simulate
         */
        function do_simulate ()
        {
            const json = do_json ();
            if ("" === json.cim)
            {
                alert ("A CIM file must be specified");
                return;
            }
            const verbose = document.getElementById ("verbose").checked ? ";verbose=true" : "";
            const keep = document.getElementById ("keep").checked ? ";keep=true" : "";
            // flip to the map while simulating
            const to_map = document.getElementById ("to_map").checked;
            if (to_map)
                window.location.hash = "map";

            const url = util.home () + "cim/estimation" + verbose + keep;
            const xmlhttp = util.createCORSRequest ("POST", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 === xmlhttp.readyState)
                    if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                    {
                        const resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status === "OK")
                        {
                            const simulation_id = resp.result.simulations[0];
                            document.getElementById ("results").innerHTML = "";
                            if (to_map)
                            {
                                const theme = new SimulationTheme ();
                                theme.setSimulation (TheSimulation.keyspaces.output, simulation_id).then (
                                    function ()
                                    {
                                        cimmap.get_themer ().removeTheme (theme);
                                        cimmap.get_themer ().addTheme (theme, true);
                                        theme.setRenderListener (() => { cimmap.set_extents (theme.getExtents ()); cimmap.zoom_extents (); theme.setRenderListener = null; });
                                    }
                                );
                            }
                            else
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select json * from " + TheSimulation.keyspaces.output + ".simulation where id='" + simulation_id + "'"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        const json = JSON.parse (resultset[0]["[json]"]);
                                        document.getElementById ("results").innerHTML = "<pre>\n" +  jsonify (json) + "\n</pre>";
                                    }
                                );
                        }
                        else
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };

            xmlhttp.send (jsonify (json));
        }

        function do_show ()
        {
            const simulation_id = document.getElementById ("simulation_id").value;
            if (document.getElementById ("to_map").checked)
            {
                const theme = new SimulationTheme ();
                theme.setSimulation (TheSimulation.keyspaces.output, simulation_id).then (
                    function ()
                    {
                        cimmap.get_themer ().removeTheme (theme);
                        cimmap.get_themer ().addTheme (theme, true);
                        window.location.hash = "map";
                        // this causes two renders: cimmap.make_map ().then (cimmap.zoom_extents);
                        // so use a kludge:
                        theme.setRenderListener (() => { cimmap.set_extents (theme.getExtents ()); cimmap.zoom_extents (); theme.setRenderListener = null; });
                    }
                );
            }
            else
                cimquery.queryPromise (
                    {
                        cassandra: true,
                        sql: "select json * from " + TheSimulation.keyspaces.output + ".simulation where id='" + simulation_id + "'"
                    }
                ).then (
                    function (resultset)
                    {
                        const json = JSON.parse (resultset[0]["[json]"]);
                        document.getElementById ("results").innerHTML = "<pre>\n" +  jsonify (json) + "\n</pre>";
                        TheSimulation = json;
                        formFill (resultset);
                    }
                );
        }

        function jsonify (data)
        {
            return (JSON.stringify (data, null, 4))
        }

        function query_transformers ()
        {
            const ret = TransformerChooser.context.items.map (item => item.value).filter (x => "" !== x);
            return (ret);
        }

        function query_players ()
        {
            const ret = [];
            PlayerChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        const selected = PlayerChoices.filter (x => x.title === item.value);
                        if (0 !== selected.length)
                            ret.push (JSON.parse (JSON.stringify (selected[0])));
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                }
            );
            return (ret);
        }

        function query_recorders ()
        {
            const ret = [];
            RecorderChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        const selected = RecorderChoices.filter (x => x.title === item.value);
                        if (0 !== selected.length)
                            ret.push (JSON.parse (JSON.stringify (selected[0])));
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            return (ret);
        }

        function query_extras ()
        {
            const ret = [];
            ExtraChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        const selected = ExtraChoices.filter (x => x.title === item.value);
                        if (0 !== selected.length)
                            ret.push (JSON.parse (JSON.stringify (selected[0])));
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            return (ret);
        }

        function formFill ()
        {
            const simulate_template =
                `
                <div class="container">
                  <div class="row justify-content-center">
                    <div class="col-12">
                      <h1>Simulate using GridLAB-D</h1>
                      <h2 id="title">{{name}}</h2>
                      <form>
                        <div class="form-row">
                          <div class="col form-group">
                            <label for="simulation_name">Name</label>
                            <input  id="simulation_name" type="text" class="form-control" aria-describedby="nameHelp" placeholder="Enter a name for the simulation" value="{{name}}">
                            <small id="nameHelp" class="form-text text-muted">Enter a unique name for the simulation - used as a file name for the details.</small>
                          </div>
                          <div class="col form-group">
                            <label for="simulation_description">Description</label>
                            <input id="simulation_description" type="text" class="form-control" aria-describedby="descriptionHelp" placeholder="Enter a description for the simulation" value="{{description}}">
                            <small id="descriptionHelp" class="form-text text-muted">Enter a user facing description for the simulation - used for drop down choice title.</small>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="cim_file">CIM file</label>
                          <select id="cim_file" class="form-control custom-select" aria-describedby="cimFileHelp">
                          </select>
                          <small id="cimFileHelp" class="form-text text-muted">Select the CIM file to use in the simulation.</small>
                        </div>
                        <div class="form-row">
                          <div class="col form-group">
                            <label for="input_keyspace">Cassandra input keyspace</label>
                            <input id="input_keyspace" type="text" class="form-control" aria-describedby="outputKeyspaceHelp" value="cimapplication">
                            <small id="outputKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for input (table <em>measured_value</em>).</small>
                          </div>
                          <div class="col form-group">
                            <label for="output_keyspace">Cassandra output keyspace</label>
                            <input id="output_keyspace" type="text" class="form-control" aria-describedby="outputKeyspaceHelp" value="cimapplication">
                            <small id="outputKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for output (table <em>simulated_value</em> and others).</small>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="simulation_timerange">Time range</label>
                          <input id="simulation_timerange" type="text" class="form-control" aria-describedby="timerangeHelp" placeholder="Enter a time range for the simulation" value="">
                          <small id="timerangeHelp" class="form-text text-muted">Enter the simulation start and end date/time.</small>
                        </div>
                        <div id="transformers" class="form-group">
                        </div>
                        <div id="players" class="form-group">
                        </div>
                        <div class="form-group">
                          <button id="collect_it_all" name="collect_it_all" type="button" class="btn btn-secondary">Collect it all</button>
                        </div>
                        <div id="recorders" class="form-group">
                        </div>
                        <div id="extras" class="form-group">
                        </div>
                        <div class="form-row">
                          <div class="col form-group">
                            <label for="events">Event detection</label>
                              <div class="form-check">
                                <input id="events" class="form-check-input" type="checkbox" name="events" aria-describedby="eventsHelp" checked>
                                <small id="eventsHelp" class="form-text text-muted">Perform event detection (overvoltage, overcurrent, overpower) after simulation.</small>
                              </div>
                          </div>
                          <div class="col form-group">
                            <label for="summarize">Summarize</label>
                              <div class="form-check">
                                <input id="summarize" class="form-check-input" type="checkbox" name="summarize" aria-describedby="summarizeHelp">
                                <small id="summarizeHelp" class="form-text text-muted">Perform summarization (utilization, load & coincidence factor) after simulation.</small>
                              </div>
                          </div>
                        </div>
                        <div class="form-row">
                          <div class="col form-group">
                            <label for="keep">Keep GridLAB-D intermediate files</label>
                              <div class="form-check">
                                <input id="keep" class="form-check-input" type="checkbox" name="keep" aria-describedby="keepHelp" checked>
                                <small id="keepHelp" class="form-text text-muted">Do not delete intermediate gridlabd calculation files (usually in /tmp on worker nodes)..</small>
                              </div>
                          </div>
                        </div>
                        <div class="form-row">
                          <div class="col form-group">
                            <label for="to_map">View on map</label>
                              <div class="form-check">
                                <input id="to_map" class="form-check-input" type="checkbox" name="to_map" aria-describedby="toMapHelp" checked>
                                <small id="toMapHelp" class="form-text text-muted">Add a theme to the map tab for simulation results.</small>
                              </div>
                          </div>
                          <div class="col form-group">
                            <label for="verbose">Verbose</label>
                              <div class="form-check">
                                <input id="verbose" class="form-check-input" type="checkbox" name="verbose" aria-describedby="verboseHelp" checked>
                                <small id="verboseHelp" class="form-text text-muted">Output messages to console as the simulation progresses.</small>
                              </div>
                          </div>
                        </div>
                        <div class="form-row">
                            <div class="col form-group">
                              <button id="do_simulate" name="do_simulate" type="button" class="btn btn-primary">Simulate</button>
                            </div>
                            <div class="col form-group">
                              <button id="do_json" name="do_json" type="button" class="btn btn-primary">Download JSON</button>
                            </div>
                        </div>
                        <div class="form-group">
                          <label for="simulation_id">Prior simulations</label>
                          <select id="simulation_id" class="form-control custom-select" aria-describedby="simulationIDHelp">
                          </select>
                          <small id="simulationIDHelp" class="form-text text-muted">Select the simulation to view on the map.</small>
                        </div>
                        <div class="form-group">
                          <button id="show_simulation" name="show_simulation" type="button" class="btn btn-primary">Show simulation</button>
                        </div>
                      </form>
                      <div id="results">
                      </div>
                    </div>
                  </div>
                </div>
                `;

            document.getElementById ("simulate").innerHTML = mustache.render
            (
                simulate_template,
                {
                    name: getName,
                    description: getDescription
                }
            );
            document.getElementById ("input_keyspace").onchange = set_input_keyspace;
            document.getElementById ("output_keyspace").onchange = set_output_keyspace;

            // see https://wireddots.com/products/datetimepicker
            const start = new Date (TheSimulation.interval.start);
            const end = new Date (TheSimulation.interval.end);
            new DateRangePicker (
                "#simulation_timerange",
                {
                    timePicker: true,
                    timePickerIncrement: 15,
                    locale: {
                        format: 'YYYY.MM.DD HH:mm'
                    },
                    timePicker24Hour: true,
                    linkedCalendars: false,
                    startDate: start,
                    endDate: end,
                    showDropdowns: true
                    //showISOWeekNumbers: true
                },
                setDateRange
            );
            if (null == TransformerChooser)
            {
                const help =
                    `
                    <small id="transformers_help" class="form-text text-muted">
                        The transformers to process - if none are provided, all are processed.
                    </small>
                    `;
                TransformerChooser = new Chooser ("transformers", "Transformers", "Transformer", null, help);
            }
            TransformerChooser.render ();
            if (null == PlayerChooser)
            {
                const help =
                    `
                    <small id="players_help" class="form-text text-muted">
                        The queries to use to pick player (load) elements.
                    </small>
                    `;
                PlayerChooser = new Chooser ("players", "Players", "Player", PlayerChoices.map (function (x) { return (x.title); }), help);
            }
            PlayerChooser.render ();
            if (null == RecorderChooser)
            {
                const help =
                    `
                    <small id="recorders_help" class="form-text text-muted">
                        The queries to use to pick recorder elements.
                    </small>
                    `;
                RecorderChooser = new Chooser ("recorders", "Recorders", "Recorder", RecorderChoices.map (function (x) { return (x.title); }), help);
            }
            RecorderChooser.render ();
            if (null == ExtraChooser)
            {
                const help =
                    `
                    <small id="extra_help" class="form-text text-muted">
                        The queries to add data to the generated JSON objects.
                    </small>
                    `;
                ExtraChooser = new Chooser ("extras", "Extras", "Extra", ExtraChoices.map (function (x) { return (x.title); }), help);
            }
            ExtraChooser.render ();

            document.getElementById ("collect_it_all").onclick = collect_it_all;
            document.getElementById ("do_simulate").onclick = do_simulate;
            document.getElementById ("do_json").onclick = download_json;
            document.getElementById ("show_simulation").onclick = do_show;
        }

        function render_prior_simulations (resultset)
        {
            const template =
            `
            {{#simulations}}
              <option value="{{id}}">{{name}}</option>
            {{/simulations}}
            `;
            document.getElementById ("simulation_id").innerHTML = mustache.render
            (
                template,
                {
                    simulations: resultset.map (row => JSON.parse (row["[json]"]))
                }
            );
        }

        function render ()
        {
            document.getElementById ("simulate").innerHTML = "";
            formFill ();
            return (getSimulationNames ().then (getFiles).then (getDateRange));
        }

        function getSimulationNames ()
        {
            return (
                cimquery.queryPromise (
                    {
                        cassandra: true,
                        sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' and keyspace_name = 'cimapplication' allow filtering".replace ("cimapplication", TheSimulation.keyspaces.output)
                    }
                ).then (
                    function (resultset)
                    {
                        if (resultset.length > 0)
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select JSON id, name, description, cim, cimreaderoptions, start_time, end_time, transformers from cimapplication.simulation".replace ("cimapplication", TheSimulation.keyspaces.output)
                                    }
                                ).then (render_prior_simulations)
                            );
                    }
                )
            );
        }

        function getFiles ()
        {
            return (
                cimfiles.fetch ("\\").then (
                    function (response)
                    {
                        if (response.status === "OK")
                        {
                            response.result.files = response.result.files.filter (
                                x =>
                                {
                                    const name = x.path.toLowerCase ();
                                    return (name.endsWith (".rdf") || name.endsWith (".xml"));
                                }
                            );
                            const file_template =
                                `
                                {{#files}}
                                    <option value="{{root}}{{path}}">{{path}}</option>
                                {{/files}}
                                `;
                            document.getElementById ("cim_file").innerHTML = mustache.render (file_template, response.result);
                        }
                        else
                            alert (response.message);
                    }
                )
            );
        }

        function setDateRange (start, end)
        {
            TheSimulation.interval =
            {
                start: start.toISOString ().replace ("Z", "+0000"), // "2018-04-24T19:24:27.884Z"
                end: end.toISOString ().replace ("Z", "+0000")
            }
        }

        function getEarliestDate (start)
        {
            return (
                cimquery.queryPromise (
                    {
                        cassandra: true,
                        sql: "select mrid, time from cimapplication.measured_value".replace ("cimapplication", TheSimulation.keyspaces.input) + " where time < " + start.getTime () + " limit 1 allow filtering"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 === resultset.length)
                            return (start);
                        else
                        {
                            const time = new Date (resultset[0].time);
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select min(time) as lo from cimapplication.measured_value".replace ("cimapplication", TheSimulation.keyspaces.input) + " where mrid = '" + resultset[0].mrid + "' and time < " + time.getTime () + " allow filtering"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        if ((0 === resultset.length) || (null == resultset[0]["lo"]))
                                            return (time);
                                        else
                                            return (getEarliestDate (new Date (resultset[0]["lo"])));
                                    }
                                )
                            );
                        }
                    }
                )
            );
        }

        function getLatestDate (end)
        {
            return (
                cimquery.queryPromise (
                    {
                        cassandra: true,
                        sql: "select mrid, time from cimapplication.measured_value".replace ("cimapplication", TheSimulation.keyspaces.input) + " where time > " + end.getTime () + " limit 1 allow filtering"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 === resultset.length)
                            return (end);
                        else
                        {
                            const time = new Date (resultset[0].time);
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select max(time) as hi from cimapplication.measured_value".replace ("cimapplication", TheSimulation.keyspaces.input) + " where mrid = '" + resultset[0].mrid + "' and time > " + time.getTime () + " allow filtering"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        if ((0 === resultset.length) || (null == resultset[0]["hi"]))
                                            return (time);
                                        else
                                            return (getLatestDate (new Date (resultset[0]["hi"])));
                                    }
                                )
                            );
                        }
                    }
                )
            );
        }

        function getDateRange ()
        {
            // unfortunately Cassandra is really, really, really bad at aggregates,
            // so we use this recursive widening bracket strategy based on specific mRID values,
            // instead of the direct query "select min(time) as low, max(time) as high from cimapplication.measured_value"
            // which takes forever
            return (
                cimquery.queryPromise (
                    {
                        cassandra: true,
                        sql: "select time from cimapplication.measured_value".replace ("cimapplication", TheSimulation.keyspaces.input) + " limit 1"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 === resultset.length)
                            alert ("no data found in cimapplication.measured_value table".replace ("cimapplication", TheSimulation.keyspaces.input));
                        else
                        {
                            const time = new Date (resultset[0].time);
                            Promise.all ([getEarliestDate (time), getLatestDate (time)]).then (
                                function (minmax)
                                {
                                    const start = minmax[0];
                                    const end = minmax[1];
                                    setDateRange (start, end);
                                    new DateRangePicker (
                                        "#simulation_timerange",
                                        {
                                            timePicker: true,
                                            timePickerIncrement: 15,
                                            locale: {
                                                format: 'YYYY.MM.DD HH:mm'
                                            },
                                            timePicker24Hour: true,
                                            linkedCalendars: false,
                                            startDate: start,
                                            endDate: end,
                                            minDate: start,
                                            maxDate: end,
                                            showDropdowns: true
                                            //showISOWeekNumbers: true
                                        },
                                        setDateRange
                                    );
                                    // unfortunately you can't set the min and max date as well, so this doesn't work:
                                    // $('#simulation_timerange').data('daterangepicker').setEndDate (end);
                                    // $('#simulation_timerange').data('daterangepicker').setStartDate (start);
                                }
                            );
                        }
                    }
                )
            );
        }

        function setReplication ()
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        // as a heuristic set the replication factor to 1 if local and 2 otherwise (sandbox)
                        cimapp.pong (false).then (
                            function (result)
                            {
                                TheSimulation.keyspaces.replication = ("local[*]" === result.spark_instance.spark_master) ? 1 : 2;
                                resolve ();
                            }
                        );
                    }
                )
            );
        }

        /**
         * @summary Render the simulations page.
         * @description Uses mustache to create HTML DOM elements that display the simulation options.
         * @function initialize
         */
        function initialize ()
        {
            setReplication ().then (render);
        }

        return (
            {
                initialize: initialize
            }
        );
    }
);
