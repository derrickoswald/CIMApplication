/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["../mustache", "../cimquery"],
    /**
     * @summary Project legend control.
     * @description UI element for the project legend.
     * @name project_legend
     * @exports project_legend
     * @version 1.0
     */
    function (mustache, cimquery)
    {
        class ProjectLegend
        {
            constructor (theme)
            {
                this._theme = theme;
                this._template =
                    `
                    <div class="card">
                      <div class="card-body" style="min-width:200px;">
                        <h5 class="card-title">
                          <span class="info_title">Legend</span>
                          <button class="close" type="button" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                          </button>
                        </h5>
                        <h6 class="card-subtitle mb-2">
                          <select id="current_project" class="form-control custom-select">
                            {{#projects}}
                            <option value="{{id}}"{{#selected}} selected{{/selected}}>{{{title}}}</option>
                            {{/projects}}
                          </select>
                        </h6>
                        <div class="card-footer">
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
                // refresh _projects
                this._projects = [];
                var self = this;
                // get the keyspaces with export data
                var promise = cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'export' allow filtering", cassandra: true })
                    .then (
                        function (data)
                        {
                            // collect all the exports
                            Promise.all (
                                data.map (
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
                                                                var json = JSON.parse (run["[json]"]);
                                                                json.keyspace = keyspace.keyspace_name;
                                                                self._projects.push (json);
                                                            }
                                                        );
                                                    }
                                                )
                                        );
                                    }
                                )
                            ).then (
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
                                            project.selected = false;
                                            project.title = project.filename;
                                            var index = project.title.lastIndexOf ("/");
                                            if (-1 != index)
                                                project.title = project.title.substring (index + 1);
                                        }
                                    );
                                    self._projects.push (
                                        {
                                            title: "Choose project",
                                            id: "",
                                            selected: true
                                        }
                                    );

                                    // display the list
                                    self._container.innerHTML = mustache.render (
                                        self._template,
                                        {
                                            "projects": self._projects
                                        }
                                    );

                                    // handle close button
                                    self._container.getElementsByClassName ("close")[0].onclick = self.close.bind (self);
                                    // handle changes
                                    document.getElementById ("current_project").onchange = self.changeProject.bind (self);
                                }
                            );
                        }
                    );
                return (this._container);
            }

            onRemove ()
            {
                this._container.parentNode.removeChild (this._container);
                delete this._slider;
                delete this._container;
                delete this._map;
            }

            close (event)
            {
                this._map.removeControl (this);
            }

            getDefaultPosition ()
            {
                return ("bottom-right");
            }

            visible ()
            {
                return ("undefined" != typeof (this._container));
            }

            changeProject (event)
            {
                var selection = event.target.value;
                this._projects.forEach (x => x.selected = false);
                var new_current = this._projects.filter (x => x.id == selection)[0];
                new_current.selected = true;
                if (this._legend_listener)
                    this._legend_listener (new_current);
            }

            legend_change_listener (fn)
            {
                this._legend_listener = fn;
            }

            initialize ()
            {
            }
        }

        return (ProjectLegend);
    }
)