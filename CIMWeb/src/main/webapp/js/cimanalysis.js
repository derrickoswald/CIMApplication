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
        /**
         * Fuse breakpoint tables.
         * - Standard: bkw,ckw,ews
         * - Custom 1: ebl
         * - Custom 2: ekz
         * - Custom 3: sak
         * @memberOf module:cimanalysis
         */
        let FuseTables =
        {
            "Standard":
            {
                "DIN": [
                    { "ik":0.0, "rating": 0.0 },
                    { "ik":65.0, "rating": 25.0 },
                    { "ik":105.0, "rating": 40.0 },
                    { "ik":140.0, "rating": 50.0 },
                    { "ik":180.0, "rating": 63.0 },
                    { "ik":240.0, "rating": 80.0 },
                    { "ik":320.0, "rating": 100.0 },
                    { "ik":380.0, "rating": 125.0 },
                    { "ik":500.0, "rating": 160.0 },
                    { "ik":650.0, "rating": 200.0 },
                    { "ik":800.0, "rating": 250.0 },
                    { "ik":1050.0, "rating": 315.0 },
                    { "ik":1300.0, "rating": 400.0 },
                    { "ik":1750.0, "rating": 500.0 },
                    { "ik":2400.0, "rating": 630.0 }
                ]
            },
            "Custom 1":
            {
                "DIN": [
                    { "ik":0.0, "rating": 6.0 },
                    { "ik":65.0, "rating": 25.0 },
                    { "ik":105.0, "rating": 35.0 },
                    { "ik":140.0, "rating": 50.0 },
                    { "ik":180.0, "rating": 50.0 },
                    { "ik":240.0, "rating": 63.0 },
                    { "ik":320.0, "rating": 100.0 },
                    { "ik":380.0, "rating": 100.0 },
                    { "ik":500.0, "rating": 160.0 },
                    { "ik":650.0, "rating": 160.0 },
                    { "ik":800.0, "rating": 200.0 },
                    { "ik":1050.0, "rating": 250.0 },
                    { "ik":1300.0, "rating": 400.0 },
                    { "ik":1750.0, "rating": 400.0 },
                    { "ik":2400.0, "rating": 500.0 }
                ]
            },
            "Custom 2":
            {
                "DIN": [
                    { "ik":0.0, "rating": 0.0 },
                    { "ik":28.0, "rating": 10.0 },
                    { "ik":40.0, "rating": 16.0 },
                    { "ik":55.0, "rating": 20.0 },
                    { "ik":70.0, "rating": 25.0 },
                    { "ik":93.0, "rating": 32.0 },
                    { "ik":120.0, "rating": 40.0 },
                    { "ik":160.0, "rating": 50.0 },
                    { "ik":190.0, "rating": 63.0 },
                    { "ik":230.0, "rating": 80.0 },
                    { "ik":305.0, "rating": 100.0 },
                    { "ik":380.0, "rating": 125.0 },
                    { "ik":490.0, "rating": 160.0 },
                    { "ik":690.0, "rating": 200.0 },
                    { "ik":820.0, "rating": 250.0 },
                    { "ik":1150.0, "rating": 315.0 },
                    { "ik":1350.0, "rating": 400.0 },
                    { "ik":1900.0, "rating": 500.0 },
                    { "ik":2500.0, "rating": 630.0 }
                ]
            },
            "Custom 3":
            {
                "DIN": [
                    { "ik":0.0, "rating": 0.0 },
                    { "ik":65.0, "rating": 25.0 },
                    { "ik":105.0, "rating": 40.0 },
                    { "ik":140.0, "rating": 50.0 },
                    { "ik":180.0, "rating": 63.0 },
                    { "ik":240.0, "rating": 80.0 },
                    { "ik":320.0, "rating": 100.0 },
                    { "ik":380.0, "rating": 125.0 },
                    { "ik":500.0, "rating": 160.0 },
                    { "ik":650.0, "rating": 200.0 },
                    { "ik":800.0, "rating": 250.0 },
                    { "ik":1050.0, "rating": 315.0 },
                    { "ik":1300.0, "rating": 400.0 },
                    { "ik":1750.0, "rating": 500.0 },
                    { "ik":2400.0, "rating": 630.0 }
                ],
                "SEV": [
                    { "ik":0.0, "rating": 0.0 },
                    { "ik":200.0, "rating": 60.0 },
                    { "ik":250.0, "rating": 75.0 },
                    { "ik":300.0, "rating": 100.0 },
                    { "ik":340.0, "rating": 125.0 },
                    { "ik":500.0, "rating": 150.0 },
                    { "ik":600.0, "rating": 200.0 },
                    { "ik":720.0, "rating": 250.0 },
                    { "ik":850.0, "rating": 300.0 },
                    { "ik":1150.0, "rating": 400.0 }
                ]
            }
        };
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
                        fuse_table: getFuseTable (), // ToDo: editable fuse table
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

        function getFuseTables ()
        {
            const ret = [];
            for (let property in FuseTables)
                if (FuseTables.hasOwnProperty (property))
                    ret.push (property);
            return (ret);
        }

        function getFuseTable ()
        {
            return (FuseTables[document.getElementById ("fuse_table").value]);
        }

        function render_fuse_tables ()
        {
            const fuse_table_template =
`
{{#fusetables}}
<a class="dropdown-item" href="#">{{.}}</a>
{{/fusetables}}
`;
            const element = document.getElementById ("fuse_table");
            const fuse_table = document.getElementById ("fuse_table_select");
            fuse_table.innerHTML = mustache.render (fuse_table_template, { fusetables: getFuseTables () });
            for (let j = 0; j < fuse_table.children.length; j++)
                fuse_table.children.item (j).onclick = (event) =>
                {
                    event.preventDefault ();
                    element.value = event.target.innerHTML;
                };
        }

        function fuse_table_modal (table)
        {
            const fuse_table_modal_template =
`<div id="fuse_table_modal" class="modal fade" tabindex="-1" role="dialog">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h2><i class="fa fa-power-off"></i> Fuse Table</h2>
                <button id="close_progress_modal" class="close" type="button" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div id="fuse_table_modal_body" class="modal-body">
                <table class='analysis-table'>
                    <tr>
                        <th>Fuse class</th><th>Up to I<sub>sc</sub> (A)</th><th>Recommended<br> Fuse Rating (A)</th>
                    </tr>
                    {{#DIN}}
                        <tr>
                            <td>DIN</td><td>{{ik}}</td><td>{{rating}}</td>
                        </tr>
                    {{/DIN}}
                    {{#SEV}}
                        <tr>
                            <td>SEV</td><td>{{ik}}</td><td>{{rating}}</td>
                        </tr>
                    {{/SEV}}
               </table>
            </div>
        </div>
    </div>
</div>
`;
            const modal = mustache.render (fuse_table_modal_template, table);
            const element = document.createElement("div");
            element.innerHTML = modal;
            if (document.getElementById ("fuse_table_modal"))
                document.getElementById ("fuse_table_modal").remove ();
            document.body.appendChild(element.children[0]);
            $("#fuse_table_modal").modal ("show");
        }

        function show_fuse_table_info (event)
        {
            event.preventDefault();
            fuse_table_modal (getFuseTable ());
        }

        function render_keyspaces ()
        {
            const keyspaces_template =
`
{{#keyspaces}}
<a class="dropdown-item" href="#">{{.}}</a>
{{/keyspaces}}
`;
            const element = document.getElementById ("shortcircuit_keyspace");
            const keyspace = document.getElementById ("shortcircuit_keyspace_select");
            keyspace.innerHTML = mustache.render (keyspaces_template, { keyspaces: getKeySpaces () });
            for (let j = 0; j < keyspace.children.length; j++)
                keyspace.children.item (j).onclick = (event) =>
                {
                    event.preventDefault ();
                    element.value = event.target.innerHTML;
                };
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
                  <div class="input-group-prepend">
                    <button id="fuse_table_info" class="btn btn-outline-secondary" type="button">&#x1F6C8;</button>
                  </div>
                  <input id="fuse_table" type="text" class="form-control" aria-describedby="fuse_tableHelp" value="Standard">
                  <div class="input-group-append">
                    <button class="btn btn-outline-secondary dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Fuse table</button>
                    <div id="fuse_table_select" class="dropdown-menu">
                      <a class="dropdown-item" href="#">One</a>
                      <a class="dropdown-item" href="#">Two</a>
                      <a class="dropdown-item" href="#">Three</a>
                    </div>
                  </div>
              </div>
            <small id='fuse_tableHelp' class='form-text text-muted'>Recommended I<sub>sc</sub>:fuse rating breakpoint table, e.g. I<sub>sc</sub><65A&#8594;25A, I<sub>sc</sub><105A&#8594;40A, I<sub>sc</sub><140A&#8594;50A, etc.</small>
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
            render_fuse_tables ();
            document.getElementById ("fuse_table_info").onclick = show_fuse_table_info;
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
            render ();
            cimcassandra.getKeyspaces ().then (setKeySpaces.bind (this)).then (render_keyspaces.bind (this));
        }

        return (
            {
                initialize: initialize
            }
        );
    }
);