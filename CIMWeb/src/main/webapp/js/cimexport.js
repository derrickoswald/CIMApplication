/**
 * @fileOverview Export data.
 * @name cimexport
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cimquery", "cimsimulate", "cim", "cimmap"],
    /**
     * @summary Functions to export CIM data in memory.
     * @name cimexport
     * @exports cimexport
     * @version 1.0
     */
    function (util, mustache, cimquery, cimsimulate, cim, cimmap)
    {
        // Data about existing simulations schema { stations: [string], simulations: [string] }
        var TheChoices;

        // the pending xml creation
        var Pending = null;

        // the base name of the currently loaded file
        var TheCurrentName = null;

        // the rdf:about text for saving
        var TheCurrentAbout = null;

        // the md:description text for saving
        var TheCurrentDescription = null;

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
                "            <a id='do_glm_export' href='' class='btn btn-primary'>Export</a>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Export CIM .rdf</h1>\n" +
                "      <form>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='save_name'>File name</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='save_name' class='form-control' type='text' name='save_name' placeholder='file name for .rdf and .zip, default \"save\"'>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='rdf_about'>About</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='rdf_about' class='form-control' type='text' name='rdf_about' placeholder='rdf:about contents'>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='md_description'>Description</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='md_description' class='form-control' type='text' name='md_description' placeholder='md:description contents'>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='difference_model'>Difference model</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='difference_model' class='form-check-input' type='checkbox' name='difference_model' style='margin-right: 20px;'>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <a id='do_rdf_export' href='' class='btn btn-primary' disabled>Export</a>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </form>\n" +
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
            document.getElementById ("save_name").onchange = save_name_change;
            document.getElementById ("rdf_about").onchange = about_change;
            document.getElementById ("md_description").onchange = description_change;
            document.getElementById ("difference_model").onchange = difference_model_change;
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
            document.getElementById ("do_glm_export").setAttribute ("href", url);
        }

        /**
         * @summary Blob to base64 conversion.
         * @description Convert the blob into base64 characters.
         * @param {Blob} blob - the blob of data
         * @param {Function} callback - the callback to recieve the converted data: signature function (base64)
         * @function blob2base64
         * @memberOf module:cimexport
         */
        function blob2base64 (blob, callback)
        {
            var reader = new FileReader ();
            reader.onload = function ()
            {
                var dataUrl = reader.result;
                var base64 = dataUrl.split (",")[1];
                callback (base64);
            };
            reader.readAsDataURL (blob);
        }

        /**
         * @summary Event handler for changing the Save As file name.
         * @description Attached to the input field for file name, sets the download attribute of the Save link.
         * @param {object} event - the change event - <em>not used</em>
         * @function save_name_change
         * @memberOf module:cimexport
         */
        function save_name_change (event)
        {
            var name = document.getElementById ("save_name").value;
            if (name.toLowerCase ().endsWith (".zip"))
                name = name.substring (0, name.length () - 4);
            if (name.toLowerCase ().endsWith (".rdf"))
                name = name.substring (0, name.length () - 4);
            TheCurrentName = name;
            var a = document.getElementById ("do_rdf_export");
            a.setAttribute ("download", name + ".zip");
            if (null == Pending)
                generate_rdf ();
        }

        /**
         * @summary Event handler for changing the rdf:about text.
         * @description Attached to the about input field.
         * @param {object} event - the change event - <em>not used</em>
         * @function about_change
         * @memberOf module:cimexport
         */
        function about_change (event)
        {
            TheCurrentAbout = document.getElementById ("rdf_about").value;
            if (null == Pending)
                generate_rdf ();
            else
                Pending.then (generate_rdf, generate_rdf);
        }

        /**
         * @summary Event handler for changing changing the md:description text.
         * @description Attached to the description input field.
         * @param {object} event - the change event - <em>not used</em>
         * @function description_change
         * @memberOf module:cimexport
         */
        function description_change (event)
        {
            TheCurrentDescription = document.getElementById ("md_description").value;
            if (null == Pending)
                generate_rdf ();
            else
                Pending.then (generate_rdf, generate_rdf);
        }

        /**
         * @summary Event handler for changing to or from a difference model.
         * @description Attached to the difference_model checkbox.
         * @param {object} event - the change event - <em>not used</em>
         * @function difference_model_change
         * @memberOf module:cimexport
         */
        function difference_model_change (event)
        {
            if (null == Pending)
                generate_rdf ();
            else
                Pending.then (generate_rdf, generate_rdf);
        }

        /**
         * @summary Event handler for Save.
         * @description Attached to the Save menu item, performs the CIM export and zipping.
         * @param {object} event - the click event - <em>not used</em>
         * @function generate_rdf
         * @memberOf module:cimexport
         */
        function generate_rdf (event)
        {
            var name = TheCurrentName || "save";
            var about = TheCurrentAbout || "";
            var description = TheCurrentDescription || "";
            if (null == cimmap.get_data ())
                Pending = Promise.resolve ("no data");
            else
                Pending =
                    new Promise (
                        function (resolve, reject)
                        {
                            // disable the link until it's ready
                            var a = document.getElementById ("do_rdf_export");
                            var difference_model = document.getElementById ("difference_model").checked;
                            a.setAttribute ("disabled", "disabled");
                            var file = name + (difference_model ? "_diff" : "") + ".zip"
                            a.setAttribute ("download", file);
                            document.getElementById ("save_name").value = file;
                            a.onclick = function (event) { event.preventDefault (); event.stopPropagation (); alert ("sorry... not ready yet"); }
                            var begin = new Date ().getTime ();
                            console.log ("starting xml creation");
                            var text = cim.write_xml (cimmap.get_data ().Element, difference_model, about, description);
                            var start = new Date ().getTime ();
                            console.log ("finished xml creation (" + (Math.round (start - begin) / 1000) + " seconds)");
                            console.log ("starting zip");
                            require (
                                ["zip/zip", "zip/mime-types"],
                                function (zip, mimeTypes)
                                {
                                    //zip.workerScriptsPath = "js/zip/";
                                    zip.useWebWorkers = false;
                                    zip.createWriter (new zip.BlobWriter (),
                                        function (writer)
                                        {
                                            writer.add (name + ".rdf", new zip.TextReader (text),
                                                function ()
                                                {
                                                    writer.close (
                                                        function (blob) // blob contains the zip file as a Blob object
                                                        {
                                                            var end = new Date ().getTime ();
                                                            console.log ("finished zip (" + (Math.round (end - start) / 1000) + " seconds)");

                                                            // this is surprisingly not performant:
                                                            // var url = URL.createObjectURL (blob);
                                                            // a.setAttribute ("href", url);

                                                            // so we do this instead
                                                            console.log ("starting base64 conversion");
                                                            blob2base64 (blob,
                                                                function (data)
                                                                {
                                                                    var finish = new Date ().getTime ();
                                                                    console.log ("finished base64 conversion (" + (Math.round (finish - end) / 1000) + " seconds)");
                                                                    a.setAttribute ("href", "data:application/zip;base64," + data);
                                                                    a.setAttribute ("type", "application/zip");
                                                                    a.removeAttribute ("disabled");
                                                                    a.onclick = function (event) { /* let event bubble */ }
                                                                    console.log ("ready (" + (Math.round (new Date ().getTime () - finish) / 1000) + " seconds)");
                                                                    resolve ("OK");
                                                                }
                                                            );
                                                        }
                                                    );
                                                }
                                            );
                                        },
                                        function (error)
                                        {
                                           console.log (error);
                                           reject (error);
                                        }
                                    );
                                }
                            );
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
