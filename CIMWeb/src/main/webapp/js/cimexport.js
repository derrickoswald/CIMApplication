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
                "          <div class='col-sm-2 col-form-label'>All</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='export_option_all' class='form-check-input' type='checkbox' value='' checked>\n" +
                "                Include all options.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-2 col-form-label'>Simulation</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='export_option_sim' class='form-check-input' type='checkbox' value='' disabled>\n" +
                "                Include simulation JSON file.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-2 col-form-label'>CIM</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='export_option_cim' class='form-check-input' type='checkbox' value='' disabled>\n" +
                "                Include CIM source XML file.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-2 col-form-label'>Input</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='export_option_in' class='form-check-input' type='checkbox' value='' disabled>\n" +
                "                Include input player files.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-2 col-form-label'>Output</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='export_option_out' class='form-check-input' type='checkbox' value='' disabled>\n" +
                "                Include output recorder files.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <a id='do_export' href='' class='btn btn-primary'>Export</a>\n" +
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
            document.getElementById ("export").innerHTML = text;
            document.getElementById ("station_directory").onchange = select_station;
            document.getElementById ("simulation_file").onchange = set_do_export_url;
            document.getElementById ("export_option_all").onchange = set_do_export_url;
            document.getElementById ("export_option_sim").onchange = set_do_export_url;
            document.getElementById ("export_option_cim").onchange = set_do_export_url;
            document.getElementById ("export_option_in").onchange = set_do_export_url;
            document.getElementById ("export_option_out").onchange = set_do_export_url;
            set_do_export_url ();
        }

        function stripJson (file)
        {
            return (file.endsWith (".json") ? file.substring (0, file.length - 5) : file);
        }

        function select_station (event)
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

        function set_do_export_url (event)
        {
            var station_directory = document.getElementById ("station_directory").value;
            var simulation_file = document.getElementById ("simulation_file").value;
            var options = "";
            if (document.getElementById ("export_option_all").checked)
            {
                document.getElementById ("export_option_sim").setAttribute ("disabled", "disabled");
                document.getElementById ("export_option_cim").setAttribute ("disabled", "disabled");
                document.getElementById ("export_option_in").setAttribute ("disabled", "disabled");
                document.getElementById ("export_option_out").setAttribute ("disabled", "disabled");
                options = ";all=true";
            }
            else
            {
                document.getElementById ("export_option_sim").removeAttribute ("disabled");
                document.getElementById ("export_option_cim").removeAttribute ("disabled");
                document.getElementById ("export_option_in").removeAttribute ("disabled");
                document.getElementById ("export_option_out").removeAttribute ("disabled");
                if (document.getElementById ("export_option_sim").checked)
                    options = options + ";sim=true";
                if (document.getElementById ("export_option_cim").checked)
                    options = options + ";cim=true";
                if (document.getElementById ("export_option_in").checked)
                    options = options + ";in=true";
                if (document.getElementById ("export_option_out").checked)
                    options = options + ";out=true";
            }
            var simulation = "/" + station_directory + "/" + simulation_file + ".json" + options;
            url = util.home () + "cim/gridlab" + simulation;
            document.getElementById ("do_export").setAttribute ("href", url);
        }

        /**
         * @summary Render the export page.
         * @description Uses mustache to create HTML DOM elements that display the export options.
         * @function initialize
         * @memberOf module:cimexport
         */
        function initialize ()
        {
            document.getElementById ("export").innerHTML = "";
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
                initialize: initialize
            }
        );
    }
)
