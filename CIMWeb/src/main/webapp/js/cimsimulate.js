/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimmap", "cimquery", "cim", "chooser", "themes/simulation_theme"],
    /**
     * @summary Functions to simulate using CIM files and measurement time series in Cassandra.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimmap, cimquery, cim, Chooser, SimulationTheme)
    {
        // The simulation details.
        var TheSimulation =
            {
                name: "Sample",
                description: "sample simulation",
                cim: "hdfs://sandbox:8020/NIS_CIM_Export_SAK_sias_current_20171023_fake-Neplan-library_fake-Trafo_with_topology.rdf",
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
                players: [],
                recorders: [],
                transformers: []
            };
        // provisional schema:
        //    {
        //        "name": <the name of the simulation and this JSON file>,
        //        "description": <textual description suitable for GUI display>,
        //        "cim": <CIM RDF file, e.g. hdfs://sandbox:8020/NIS_CIM_Export_SAK_sias_current_20171023_fake-Neplan-library_fake-Trafo_with_topology.rdf>,
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
        //        "transformers": [
        //            "TRA2755"
        //        ],
        //        "players": [
        //            {
        //                "title": "house services",
        //                "query": "select c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid, 'energy' type, concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'energy' type, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t, TopologicalNode n where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and t.TopologicalNode = n.IdentifiedObject.mRID and n.TopologicalIsland = '%s'",
        //                "cassandraquery": "select cimapplication.subtract_offset (time, interval) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value_by_day where mrid='%s' and type='%s'",
        //                "bind": [
        //                    "mrid",
        //                    "type"
        //                ]
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
                        'energy' type,
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
                "cassandraquery": "select cimapplication.subtract_offset (time, interval) as time, cimapplication.multiply (real_a, 4.0) as real, cimapplication.multiply (imag_a, 4.0) as imag from cimapplication.measured_value_by_day where mrid='%s' and type='%s'",
                "bind": [
                    "mrid",
                    "type"
                ]

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
                        n.IdentifiedObject.mRID parent,
                        'voltage' type,
                        'voltage' property,
                        'Volts' unit,
                        n.TopologicalIsland island
                    from
                        TopologicalNode n
                    `,
                "interval": 900,
                "aggregations": [
                    {
                        "intervals": 1,
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
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
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'power' type,
                        'power_out' property,
                        'Volt-Amperes' unit,
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
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
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
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
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
                        p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'energy' type,
                        'power_losses' property,
                        'Volt-Amperes' unit,
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
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
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
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
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
                        concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_losses_recorder') name,
                        a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
                        'energy' type,
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
                        "ttl": 1800
                    },
                    {
                        "intervals": 4,
                        "ttl": 3600
                    },
                    {
                        "intervals": 12,
                        "ttl": 7200
                    },
                    {
                        "intervals": 96,
                        "ttl": null
                    }
                ]
            }
        ];

        // accessors
        function getName ()
        {
            return ((null != TheSimulation) ? TheSimulation.name : "");
        }
        function getDescription ()
        {
            return ((null != TheSimulation) ? TheSimulation.description : "");
        }

        /**
         * @summary Run the simulation using GridLAB-D to populate simmulated_value_by_day Cassandra table.
         * @description Execute gridlabd for the simulation parameters provided by the user.
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
            TheSimulation.players = query_players ();
            TheSimulation.recorders = query_recorders ();
            document.getElementById ("results").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";
            // flip to the map while simulating
            if (document.getElementById ("to_map").value)
                window.location.hash = "map";

            var url;
            var xmlhttp;

            url = util.home () + "cim/estimation;verbose=true;keep=true";
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
                            if (document.getElementById ("to_map").value)
                            {
                                var theme = new SimulationTheme ();
                                cimmap.get_themer ().removeTheme (theme);
                                theme = cimmap.get_themer ().addTheme (theme);
                                theme.setSimulation (simulation_id);
                            }
                            else
                            {
                                cimquery.query (
                                    "select json * from cimapplication.simulation where id='" + simulation_id + "'",
                                    true,
                                    "",
                                    "",
                                    function (data)
                                    {
                                        var json = JSON.parse (data[0]["[json]"]);
                                        document.getElementById ("results").innerHTML = "<pre>\n" +  jsonify (json) + "\n</pre>";
                                    }
                                );
                            }
                        }
                        else
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };

            xmlhttp.send (JSON.stringify (TheSimulation, null, 4));
        }

        function jsonify (data)
        {
            return (JSON.stringify (data, null, 4))
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
                        var selected = PlayerChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            ret.push (selected[0]);
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
                        var selected = RecorderChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            ret.push (selected[0]);
                        else
                            ret.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            return (ret);
        }

        function render ()
        {
            var simulate_template =
                `
                <div class="container">
                  <div class="row justify-content-center">
                    <div class="col-12">
                      <h1>Simulate using GridLAB-D</h1>
                      <h2 id="title">{{name}}</h2>
                      <form>
                        <div class="form-group">
                          <label for="simulation_name">Name</label>
                          <input  id="simulation_name" type="text" class="form-control"aria-describedby="nameHelp" placeholder="Enter a name for the simulation" value="{{name}}">
                          <small id="nameHelp" class="form-text text-muted">Enter a unique name for the simulation - used as a file name for the details.</small>
                        </div>
                        <div class="form-group">
                          <label for="simulation_description">Description</label>
                          <input id="simulation_description" type="text" class="form-control"aria-describedby="descriptionHelp" placeholder="Enter a description for the simulation" value="{{description}}">
                          <small id="descriptionHelp" class="form-text text-muted">Enter a user facing description for the simulation - used for drop down choice title.</small>
                        </div>
                        <div class="form-group">
                          <label for="simulation_timerange">Time range</label>
                          <input id="simulation_timerange" type="text" class="form-control"aria-describedby="timerangeHelp" placeholder="Enter a time range for the simulation" value="{{description}}">
                          <small id="timerangeHelp" class="form-text text-muted">Enter the simulation start and end date/time.</small>
                        </div>
                        <div id="players" class="form-group">
                        </div>
                        <div id="recorders" class="form-group">
                        </div>
                        <div class="form-group">
                          <label for="to_map">View on map</label>
                            <div class='form-check'>
                              <input id="to_map" class="form-check-input" type="checkbox" name="to_map" aria-describedby="toMapHelp" checked>
                              <small id="toMapHelp" class="form-text text-muted">Add a theme to the map tab for simulation results.</small>
                            </div>
                        </div>
                        <button id="do_simulate" name="do_simulate" type="button" class="btn btn-primary">Simulate</button>
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
            document.getElementById ("do_simulate").onclick = do_simulate;
            // see https://wireddots.com/products/datetimepicker
            var start = new Date (TheSimulation.interval.start);
            var end = new Date (TheSimulation.interval.end);
            $('#simulation_timerange').daterangepicker (
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
        }

        function setDateRange (start, end, label)
        {
            TheSimulation.interval =
            {
                start: start.toISOString ().replace ("Z", "+0000"), // "2018-04-24T19:24:27.884Z"
                end: end.toISOString ().replace ("Z", "+0000")
            }
        }

        function getDateRange ()
        {
//            var sql = "select distimct mrid, type, date from cimapplication.measured_value_by_day limit 20";
//            var nex = "select distinct mrid, type, date from cimapplication.measured_value_by_day where date<'2017-07-17' allow filtering";
//            var lim = "select min(time) as low, max(time) as high from cimapplication.measured_value_by_day";
//            val low = "select * from cimapplication.measured_value_by_day where time='2017-07-17T23:00:00.00' allow filtering";
            cimquery.queryPromise (
                {
                    cassandra: true,
                    sql: "select min(time) as low, max(time) as high from cimapplication.measured_value_by_day"
                }
            ).then (
                function (resultset)
                {
                    var start = new Date (resultset.result[0].low);
                    var end = new Date (resultset.result[0].high);
                    $('#simulation_timerange').daterangepicker (
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
//                    $('#simulation_timerange').data('daterangepicker').setEndDate (end);
//                    $('#simulation_timerange').data('daterangepicker').setStartDate (start);
                }
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
            document.getElementById ("simulate").innerHTML = "";
            render ();
            getDateRange ();
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
