/**
 * @fileOverview Calculate short circuit values.
 * @name cimanalysis
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap", "themes/analysis_theme"],
    /**
     * @summary Functions to perform short circuit calculations.
     * @name cimanalysis
     * @exports cimanalysis
     * @version 1.0
     */
    function (util, mustache, cim, cimmap, AnalysisTheme)
    {
        // The analysis details.
        // provisional schema:
        //{
        //    "parameters": {
        //        "verbose": false,
        //        "description": "CIMApplication",
        //        "default_short_circuit_power": 200000000,
        //        "default_short_circuit_impedance": "0.43779-1.20281j",
        //        "default_transformer_power_rating": 630000,
        //        "default_transformer_impedance": "0.0059+0.03956j",
        //        "base_temperature": 20,
        //        "low_temperature": 60,
        //        "high_temperature": 90,
        //        "cmax": 1,
        //        "cmin": 0.9,
        //        "worstcasepf": true,
        //        "cosphi": "NaN",
        //        "trafos": "all",
        //        "workdir": "null"
        //    },
        //    "records": [
        //        {
        //            "node": "HAS111_topo",
        //            "equipment": "ABG7456",
        //            "terminal": 1,
        //            "container": "STA12",
        //            "errors": [],
        //            "tx": "TRA154",
        //            "prev": "self",
        //            "low_r": 0.0033536161143749997,
        //            "low_x": 0.014948997477524999,
        //            "low_r0": 0.00308,
        //            "low_x0": 0.0157007515744,
        //            "low_ik": 13368.858758173696,
        //            "low_ik3pol": 15073.877937122674,
        //            "low_ip": 32402.162369335812,
        //            "low_sk": 10443488.981675202,
        //            "imax_3ph_low": 904.4326762273602,
        //            "imax_1ph_low": 452.2163381136801,
        //            "imax_2ph_low": 783.2616736256401,
        //            "imax_3ph_med": 452.2163381136801,
        //            "imax_1ph_med": 226.10816905684004,
        //            "imax_2ph_med": 391.63083681282006,
        //            "high_r": 0.0033536161143749997,
        //            "high_x": 0.014948997477524999,
        //            "high_r0": 0.00308,
        //            "high_x0": 0.0157007515744,
        //            "high_ik": 13368.858758173696,
        //            "high_ik3pol": 15073.877937122674,
        //            "high_ip": 32402.162369335812,
        //            "high_sk": 10443488.981675202,
        //            "fuse": 630,
        //            "fuseOK": false
        //        },
        //    ...
        //    ]
        //}
        var TheAnalysis;

        /**
         * @summary Execute short circuit calculation.
         * @description Perform a short circuit calculation on loaded CIM data.
         * @return a Promise to resolve or reject the analysis
         * @function analyze
         * @memberOf module:cimanalysis
         */
        function analyze ()
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        var url;
                        var xmlhttp;

                        // ToDo: validation
                        var network_power = Number (document.getElementById ("network_power").value);
                        var network_resistance = Number (document.getElementById ("network_resistance").value);
                        var network_reactance = Number (document.getElementById ("network_reactance").value);
                        var transformer_power = Number (document.getElementById ("transformer_power").value);
                        var transformer_resistance = Number (document.getElementById ("transformer_resistance").value);
                        var transformer_reactance = Number (document.getElementById ("transformer_reactance").value);
                        var tbase = Number (document.getElementById ("tbase").value);
                        var tlow = Number (document.getElementById ("tlow").value);
                        var thigh = Number (document.getElementById ("thigh").value);
                        var cmax = Number (document.getElementById ("cmax").value);
                        var cmin = Number (document.getElementById ("cmin").value);
                        var motor_power_factor = document.getElementById ("motor_power_factor").value;
                        var cosphi = motor_power_factor == "" ? Number.NaN : Number (motor_power_factor);
                        url = util.home () + "cim/short_circuit" +
                            ";network_short_circuit_power=" + network_power +
                            ";network_short_circuit_resistance=" + network_resistance +
                            ";network_short_circuit_reactance=" + network_reactance +
                            ";transformer_power_rating=" + transformer_power +
                            ";transformer_resistance=" + transformer_resistance +
                            ";transformer_reactance=" + transformer_reactance +
                            ";tbase=" + tbase +
                            ";tlow=" + tlow +
                            ";thigh=" + thigh +
                            ";cmax=" + cmax +
                            ";cmin=" + cmin;
                        if (!isNaN (cosphi))
                            url = url + ";cosphi=" + cosphi;
                        xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 == xmlhttp.readyState)
                                if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                                {
                                    try
                                    {
                                        var resp = JSON.parse (xmlhttp.responseText);
                                        if (resp.status == "OK")
                                            resolve (resp.result);
                                        else
                                            reject (resp.message);
                                    }
                                    catch (exception)
                                    {
                                        reject (exception.toString ());
                                    }
                                }
                                else
                                    reject ("xmlhttp.status = " + xmlhttp.status);
                        };
                        xmlhttp.send ()
                    }
                )
            );
        }

        /**
         * @summary Execute short circuit calculation.
         * @description Perform a short circuit calculation on loaded CIM data and insert the results in the analysis_results div.
         * @param {object} event - optional, the click event
         * @function do_analysis
         * @memberOf module:cimanalysis
         */
        function do_analysis (event)
        {
            function successCallback (data)
            {
                document.getElementById ("analysis_results").innerHTML = "<pre>\n" + JSON.stringify (data, null, 4) + "</pre>";
            }

            function failureCallback (message)
            {
                alert ("analysis failed: " + message);
            }

            analyze ().then (successCallback, failureCallback);
        }


        /**
         * @summary Execute short circuit calculation.
         * @description Perform a short circuit calculation on loaded CIM data.
         * @param {object} event - optional, the click event
         * @function do_analysis
         * @memberOf module:cimanalysis
         */
        function to_map (event)
        {
            function successCallback (data)
            {
                TheAnalysis = data
                var theme = new AnalysisTheme (TheAnalysis.records);
                cimmap.get_themer ().removeTheme (theme);
                cimmap.get_themer ().addTheme (theme);
                alert ("successfully sent results to the map");
            }

            function failureCallback (message)
            {
                alert ("failed to send results to the map: " + message);
            }

            analyze ().then (successCallback, failureCallback);
        }

        /**
         * @summary Render the shor circuit page.
         * @description Uses mustache to create HTML DOM elements that display the short circuit options.
         * @function initialize
         * @memberOf module:cimanalysis
         */
        function initialize ()
        {
            document.getElementById ("analysis").innerHTML = "";
            var analysis_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-12' style='margin-top: 40px;'>\n" +
                "      <form id='analysis_form' role='form' style='width: 100%'>\n" +
                "        <h4>Short Circuit</h4>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='network_power'>Network power</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='network_power' class='form-control' type='text' name='network_power' aria-describedby='network_powerHelp' value='200.0e6'>\n" +
                "            <small id='network_powerHelp' class='form-text text-muted'>Default supply network available short circuit power, to be used if no equivalent injection is found (VA).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='network_resistance'>Network resistance</label>\n" +
                "          <div class='col-sm-4'>\n" +
                "            <input id='network_resistance' class='form-control' type='text' name='network_resistance' aria-describedby='network_resistanceHelp' value='0.437785783'>\n" +
                "            <small id='network_resistanceHelp' class='form-text text-muted'>Default supply network short circuit resistance, to be used if no equivalent injection is found (Ω).</small>\n" +
                "          </div>\n" +
                "          <label class='col-sm-2 col-form-label' for='network_reactance'>Network reactance</label>\n" +
                "          <div class='col-sm-4'>\n" +
                "            <input id='network_reactance' class='form-control' type='text' name='network_reactance' aria-describedby='network_reactanceHelp' value='-1.202806555'>\n" +
                "            <small id='network_reactanceHelp' class='form-text text-muted'>Default supply network short circuit reactance, to be used if no equivalent injection is found (Ω).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='transformer_power'>Transformer power</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='transformer_power' class='form-control' type='text' name='transformer_power' aria-describedby='transformer_powerHelp' value='630000'>\n" +
                "            <small id='transformer_powerHelp' class='form-text text-muted'>Default transformer rated power, to be used if it wasn't specified for a transformer (VA).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='transformer_resistance'>Transformer resistance</label>\n" +
                "          <div class='col-sm-4'>\n" +
                "            <input id='transformer_resistance' class='form-control' type='text' name='transformer_resistance' aria-describedby='transformer_resistanceHelp' value='0.005899999998374999'>\n" +
                "            <small id='transformer_resistanceHelp' class='form-text text-muted'>Default transformer characteristic resistance, to be used if it wasn't specified for a transformer (Ω).</small>\n" +
                "          </div>\n" +
                "          <label class='col-sm-2 col-form-label' for='transformer_reactance'>Transformer reactance</label>\n" +
                "          <div class='col-sm-4'>\n" +
                "            <input id='transformer_reactance' class='form-control' type='text' name='transformer_reactance' aria-describedby='transformer_reactanceHelp' value='0.039562482211875'>\n" +
                "            <small id='transformer_reactanceHelp' class='form-text text-muted'>Default transformer characteristic reactance, to be used if it wasn't specified for a transformer (Ω).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='tbase'>Base temperature</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='tbase' class='form-control' type='text' name='tbase' aria-describedby='tbaseHelp' value='20.0'>\n" +
                "            <small id='tbaseHelp' class='form-text text-muted'>Basis temperature used in the CIM file impedances (°C).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='tlow'>Low temperature</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='tlow' class='form-control' type='text' name='tlow' aria-describedby='tlowHelp' value='60.0'>\n" +
                "            <small id='tlowHelp' class='form-text text-muted'>Low temperature (minimum impedances, maximum fault level) for calculations used for rating equipment (°C).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='thigh'>Low temperature</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='thigh' class='form-control' type='text' name='thigh' aria-describedby='thighHelp' value='90.0'>\n" +
                "            <small id='thighHelp' class='form-text text-muted'>High temperature (maximum impedances, minimum fault level) for calculations used for protections settings (°C).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='cmax'>C<sub>max</sub></label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='cmax' class='form-control' type='text' name='cmax' aria-describedby='cmaxHelp' value='1.0'>\n" +
                "            <small id='cmaxHelp' class='form-text text-muted'>Voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='cmin'>C<sub>min</sub></label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='cmin' class='form-control' type='text' name='cmin' aria-describedby='cminHelp' value='0.9'>\n" +
                "            <small id='cminHelp' class='form-text text-muted'>Voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <h4>Maximum Starting Current</h4>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='motor_power_factor'>Motor power factor</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='motor_power_factor' class='form-control' type='text' name='motor_power_factor' aria-describedby='motor_power_factorHelp' value=''>\n" +
                "            <small id='motor_power_factorHelp' class='form-text text-muted'>Power factor of motor load at startup, e.g cos(60°)=0.5, if not specified worst-case is assumed (dimensionless).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <button id='do_analysis' type='button' class='btn btn-primary'>Execute</button>\n" +
                "          <button id='analysis_to_map' name='analysis_to_map' type='button' class='btn btn-primary'>Send to map</button>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "      <div id='analysis_results'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            var text = mustache.render (analysis_template);
            document.getElementById ("analysis").innerHTML = text;
            document.getElementById ("do_analysis").onclick = do_analysis;
            document.getElementById ("analysis_to_map").onclick = to_map;

        }

        return (
            {
                initialize: initialize
            }
        );
    }
)