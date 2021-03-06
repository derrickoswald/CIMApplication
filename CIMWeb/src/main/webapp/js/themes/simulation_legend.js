/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["mustache", "./clock"],
    /**
     * @summary Simulation legend control.
     * @description UI element for the simulation legend.
     * @exports simulation_legend
     * @version 1.0
     */
    function (mustache, Clock)
    {
        class SimulationLegend
        {
            constructor (theme)
            {
                this._theme = theme;
                this._clock = new Clock ();
                this._quality_factors =
                [
                    {
                        title: "Events",
                        id: "events",
                        selected: false
                    },
                    {
                        title: "Load factor (0 &rarr; 1)",
                        id: "load_factor",
                        selected: false
                    },
                    {
                        title: "Coincidence factor (0 &rarr; 1)",
                        id: "coincidence_factor",
                        selected: false
                    },
                    {
                        title: "Diversity factor (1 &rarr; &infin;)",
                        id: "diversity_factor",
                        selected: false
                    },
                    {
                        title: "Responsibility factor (0 &rarr; 1)",
                        id: "responsibility_factor",
                        selected: false
                    },
                    {
                        title: "Smart meter measurements (Wh)",
                        id: "measurements",
                        selected: false
                    }
                ];
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
                          <select id="quality_factor" class="form-control custom-select">
                            {{#quality_factors}}
                            <option value="{{id}}"{{#selected}} selected{{/selected}}>{{{title}}}</option>
                            {{/quality_factors}}
                          </select>
                        </h6>
                        <div class="card-text">
                          <input id="simulation_date" class="form-control" type="date" name="simulation_date" max="3000-12-31" min="1000-01-01">
                          <span>{{{clock}}}</span>
                        </div>
                        <div class="card-footer">
                          <span id="player_action">{{{play}}}</span>
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
                const self = this;
                this._container.innerHTML = mustache.render (
                    this._template,
                    {
                        "quality_factors": self._quality_factors,
                        "clock": () => self._clock.getSVG (),
                        "play":  () => self.play ()
                    }
                );
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

            play ()
            {
                return (
`
<svg
     xmlns:dc="http://purl.org/dc/elements/1.1/"
     xmlns:cc="http://creativecommons.org/ns#"
     xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
     xmlns="http://www.w3.org/2000/svg"
     id="svg8"
     version="1.1"
     viewBox="0 0 6.5391078 7.3986402"
     height="5mm"
     width="5mm">
    <defs
         id="defs2" />
    <metadata
         id="metadata5">
        <rdf:RDF>
            <cc:Work
                 rdf:about="">
                <dc:format>image/svg+xml</dc:format>
                <dc:type
                     rdf:resource="http://purl.org/dc/dcmitype/StillImage" />
            </cc:Work>
        </rdf:RDF>
    </metadata>
    <g
         transform="translate(-13.359356,-215.3175)"
         id="layer1">
        <path
             d="m 19.407059,219.01682 -2.778125,1.60395 -2.778125,1.60395 0,-3.2079 0,-3.20791 2.778125,1.60395 z"
             id="path817"
             style="fill:#00ff00;fill-opacity:1;stroke:#00ff00;stroke-width:0.9829067;stroke-linecap:round;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-dashoffset:0;stroke-opacity:1" />
    </g>
</svg>
`
                );
            }

            pause ()
            {
                return (
`
<svg
     xmlns:dc="http://purl.org/dc/elements/1.1/"
     xmlns:cc="http://creativecommons.org/ns#"
     xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
     xmlns="http://www.w3.org/2000/svg"
     id="svg8"
     version="1.1"
     viewBox="0 0 6.6831808 6.6831813"
     height="5mm"
     width="5mm">
    <defs
         id="defs2" />
    <metadata
         id="metadata5">
        <rdf:RDF>
            <cc:Work
                 rdf:about="">
                <dc:format>image/svg+xml</dc:format>
                <dc:type
                     rdf:resource="http://purl.org/dc/dcmitype/StillImage" />
            </cc:Work>
        </rdf:RDF>
    </metadata>
    <g
         transform="translate(-13.419634,-215.68969)"
         id="layer1">
        <rect
             y="216.12086"
             x="13.850807"
             height="5.8208351"
             width="5.8208351"
             id="rect893"
             style="fill:#ff0000;fill-opacity:1;stroke:#ff0000;stroke-width:0.86234593;stroke-linecap:round;stroke-linejoin:round;stroke-miterlimit:4;stroke-dasharray:none;stroke-dashoffset:0;stroke-opacity:1" />
    </g>
</svg>
`
                );
            }

            currentQualityFactor ()
            {
                const selected = this._quality_factors.filter (x => x.selected);
                const current = (0 !== selected.length) ? selected[0] : this._quality_factors[0];
                return (current.id);
            }

            changeQualityFactor (event)
            {
                const selection = event.target.value;
                this._quality_factors.forEach (x => x.selected = false);
                this._quality_factors.filter (x => x.id === selection)[0].selected = true;
                if (this._legend_listener)
                    this._legend_listener (selection);
            }

            initialize ()
            {
                document.getElementById ("quality_factor").onchange = this.changeQualityFactor.bind (this);

                // https://github.com/seiyria/bootstrap-slider v10.0.0
                this._slider = new Slider (
                    document.getElementById ("simulation_slider"),
                    {
                        step: 1000 * 60 * 60 * 24, // one day in milliseconds
                        min: this._times.start,
                        max: this._times.end,
                        formatter: function (value)
                        {
                            const t = new Date (value);
                            return (t.toTimeString ().substring (0, 8));
                        },
                        value: this._times.start
                    }
                );
                this._slider.on ("change", this.legend_change.bind (this));
                this.setCalendar ();
                document.getElementById ("player_action").onclick = this.play_pause.bind (this);
            }

            legend_change (obj)
            {
                const value = new Date (obj.newValue);
                this._clock.setTime (value);
                let date = value.toISOString ();
                date = date.substring (0, date.indexOf ("T"));
                let calendar = document.getElementById ("simulation_date");
                calendar.value = date;
                if (this._legend_listener)
                    this._legend_listener (obj.newValue);
            }

            legend_change_listener (fn)
            {
                this._legend_listener = fn;
            }

            setCalendar ()
            {
                const calendar = document.getElementById ("simulation_date");
                if (calendar)
                {
                    let start = new Date (this._times.start).toISOString ();
                    start = start.substring (start.indexOf ("T"));
                    let end = new Date (this._times.end).toISOString ();
                    end = start.substring (end.indexOf ("T"));
                    calendar.setAttribute ("min", start);
                    calendar.setAttribute ("max", end);
                }
            }

            setTimes (times) // { start: start, end: end }
            {
                this._times = times;
                this.setCalendar ();
            }

            getTimes ()
            {
                 return (this._times);
            }

            advance ()
            {
                let now = this._slider.getValue ();
                now += 1000 * 60 * 60 * 24; // one day in milliseconds
                if (now > this._times.end)
                    this.play_pause ();
                else
                    this._slider.setValue (now, undefined, true);
            }

            play_pause ()
            {
                if (this.playing)
                {
                    window.clearInterval (this.playing);
                    delete this.playing;
                    document.getElementById ("player_action").innerHTML = this.play ();
                }
                else
                {
                    this.playing = window.setInterval (this.advance.bind (this), 2000); // two seconds
                    document.getElementById ("player_action").innerHTML = this.pause ();
                }
            }
        }

        return (SimulationLegend);
    }
);