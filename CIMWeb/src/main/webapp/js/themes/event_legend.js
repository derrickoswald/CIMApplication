/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["mustache", "../cimquery", "../cimcassandra"],
    /**
     * @summary Event legend control.
     * @description UI element for the event legend.
     * @exports event_legend
     * @version 1.0
     */
    function (mustache, cimquery, cimcassandra)
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
                cimcassandra.getAllSimulations ().then (
                    simulations =>
                    {
                        this._simulations = simulations;
                        const item = {
                            name: "Choose simulation",
                            id: "",
                            selected: true
                        };
                        this._simulations.push (item);
                        // display the list
                        this._container.innerHTML = mustache.render (
                            this._template,
                            {
                                "simulations": this._simulations
                            }
                        );
                        this._simulations.splice (this._simulations.length - 1, 1);
                        // handle close button
                        this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                        // handle changes
                        document.getElementById ("current_simulation").onchange = this.changeSimulation.bind (this);

                    }
                );

                return (this._container);
            }

            onRemove ()
            {
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            close ()
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
                const selection = event.target.value;
                const simulation = this._simulations.filter (x => x.id === selection)[0];
                if (this._legend_listener)
                    cimcassandra.getSimulationDetails (simulation).then ((sim) => this._legend_listener (sim));
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
);