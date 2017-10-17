/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimquery", "cim", "cimexport"],
    /**
     * @summary Functions to simulate using CIM data in memory.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimquery, cim, cimexport)
    {
        // The island RDF export.
        var TheRDF;

        // The parsed CIM data.
        var CIM_Data;

        // The simulation details.
        // provisional schema:
        // {
        //     island: <topological node of the island to simulate>,
        //     station: <station of the island to simulate>,
        //     cim: <CIM RDF file containing the island>
        //     loads: [
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
                var simulation = JSON.stringify (TheSimulation, null, 4);
                function callback (response)
                {
                    if (response.status == "OK")
                        cimexport.exportIsland ("/" + getStation () +"/" + getIsland () + "/simulation.json", function (data) { document.getElementById ("cim").innerHTML = "<pre>\n" +  data + "</pre>"; } )
                    else
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }
                cimfiles.put ("/" + getStation () + "/" + getIsland () + "/simulation.json", simulation, callback)
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

        function parse_rdf (data)
        {
            var blob;

            function callback (result) // {context: context, parsed: parsed}
            {
                CIM_Data = result.parsed;
            }

            blob = new Blob ([data], {type : 'application/xml'});
            cim.read_xml_blob (blob, callback)
        }


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

        function query_loads ()
        {
            directory = "/data/";

            cimfiles.fetch (directory,
                function (response)
                {
                    if (response.status == "FAIL")
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                    else
                    {
                        var root = response.result.root.substring (response.result.filesystem.length); // like hdfs://0eef240033b6:8020/data/ => /data/
                        var randomfiles = response.result.files.map (function (item) { return (root + item.path); });
                        cimquery.query (
                            // ToDo: this query assumes transformers are in a Bay which is directly in a Substation
                            "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode node from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                            function (data)
                            {
                                function getRandomInt (min, max)
                                {
                                    min = Math.ceil (min);
                                    max = Math.floor (max);
                                    return (Math.floor (Math.random () * (max - min)) + min); //The maximum is exclusive and the minimum is inclusive
                                }
                                TheSimulation.loads = data.map (function (item) { item.player = randomfiles[getRandomInt (0, randomfiles.length)]; return (item); })
                                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>"
                            }
                        );
                    }
                }
            );
        }

        /**
         * Set up the simulation data
         * @param {string} island - the name of the Topological island for the simulation.
         * @param {string} station - the name of the station for the simulation.
         * @param {string} cim - the name of the CIM file for the simulation.
         */
        function setup (island, station, cim)
        {
            TheSimulation =
            {
                 island: island,
                 station: station,
                 cim: cim
            };
            document.getElementById ("title").innerHTML = getIsland ();
            function callback (result)
            {
                // not sure if we need this
                parse_rdf (TheRDF);

                // since the island is loaded, reset the select_island dropdown
                document.getElementById ("simulation_island").innerHTML = "<option value='" + station + "/" + island + "' selected>" + station + " (" + island + ")</option>";
                // hard coded random loads
                query_loads ();
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
            var selection;
            var island;
            var station;
            var directory;
            var rdf;
            var cim;

            selection = document.getElementById ("simulation_island").value;
            island = selection.substring (0, selection.indexOf ("/"));
            station = selection.substring (selection.indexOf ("/") + 1);
            if (("undefined" != typeof (island)) && ("" != island))
            {
                // check if the rdf exists already
                directory = "/" + station + "/" + island + "/";
                rdf = island + ".rdf";
                cim = directory + rdf;

                function callback (data)
                {
                    TheRDF = data;
                    setup (island, station, cim);
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
                "      <h2 id='title'></h2>\n" +
                "      <form>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_island'>Island</label>\n" +
                "          <select id='simulation_island' class='form-control' name='island'>\n" +
                "{{#data}}\n" +
                "            <option value='{{island}}/{{station}}'{{is_selected}}>{{station}} ({{island}})</option>\n" +
                "{{/data}}\n" +
                "          </select>\n" +
                "        </div>\n" +
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
                    is_selected: function () { return ((this.mRID == getIsland ()) ? " selected" : ""); },
                    data: data
                }
            );
            document.getElementById ("main").innerHTML = text;
            document.getElementById ("simulation_island").onchange = select_island;
            document.getElementById ("do_simulate").onclick = do_simulate;
            document.getElementById ("title").innerHTML = getIsland ();
            if (null != TheSimulation)
                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>"
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
            {
                // get the list of stations
                cimquery.query (
                    "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from Substation s",
                    function (data)
                    {
                        var stations = data.map (function (obj) { return (obj.station); });
                        // list files and try to match
                        var directory = "/"
                        cimfiles.fetch (directory,
                            function (response)
                            {
                                if (response.status == "FAIL")
                                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                                else
                                {
                                    var have = response.result.files.filter (function (file) { return (-1 != stations.indexOf (file.path)); });
                                    if (0 == have.length)
                                        // query for topological islands
                                        cimquery.query (
                                            "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Bay b, Substation s where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID and b.Substation = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID", // "select i.IdentifiedObject.mRID from TopologicalIsland i",
                                            render
                                        );
                                    else
                                    {
                                        // choose the first one for now
                                        directory = directory + have[0].path + "/";
                                        cimfiles.fetch (directory,
                                            function (response)
                                            {
                                                if (response.status == "FAIL")
                                                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                                                else
                                                {
                                                    // choose the first one for now
                                                    if (0 < response.result.files.length)
                                                    {
                                                        directory = directory + response.result.files[0].path + "/";
                                                        // get the simulation json
                                                        cimfiles.get (directory + "simulation.json",
                                                            function (data)
                                                            {
                                                                TheSimulation = JSON.parse (data);
                                                                render ([ { island:  getIsland (), station: getStation () } ]);
                                                            },
                                                            function (response) { alert (JSON.stringify (response, null, 4)); }
                                                        );
                                                    }
                                                    else
                                                        // query for topological islands
                                                        cimquery.query (
                                                            "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Bay b, Substation s where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID and b.Substation = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID", // "select i.IdentifiedObject.mRID from TopologicalIsland i",
                                                            render
                                                        );
                                                }
                                            }
                                        );
                                    }
                                }
                            }
                        );
                    }
                );
            }
            else
                render ([ { island:  getIsland (), station: getStation () } ]);
        }

        return (
            {
                initialize: initialize
            }
        );
    }
)
