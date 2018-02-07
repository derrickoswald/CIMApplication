/**
 * @fileOverview Calculate short circuit values.
 * @name cimanalysis
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap"],
    /**
     * @summary Functions to perform short circuit calculations.
     * @name cimanalysis
     * @exports cimanalysis
     * @version 1.0
     */
    function (util, mustache, cim, cimmap)
    {
        /**
         * @summary Execute short circuit calculation.
         * @description Perform a short circuit calculation on loaded CIM data.
         * @param {object} event - optional, the click event
         * @function do_analysis
         * @memberOf module:cimanalysis
         */
        function do_analysis (event)
        {
            var url;
            var xmlhttp;

            // ToDo: validation
            var network_power = Number (document.getElementById ("network_power").value);
            var network_angle = Number (document.getElementById ("network_angle").value);
            var cmax = Number (document.getElementById ("cmax").value);
            var cmin = Number (document.getElementById ("cmin").value);
            var motor_power_factor = Number (document.getElementById ("motor_power_factor").value);
            var starting_ratio = Number (document.getElementById ("starting_ratio").value);
            url = util.home () + "cim/short_circuit" +
                ";network_short_circuit_power=" + network_power +
                ";network_short_circuit_angle=" + network_angle +
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
                        var resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                            document.getElementById ("analysis_results").innerHTML = "<pre>\n" + JSON.stringify (resp.result, null, 4) + "</pre>";
                        else
                            alert (resp.message);
                    }
            };
            xmlhttp.send ()
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
                "          <label class='col-sm-2 col-form-label' for='network_angle'>Network angle</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <input id='network_angle' class='form-control' type='text' name='network_angle' aria-describedby='network_angleHelp' value='-70.0'>\n" +
                "            <small id='network_angleHelp' class='form-text text-muted'>Default supply network short circuit power angle, to be used if no equivalent injection is found (degrees).</small>\n" +
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
                "        <h4>Maximum Inrush Current</h4>\n" +
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
        }

        return (
            {
                initialize: initialize
            }
        );
    }
)