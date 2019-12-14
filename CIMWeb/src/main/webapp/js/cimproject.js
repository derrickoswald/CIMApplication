/**
 * Project control for CIM Application
 */
"use strict";

define
(
    ["mustache", "cimquery", "model/Wires", "model/UserDefinedModels"],
    /**
     * @summary Project control.
     * @description UI element for displaying project related information.
     * @exports cimproject
     * @version 1.0
     */
    function (mustache, cimquery, Wires, UserDefinedModels)
    {
        class CIMProject
        {
            constructor (cimmap, projecttheme)
            {
                this._cimmap = cimmap;
                this._projecttheme = projecttheme;
                this._keyspaces = undefined;
                this._input_keyspace = undefined;
                this._current_project = undefined;
                this._template =
`
<div class="card">
    <div class="card-body" style="min-width:600px;">
        <h5 class="card-title">
            <span class="info_title">Project</span>
            <button class="close" type="button" aria-label="Close">
                <span aria-hidden="true">&times;</span>
            </button>
        </h5>
        <h6 class="card-subtitle mb-2"></h6>
        <div id="project_info" class="card-text">
            <select id="current_project" class="form-control custom-select">
                <option value = "" class="text-muted" selected disabled hidden>Choose the export to use as a basis.</option>
{{#projects}}
                <option value="{{id}}"{{#selected}} selected{{/selected}}>{{{title}}}</option>
{{/projects}}
            </select>
            <em><small class="form-text text-muted">Select an export to load transformer service areas from</small></em>
            <select id="input_keyspace" class="form-control custom-select">
                <option value = "" class="text-muted" selected disabled hidden>Choose the keyspace with meter data.</option>
{{#keyspaces}}
                <option value="{{keyspace_name}}"{{#selected}} selected{{/selected}}>{{{keyspace_name}}}</option>
{{/keyspaces}}
            </select>
            <em><small class="form-text text-muted">Select the input data keyspace for measured and synthetic data</small></em>
            <select id="synthesis" class="form-control custom-select">
                <option value = "" class="text-muted" selected disabled hidden>Choose the synthetic load profile.</option>
{{#syntheses}}
                <option value="{{synthesis}}"{{#selected}} selected{{/selected}}>{{{synthesis}}}</option>
{{/syntheses}}
            </select>
            <em><small class="form-text text-muted">Select the load profile for an energy consumer</small></em>
        </div>
        <div class="card-footer">
            <button id="synthesis_assign" type="button" class="btn btn-primary" disabled>Assign</button>
        </div>
    </div>
</div>
`;
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl";
                this._container.innerHTML = mustache.render (
                    this._template,
                    {
                        "projects": [],
                        "keyspaces": [],
                        "syntheses": []
                    }
                );
                return (this._container);
            }

            onRemove ()
            {
                this._cimmap.remove_feature_listener (this);
                // destroy the container
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            getDefaultPosition ()
            {
                return ("bottom-right");
            }

            close ()
            {
                this._map.removeControl (this);
            }

            visible ()
            {
                return ("undefined" != typeof (this._container));
            }

            initialize ()
            {
                // refresh _projects and syntheses
                this._projects = [];
                this._syntheses = [];
                // get the keyspaces with export data
                cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'export' allow filtering", cassandra: true })
                    .then (
                        (data) =>
                        {
                            // collect all the exports
                            const promises = data.map (
                                (keyspace) =>
                                    cimquery.queryPromise ({ sql: `select JSON * from ${keyspace.keyspace_name}.export`, cassandra: true })
                                        .then (
                                            (runs) =>
                                            {
                                                runs.map (
                                                    (run) =>
                                                    {
                                                        const json = JSON.parse (run["[json]"]);
                                                        json.keyspace = keyspace.keyspace_name;
                                                        this._projects.push (json);
                                                    }
                                                );
                                            }
                                        )
                            );
                            // get the keyspaces with meter data
                            promises.push (
                                cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'measured_value' allow filtering", cassandra: true })
                                    .then (
                                        (data) =>
                                        {
                                            this._keyspaces = data;
                                        }
                                    )
                            );
                            Promise.all (promises).then (
                                () =>
                                {
                                    // condition the list for display
                                    this._projects.map (
                                        (project) =>
                                        {
                                            // id                                   | filename                                        | filesize  | filetime                        | runtime
                                            //--------------------------------------+-------------------------------------------------+-----------+---------------------------------+---------------------------------
                                            // 9482460b-e4ac-4050-9131-6bef2d04a103 |                               data/DemoData.rdf |    727832 | 2019-03-28 10:41:55.000000+0000 | 2019-03-28 10:42:50.964000+0000
                                            // 60a05bf2-cc3c-4ef6-b4de-fa95b5645639 | hdfs://sandbox:8020/sak_cim_export_stripe_2.rdf | 361012447 | 2019-03-28 09:29:01.144000+0000 | 2019-03-28 11:15:17.130000+0000
                                            project.title = `${project.filename} (${project.runtime})`;
                                            const index = project.title.lastIndexOf ("/");
                                            if (-1 !== index)
                                                project.title = project.title.substring (index + 1);
                                        }
                                    );

                                    // display the list
                                    this._container.innerHTML = mustache.render (
                                        this._template,
                                        {
                                            "projects": this._projects,
                                            "keyspaces": this._keyspaces,
                                            "syntheses": this._syntheses
                                        }
                                    );

                                    // handle close button
                                    this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                                    // handle assign button
                                    this._container.getElementsByClassName ("btn")[0].onclick = this.assign.bind (this);
                                    if (1 < this._projects.length)
                                        // handle changes
                                        document.getElementById ("current_project").onchange = this.changeProject.bind (this);
                                    else
                                    {
                                        // choose the only project automatically
                                        this._current_project = this._projects[0];
                                        this._projecttheme.setProject (this._current_project);
                                    }
                                    document.getElementById ("input_keyspace").onchange = this.changeKeyspace.bind (this);
                                    // handle element selection
                                    this._cimmap.add_feature_listener (this);
                                    if (this._cimmap.get_selected_feature ())
                                        this.selection_change (this._cimmap.get_selected_feature (), this._cimmap.get_selected_features ());
                                }
                            );
                        }
                    );
            }

            getProjectKeyspace ()
            {
                return (this._current_project ? this._current_project.keyspace : undefined);
            }

            getProjectId ()
            {
                return (this._current_project ? this._current_project.id : undefined);
            }

            getInputKeyspace ()
            {
                return (this._input_keyspace);
            }

            changeProject (event)
            {
                const selection = event.target.value;
                if ("" !== selection)
                {
                    this._current_project = this._projects.filter (x => x.id === selection)[0];
                    this._projects.forEach (x => x.selected = x.id === selection);
                    this._projecttheme.setProject (this._current_project);
                }
            }

            changeKeyspace (event)
            {
                const selection = event.target.value;
                if ("" !== selection)
                {
                    this._input_keyspace = this._keyspaces.filter (x => x.id === selection)[0];
                    this._keyspaces.forEach (x => x.selected = x.id === selection);
                    cimquery.queryPromise ({ sql: `select distinct synthesis,type,period from ${ selection }.synthesized_value`, cassandra: true })
                        .then (
                            (data) =>
                            {
                                this._syntheses = data;
                                // display the list
                                this._container.innerHTML = mustache.render (
                                    this._template,
                                    {
                                        "projects": this._projects,
                                        "keyspaces": this._keyspaces,
                                        "syntheses": this._syntheses
                                    }
                                );
                            }
                        );
                }
            }

            /**
             * Allow assigning the load dynamics to the selected object.
             */
            selection_change (current_feature, current_selection)
            {
                const data = this._cimmap.get_data ();
                const button = document.getElementById ("synthesis_assign");
                if (data && data["Element"] && data["Element"][current_feature])
                {
                    const feature = data["Element"][current_feature];
                    button.disabled = (feature.cls !== "EnergyConsumer");
                }
                else
                    button.disabled = true;
            }

            hashCode (string)
            {
                let hash = 0;
                if (string.length === 0) return hash;
                for (let i = 0; i < string.length; i++)
                {
                    const chr = string.charCodeAt (i);
                    hash  = ((hash << 5) - hash) + chr;
                    hash |= 0; // Convert to 32bit integer
                }
                return (hash);
            };

            /**
             * Assign a user defined load object to the energy consumer
             */
            assign ()
            {
                const current_feature = this._cimmap.get_selected_feature ();
                const data = this._cimmap.get_data ();
                if (data && data["Element"] && data["Element"][current_feature])
                {
                    let energy_consumer = data["Element"][current_feature];
                    if (energy_consumer.cls === "EnergyConsumer")
                    {
                        const load_profile = document.getElementById ("synthesis").value;

                        // generate a consistent mRID for the LoadUserDefined based on the load profile name
                        const mrid = "LoadUserDefined_"+ Math.abs (this.hashCode (load_profile)).toString (16);
                        const load =
                            {
                                id: mrid,
                                mRID: mrid,
                                cls: "LoadUserDefined",
                                name: load_profile,
                                proprietary: true
                            };
                        // insert the load into the in-memory CIM data
                        const user_defined_load = new UserDefinedModels.LoadUserDefined (load, data);
                        // assign it to the energy consumer
                        energy_consumer.LoadDynamics = user_defined_load.id;
                        // insert the consumer into the in-memory CIM data
                        energy_consumer = new Wires.EnergyConsumer (energy_consumer, data);
                        // warn if the PSRType conflicts with the standard player query
                        if (energy_consumer.PSRType !== "PSRType_newHouseService")
                            alert ("EnergyConsumer PSRType is not 'PSRType_newHouseService' as expected");
                    }
                }
            }
        }

        return (CIMProject);
    }
);