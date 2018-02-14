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
        //[
        //    {
        //        "node": "Line_3_node_1_topo",
        //        "equipment": "Line_3_node_1",
        //        "tx": "T1",
        //        "prev": "T1_node_2_topo",
        //        "r": 0.06074579999974,
        //        "x": 0.023079997153900002,
        //        "r0": 0.23719399999973997,
        //        "x0": 0.08032999715389999,
        //        "ik": 1639.1965106015975,
        //        "ik3pol": 3553.876001557771,
        //        "ip": 5128.291999404527,
        //        "sk": 2462197.519399116,
        //        "motor_3ph_max_low": 110057.15878818948,
        //        "motor_1ph_max_low": 31770.765126303293,
        //        "motor_l_l_max_low": 86403.53609172089,
        //        "motor_3ph_max_med": 55028.57939409474,
        //        "motor_1ph_max_med": 15885.382563151647,
        //        "motor_l_l_max_med": 43201.768045860445
        //    },
        //     ...
        // ]
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
                        var cmax = Number (document.getElementById ("cmax").value);
                        var cmin = Number (document.getElementById ("cmin").value);
                        var motor_power_factor = Number (document.getElementById ("motor_power_factor").value);
                        var starting_ratio = Number (document.getElementById ("starting_ratio").value);
                        url = util.home () + "cim/short_circuit" +
                            ";network_short_circuit_power=" + network_power +
                            ";network_short_circuit_resistance=" + network_resistance +
                            ";network_short_circuit_reactance=" + network_reactance +
                            ";transformer_power_rating=" + transformer_power +
                            ";transformer_resistance=" + transformer_resistance +
                            ";transformer_reactance=" + transformer_reactance +
                            ";cmax=" + cmax +
                            ";cmin=" + cmin +
                            ";cosphi=" + motor_power_factor +
                            ";starting_ratio=" + starting_ratio;
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
                var theme = new AnalysisTheme (TheAnalysis);
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
                "            <input id='motor_power_factor' class='form-control' type='text' name='motor_power_factor' aria-describedby='motor_power_factorHelp' value='0.5'>\n" +
                "            <small id='motor_power_factorHelp' class='form-text text-muted'>Power factor of motor load at startup (dimensionless).</small>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='starting_ratio'>Starting current ratio</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='starting_ratio' class='form-control' type='text' name='starting_ratio' aria-describedby='starting_ratioHelp' value='1.0'>\n" +
                "            <small id='starting_ratioHelp' class='form-text text-muted'>Ratio between initial starting current I<sub>A</sub> and rated current I<sub>N</sub>.</small>\n" +
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