/**
 * Chart control for CIM Application
 */
"use strict";

define
(
    ["highstock", "cimquery", "mustache"],
    /**
     * @summary Chart control.
     * @description UI element for displaying measured, simulated and summarized data.
     * @name cimchart
     * @exports cimchart
     * @version 1.0
     */
    function (notaAMDmodule, cimquery, mustache)
    {
        class CIMChart
        {
            constructor (cimmap)
            {
                this._keyspace = "cimapplication";
                this._cimmap = cimmap;
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl card card_resizeable";
                // add the chart div
                var text = document.createElement ("div");
                text.id = "chart";
                text.className = "card-body";
                text.setAttribute ("style", "min-width: 600px; height: 400px; margin: 0 auto; position: relative;");
                text.innerHTML = "";
                this._container.appendChild (text);
                // add close button
                var close = document.createElement ("button");
                close.className = "close";
                close.setAttribute ("type", "button");
                close.setAttribute ("aria-label", "Close");
                close.setAttribute ("style", "position: absolute; top: 2px; right: 8px;");
                close.innerHTML = `<span aria-hidden="true">&times;</span>`;
                this._container.appendChild (close);
                this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                this._cimmap.add_feature_listener (this);
                this.chooseKeyspace (text);
                return (this._container);
            }

            onRemove ()
            {
                this._cimmap.remove_feature_listener (this);
                // destroy the chart
                if (this._theChart)
                    delete this._theChart;
                // destroy the container
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            getDefaultPosition ()
            {
                return ("bottom-left");
            }

            close (event)
            {
                this._map.removeControl (this);
            }

            visible ()
            {
                return ("undefined" != typeof (this._container));
            }

            changeKeyspace (event)
            {
                var keyspace = document.getElementById ("current_keyspace").value;
                this._keyspace = keyspace;
                this.initialize ();
            }

            chooseKeyspace (div)
            {
                var self = this;
                // get the keyspaces with simulation data
                var promise = cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering", cassandra: true })
                    .then (
                        function (resultset)
                        {
                            if (resultset.length > 0)
                            {
                                resultset.forEach (x => { if (x.keyspace_name == self._keyspace) x.selected = true });
                                var template =
                                `
<h6 style="margin-top: 20px;">
    <select id="current_keyspace" class="form-control custom-select">
        {{#keyspaces}}
        <option value="{{keyspace_name}}"{{#selected}} selected{{/selected}}>{{{keyspace_name}}}</option>
        {{/keyspaces}}
    </select>
</h6>
                                `;
                                var text = mustache.render (template, { keyspaces: resultset })
                                div.innerHTML = text;
                                document.getElementById ("current_keyspace").addEventListener ("change", self.changeKeyspace.bind (self));
                            }
                        }
                    );
            }

            initialize ()
            {
                this._simulations = [];
                var self = this;
                cimquery.queryPromise ({ sql: "select json * from " + self._keyspace + ".simulation", cassandra: true })
                    .then (
                        data =>
                        {
                            return (
                                Promise.all (
                                    data.map (
                                        row =>
                                        {
                                            var simulation = JSON.parse (row["[json]"]);
                                            self._simulations.push (simulation);
                                            return (
                                                cimquery.queryPromise ({ sql: "select * from " + self._keyspace + ".simulation_player where id = '" + simulation.id + "'", cassandra: true })
                                                    .then (players => simulation.players = players)
                                                    .then (() => { return (cimquery.queryPromise ({ sql: "select * from " + self._keyspace + ".simulation_recorder where id = '" + simulation.id + "'", cassandra: true })); })
                                                    .then (recorders => simulation.recorders = recorders)
                                                );
                                        }
                                    )
                                )
                                .then (
                                    () =>
                                    {
                                        // check for an already selected element
                                        if (self._cimmap.get_selected_feature ())
                                            self.selection_change (self._cimmap.get_selected_feature (), self._cimmap.get_selected_features ());
                                    }
                                )
                            );
                        }
                    );
            }

            // series is an array of objects with at least { name: "XXX", data: [] }
            setChart (title, series)
            {
                // delete any existing chart
                var chart = document.getElementById ("chart");
                if (chart)
                    chart.innerHTML = "";
                // create the chart
                series.forEach (data =>
                    {
                        if (!data.step)
                            data.step = true;
                        if (!data.tooltip)
                            data.tooltip =
                                {
                                    valueDecimals: 2
                                };
                    }
                );

                this._theChart = window.Highcharts.stockChart
                (
                    'chart',
                    {

                        title:
                        {
                            text: title
                        },

                        xAxis:
                        {
                            minRange: 10800 * 1000 // 3 hours
                        },

                        dataGrouping:
                        {
                            enabled: false
                        },

                        series: series
                    }
                );
            }

            deleteChartCursor ()
            {
                var chart = this._theChart;
                if (chart.cursor)
                {
                    chart.cursor.destroy ();
                    delete chart.cursor;
                }
            }

            // see: http://jsfiddle.net/aryzhov/pkfst550/
            binarySearch (ar, el, compare_fn)
            {
                var m = 0;
                var n = ar.length - 1;
                while (m <= n)
                {
                    var k = (n + m) >> 1;
                    var cmp = compare_fn (el, ar[k]);
                    if (cmp > 0)
                        m = k + 1;
                    else if(cmp < 0)
                        n = k - 1;
                    else
                        return k;
                }
                return -m - 1;
            }

            drawChartCursor (value)
            {
                var chart = this._theChart;
                var points = chart.series[0].points;
                var min = points[0].x;
                var max = points[points.length - 1].x;
                if ((min <= value) && (max >= value))
                {
                    var target = this.binarySearch (points, value, (t, p) => t - p.x);
                    var point = (target < 0) ? points[Math.min (-(target + 1), points.length - 1)] : points[target];
                    var x = chart.plotLeft + point.plotX;
                    var path = ['M', x, chart.plotTop, 'L', x, chart.plotTop + chart.plotHeight];

                    if (chart.cursor)
                        // update line
                        chart.cursor.attr({ d: path });
                    else
                        // draw line
                        chart.cursor = chart.renderer.path (path).attr ({ 'stroke-width': 2, stroke: 'green', zIndex: 10 }).add ();
                }
                else
                    this.deleteChartCursor ();
            }

            clearChart (contents)
            {
                contents = contents || "";
                var chart = document.getElementById ("chart");
                if (chart)
                    chart.innerHTML = contents;
                if (this._theChart)
                    delete this._theChart;
            }

            getDataFor (feature)
            {
                var chart = document.getElementById ("chart");
                if (chart)
                    chart.innerHTML = "<b>fetching data for " + feature + "</b>";
                // find out what data we have, compose a { name: xxx, query: yyy } array
                var queries = [];
                this._simulations.forEach (
                    simulation =>
                    {
                        var start = new Date (simulation.start_time).getTime ();
                        var end = new Date (simulation.end_time).getTime ();
                        simulation.players.forEach (
                            player =>
                            {
                                if (player.mrid == feature)
                                    queries.push (
                                        {
                                            name: simulation.name + " " + player.type + " (Wh)", // ToDo: how to get units from the query
                                            sql: "select time, real_a, imag_a from " + simulation.input_keyspace + ".measured_value where mrid = '" + player.mrid + "' and type = '" + player.type + "' and time >= " + start + " and time <= " + end + " allow filtering",
                                            cassandra: true
                                        }
                                    );
                            }
                        );
                        simulation.recorders.forEach (
                            recorder =>
                            {
                                if (recorder.mrid == feature)
                                {
                                    var biggest = -1;
                                    for (var interval in recorder.aggregations)
                                        if (recorder.aggregations.hasOwnProperty (interval))
                                            if (interval > biggest)
                                                biggest = interval;
                                    var period = (recorder.interval * 1000) * biggest;
                                    queries.push (
                                        {
                                            name: simulation.name + " " + recorder.type + " (" + recorder.unit + ")",
                                            sql: "select time, real_a, imag_a from " + simulation.output_keyspace + ".simulated_value where simulation = '" + simulation.id + "' and mrid = '" + recorder.mrid + "' and type = '" + recorder.type + "' and period = " + period,
                                            cassandra: true
                                        }
                                    );
                                }
                            }
                        );
                    }
                );

                if (0 != queries.length)
                {
                    var self = this;
                    Promise.all (
                        queries.map (
                            query =>
                            {
                                return (
                                    cimquery.queryPromise (query)
                                        .then (data =>
                                            {
                                                // make a series
                                                function rms (r, i) { return (Math.sqrt (r * r + i * i)); }
                                                var values = data.map (
                                                    row =>
                                                    {
                                                        return ([(new Date (row.time)).getTime (), rms (row.real_a, row.imag_a)]);
                                                    }
                                                )
                                                .sort ((a, b) => a[0] - b[0]);
                                                return ({ name: query.name, data: values});
                                            }
                                        )
                                    );
                            }
                        )
                    )
                    .then (series => self.setChart.call (self, feature, series));
                }
                else
                    this.clearChart ("<b>no data for " + feature + "</b>");
            }

            /**
             * Connect the selected object at user selected terminal synchronously.
             */
            selection_change (current_feature, current_selection)
            {
                if (null != current_feature)
                    var data = this.getDataFor (current_feature);
                else
                    this.clearChart ();
            }
        }

        return (CIMChart);
    }
)
