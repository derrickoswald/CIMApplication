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
                this._onMap = false;
                this._theme = theme;
                this._template =
                "<div class='card'>\n" +
                "  <div class='card-body'>\n" +
                "    <h5 class='card-title'>Legend\n" +
                "    <label for='simulation_slider'>\n" +
                "      <input id='simulation_slider' type='text'/>\n" +
                "      Time\n" +
                "    </label>\n" +
                "  </div>\n" +
                "</div>\n";
            }

            onAdd (map)
            {
                this._map = map;
                this._items = this._theme.getItems ();
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
                this._onMap = true;
                return (this._container);
            }

            onRemove ()
            {
                this._container.parentNode.removeChild (this._container);
                this._map = undefined;
                this._onMap = false;
            }

            getDefaultPosition ()
            {
                return ("bottom-right");
            }

            visible ()
            {
                return (this._onMap);
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
        }

        return (SimulationLegend);
    }
)