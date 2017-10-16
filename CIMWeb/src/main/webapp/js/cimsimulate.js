/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimquery", "cim"],
    /**
     * @summary Functions to simulate using CIM data in memory.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimquery, cim)
    {
        // The island being worked on.
        var TheIsland;

        // The island RDF export.
        var TheRDF;

        // The parsed CIM data.
        var CIM_Data;

        function do_simulate ()
        {
            var island = document.getElementById ("simulation_island").value;
            if (("undefined" != typeof (island)) && ("" != island))
                alert (island);
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
                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (CIM_Data) + "\n</pre>"
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

        function setup ()
        {
            function callback (result)
            {
                alert (JSON.stringify (result, null, 4));
                // since the island is loaded, reset the select_island dropdown
                document.getElementById ("simulation_island").innerHTML = "<option value='" + TheIsland + "' selected>" + TheIsland + "</option>";
            }
            do_load ("/simulate/" + TheIsland + "/" + TheIsland + ".rdf", callback)
            parse_rdf (TheRDF);
        }

        /**
         * @summary Call the export RESTful service.
         * @description Invokes the server side export function.
         * @param {string} island - the island name from the topology.
         * @function exportIsland
         * @memberOf module:cimsimulate
         */
        function exportIsland (island)
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
                        function callback (response)
                        {
                            if (response.status == "OK")
                            {
                                TheIsland = island;
                                document.getElementById ("title").innerHTML = TheIsland;
                                TheRDF = xmlhttp.responseText;
                                setup ();
                            }
                            else
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                        }

                        cimfiles.put ("/simulate/" + island + "/" + island + ".rdf", xmlhttp.responseText, callback);
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
            var island;
            var directory;
            var rdf;

            island = document.getElementById ("simulation_island").value;
            if (("undefined" != typeof (island)) && ("" != island))
            {
                function callback (data)
                {
                    TheIsland = island;
                    document.getElementById ("title").innerHTML = TheIsland;
                    TheRDF = data;
                    setup ();
                }

                function error (response)
                {
                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }

                // check if the rdf exists already
                directory = "/simulate/" + island + "/";
                rdf = island + ".rdf";
                cimfiles.fetch (directory,
                    function (response)
                    {
                        if ((response.status == "FAIL") || (0 == response.result.files.filter (function (file) { return (file.path == rdf); })))
                            exportIsland (island);
                        else
                            cimfiles.get (directory + rdf, callback, error);
                    }
                );
            }
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
                "            <option value='{{mRID}}'{{is_selected}}>{{mRID}}</option>\n" +
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

            cimquery.query ("select i.IdentifiedObject.mRID from TopologicalIsland i",
                function (data)
                {
                    var text = mustache.render
                    (
                        simulate_template,
                        {
                            is_selected: function () { return ((this.mRID == TheIsland) ? " selected" : ""); },
                            data: data
                        }
                    );
                    document.getElementById ("main").innerHTML = text;
                    document.getElementById ("simulation_island").onchange = select_island;
                    document.getElementById ("do_simulate").onclick = do_simulate;
                    document.getElementById ("title").innerHTML = (null != TheIsland) ? TheIsland : "";
                    if (null != CIM_Data)
                        document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (CIM_Data) + "\n</pre>"
                }
            );
        }

        return (
            {
                initialize: initialize
            }
        );
    }
)
