/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimquery", "cim", "chooser"],
    /**
     * @summary Functions to simulate using CIM data in memory.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimquery, cim, chooser)
    {
        // The island RDF export.
        var TheRDF;

        // User specified player object queries
        var PlayerChooser;
        var PlayerChoices = [
            {
                title: "Constant power for all EnergyConsumer with PSRType == 'PSRType_HouseService'",
                sql: "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                directory: "/data/", // hard coded random loads
                execute: function (item, callback)
                {
                    cimfiles.fetch (item.directory,
                        function (response)
                        {
                            if (response.status == "FAIL")
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                            else
                            {
                                var root = response.result.root.substring (response.result.filesystem.length); // like hdfs://0eef240033b6:8020/data/ => /data/
                                item.files = response.result.files.map (function (item) { return (root + item.path); });
                                cimquery.query (
                                    item.sql,
                                    function (data)
                                    {
                                        function getRandomInt (min, max)
                                        {
                                            min = Math.ceil (min);
                                            max = Math.floor (max);
                                            return (Math.floor (Math.random () * (max - min)) + min); //The maximum is exclusive and the minimum is inclusive
                                        }
                                        callback (data.map (function (row) { row.player = item.files[getRandomInt (0, item.files.length)]; return (row); }));
                                    }
                                );
                            }
                        }
                   );
               }
            }
        ];

        // User specified recorder object queries
        var RecorderChooser;
        var RecorderChoices = [
            {
                title: "All cable currents",
                sql: "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current_in' property, concat ('output_data/', a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID,  '_current.csv') file from ACLineSegment a",
                execute: function (item, callback)
                {
                    cimquery.query (
                        item.sql,
                        callback);
                }
            },
            {
                title: "All node voltages",
                sql: "select concat (n.IdentifiedObject.mRID, '_voltage_recorder') name, n.IdentifiedObject.mRID parent, 'voltage' property, concat ('output_data/', n.IdentifiedObject.mRID, '_voltage.csv') file from TopologicalNode n",
                execute: function (item, callback)
                {
                    cimquery.query (
                        item.sql,
                        callback);
                }
            }
        ];


        // The parsed CIM data.
        // var CIM_Data;

        // The simulation details.
        // provisional schema:
        // {
        //     name: <the name of the simulation and this JSON file>
        //     island: <topological node of the island to simulate>,
        //     station: <station of the island to simulate>,
        //     cim: <CIM RDF file containing the island>
        //     players: [
        //         {
        //             "name": "HASXXXXX_load",
        //             "node": "HASXXXXX_fuse_topo",
        //             "player": "/data/HASXXXXX.csv
        //         },
        //         ...
        //     ]
        //
        //
        //
        // }
        var TheSimulation;

        // accessors
        function getName ()
        {
            return ((null != TheSimulation) ? TheSimulation.name : "");
        }
        function getIsland ()
        {
            return ((null != TheSimulation) ? TheSimulation.island : "");
        }
        function getStation ()
        {
            return ((null != TheSimulation) ? TheSimulation.station : "");
        }
        function getCIM ()
        {
            return ((null != TheSimulation) ? TheSimulation.cim : "");
        }

        function do_simulate ()
        {
            if (null != TheSimulation)
            {
                var name = "/" + getStation () +"/" + encodeURIComponent (getName ()) + ".json";
                var simulation = JSON.stringify (TheSimulation, null, 4);
                function callback (response)
                {
                    if (response.status == "OK")
                        alert ("simulating...")
                    else
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }
                cimfiles.put (name, simulation, callback)
            }
        }

        function htmlify (str)
        {
            return (str.replace (/</g, "&lt;").replace (/>/g, "&gt;"))
        }

        function jsonify (data)
        {
            return (JSON.stringify (data, null, 4))
        }

//        function parse_rdf (data)
//        {
//            var blob;
//
//            function callback (result) // {context: context, parsed: parsed}
//            {
//                CIM_Data = result.parsed;
//            }
//
//            blob = new Blob ([data], {type : 'application/xml'});
//            cim.read_xml_blob (blob, callback)
//        }


        /**
         * @summary Read the file contents in Spark.
         * @description Trigger CIMReader to read in the file.
         * @param {string} path - the file to load
         * @param {function} callback - the function accepting the load JSON with signature: function (response)
         * @function do_load
         * @memberOf module:cimsimulate
         */
        function do_load (path, callback)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/load" + path;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                            callback (resp);
                        else
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        function query_players ()
        {
            // reset the list
            TheSimulation.players = [];
            document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";

            var queries = [];
            PlayerChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = PlayerChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            queries = queries.concat (selected);
                        else
                            TheSimulation.players.push (JSON.parse (item.value)); // assume raw JSON
                    }
                }
            );
            var items = queries.length;
            queries.forEach (
                function (item)
                {
                    item.execute (item,
                        function (list)
                        {
                            TheSimulation.players = TheSimulation.players.concat (list);
                            items = items - 1;
                            if (0 == items)
                                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";
                        }
                    );
                }
            );
        }

        function query_recorders ()
        {
            // reset the list
            TheSimulation.recorders = [];
            document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";

            var queries = [];
            RecorderChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = RecorderChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            queries = queries.concat (selected);
                        else
                            TheSimulation.recorders.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            var items = queries.length;
            queries.forEach (
                function (item)
                {
                    item.execute (item,
                        function (list)
                        {
                            TheSimulation.recorders = TheSimulation.recorders.concat (list);
                            items = items - 1;
                            if (0 == items)
                                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";
                        }
                    );
                }
            );
        }

        function do_refresh ()
        {
            TheSimulation.name = document.getElementById ("simulation_name").value;
            query_players ();
            query_recorders ();
        }

        /**
         * Set up the simulation data
         * @param {string} name - the name of the simulation.
         * @param {string} island - the name of the Topological island for the simulation.
         * @param {string} station - the name of the station for the simulation.
         * @param {string} cim - the name of the CIM file for the simulation.
         */
        function setup (name, island, station, cim)
        {
            TheSimulation =
            {
                 name: name,
                 island: island,
                 station: station,
                 cim: cim
            };
            // update the name
            document.getElementById ("title").innerHTML = name;
            function callback (result)
            {
                // not sure if we need this
                // parse_rdf (TheRDF);

                // since the island is loaded, reset the select_island dropdown
                document.getElementById ("simulation_island").innerHTML = "<option value='" + station + "/" + island + "' selected>" + station + " (" + island + ")</option>";
            }
            do_load (cim, callback)
        }

        /**
         * @summary Call the export RESTful service.
         * @description Invokes the server side export function.
         * @param {string} island - the island name from the topology
         * @param {string} cim - the name of the cim file
         * @param {function} callback - the function to call back with signature function (data)
         * @function exportIsland
         * @memberOf module:cimsimulate
         */
        function exportIsland (island, cim, callback)
        {
            var url;
            var xmlhttp;

            url = util.home () + "cim/export/" + island;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        function callback2 (response)
                        {
                            if (response.status == "OK")
                                callback (xmlhttp.responseText);
                            else
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                        }

                        cimfiles.put (cim, xmlhttp.responseText, callback2);
                    }
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Event handler for island change.
         * @description Invokes the server side export function or gets the existing island RDF.
         * @param {object} event - the event that triggered the change <em>not used</em>.
         * @function select_island
         * @memberOf module:cimsimulate
         */
        function select_island (event)
        {
            var name;
            var selection;
            var island;
            var station;
            var directory;
            var rdf;
            var cim;

            selection = document.getElementById ("simulation_island").value;
            island = selection.substring (0, selection.indexOf ("/"));
            station = selection.substring (selection.indexOf ("/") + 1);
            name = document.getElementById ("simulation_name").value;
            if ("" == name)
                name = island;
            if (("undefined" != typeof (island)) && ("" != island))
            {
                // check if the rdf exists already
                directory = "/" + station + "/" + island + "/";
                rdf = island + ".rdf";
                cim = directory + rdf;

                function callback (data)
                {
                    TheRDF = data;
                    setup (name, island, station, cim);
                }

                function error (response)
                {
                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }

                cimfiles.fetch (directory,
                    function (response)
                    {
                        if ((response.status == "FAIL") || (0 == response.result.files.filter (function (file) { return (file.path == rdf); })))
                            exportIsland (island, cim, callback);
                        else
                            cimfiles.get (cim, callback, error);
                    }
                );
            }
        }

        function render (data)
        {
            var simulate_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Simulate using GridLAB-D</h1>\n" +
                "      <h2 id='title'>{{name}}</h2>\n" +
                "      <form>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_name'>Name</label>\n" +
                "          <input  id='simulation_name' type='text' class='form-control'aria-describedby='nameHelp' placeholder='Enter a name for the simulation' value='{{name}}'>\n" +
                "          <small id='nameHelp' class='form-text text-muted'>Enter a unique name for the simulation - used as a file name for the details.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_island'>Island</label>\n" +
                "          <select id='simulation_island' class='form-control' name='island'>\n" +
                "{{#data}}\n" +
                "            <option value='{{island}}/{{station}}'{{is_selected}}>{{station}} ({{island}})</option>\n" +
                "{{/data}}\n" +
                "          </select>\n" +
                "          <small id='islandHelp' class='form-text text-muted'>Choose the topoogical island (Trafokreis) - usually associated with a transformer secondary terminal.</small>\n" +
                "        </div>\n" +
                "        <div id='players' class='form-group'>\n" +
                "        </div>\n" +
                "        <div id='recorders' class='form-group'>\n" +
                "        </div>\n" +
                "        <button id='do_refresh' name='do_refresh' type='button' class='btn btn-primary'>Refresh</button>\n" +
                "        <button id='do_simulate' name='do_simulate' type='button' class='btn btn-primary'>Simulate</button>\n" +
                "      </form>\n" +
                "      <div id='cim'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            var text = mustache.render
            (
                simulate_template,
                {
                    name: getName,
                    is_selected: function () { return ((this.mRID == getIsland ()) ? " selected" : ""); },
                    data: data
                }
            );
            document.getElementById ("main").innerHTML = text;
            document.getElementById ("simulation_island").onchange = select_island;
            document.getElementById ("do_refresh").onclick = do_refresh;
            document.getElementById ("do_simulate").onclick = do_simulate;
            if (null == PlayerChooser)
            {
                var help =
                    "<span id='players_help' class='help-block'>" +
                        "The queries to use to pick player (load) elements." +
                    "</span>";
                PlayerChooser = new chooser.Chooser ("players", "Players", "Player", PlayerChoices.map (function (x) { return (x.title); }), help);
            }
            PlayerChooser.render ();
            if (null == RecorderChooser)
            {
                var help =
                    "<span id='recorders_help' class='help-block'>" +
                        "The queries to use to pick recorder elements." +
                    "</span>";
                RecorderChooser = new chooser.Chooser ("recorders", "Recorders", "Recorder", RecorderChoices.map (function (x) { return (x.title); }), help);
            }
            RecorderChooser.render ();
            if (null != TheSimulation)
                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>"
        }

        function query_islands ()
        {
            // query for topological islands
            cimquery.query (
                // simple TopologicalIsland query
                // "select i.IdentifiedObject.mRID from TopologicalIsland i",
                // ToDo: this query assumes transformers are in a Bay which is directly in a Substation
                // "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Substation s, Bay b where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID and b.Substation = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID",
                // ToDo: this query assumes transformers are directly in a Substation
                "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Substation s        where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID",
                render
            );
        }

        function getStations (callback)
        {
            // get the list of stations
            cimquery.query (
                "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from Substation s",
                function (data)
                {
                    var rdfstations = data.map (function (obj) { return (obj.station); });
                    // list files and try to match
                    var directory = "/"
                    cimfiles.fetch (directory,
                        function (response)
                        {
                            if (response.status == "FAIL")
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                            else
                            {
                                var stations = response.result.files.filter (function (file) { return (file.is_directory && (-1 != rdfstations.indexOf (file.path))); }).map (function (file) { return (file.path); });
                                callback (stations);
                            }
                        }
                    );
                }
            );
        }

        function getSimulations (station, callback)
        {
            // choose the first one for now
            var directory = "/" + station + "/";
            cimfiles.fetch (directory,
                function (response)
                {
                    if (response.status == "FAIL")
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                    else
                    {
                        var simulations = response.result.files.filter (function (file) { return (!file.is_directory && file.path.endsWith (".json")); } ).map (function (file) { return (file.path); });
                        callback (simulations);
                    }
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
            document.getElementById ("main").innerHTML = "";
            if (null == TheSimulation)
                getStations (function (stations) {
                    if (0 == stations.length)
                        query_islands ();
                    else
                        getSimulations (stations[0], function (simulations) {
                            // choose the first one for now
                            if (0 < simulations.length)
                            {
                                var simulation = "/" + stations[0] +  "/" + simulations[0];
                                // get the simulation json
                                cimfiles.get (simulation,
                                    function (data)
                                    {
                                        TheSimulation = JSON.parse (data);
                                        render ([ { island:  getIsland (), station: getStation () } ]);
                                    },
                                    function (response) { alert (JSON.stringify (response, null, 4)); }
                                );
                            }
                            else
                                query_islands ();
                        });
                });
            else+
                render ([ { island:  getIsland (), station: getStation () } ]);
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
