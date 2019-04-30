/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["../mustache", "../cimquery"],
    /**
     * @summary Event legend control.
     * @description UI element for the event legend.
     * @exports event_legend
     * @version 1.0
     */
    function (mustache, cimquery)
    {
        class EventLegend
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
                          <select id="current_simulation" class="form-control custom-select">
                            {{#simulations}}
                            <option value="{{id}}"{{#selected}} selected{{/selected}}>{{{name}}}</option>
                            {{/simulations}}
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
                // refresh simulations
                this._simulations = [];
                var self = this;
                // get the keyspaces with simulations
                var promise = cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering", cassandra: true })
                    .then (
                        function (data)
                        {
                            // collect all the simulations
                            Promise.all (
                                data.map (
                                    function (keyspace)
                                    {
                                        return (
                                            cimquery.queryPromise ({ sql: "select JSON * from " + keyspace.keyspace_name + ".simulation", cassandra: true })
                                                .then (
                                                    function (runs)
                                                    {
                                                        runs.map (
                                                            function (run)
                                                            {
                                                                var json = JSON.parse (run["[json]"]);
                                                                self._simulations.push (json);
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
                                    self._simulations.map (
                                        function (simulation)
                                        {
                                            simulation.selected = false;
                                        }
                                    );
                                    self._simulations.push (
                                        {
                                            name: "Choose simulation",
                                            id: "",
                                            selected: true
                                        }
                                    );

                                    // display the list
                                    self._container.innerHTML = mustache.render (
                                        self._template,
                                        {
                                            "simulations": self._simulations
                                        }
                                    );

                                    // handle close button
                                    self._container.getElementsByClassName ("close")[0].onclick = self.close.bind (self);
                                    // handle changes
                                    document.getElementById ("current_simulation").onchange = self.changeSimulation.bind (self);
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

            changeSimulation (event)
            {
                var selection = event.target.value;
                this._simulations.forEach (x => x.selected = false);
                var new_current = this._simulations.filter (x => x.id === selection)[0];
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

        return (EventLegend);
    }
)