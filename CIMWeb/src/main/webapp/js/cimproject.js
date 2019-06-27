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
                this._template =
`
<div class="card">
    <div class="card-body" style="min-width:200px;">
        <h5 class="card-title">
            <span class="info_title">Project</span>
            <button class="close" type="button" aria-label="Close">
                <span aria-hidden="true">&times;</span>
            </button>
        </h5>
        <h6 class="card-subtitle mb-2"></h6>
        <div id='project_info' class="card-text">
            <select id="current_project" class="form-control custom-select">
{{#projects}}
                <option value="{{id}}"{{#selected}} selected{{/selected}}>{{{title}}}</option>
{{/projects}}
            </select>
            <em><small class='form-text text-muted'>Select a project to load transformer service areas from</small></em>
            <select id="synthesis" class="form-control custom-select">
{{#syntheses}}
                <option value="{{synthesis}}"{{#selected}} selected{{/selected}}>{{{synthesis}}}</option>
{{/syntheses}}
            </select>
            <em><small class='form-text text-muted'>Select the load profile for an energy consumer</small></em>
        </div>
        <div class="card-footer">
            <button id='synthesis_assign' type='button' class='btn btn-primary' disabled>Assign</button>
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
                const self = this;
                // get the keyspaces with export data
                cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'export' allow filtering", cassandra: true })
                    .then (
                        function (data)
                        {
                            // collect all the exports
                            const promises = data.map (
                                function (keyspace)
                                {
                                    return (
                                        cimquery.queryPromise ({ sql: "select JSON * from " + keyspace.keyspace_name + ".export", cassandra: true })
                                            .then (
                                                function (runs)
                                                {
                                                    runs.map (
                                                        function (run)
                                                        {
                                                            const json = JSON.parse (run["[json]"]);
                                                            json.keyspace = keyspace.keyspace_name;
                                                            self._projects.push (json);
                                                        }
                                                    );
                                                }
                                            )
                                    );
                                }
                            );
                            promises.push (
                                cimquery.queryPromise ({ sql: "select distinct synthesis,type,period from cimapplication.synthesized_value", cassandra: true })
                                    .then (
                                        function (data)
                                        {
                                            self._syntheses = data.map ((row, index) => { row.selected = index === 0; return (row); });
                                        }
                                    )
                            );
                            Promise.all (promises).then (
                                function ()
                                {
                                    // condition the list for display
                                    self._projects.map (
                                        function (project)
                                        {
                                            // id                                   | filename                                        | filesize  | filetime                        | runtime
                                            //--------------------------------------+-------------------------------------------------+-----------+---------------------------------+---------------------------------
                                            // 9482460b-e4ac-4050-9131-6bef2d04a103 |                               data/DemoData.rdf |    727832 | 2019-03-28 10:41:55.000000+0000 | 2019-03-28 10:42:50.964000+0000
                                            // 60a05bf2-cc3c-4ef6-b4de-fa95b5645639 | hdfs://sandbox:8020/sak_cim_export_stripe_2.rdf | 361012447 | 2019-03-28 09:29:01.144000+0000 | 2019-03-28 11:15:17.130000+0000
                                            project.selected = 1 < self._projects.length;
                                            project.title = project.filename;
                                            const index = project.title.lastIndexOf ("/");
                                            if (-1 !== index)
                                                project.title = project.title.substring (index + 1);
                                        }
                                    );

                                    // display the list
                                    self._container.innerHTML = mustache.render (
                                        self._template,
                                        {
                                            "projects": self._projects,
                                            "syntheses": self._syntheses
                                        }
                                    );

                                    // handle close button
                                    self._container.getElementsByClassName ("close")[0].onclick = self.close.bind (self);
                                    // handle assign button
                                    self._container.getElementsByClassName ("btn")[0].onclick = self.assign.bind (self);
                                    if (1 < self._projects.length)
                                        // handle changes
                                        document.getElementById ("current_project").onchange = self.changeProject.bind (self);
                                    else
                                    {
                                        // choose the only project automatically
                                        const new_current = self._projects[0];
                                        self._projecttheme.setProject (new_current.keyspace, new_current.id);
                                    }
                                    // handle element selection
                                    self._cimmap.add_feature_listener (self);
                                    if (self._cimmap.get_selected_feature ())
                                        self.selection_change (self._cimmap.get_selected_feature (), self._cimmap.get_selected_features ());
                                }
                            );
                        }
                    );
            }

            changeProject (event)
            {
                const selection = event.target.value;
                const new_current = this._projects.filter (x => x.id === selection)[0];
                this._projects.forEach (x => x.selected = x.id === new_current.id);
                this._projecttheme.setProject (new_current.keyspace, new_current.id);
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
                    const chr   = string.charCodeAt (i);
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