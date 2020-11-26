/**
 * @fileOverview Calculate short circuit values.
 * @name cimanalysis
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap", "cimquery", "cimcassandra", "cimstatus", "themes/analysis_theme"],
    /**
     * @summary Functions to perform short circuit calculations.
     * @name cimanalysis
     * @exports cimanalysis
     * @version 1.0
     */
    function (util, mustache, cim, cimmap, cimquery, cimcassandra, CIMStatus, AnalysisTheme)
    {
        let TheAnalysis;
        let KeySpaces = [];

        function getKeySpaces () { return (KeySpaces); }
        function setKeySpaces (keyspaces) { KeySpaces = keyspaces; }

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
         * @param {string} id a unique id for this run
         * @return a Promise to resolve or reject the analysis
         * @function analyze
         * @memberOf module:cimanalysis
         */
        function analyze (id)
        {
            const info = cimmap.get_loaded ();
            if (null != info)
            {
                if (!info.options["ch.ninecode.cim.do_topo"] && !info.options["ch.ninecode.cim.do_topo_islands"])
                    return (new Promise ((resolve, reject) => reject ("loaded CIM file was not topologically processed")));
                else
                {
                    if (!(info.options["ch.ninecode.cim.force_retain_fuses"] === "ForceTrue"))
                        alert ("Fuses may not be processed since force_retain_fuses=ForceTrue was not specified");

                    // ToDo: validation
                    const options = {
                        id: id,
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
                        fuse_table: Number (document.getElementById ("fuse_table").value), // ToDo: editable fuse table
                        messagemax: 5,
                        batchsize: 10000,
                        trafos: "",
                        cable_impedance_limit: 5.0,
                        workdir: derive_work_dir (cimmap.get_loaded ().files[0]),
                        calculate_public_lighting: false,
                        output: "Cassandra",
                        keyspace: document.getElementById ("shortcircuit_keyspace").value,
                        replication: 1
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
                    return (
                            util.makeRequest ("POST", url, JSON.stringify (options, null, 4)).then (
                                    (xmlhttp) =>
                                    {
                                        return (
                                                new Promise (
                                                        function (resolve, reject)
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
                                                )
                                        );
                                    }
                            )
                    );
                }
            }
            else
                return (new Promise ((resolve, reject) => reject ("no CIM file is loaded")));
        }

        function getRandomInt (max)
        {
            return (Math.floor (Math.random () * Math.floor (max)));
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
            const id = "ShortCircuit" + getRandomInt (1e9);
            const status = new CIMStatus ("progress_modal", "progress", id);
            // flip to the map while analysing
            const to_map = document.getElementById ("analysis_to_map").checked;
            if (to_map)
                window.location.hash = "map";

            function successCallback (data)
            {
                status.stop();
                if (to_map)
                {
                    TheAnalysis.records = data;
                    const theme = new AnalysisTheme (TheAnalysis.records);
                    cimmap.get_themer ().removeTheme (theme);
                    cimmap.get_themer ().addTheme (theme, true);
                }
                else
                    document.getElementById ("analysis_results").innerHTML = "<pre>" + JSON.stringify (data, null, 4) + "</pre>";
            }

            function failureCallback (message)
            {
                status.stop();
                alert ("analysis failed: " + message);
            }

            function select (data)
            {
                console.log (JSON.stringify (data, null, 4));
                TheAnalysis = data;
                const keyspace = data.parameters.keyspace;
                const id = data.result.id;
                return (cimquery.queryPromise ({ sql: `select equipment,trafo,errors,low_r,low_x,low_r0,low_x0,low_ip,low_sk,low_ik,low_ik3pol,imax_3ph_low,imax_2ph_low,imax_1ph_low,imax_3ph_med,imax_2ph_med,imax_1ph_med,fuses,fusemax,fuseok from ${keyspace}.shortcircuit where id='${id}'`, cassandra: true }));
            }

            status.start ();
            if (to_map)
                analyze (id).then (select).then (successCallback, failureCallback);
            else
                analyze (id).then (successCallback, failureCallback);
        }

        /**
         * @summary Render the short circuit page.
         * @description Uses mustache to create HTML DOM elements that display the short circuit options.
         * @function render
         * @memberOf module:cimanalysis
         */
        function render ()
        {
            document.getElementById ("analysis").innerHTML = "";
            const analysis_template =
`
<div class='container'>
  <div class='row justify-content-center'>
    <div class='col-12' style='margin-top: 40px;'>
      <form id='analysis_form' role='form' style='width: 100%'>
        <h3>Short Circuit</h3>
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
          <div class="col form-group">
              <div class="input-group">
                  <input id="fuse_table" type="text" class="form-control" aria-describedby="fuse_tableHelp" value="1">
                  <div class="input-group-append">
                    <button class="btn btn-outline-secondary dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Fuse table</button>
                    <div id="fuse_table_select" class="dropdown-menu">
                      <a class="dropdown-item" href="#">Customer 1</a>
                      <a class="dropdown-item" href="#">Customer 2</a>
                      <a class="dropdown-item" href="#">Customer 3</a>
                      <a class="dropdown-item" href="#">Customer 4</a>
                    </div>
                  </div>
              </div>
            <small id='fuse_tableHelp' class='form-text text-muted'>Recommended I<sub>k</sub>:fuse rating breakpoint table, e.g. Customer 1: 105&#8594;40A, 140&#8594;50A &#8230; or Customer 2: 120&#8594;40A, 160&#8594;50A &#8230;.</small>
          </div>
        </div>
        <h4>Output</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='shortcircuit_keyspace'>Cassandra keyspace</label>
          <div class="col form-group">
              <div class="input-group">
                  <input id="shortcircuit_keyspace" type="text" class="form-control" aria-describedby="shortcircuitKeyspaceHelp" value="cimapplication">
                  <div class="input-group-append">
                    <button class="btn btn-outline-secondary dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Keyspace</button>
                    <div id="shortcircuit_keyspace_select" class="dropdown-menu">
                      <a class="dropdown-item" href="#">One</a>
                      <a class="dropdown-item" href="#">Two</a>
                      <a class="dropdown-item" href="#">Three</a>
                    </div>
                  </div>
              </div>
            <small id="shortcircuitKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for output (tables <em>shortcircuit</em>, <em>nullungsbedingung</em>, etc.).</small>
          </div>
        </div>
        <div class="form-row">
          <div class="col form-group">
            <label for="analysis_to_map">View on map</label>
              <div class="form-check">
                <input id="analysis_to_map" class="form-check-input" type="checkbox" name="analysis_to_map" aria-describedby="analysisToMapHelp" checked>
                <small id="analysisToMapHelp" class="form-text text-muted">Add a theme to the map tab for analysis results.</small>
              </div>
          </div>
        </div>
        <div class='form-group'>
          <button id='do_analysis' type='button' class='btn btn-primary'>Execute</button>
        </div>
      </form>
      <div id='analysis_results'>
      </div>
    </div>
  </div>
</div>
`;

            document.getElementById ("analysis").innerHTML = mustache.render (analysis_template);
            const contents = getKeySpaces ().map (keyspace => `<a class="dropdown-item" href="#">${keyspace}</a>`).join ("\n");
            const keyspace = document.getElementById ("shortcircuit_keyspace_select");
            keyspace.innerHTML = contents;
            for (var j = 0; j < keyspace.children.length; j++)
                keyspace.children.item (j).onclick = (event) => { event.preventDefault (); const element = document.getElementById ("shortcircuit_keyspace"); element.value = event.target.innerHTML; element.onchange (); };
            document.getElementById ("do_analysis").onclick = do_analysis;
        }

        /**
         * @summary Initalize the short circuit page.
         * @description Get the keyspaces then render the short circuit page.
         * @function initialize
         * @memberOf module:cimanalysis
         */
        function initialize ()
        {
            cimcassandra.getKeyspaces ().then (setKeySpaces.bind(this)).then (render.bind (this));
        }

        return (
            {
                initialize: initialize
            }
        );
    }
);