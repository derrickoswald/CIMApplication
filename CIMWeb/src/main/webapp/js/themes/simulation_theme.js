/**
 * Simulation theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./simulation_legend"],
    /**
     * @summary Theme on simulation output.
     * @description Theme class for colorizing by (eventually percent maximum) cable current and (eventually) deviation from nominal voltage.
     * @name simulation_theme
     * @exports simulation_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, SimulationLegend)
    {
        class SimulationTheme extends DefaultTheme
        {
            constructor (simulation, times)
            {
                super ();
                this._simulation = simulation;
                this._times = times;
                this._legend = new SimulationLegend (this, times);
                this._legend.legend_change_listener (this.slider_changed.bind (this));
            }

            getName ()
            {
                return ("SimulationTheme");
            }

            getTitle ()
            {
                return ("Simulation results");
            }

            getDescription ()
            {
                return ("Equipment and cables colored by simulation values.");
            }

            getLegend ()
            {
                return (this._legend);
            }

            slider_changed (value)
            {
                var val = "T" + value;
                this._TheMap.setPaintProperty ("lines", "line-color", { type: "exponential", property: val, stops: [ [0.0, "RGB(102, 255, 0)"], [10.0, "RGB(355,0,127)"] ],});
            }
        }

        return (SimulationTheme);
    }
)