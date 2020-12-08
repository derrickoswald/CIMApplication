/**
 * @fileOverview Calculate maximum feed in values.
 * @name maximumfeedin
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cimmap", "cimquery", "cimcassandra", "cimstatus", "themes/maximumfeedin_theme"],
    /**
     * @summary Functions to perform maximum feed in calculations.
     * @name maximumfeedin
     * @exports maximumfeedin
     * @version 1.0
     */
    function (util, mustache, cimmap, cimquery, cimcassandra, CIMStatus, MaximumFeedInTheme)
    {
        /**
         * Object that presents a GUI and performs maximum feed in calculations.
         * @param {HTMLElement} target the HTMLElement to put the contents in.
         * @class
         */
        class MaximumFeedIn
        {
            constructor (target)
            {
                this.target = target;
                this.TheAnalysis = {};
                this.KeySpaces = [];
            }

            getKeySpaces () { return (this.KeySpaces); }
            setKeySpaces (keyspaces) { this.KeySpaces = keyspaces; }

            derive_work_dir (file)
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
             * @summary Execute maximum feed in calculation.
             * @description Perform a maximum PV power calculation on loaded CIM data.
             * @param {string} id a unique id for this run
             * @return a Promise to resolve or reject the analysis
             * @function analyze
             */
            analyze (id)
            {
                const info = cimmap.get_loaded ();
                if (null != info)
                {
                    if (!info.options["ch.ninecode.cim.do_topo"] && !info.options["ch.ninecode.cim.do_topo_islands"])
                        return (new Promise ((resolve, reject) => reject ("loaded CIM file was not topologically processed")));
                    else
                    {
                        if (!(info.options["ch.ninecode.cim.force_retain_fuses"] === "ForceTrue"))
                            alert ("Feeder processing may be erroneous since force_retain_fuses=ForceTrue was not specified");
                        if (!(info.options["ch.ninecode.cim.force_retain_switches"] === "ForceTrue"))
                            alert ("Feeder processing may be erroneous since force_retain_switches=ForceTrue was not specified");

                        // ToDo: validation
                        const options = {
                            id: id,
                            workdir: this.derive_work_dir (cimmap.get_loaded ().files[0]),
                            verbose: true,
                            description: "cim maximum feed in",
                            three: document.getElementById ("maximumfeedin_three_phase").checked,
                            precalculation: false,
                            trafos: "",
                            export_only: false,
                            all: document.getElementById ("maximumfeedin_all").checked,
                            erase: false,
                            simulation: -1,
                            reference: -1,
                            delta: 1e-6,
                            precalc_factor: Number (document.getElementById ("maximumfeedin_precalc").value),
                            cosphi: Number (document.getElementById ("maximumfeedin_cosphi").value),
                            voltage_threshold: Number (document.getElementById ("maximumfeedin_threshold1").value),
                            voltage_threshold2: Number (document.getElementById ("maximumfeedin_threshold2").value),
                            ignore_other: document.getElementById ("maximumfeedin_ignore_other").checked,
                            cable_impedance_limit: 5.0,
                            base_temperature: Number (document.getElementById ("maximumfeedin_tbase").value),
                            sim_temperature: Number (document.getElementById ("maximumfeedin_tsim").value),
                            output: "Cassandra",
                            outputfile: "simulation/results.db",  // not used because of the above output type
                            keyspace: document.getElementById ("maximumfeedin_keyspace").value,
                            replication: Number (document.getElementById ("maximumfeedin_cassandra_replication").value)
                        };

                        const url = util.home () + "cim/maximumfeedin";
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

            getRandomInt (max)
            {
                return (Math.floor (Math.random () * Math.floor (max)));
            }

            updateResults (data)
            {
                if (document.getElementById ("maximumfeedin_to_map").checked)
                {
                    this.TheAnalysis.records = data;
                    const theme = new MaximumFeedInTheme ();
                    theme.setAnalysis (this.TheAnalysis.records);
                    cimmap.get_themer ().removeTheme (theme);
                    cimmap.get_themer ().addTheme (theme, true);
                    window.location.hash = "map";
                }
                else
                    document.getElementById ("maximumfeedin_results").innerHTML = "<pre>" + JSON.stringify (data, null, 4) + "</pre>";
            }

            /**
             * @summary Execute maximum feed-in calculation.
             * @description Perform a maximum PV power calculation on loaded CIM data.
             * @param {object} event - optional, the click event
             * @function do_analysis
             */
            do_maximumfeedin (event)
            {
                const id = "MaximumFeedIn" + this.getRandomInt (1e9);
                const status = new CIMStatus (id);

                let successCallback = (data) =>
                {
                    status.stop();
                    this.updateResults (data);
                };

                let failureCallback = (message) =>
                {
                    status.stop();
                    alert ("analysis failed: " + JSON.stringify (message, null, 4));
                };

                let select = (data) =>
                {
                    console.log (JSON.stringify (data, null, 4));
                    this.TheAnalysis = data;
                    const keyspace = this.TheAnalysis.parameters.keyspace;
                    const id = this.TheAnalysis.id;
                    return (cimquery.queryPromise ({ sql: `select house,maximum,trafo,feeder,reason,details from ${keyspace}.maximumfeedin where id='${id}'`, cassandra: true }));
                };

                status.start ();
                if (document.getElementById ("maximumfeedin_to_map").checked)
                    this.analyze (id).then (select).then (successCallback, failureCallback);
                else
                    this.analyze (id).then (successCallback, failureCallback);
            }

            jsonify (data)
            {
                return (JSON.stringify (data, null, 4))
            }

            do_show ()
            {
                const id = document.getElementById ("maximumfeedin_id").value;
                const keyspace = document.getElementById ("maximumfeedin_keyspace").value;

                cimquery.queryPromise (
                        {
                            cassandra: true,
                            sql: `select house,maximum,trafo,feeder,reason,details from ${keyspace}.maximumfeedin where id='${id}'`
                        }
                ).then (this.updateResults.bind (this));
            }

            render_keyspaces ()
            {
                const keyspaces_template =
`
{{#keyspaces}}
<a class="dropdown-item" href="#">{{.}}</a>
{{/keyspaces}}
`;
                const element = document.getElementById ("maximumfeedin_keyspace");
                const keyspace = document.getElementById ("maximumfeedin_keyspace_select");
                keyspace.innerHTML = mustache.render (keyspaces_template, { keyspaces: this.getKeySpaces () });
                for (let j = 0; j < keyspace.children.length; j++)
                    keyspace.children.item (j).onclick = (event) =>
                    {
                        event.preventDefault ();
                        element.value = event.target.innerHTML;
                        element.onchange ();
                    };
            }

            set_keyspace (event)
            {
                const keyspace = document.getElementById ("maximumfeedin_keyspace").value;
                let successCallback = (data) =>
                {
                    // ToDo: what about run?
                    const template =
`
    {{#analyses}}
        <option value="{{id}}">{{description}} {{timestamp}} ({{run}})</option>
    {{/analyses}}
`;
                    function timestamp ()
                    {
                        const date = new Date (this.run_time);
                        return (date.toString ());
                    }
                    document.getElementById ("maximumfeedin_id").innerHTML = mustache.render
                    (
                        template,
                        {
                            analyses: data,
                            timestamp: timestamp
                        }
                    );
                };

                cimquery.queryPromise
                (
                    {
                        cassandra: true,
                        sql: `select id,run_time,run,description from ${keyspace}.maximumfeedin_run`
                    }
                ).then (successCallback);
            }

            /**
             * @summary Render the maximum feed-in  page.
             * @description Uses mustache to create HTML DOM elements that display the maximum feed-in options.
             * @function render
             */
            render ()
            {
                this.target.innerHTML = "";
                const maximumfeedin_template =
`
<div class='container'>
  <div class='row justify-content-center'>
    <div class='col-12' style='margin-top: 40px;'>
      <form id='maximumfeedin_form' role='form' style='width: 100%'>
        <h4>Output</h4>
        <div class='form-group row'>
            <label class='col-sm-2 col-form-label' for='maximumfeedin_keyspace'>Cassandra keyspace</label>
            <div class='col-sm-4'>
              <div class="input-group">
                  <input id="maximumfeedin_keyspace" type="text" class="form-control" aria-describedby="maximumfeedinKeyspaceHelp" value="cimapplication">
                  <div class="input-group-append">
                    <button class="btn btn-outline-secondary dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Keyspace</button>
                    <div id="maximumfeedin_keyspace_select" class="dropdown-menu">
                      <a class="dropdown-item" href="#">One</a>
                      <a class="dropdown-item" href="#">Two</a>
                      <a class="dropdown-item" href="#">Three</a>
                    </div>
                  </div>
              </div>
              <small id="maximumfeedinKeyspaceHelp" class="form-text text-muted">Enter the Cassandra keyspace to be used for output (tables <em>maximumfeedin_run</em>, <em>maximumfeedin</em>).</small>
            </div>
            <label class='col-sm-2 col-form-label' for='cassandra_replication'>Cassandra replication</label>
            <div class='col-sm-4'>
                <input id='maximumfeedin_cassandra_replication' class='form-control' type='text' name='maximumfeedin_cassandra_replication' aria-describedby='maximumfeedin_cassandra_replicationHelp' value='1'>
                <small id='maximumfeedin_cassandra_replicationHelp' class='form-text text-muted'>Cassandra keyspace replication.<br>Used only if creating the keyspace.</small>
            </div>
        </div>
        <h4>Options</h4>
        <div class="form-row">
          <div class="col form-group">
            <label for="maximumfeedin_three_phase">Three phase calculation</label>
            <div class="form-check">
              <input id="maximumfeedin_three_phase" class="form-check-input" type="checkbox" name="maximumfeedin_three_phase" aria-describedby="threePhaseHelp">
              <small id="threePhaseHelp" class="form-text text-muted">Perform analysis as balanced three phase, not just single phase.</small>
            </div>
          </div>
          <div class="col form-group">
            <label for="maximumfeedin_all">Process all transformers</label>
            <div class="form-check">
              <input id="maximumfeedin_all" class="form-check-input" type="checkbox" name="maximumfeedin_all" aria-describedby="allTransformersHelp" checked>
              <small id="allTransformersHelp" class="form-text text-muted">Perform load-flow on all transformer service areas, not just ones with PV or non-radial networks.</small>
            </div>
          </div>
          <div class="col form-group">
            <label for="maximumfeedin_ignore_other">Ignore adjacent feeders</label>
            <div class="form-check">
              <input id="maximumfeedin_ignore_other" class="form-check-input" type="checkbox" name="maximumfeedin_ignore_other" aria-describedby="ignoreOtherHelp">
              <small id="ignoreOtherHelp" class="form-text text-muted">Ignore cable currents on neighboring feeders.</small>
            </div>
          </div>
        </div>
        <h4>Factors</h4>
        <div class="form-row">
          <label class='col-sm-2 col-form-label' for='maximumfeedin_precalc'>Precalculation factor</label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_precalc' class='form-control' type='text' name='maximumfeedin_precalc' aria-describedby='maximumfeedinPrecalcHelp' value='2.5'>
            <small id='maximumfeedinPrecalcHelp' class='form-text text-muted'>Factor to multiply precalculation results for load-flow upper limit.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='maximumfeedin_cosphi'>Cos ɸ</label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_cosphi' class='form-control' type='text' name='maximumfeedin_cosphi' aria-describedby='maximumfeedinCosphiHelp' value='1.0'>
            <small id='maximumfeedinCosphiHelp' class='form-text text-muted'>Power factor for photo-voltaic installations, positive leading, negative lagging.</small>
          </div>
        </div>
        <h4>Thresholds</h4>
        <div class="form-row">
          <label class='col-sm-2 col-form-label' for='maximumfeedin_threshold1'>Voltage direct</label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_threshold1' class='form-control' type='text' name='maximumfeedin_threshold1' aria-describedby='maximumfeedinThreshold1Help' value='3.0'>
            <small id='maximumfeedinThreshold1Help' class='form-text text-muted'>Voltage threshold for the feeder of the house under test (%).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='maximumfeedin_threshold2'>Voltage adjacent</label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_threshold2' class='form-control' type='text' name='maximumfeedin_threshold2' aria-describedby='maximumfeedinThreshold2Help' value='3.0'>
            <small id='maximumfeedinThreshold2Help' class='form-text text-muted'>Voltage threshold for neighboring feeders of the house under test (%).</small>
          </div>
        </div>
        <h4>Temperatures</h4>
        <div class='form-row'>
          <label class='col-sm-2 col-form-label' for='maximumfeedin_tbase'>Base temperature</label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_tbase' class='form-control' type='text' name='maximumfeedin_tbase' aria-describedby='maximumfeedinTbaseHelp' value='20.0'>
            <small id='maximumfeedinTbaseHelp' class='form-text text-muted'>Base temperature used in the CIM file (°C).</small>
          </div>
          <label class='col-sm-2 col-form-label' for='maximumfeedin_tsim'>Analysis temperature</sub></label>
          <div class='col-sm-4'>
            <input id='maximumfeedin_tsim' class='form-control' type='text' name='maximumfeedin_tsim' aria-describedby='maximumfeedinTsimHelp' value='20.0'>
            <small id='maximumfeedinTsimHelp' class='form-text text-muted'>Analysis temperature (°C).</small>
          </div>
        </div>
        <div class="form-row">
          <div class="col form-group">
            <label for="maximumfeedin_to_map">View on map</label>
            <div class="form-check">
              <input id="maximumfeedin_to_map" class="form-check-input" type="checkbox" name="maximumfeedin_to_map" aria-describedby="maximumfeedinToMapHelp" checked>
              <small id="maximumfeedinToMapHelp" class="form-text text-muted">Add a theme to the map tab for analysis results.</small>
            </div>
          </div>
        </div>
        <div class='form-group'>
          <button id='do_maximumfeedin' type='button' class='btn btn-primary'>Execute</button>
          <div id="maximumfeedin_warning" class="alert alert-warning" role="alert" style="display: none"></div>
        </div>
        <div class="form-group">
          <label for="maximumfeedin_id">Prior Analysis</label>
          <select id="maximumfeedin_id" class="form-control custom-select" aria-describedby="maximumfeedinIDHelp">
          </select>
          <small id="maximumfeedinIDHelp" class="form-text text-muted">Select the analysis to view on the map.</small>
        </div>
        <div class="form-group">
          <button id="show_maximumfeedin" name="show_maximumfeedin" type="button" class="btn btn-primary">Show analysis</button>
        </div>
      </form>
      <div id='maximumfeedin_results'>
      </div>
    </div>
  </div>
</div>
`;

                this.target.innerHTML = mustache.render (maximumfeedin_template);
                document.getElementById ("do_maximumfeedin").onclick = this.do_maximumfeedin.bind (this);
                document.getElementById ("maximumfeedin_keyspace").onchange = this.set_keyspace.bind (this);
                document.getElementById ("show_maximumfeedin").onclick = this.do_show.bind (this);
            }

            /**
             * @summary Initalize the maximum feed-in page.
             * @description Get the keyspaces then render the maximum feed-in page.
             * @function initialize
             */
            initialize ()
            {
                this.render ();
            }

            /**
             * @summary Update the page.
             * @description Called if the page is already initialized and the page is again being shown.
             * @function focus
             */
            focus ()
            {
                // update keyspace list
                cimcassandra.getKeyspaces ().then (this.setKeySpaces.bind (this)).then (this.render_keyspaces.bind (this));

                // update previous analyses choices
                this.set_keyspace ();

                // update Execute button
                const do_button = document.getElementById ("do_maximumfeedin");
                const show_button = document.getElementById ("show_maximumfeedin");
                const warning = document.getElementById ("maximumfeedin_warning");
                const info = cimmap.get_loaded ();
                if (null != info)
                {
                    if (!info.options["ch.ninecode.cim.do_topo"] && !info.options["ch.ninecode.cim.do_topo_islands"])
                    {
                        warning.innerHTML = "loaded CIM file was not topologically processed";
                        warning.style.display = "block";
                        do_button.disabled = true;
                        show_button.disabled = true;
                    }
                    else
                    {
                        if (!(info.options["ch.ninecode.cim.force_retain_fuses"] === "ForceTrue"))
                        {
                            warning.innerHTML = "Feeder processing may be erroneous since force_retain_fuses=ForceTrue was not specified";
                            warning.style.display = "block";
                        }
                        else if (!(info.options["ch.ninecode.cim.force_retain_switches"] === "ForceTrue"))
                        {
                            warning.innerHTML = "Feeder processing may be erroneous since force_retain_switches=ForceTrue was not specified";
                            warning.style.display = "block";
                        }
                        else
                        {
                            warning.innerHTML = "";
                            warning.style.display = "none";
                        }
                        do_button.disabled = false;
                        show_button.disabled = false;
                    }
                }
                else
                {
                    warning.innerHTML = "no CIM file is loaded";
                    warning.style.display = "block";
                    do_button.disabled = true;
                }
            }

            /**
             * @summary Close down the page.
             * @description Called if the page is being hidden.
             * @function blur
             */
            blur ()
            {
            }

        }

        return (MaximumFeedIn);
    }
);