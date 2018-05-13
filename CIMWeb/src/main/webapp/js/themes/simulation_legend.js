/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["../mustache", "./clock", "../daterangepicker"],
    /**
     * @summary Sumulation legend control.
     * @description UI element for the simulation legend.
     * @name simulation_legend
     * @exports simulation_legend
     * @version 1.0
     */
    function (mustache, Clock, DateRangePicker)
    {
        class SimulationLegend
        {
            constructor (theme)
            {
                this._theme = theme;
                this._clock = new Clock ();
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
                          <input id="simulation_date" class="form-control" type="date" name="simulation_date" max="3000-12-31" min="1000-01-01">
                          <span>{{{clock}}}</span>
                        </div>
                        <div class="card-footer">
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
                var self = this;
                this._container.innerHTML = mustache.render (this._template, { "clock": (text, render) => self._clock.getSVG () });
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

            initialize ()
            {
                // https://github.com/seiyria/bootstrap-slider v10.0.0
                this._slider = new Slider (
                    document.getElementById ("simulation_slider"),
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
                this._slider.on ("change", this.legend_change.bind (this));

                var calendar = document.getElementById ("simulation_date");
                if (calendar)
                {
                    var start = new Date (this._times.start).toISOString ();
                    start = start.substring (start.indexOf ("T"));
                    var end = new Date (this._times.end).toISOString ();
                    end = start.substring (end.indexOf ("T"));
                    calendar.setAttribute ("min", start);
                    calendar.setAttribute ("max", end);
                }

//                           <span><input id="simulation_date" type="text" value=""/></span>

//                var start = new Date (this._times.start);
//                var end = new Date (this._times.end);
//                this._daterange = new DateRangePicker (
//                    "#simulation_date",
//                    {
//                        timePicker: true,
//                        timePickerIncrement: 15,
//                        locale: {
//                            format: 'YYYY.MM.DD HH:mm'
//                        },
//                        timePicker24Hour: true,
//                        linkedCalendars: false,
//                        singleDatePicker: true,
//                        startDate: start,
//                        endDate: end,
//                        minDate: start,
//                        maxDate: end,
//                        showDropdowns: true,
//                        opens: "up"
//                        //showISOWeekNumbers: true
//                    },
//                    (start, end, label) => false
//                );
            }

            legend_change (obj)
            {
                var value = new Date (obj.newValue);
                this._clock.setTime (value);
                var date = value.toISOString ();
                date = date.substring (0, date.indexOf ("T"));
                var calendar = document.getElementById ("simulation_date");
                calendar.value = date;
                if (this._legend_listener)
                    this._legend_listener (obj.newValue);
            }

            legend_change_listener (fn)
            {
                this._legend_listener = fn;
            }

            setTimes (times) // { start: start, end: end }
            {
                this._times = times;
                var calendar = document.getElementById ("simulation_date");
                if (calendar)
                {
                    var start = new Date (this._times.start).toISOString ();
                    start = start.substring (start.indexOf ("T"));
                    var end = new Date (this._times.end).toISOString ();
                    end = start.substring (end.indexOf ("T"));
                    calendar.setAttribute ("min", start);
                    calendar.setAttribute ("max", end);
                }
            }

            getTimes ()
            {
                 return (this._times);
            }
        }

        return (SimulationLegend);
    }
)