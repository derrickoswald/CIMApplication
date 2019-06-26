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
        let TheAnalysis;

        function derive_work_dir (file)
        {
            let ret = "/simulation/";
            try
            {
                let url = new URL (file);
                const protocol = url.protocol;
                switch (protocol)
                {
                    case "hdfs:":
                        url = new URL (file.replace ("hdfs:", "http:"));
                        const last1 = url.pathname.lastIndexOf ("/", file.length - 1);
                        ret = protocol + "//" + url.host + ((last1 !== -1) ? url.pathname.substring (0, last1) : "") + ret;
                        break;
                    case "file:":
                        const last2 = url.pathname.lastIndexOf ("/", file.length - 1);
                        ret = protocol + "//" + ((last2 !== -1) ? url.pathname.substring (0, last2) : "") + ret;
                        break;
                }
            }
            catch (error)
            {
            }
            return (ret)
        }

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
                        // ToDo: validation
                        const options = {
                            verbose: true,
                            description: "cim analyze",
                            default_short_circuit_power_max: Number (document.getElementById ("network_power_max").value),
                            default_short_circuit_impedance_max: {
                                re: Number (document.getElementById ("network_resistance_max").value),
                                im: Number (document.getElementById ("network_reactance_max").value)
                            },
                            default_short_circuit_power_min: Number (document.getElementById ("network_power_min").value),
                            default_short_circuit_impedance_min: {
                                re: Number (document.getElementById ("network_resistance_min").value),
                                im: Number (document.getElementById ("network_reactance_min").value)
                            },
                            default_transformer_power_rating: Number (document.getElementById ("transformer_power").value),
                            default_transformer_impedance: {
                                re: Number (document.getElementById ("transformer_resistance").value),
                                im: Number (document.getElementById ("transformer_reactance").value)
                            },
                            base_temperature: Number (document.getElementById ("tbase").value),
                            low_temperature: Number (document.getElementById ("tlow").value),
                            high_temperature: Number (document.getElementById ("thigh").value),
                            cmax: Number (document.getElementById ("cmax").value),
                            cmin: Number (document.getElementById ("cmin").value),
                            fuse_table: Number (document.getElementById ("fuse_table").value),
                            messagemax: Number (document.getElementById ("messagemax").value),
                            batchsize: Number (document.getElementById ("batchsize").value),
                            trafos: "",
                            workdir: derive_work_dir (cimmap.get_loaded ().files[0])
                        };
                        const pf = document.getElementById ("motor_power_factor").value;
                        if (("" === pf) || isNaN (Number (pf)))
                        {
                            options.worstcasepf = true;
                            options.cosphi = null;
                        }
                        else
                        {
                            options.worstcasepf = false;
                            options.cosphi = Number (pf)
                        }
                        const url = util.home () + "cim/short_circuit";
                        const xmlhttp = util.createCORSRequest ("POST", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                {
                                    try
                                    {
                                        const resp = JSON.parse (xmlhttp.responseText);
                                        if (resp.status === "OK")
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
                        xmlhttp.send (JSON.stringify (options, null, 4));
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
            const info = cimmap.get_loaded ();
            if (null != info)
            {
                if (!info.options["ch.ninecode.cim.do_topo"])
                    alert ("WARNING: loaded CIM file was not topological processed");

                function successCallback (data)
                {
                    document.getElementById ("analysis_results").innerHTML = "<pre>" + JSON.stringify (data, null, 4) + "</pre>";
                }

                function failureCallback (message)
                {
                    alert ("analysis failed: " + message);
                }

                analyze ().then (successCallback, failureCallback);
            }
            else
                alert ("no CIM file is loaded");
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
                TheAnalysis = data;
                const theme = new AnalysisTheme (TheAnalysis.records);
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
            const analysis_template =
`
<div class='container'>
  <div class='row justify-content-center'>
    <div class='col-12' style='margin-top: 40px;'>
      <form id='analysis_form' role='form' style='width: 100%'>
        <h4>Short Circuit</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='network_power_max'>Network power max</label>
          <div class='col-sm-10'>
            <input id='network_power_max' class='form-control' type='text' name='network_power_max' aria-describedby='network_power_maxHelp' value='200.0e6'>
            <small id='network_power_maxHelp' class='form-text text-muted'>Default maximum supply network available short circuit power, to be used if no equivalent injection is found (VA).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='network_resistance_max'>Network resistance max</label>
          <div class='col-sm-4'>
            <input id='network_resistance_max' class='form-control' type='text' name='network_resistance_max' aria-describedby='network_resistance_maxHelp' value='0.437785783'>
            <small id='network_resistance_maxHelp' class='form-text text-muted'>Default maximum supply network short circuit resistance, to be used if no equivalent injection is found (Ω).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='network_reactance_max'>Network reactance max</label>
          <div class='col-sm-4'>
            <input id='network_reactance_max' class='form-control' type='text' name='network_reactance_max' aria-describedby='network_reactance_maxHelp' value='-1.202806555'>
            <small id='network_reactance_maxHelp' class='form-text text-muted'>Default maximum supply network short circuit reactance, to be used if no equivalent injection is found (Ω).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='network_power_min'>Network power min</label>
          <div class='col-sm-10'>
            <input id='network_power_min' class='form-control' type='text' name='network_power_min' aria-describedby='network_power_minHelp' value='100.0e6'>
            <small id='network_power_minHelp' class='form-text text-muted'>Default minimum supply network available short circuit power, to be used if no equivalent injection is found (VA).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='network_resistance_min'>Network resistance min</label>
          <div class='col-sm-4'>
            <input id='network_resistance_min' class='form-control' type='text' name='network_resistance_min' aria-describedby='network_resistance_minHelp' value='0.437785783'>
            <small id='network_resistance_minHelp' class='form-text text-muted'>Default minimum supply network short circuit resistance, to be used if no equivalent injection is found (Ω).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='network_reactance_min'>Network reactance min</label>
          <div class='col-sm-4'>
            <input id='network_reactance_min' class='form-control' type='text' name='network_reactance_min' aria-describedby='network_reactance_minHelp' value='-1.202806555'>
            <small id='network_reactance_minHelp' class='form-text text-muted'>Default minimum supply network short circuit reactance, to be used if no equivalent injection is found (Ω).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='transformer_power'>Transformer power</label>
          <div class='col-sm-10'>
            <input id='transformer_power' class='form-control' type='text' name='transformer_power' aria-describedby='transformer_powerHelp' value='630000'>
            <small id='transformer_powerHelp' class='form-text text-muted'>Default transformer rated power, to be used if it wasn't specified for a transformer (VA).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='transformer_resistance'>Transformer resistance</label>
          <div class='col-sm-4'>
            <input id='transformer_resistance' class='form-control' type='text' name='transformer_resistance' aria-describedby='transformer_resistanceHelp' value='0.005899999998374999'>
            <small id='transformer_resistanceHelp' class='form-text text-muted'>Default transformer characteristic resistance, to be used if it wasn't specified for a transformer (Ω).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='transformer_reactance'>Transformer reactance</label>
          <div class='col-sm-4'>
            <input id='transformer_reactance' class='form-control' type='text' name='transformer_reactance' aria-describedby='transformer_reactanceHelp' value='0.039562482211875'>
            <small id='transformer_reactanceHelp' class='form-text text-muted'>Default transformer characteristic reactance, to be used if it wasn't specified for a transformer (Ω).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='tbase'>Base temperature</label>
          <div class='col-sm-2'>
            <input id='tbase' class='form-control' type='text' name='tbase' aria-describedby='tbaseHelp' value='20.0'>
            <small id='tbaseHelp' class='form-text text-muted'>Base temperature used in the CIM file (°C).</small>
          </div>
          <label class='col-sm-1 col-form-label' for='tlow'>T<sub>Low</sub></label>
          <div class='col-sm-3'>
            <input id='tlow' class='form-control' type='text' name='tlow' aria-describedby='tlowHelp' value='60.0'>
            <small id='tlowHelp' class='form-text text-muted'>Low temperature (minimum impedances, maximum fault level) for calculations used for rating equipment (°C).</small>
          </div>
          <label class='col-sm-1 col-form-label' for='thigh'>T<sub>High</sub></label>
          <div class='col-sm-3'>
            <input id='thigh' class='form-control' type='text' name='thigh' aria-describedby='thighHelp' value='90.0'>
            <small id='thighHelp' class='form-text text-muted'>High temperature (maximum impedances, minimum fault level) for calculations used for protections settings (°C).</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='cmax'>C<sub>max</sub></label>
          <div class='col-sm-4'>
            <input id='cmax' class='form-control' type='text' name='cmax' aria-describedby='cmaxHelp' value='1.0'>
            <small id='cmaxHelp' class='form-text text-muted'>Voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='cmin'>C<sub>min</sub></label>
          <div class='col-sm-4'>
            <input id='cmin' class='form-control' type='text' name='cmin' aria-describedby='cminHelp' value='0.9'>
            <small id='cminHelp' class='form-text text-muted'>Voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless).</small>
          </div>
        </div>
        <h4>Maximum Starting Current</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='motor_power_factor'>Motor power factor</label>
          <div class='col-sm-10'>
            <input id='motor_power_factor' class='form-control' type='text' name='motor_power_factor' aria-describedby='motor_power_factorHelp' value=''>
            <small id='motor_power_factorHelp' class='form-text text-muted'>Power factor of motor load at startup, e.g cos(60°)=0.5, if not specified worst-case is assumed (dimensionless).</small>
          </div>
        </div>
        <h4>Fuse Check</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='fuse_table'>Fuse table</label>
          <div class='col-sm-10'>
            <select id='fuse_table' class='form-control custom-select' name='fuse_table' aria-describedby='fuse_tableHelp'>
                <option value='1' selected>Customer 1</option>
                <option value='2'>Customer 2</option>
            </select>
            <small id='fuse_tableHelp' class='form-text text-muted'>Recommended I<sub>k</sub>:fuse rating breakpoint table, e.g. Customer 1: 105&#8594;40A, 140&#8594;50A &#8230; or Customer 2: 120&#8594;40A, 160&#8594;50A &#8230;.</small>
          </div>
        </div>
        <h4>Other</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='messagemax'>Message list limit</label>
          <div class='col-sm-4'>
            <input id='messagemax' class='form-control' type='text' name='messagemax' aria-describedby='messagemaxHelp' value='5'>
            <small id='messagemaxHelp' class='form-text text-muted'>Specify the maximum number of error/warning messages collected per node.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='batchsize'>Commit batch size</label>
          <div class='col-sm-4'>
            <input id='batchsize' class='form-control' type='text' name='batchsize' aria-describedby='batchsizeHelp' value='10000'>
            <small id='batchsizeHelp' class='form-text text-muted'>Specify the maximum batch size of records to store in the result database.</small>
          </div>
        </div>
        <div class='form-group'>
          <button id='do_analysis' type='button' class='btn btn-primary'>Execute</button>
          <button id='analysis_to_map' name='analysis_to_map' type='button' class='btn btn-primary'>Send to map</button>
        </div>
      </form>
      <div id='analysis_results'>
      </div>
    </div>
  </div>
</div>
`;

            document.getElementById ("analysis").innerHTML = mustache.render (analysis_template);
            document.getElementById ("do_analysis").onclick = do_analysis;
            document.getElementById ("analysis_to_map").onclick = to_map;
        }

        return (
            {
                initialize: initialize
            }
        );
    }
);