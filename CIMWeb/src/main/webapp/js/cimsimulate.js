/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimmap", "cimquery", "cim", "chooser", "daterangepicker", "themes/simulation_theme"],
    /**
     * @summary Functions to simulate using CIM files and measurement time series in Cassandra.
     * Clean up with script:
truncate table cimapplication.simulated_value;
truncate table cimapplication.simulation;
truncate table cimapplication.geojson_points;
truncate table cimapplication.geojson_lines;
truncate table cimapplication.geojson_polygons;
truncate table cimapplication.key_value;
truncate table cimapplication.utilization;
truncate table cimapplication.utilization_summary_by_day;
truncate table cimapplication.utilization_summary_by_day_by_transformer;
truncate table cimapplication.utilization_historical;
truncate table cimapplication.load_factor_by_day;
truncate table cimapplication.coincidence_factor_by_day;
truncate table cimapplication.responsibility_by_day;
truncate table cimapplication.voltage_deviation_by_day;
truncate table cimapplication.voltage_deviation_summary_by_day;
truncate table cimapplication.losses_by_day;
truncate table cimapplication.losses_summary_by_day;
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimmap, cimquery, cim, Chooser, DateRangePicker, SimulationTheme)
    {
        // The Cassandra keyspace where measurement data is read from for player files
        var input_keyspace = "cimapplication";

        // The Cassandra keyspace where simulation results are stored - recorder files, summaries, simulations
        var output_keyspace = "cimapplication";

        // The simulation details.
        var TheSimulation =
            {
                name: "DemoData",
                description: "simulation with demo data",
                cim: "hdfs://sandbox:8020/DemoData.rdf",
                cimreaderoptions: {
                    "ch.ninecode.cim.do_about": false,
                    "ch.ninecode.cim.do_normalize": false,
                    "ch.ninecode.cim.do_deduplication": false,
                    "ch.ninecode.cim.make_edges": false,
                    "ch.ninecode.cim.do_join": false,
                    "ch.ninecode.cim.do_topo_islands": false,
                    "ch.ninecode.cim.do_topo": false,
                    "ch.ninecode.cim.split_maxsize": 67108864
                },
                interval: {
                    "start": "2017-07-18T00:00:00.000+0100",
                    "end": "2017-07-19T00:00:00.000+0100"
                },
                keyspaces: {
                    "read": "cimapplication",
                    "write": "cimappplication"
                },
                players: [],
                recorders: [],
                transformers: [],
                extras: []
            };
        // provisional schema:
        //    {
        //        "name": <the name of the simulation and this JSON file>,
        //        "description": <textual description suitable for GUI display>,
        //        "cim": <CIM RDF file, e.g. hdfs://sandbox:8020/some.rdf>,
        //        "cimreaderoptions": {
        //            "ch.ninecode.cim.do_about": false,
        //            "ch.ninecode.cim.do_normalize": false,
        //            "ch.ninecode.cim.do_deduplication": false,
        //            "ch.ninecode.cim.make_edges": false,
        //            "ch.ninecode.cim.do_join": false,
        //            "ch.ninecode.cim.do_topo_islands": false,
        //            "ch.ninecode.cim.do_topo": false,
        //            "ch.ninecode.cim.split_maxsize": 67108864
        //        },
        //        "interval": {
        //            "start": "2017-07-18T00:00:00.000+0100",
        //            "end": "2017-07-19T00:00:00.000+0100"
        //        },
        //        keyspaces: {
        //            "read": "cimapplication",
        //            "write": "cimappplication"
        //        },
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
        //        ]
        //    }

        var TransformerChooser;

        // User specified player object queries
        var PlayerChooser;
        var PlayerChoices = [
            {
                "title": "Measured power for all house services",
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
            }
        ];

        // User specified recorder object queries
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Node_Parameters
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Link_Parameters
        var RecorderChooser;
        var RecorderChoices = [
            {
                "title": "All node voltages",
                "query":
                    `
                    select
                        concat (n.IdentifiedObject.mRID, '_voltage_recorder') name,
                        ifnull (t.ConductingEquipment, n.IdentifiedObject.mRID) mrid,
                        n.IdentifiedObject.mRID parent,
                        'voltage' type,
                        'voltage' property,
                        'Volts' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n
                    left outer join
                        (
                            select
                                distinct (t1.TopologicalNode) TopologicalNode, first (t1.ConductingEquipment) ConductingEquipment
                            from
                                Terminal t1
                            where
                                t1.ConductingEquipment not in
                                (
                                    select
                                        t2.ConductingEquipment
                                    from
                                        Terminal t2
                                    where
                                        ACDCTerminal.sequenceNumber > 1
                                )
                            group by t1.TopologicalNode
                        ) t
                    on
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
                "title": "All transformer power flows",
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
                "title": "All transformer output currents",
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
                "title": "All transformer power losses",
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
                "title": "All cable currents",
                "query":
                    `
                    select
                        concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_current_recorder') name,
                        a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'current' type,
                        'current_in' property,
                        'Amperes' unit,
                        n.TopologicalIsland island
                    from
                        ACLineSegment a,
                        Terminal t1,
                        Terminal t2,
                        TopologicalNode n
                    where
                        (
                            t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                            t1.ACDCTerminal.sequenceNumber = 1 and
                            t1.TopologicalNode != n.IdentifiedObject.mRID
                        )
                        and
                        (
                            t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                            t2.ACDCTerminal.sequenceNumber = 2 and
                            t2.TopologicalNode = n.IdentifiedObject.mRID
                        )
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
                "title": "All cable losses",
                "query":
                    `
                    select
                        concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name,
                        a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
                        a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'losses' type,
                        'power_losses' property,
                        'Wh' unit,
                        n.TopologicalIsland island
                    from
                        ACLineSegment a,
                        Terminal t1,
                        Terminal t2,
                        TopologicalNode n
                    where
                        (
                            t1.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                            t1.ACDCTerminal.sequenceNumber = 1 and
                            t1.TopologicalNode != n.IdentifiedObject.mRID
                        )
                        and
                        (
                            t2.ConductingEquipment = a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
                            t2.ACDCTerminal.sequenceNumber = 2 and
                            t2.TopologicalNode = n.IdentifiedObject.mRID
                        )
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
        var ExtraChooser;
        var ExtraChoices = [
            {
                "title": "ratedCurrent",
                "query": "select l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (w.ratedCurrent as string) value from ACLineSegment l, WireInfo w where w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet"
            },
            {
                "title": "ratedS",
                "query": "select p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (e.ratedS as string) value from PowerTransformer p, PowerTransformerEnd e where e.PowerTransformer = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and e.TransformerEnd.endNumber = 1"
            },
            {
                "title": "nominalVoltage",
                "query": "select e.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key, cast (v.nominalVoltage * 1000.0 as string) value from EnergyConsumer e, BaseVoltage v where e.ConductingEquipment.BaseVoltage = v.IdentifiedObject.mRID"
            }
        ];

        function set_input_keyspace (event)
        {
            input_keyspace = event.target.value;
            getDateRange ();
        }

        function set_output_keyspace (event)
        {
            output_keyspace = event.target.value;
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
            var items = RecorderChooser.context.items.filter (item => item.value != "");
            RecorderChoices.forEach (
                choice =>
                    {
                        if (!items.find (item => choice.title == item.value))
                            items.push ({ value: choice.title });
                    }
                );
            RecorderChooser.context.items = items;
            RecorderChooser.render ();
            items = ExtraChooser.context.items.filter (item => item.value != "");
            ExtraChoices.forEach (
                choice =>
                    {
                        if (!items.find (item => choice.title == item.value))
                            items.push ({ value: choice.title });
                    }
                );
            ExtraChooser.context.items = items;
            ExtraChooser.render ();
        }

        /**
         * @summary Run the simulation using GridLAB-D to populate simmulated_value_by_day Cassandra table.
         * @description Execute gridlabd for the simulation parameters provided by the user.
         * Typical command line call: wget --output-document=simulation.log --post-file=sak.json "http://localhost:9080/cimweb/cim/estimation;verbose=true;keep=true"
         * @function do_simulate
         * @memberOf module:cimsimulate
         */
        function do_simulate ()
        {
            var name = document.getElementById ("simulation_name").value;
            var description = document.getElementById ("simulation_description").value;
            if (name != "")
                TheSimulation.name = name;
            if (description != "")
                TheSimulation.description = description;
            TheSimulation.cim = document.getElementById ("cim_file").value;
            if ("" == TheSimulation.cim)
            {
                alert ("A CIM file must be specified");
                return;
            }
            TheSimulation.keyspaces.read = document.getElementById ("input_keyspace").value;
            TheSimulation.keyspaces.write = document.getElementById ("output_keyspace").value;
            TheSimulation.transformers = query_transformers ();
            TheSimulation.players = query_players ();
            TheSimulation.recorders = query_recorders ();
            TheSimulation.extras = query_extras ();
            document.getElementById ("results").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";
            var verbose = document.getElementById ("verbose").checked ? ";verbose=true" : "";
            var keep = document.getElementById ("keep").checked ? ";keep=true" : "";
            var summarize = document.getElementById ("summarize").checked ? ";summarize=true" : "";
            // flip to the map while simulating
            var to_map = document.getElementById ("to_map").checked;
            if (to_map)
                window.location.hash = "map";

            var url;
            var xmlhttp;

            url = util.home () + "cim/estimation" + verbose + keep + summarize;
            xmlhttp = util.createCORSRequest ("POST", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                        {
                            var simulation_id = resp.result.simulations[0];
                            document.getElementById ("results").innerHTML = "";
                            if (to_map)
                            {
                                var theme = new SimulationTheme ();
                                theme.setSimulation (output_keyspace, simulation_id).then (
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
                                        sql: "select json * from " + output_keyspace + ".simulation where id='" + simulation_id + "'"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        var json = JSON.parse (resultset[0]["[json]"]);
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

            xmlhttp.send (JSON.stringify (TheSimulation, null, 4));
        }

        function do_show ()
        {
            var simulation_id = document.getElementById ("simulation_id").value;
            if (document.getElementById ("to_map").checked)
            {
                var theme = new SimulationTheme ();
                theme.setSimulation (output_keyspace, simulation_id).then (
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
                        sql: "select json * from " + output_keyspace + ".simulation where id='" + simulation_id + "'"
                    }
                ).then (
                    function (resultset)
                    {
                        var json = JSON.parse (resultset[0]["[json]"]);
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
            var ret = TransformerChooser.context.items.map (item => item.value).filter (x => "" != x);
            return (ret);
        }

        function query_players ()
        {
            var ret = [];
            PlayerChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = PlayerChoices.filter (x => x.title == item.value);
                        if (0 != selected.length)
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
            var ret = [];
            RecorderChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = RecorderChoices.filter (x => x.title == item.value);
                        if (0 != selected.length)
                            ret.push (JSON.parse (JSON.stringify (selected[0])));
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            return (ret);
        }

        function query_extras ()
        {
            var ret = [];
            ExtraChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = ExtraChoices.filter (x => x.title == item.value);
                        if (0 != selected.length)
                            ret.push (JSON.parse (JSON.stringify (selected[0])));
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            return (ret);
        }

        function formFill ()
        {
            var simulate_template =
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
                            <input  id="simulation_name" type="text" class="form-control"aria-describedby="nameHelp" placeholder="Enter a name for the simulation" value="{{name}}">
                            <small id="nameHelp" class="form-text text-muted">Enter a unique name for the simulation - used as a file name for the details.</small>
                          </div>
                          <div class="col form-group">
                            <label for="simulation_description">Description</label>
                            <input id="simulation_description" type="text" class="form-control"aria-describedby="descriptionHelp" placeholder="Enter a description for the simulation" value="{{description}}">
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
                            <input id="input_keyspace" type="text" class="form-control"aria-describedby="outputKeyspaceHelp" value="cimapplication">
                            <small id="outputKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for input (table <em>measured_value</em>).</small>
                          </div>
                          <div class="col form-group">
                            <label for="output_keyspace">Cassandra output keyspace</label>
                            <input id="output_keyspace" type="text" class="form-control"aria-describedby="outputKeyspaceHelp" value="cimapplication">
                            <small id="outputKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for output (table <em>simulated_value</em> and others).</small>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="simulation_timerange">Time range</label>
                          <input id="simulation_timerange" type="text" class="form-control"aria-describedby="timerangeHelp" placeholder="Enter a time range for the simulation" value="">
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
                            <label for="summarize">Summarize</label>
                              <div class="form-check">
                                <input id="summarize" class="form-check-input" type="checkbox" name="summarize" aria-describedby="summarizeHelp" checked>
                                <small id="summarizeHelp" class="form-text text-muted">Perform summarization (utilization, load & coincidence factor) after simulation.</small>
                              </div>
                          </div>
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
                        <div class="form-group">
                          <button id="do_simulate" name="do_simulate" type="button" class="btn btn-primary">Simulate</button>
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

            var text = mustache.render
            (
                simulate_template,
                {
                    name: getName,
                    description: getDescription
                }
            );
            document.getElementById ("simulate").innerHTML = text;
            document.getElementById ("input_keyspace").onchange = set_input_keyspace;
            document.getElementById ("output_keyspace").onchange = set_output_keyspace;

            // see https://wireddots.com/products/datetimepicker
            var start = new Date (TheSimulation.interval.start);
            var end = new Date (TheSimulation.interval.end);
            var dater = new DateRangePicker (
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
                var help =
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
                var help =
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
                var help =
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
                var help =
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
            document.getElementById ("show_simulation").onclick = do_show;
        }

        function render_prior_simulations (resultset)
        {
            var template =
            `
            {{#simulations}}
              <option value="{{id}}">{{name}}</option>
            {{/simulations}}
            `;
            var text = mustache.render
            (
                template,
                {
                    simulations: resultset.map (row => JSON.parse (row["[json]"]))
                }
            );
            document.getElementById ("simulation_id").innerHTML = text;
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
                        sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' and keyspace_name = 'cimapplication' allow filtering".replace ("cimapplication", output_keyspace)
                    }
                ).then (
                    function (resultset)
                    {
                        if (resultset.length > 0)
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select JSON id, name, description, cim, cimreaderoptions, start_time, end_time, transformers from cimapplication.simulation".replace ("cimapplication", output_keyspace)
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
                cimfiles.fetchPromise ("\\").then (
                    function (response)
                    {
                        if (response.status == "OK")
                        {
                            response.result.files = response.result.files.filter (
                                x =>
                                {
                                    var name = x.path.toLowerCase ();
                                    return (name.endsWith (".rdf") || name.endsWith (".xml"));
                                }
                            );
                            var file_template =
                                `
                                {{#files}}
                                    <option value="{{root}}{{path}}">{{path}}</option>
                                {{/files}}
                                `;
                            var text = mustache.render (file_template, response.result);
                            document.getElementById ("cim_file").innerHTML = text;
                        }
                        else
                            alert (response.message);
                    }
                )
            );
        }

        function setDateRange (start, end, label)
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
                        sql: "select mrid, time from cimapplication.measured_value".replace ("cimapplication", input_keyspace) + " where time < " + start.getTime () + " limit 1 allow filtering"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 == resultset.length)
                            return (start);
                        else
                        {
                            var time = new Date (resultset[0].time);
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select min(time) as lo from cimapplication.measured_value".replace ("cimapplication", input_keyspace) + " where mrid = '" + resultset[0].mrid + "' and time < " + time.getTime () + " allow filtering"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        if ((0 == resultset.length) || (null == resultset[0].lo))
                                            return (time);
                                        else
                                            return (getEarliestDate (new Date (resultset[0].lo)));
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
                        sql: "select mrid, time from cimapplication.measured_value".replace ("cimapplication", input_keyspace) + " where time > " + end.getTime () + " limit 1 allow filtering"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 == resultset.length)
                            return (end);
                        else
                        {
                            var time = new Date (resultset[0].time);
                            return (
                                cimquery.queryPromise (
                                    {
                                        cassandra: true,
                                        sql: "select max(time) as hi from cimapplication.measured_value".replace ("cimapplication", input_keyspace) + " where mrid = '" + resultset[0].mrid + "' and time > " + time.getTime () + " allow filtering"
                                    }
                                ).then (
                                    function (resultset)
                                    {
                                        if ((0 == resultset.length) || (null == resultset[0].hi))
                                            return (time);
                                        else
                                            return (getLatestDate (new Date (resultset[0].hi)));
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
                        sql: "select time from cimapplication.measured_value".replace ("cimapplication", input_keyspace) + " limit 1"
                    }
                ).then (
                    function (resultset)
                    {
                        if (0 == resultset.length)
                            alert ("no data found in cimapplication.measured_value table".replace ("cimapplication", input_keyspace));
                        else
                        {
                            var time = new Date (resultset[0].time);
                            Promise.all ([getEarliestDate (time), getLatestDate (time)]).then (
                                function (minmax)
                                {
                                    var start = minmax[0];
                                    var end = minmax[1];
                                    setDateRange (start, end);
                                    var dater = new DateRangePicker (
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

        /**
         * @summary Render the simulations page.
         * @description Uses mustache to create HTML DOM elements that display the simulation options.
         * @function initialize
         * @memberOf module:cimsimulate
         */
        function initialize ()
        {
            render ();
        }

// placeholders until Export is fixed:
        function getStations (callback)
        {
            callback ([]);
        }

        function getSimulations (station, callback)
        {
            callback ([]);
        }

        return (
            {
                initialize: initialize,
                getStations: getStations,
                getSimulations: getSimulations
            }
        );
    }
)
