/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["../mustache"],
    /**
     * @summary Sumulation legend control.
     * @description UI element for the simulation legend.
     * @name simulation_legend
     * @exports simulation_legend
     * @version 1.0
     */
    function (mustache)
    {
        class SimulationLegend
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
                        <h6 class="card-subtitle mb-2"></h6>
                        <div class="card-text">
                        <label for="simulation_slider">
                          <input id="simulation_slider" type="text"/>
                          Time
                        </label>
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
                this._container.innerHTML = mustache.render (this._template);
                // https://github.com/seiyria/bootstrap-slider v10.0.0
                this._slider = new Slider (
                    this._container.getElementsByTagName ("input")[0],
                    {
                        step: 1000 * 60 * 15, // 15 minutes in milliseconds
                        min: this._times.start,
                        max: this._times.end,
                        formatter: function (value)
                        {
                            var t = new Date (value);
                            return (t.toTimeString ().substring (0, 8));
                        },
                        value: this._times.start
                    }
                );
                this._slider.on ("slide", this.legend_change.bind (this));
                this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
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

            legend_change (value)
            {
                if (this._legend_listener)
                    this._legend_listener (value);
            }

            legend_change_listener (fn)
            {
                this._legend_listener = fn;
            }

            setTimes (times) // { start: start, end: end }
            {
                 this._times = times;
            }

            getTimes ()
            {
                 return (this._times);
            }
        }

        return (SimulationLegend);
    }
)