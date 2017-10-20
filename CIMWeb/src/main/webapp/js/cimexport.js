/**
 * @fileOverview Export data.
 * @name cimexport
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cimquery", "cimsimulate"],
    /**
     * @summary Functions to export CIM data in memory.
     * @name cimexport
     * @exports cimexport
     * @version 1.0
     */
    function (util, mustache, cimquery, cimsimulate)
    {
        // Data about existing simulations schema { stations: [string], simulations: [string] }
        var TheChoices;

        /**
         * @summary Call the gridlab RESTful service.
         * @description Invokes the server side gridlab function.
         * @param {string} simulation - the simulation file name.
         * @function exportSimulation
         * @memberOf module:cimexport
         */
        function exportSimulation (simulation, callback)
        {
            var url;
            var xmlhttp;

            simulation = simulation.startsWith ("/") ? simulation : "/" + simulation;
            url = util.home () + "cim/gridlab" + simulation;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        callback (xmlhttp.responseText);
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Execute an export.
         * @description Gets the user's input and generates the .glm for the simulation.
         * @function do_export
         * @memberOf module:cimexport
         */
        function do_export ()
        {
            var station_directory = document.getElementById ("station_directory").value;
            if (("undefined" != typeof (station_directory)) && ("" != station_directory))
            {
                var simulation_file = document.getElementById ("simulation_file").value;
                if (("undefined" != typeof (simulation_file)) && ("" != simulation_file))
                {
                    var simulation = "/" + station_directory + "/" + simulation_file + ".json";
                    exportSimulation (simulation, function (data) { document.getElementById ("glm").innerHTML = "<pre>\n" +  data + "</pre>"; } );
                }
            }
        }

        function render ()
        {
            var export_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Export GridLAB-D .glm</h1>\n" +
                "      <form>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='station_directory'>Station</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <select id='station_directory' class='form-control' name='station_directory'>\n" +
                "{{#stations}}\n" +
                "              <option value='{{.}}'>{{.}}</option>\n" +
                "{{/stations}}\n" +
                "            </select>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='simulation_file'>Simulation</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <select id='simulation_file' class='form-control' name='simulation_file'>\n" +
                "{{#simulations}}\n" +
                "              <option value='{{.}}'>{{.}}</option>\n" +
                "{{/simulations}}\n" +
                "            </select>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <button id='do_export' type='button' class='btn btn-primary'>Export</button>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "      <div id='glm'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";
            var text = mustache.render
            (
                export_template,
                TheChoices
            );
            document.getElementById ("main").innerHTML = text;
            document.getElementById ("station_directory").onchange = select_station;
            document.getElementById ("do_export").onclick = do_export;
        }

        function stripJson (file)
        {
            return (file.endsWith (".json") ? file.substring (0, file.length - 5) : file);
        }

        function select_station ()
        {
            var station = document.getElementById ("station_directory").value;
            if (("undefined" != typeof (station)) && ("" != station))
                cimsimulate.getSimulations (station,
                    function (simulations)
                    {
                        TheChoices.simulations = simulations.map (stripJson);
                        render ();
                    }
                );
        }

        /**
         * @summary Render the export page.
         * @description Uses mustache to create HTML DOM elements that display the export options.
         * @function initialize
         * @memberOf module:cimexport
         */
        function initialize ()
        {
            document.getElementById ("main").innerHTML = "";
            cimsimulate.getStations (
                function (stations)
                {
                    TheChoices = { stations: stations, simulations: [] };
                    render ();
                    select_station ();
                }
            );
        }

        return (
            {
                initialize: initialize,
                exportSimulation: exportSimulation
            }
        );
    }
)
