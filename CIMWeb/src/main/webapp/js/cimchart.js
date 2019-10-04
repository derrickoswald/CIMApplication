/**
 * Chart control for CIM Application
 */
"use strict";

define
(
    ["highstock", "cimquery", "cimcassandra", "mustache"],
    /**
     * @summary Chart control.
     * @description UI element for displaying measured, simulated and summarized data.
     * @exports cimchart
     * @version 1.0
     */
    function (notaAMDmodule, cimquery, cimcassandra, mustache)
    {
        class CIMChart
        {
            constructor (cimmap)
            {
                this._cimmap = cimmap;
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl card card_resizeable";
                // add the chart div
                const text = document.createElement ("div");
                text.id = "chart";
                text.className = "card-body";
                text.setAttribute ("style", "min-width: 1000px; height: 500px; margin: 0 auto; position: relative;");
                this._container.appendChild (text);

                // add choices dropdown placeholder
                const dropdown = document.createElement ("div");
                dropdown.id = "chart_source_menu";
                dropdown.setAttribute ("style", "position: absolute; top: 2px; left: 8px; display: none;");
                dropdown.innerHTML = `
<div class="dropdown show">
  <a class="dropdown-toggle" href="#" role="button" id="chart_sources_link" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
    Sources
  </a>
  <div id="chart_sources" class="dropdown-menu" aria-labelledby="chart_source_link">
  </div>
</div>
`;
                this._container.appendChild (dropdown);

                // add close button
                const close = document.createElement ("button");
                close.className = "close";
                close.setAttribute ("type", "button");
                close.setAttribute ("aria-label", "Close");
                close.setAttribute ("style", "position: absolute; top: 2px; right: 8px;");
                close.innerHTML = `<span aria-hidden="true">&times;</span>`;
                this._container.appendChild (close);
                this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                this._cimmap.add_feature_listener (this);
                return (this._container);
            }

            onRemove ()
            {
                this._cimmap.remove_feature_listener (this);
                // destroy the chart
                if (this._theChart)
                    delete this._theChart;
                // destroy the container
                if (this._container)
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

            changeSimulations (event)
            {
                const simulation = event.target.value;
                const checked = event.target.checked;
                const index = this._selected_simulations.indexOf (simulation);
                if (index > -1)
                    this._selected_simulations.splice (index, 1);
                if (checked)
                    this._selected_simulations.push (simulation);
            }

            chooseSimulations (div)
            {
                const template =
                    `
<div style="margin-top: 20px;">
    {{#simulations}}
    <div class="form-check">
        <input id="{{id}}" class="form-check-input" type="checkbox" name="{{name}}" value="{{id}}">
        <label class="form-check-label" for="{{id}}">{{name}} {{description}}</label>
    </div>
    {{/simulations}}
</div>
`;
                div.innerHTML = mustache.render (template, { simulations: this._simulations });
                this._selected_simulations = this._selected_simulations || [];
                const elements = div.getElementsByTagName ("INPUT");
                for (let i = 0; i < elements.length; i++)
                {
                    const checkbox = elements[i];
                    if (-1 !== this._selected_simulations.indexOf (checkbox.getAttribute ("id")))
                        checkbox.setAttribute ("checked", true);
                    checkbox.addEventListener ("change", this.changeSimulations.bind (this));
                }
            }

            initialize ()
            {
                let self = this;
                cimcassandra.getAllSimulationsWithDetails ()
                .then (
                    (simulations) =>
                    {
                        self._simulations = simulations;

                        // for now, jam the chooseSimulations in the blank panel
                        this.chooseSimulations (document.getElementById ("chart"));

                        // check for an already selected element
                        if (self._cimmap.get_selected_feature ())
                            self.selection_change (self._cimmap.get_selected_feature (), self._cimmap.get_selected_features ());
                    }
                );
            }

            /**
             * Load new data depending on the selected min and max.
             */
            refetch (event)
            {
                if (this._selected_simulations && 0 < this._selected_simulations.length)
                {
                    // this._theChart.showLoading ("Querying Cassandra...");
                    // const simulation = this._simulations[0];
                    // console.log ("from " + event.min + " to " + event.max);
                    // const self = this;
                    // cimquery.queryPromise ({sql: `select * from ${ simulation.output_keyspace }.simulation`, cassandra: true})
                    //     .then (data =>
                    //         {
                    //             // chart.series[0].setData(data);
                    //             self._theChart.hideLoading ();
                    //         }
                    //     );
                }
            }

            // series is an array of objects with at least { name: "XXX", data: [] }
            setChart (title, series)
            {
                // delete any existing chart
                const chart = document.getElementById ("chart");
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
                        chart:
                        {
                            marginRight: 200,
                            styledMode: false
                        },

                        title:
                        {
                            text: title
                        },

                        xAxis:
                        {
                            minRange: 10800 * 1000, // 3 hours
                            events:
                            {
                                afterSetExtremes: this.refetch.bind (this)
                            }
                        },

                        dataGrouping:
                        {
                            enabled: false
                        },

                        series: series,

                        navigator:
                        {
                            adaptToUpdatedData: false
                        },

                        legend:
                        {
                            enabled: true,
                            floating: true,
                            backgroundColor: '#FCFFC5',
                            borderColor: 'black',
                            borderWidth: 1,
                            layout: 'vertical',
                            align: 'right'
                        }
                    }
                );
            }

            deleteChartCursor ()
            {
                const chart = this._theChart;
                if (chart.cursor)
                {
                    chart.cursor.destroy ();
                    delete chart.cursor;
                }
            }

            // see: http://jsfiddle.net/aryzhov/pkfst550/
            binarySearch (ar, el, compare_fn)
            {
                let m = 0;
                let n = ar.length - 1;
                while (m <= n)
                {
                    const k = (n + m) >> 1;
                    const cmp = compare_fn (el, ar[k]);
                    if (cmp > 0)
                        m = k + 1;
                    else if (cmp < 0)
                        n = k - 1;
                    else
                        return (k);
                }
                return (-m - 1);
            }

            drawChartCursor (value)
            {
                const chart = this._theChart;
                const points = chart.series[0].points;
                const min = points[0].x;
                const max = points[points.length - 1].x;
                if ((min <= value) && (max >= value))
                {
                    const target = this.binarySearch (points, value, (t, p) => t - p.x);
                    const point = (target < 0) ? points[Math.min (-(target + 1), points.length - 1)] : points[target];
                    const x = chart.plotLeft + point.plotX;
                    const path = ['M', x, chart.plotTop, 'L', x, chart.plotTop + chart.plotHeight];

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
                const chart = document.getElementById ("chart");
                if (chart)
                    chart.innerHTML = contents;
                if (this._theChart)
                    delete this._theChart;
                const menu = document.getElementById ("chart_source_menu");
                menu.style.display = "none";
            }

            getDataFor (feature)
            {
                const chart = document.getElementById ("chart");
                if (chart)
                    chart.innerHTML = "<b>fetching data for " + feature + "</b>";
                // find out what data we have, compose a { name: xxx, query: yyy } array
                const queries = [];
                this._simulations.filter (x => this._selected_simulations.includes (x.id)).forEach (
                    simulation =>
                    {
                        const start = new Date (simulation.start_time).getTime ();
                        const end = new Date (simulation.end_time).getTime ();
                        simulation.players.forEach (
                            player =>
                            {
                                if (player.mrid === feature)
                                    queries.push (
                                        {
                                            name: simulation.name + " " + player.type + " (Wh)", // ToDo: how to get units from the query
                                            sql: `select time, real_a, imag_a from ${ simulation.input_keyspace }.measured_value where mrid = '${ player.mrid }' and type = '${ player.type }' and time >= ${ start } and time <= ${ end } allow filtering`,
                                            cassandra: true
                                        }
                                    );
                            }
                        );
                        simulation.recorders.forEach (
                            recorder =>
                            {
                                if (recorder.mrid === feature)
                                {
                                    let smallest = Number.MAX_VALUE;
                                    for (let interval in recorder.aggregations)
                                        if (recorder.aggregations.hasOwnProperty (interval))
                                            if (interval < smallest)
                                                smallest = interval;
                                    const period = (recorder.interval * 1000) * smallest;
                                    queries.push (
                                        {
                                            name: simulation.name + " " + recorder.type + " (" + recorder.unit + ")",
                                            sql: `select time, real_a, imag_a from ${ simulation.output_keyspace }.simulated_value where simulation = '${ simulation.id }' and mrid = '${ recorder.mrid }' and type = '${ recorder.type }' and period = ${ period }`,
                                            cassandra: true
                                        }
                                    );
                                }
                            }
                        );
                    }
                );

                if (0 !== queries.length)
                {
                    const menu = document.getElementById ("chart_source_menu");
                    menu.style.display = "block";
                    const sources = document.getElementById ("chart_sources");
                    queries.forEach (x => { x.checked = true });
                    const template = `
    {{#queries}}
    <div class="form-check">
        <input id="{{name}}" class="form-check-input" type="checkbox" name="{{name}}" value="{{name}}" {{#checked}} checked{{/checked}}>
        <label class="form-check-label" for="{{name}}">{{name}}</label>
    </div>
    {{/queries}}
`;
                    sources.innerHTML = mustache.render (template, { queries: queries });
                    const self = this;
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
                                                const values = data.map (
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
                    .then (series => self.setChart.call (self, feature, series))
                    .then (() => self.checkForEvents.call (self, feature));
                }
                else
                {
                    // try for raw data
                    const keyspaces = [];
                    this._simulations.forEach (
                        simulation =>
                        {
                            if (!keyspaces.includes (simulation.input_keyspace))
                                keyspaces.push (simulation.input_keyspace);
                        }
                    );
                    if (0 !== keyspaces.length)
                    {
                        const self = this;
                        Promise.all (
                            keyspaces.map (
                                keyspace =>
                                {
                                    const name = keyspace + " Measured Values (Wh)"; // ToDo: how to get units from the query
                                    const sql = `select time, real_a, imag_a from ${ keyspace }.measured_value where mrid = '${ feature }' and type = 'energy'`;
                                    return (
                                        cimquery.queryPromise ({sql: sql, cassandra: true})
                                            .then (data =>
                                                {
                                                    // make a series
                                                    function rms (r, i) { return (Math.sqrt (r * r + i * i)); }
                                                    const values = data.map (
                                                        row =>
                                                        {
                                                            return ([(new Date (row.time)).getTime (), rms (row.real_a, row.imag_a)]);
                                                        }
                                                    )
                                                    .sort ((a, b) => a[0] - b[0]);
                                                    return ({ name: name, data: values});
                                                }
                                            )
                                        );
                                }
                            )
                        )
                        .then (
                            series =>
                            {
                                const data = series.filter (x => 0 !== x.data.length);
                                if (0 !== data.length)
                                    self.setChart.call (self, feature, data);
                                else
                                    self.clearChart ("<b>no data for " + feature + "</b>");
                            }
                        );
                    }
                    else
                        this.clearChart ("<b>no data for " + feature + "</b>");
                }
            }

            checkForEvents (feature)
            {
                const self = this;
                self._simulations.filter (x => self._selected_simulations.includes (x.id)).forEach (
                    simulation =>
                    {
                        cimquery.queryPromise ({ sql: `select mrid, type, severity, TOUNIXTIMESTAMP(start_time) as start_time, TOUNIXTIMESTAMP(end_time) as end_time from ${ simulation.output_keyspace }.simulation_event where simulation='${ simulation.id }' and mrid = '${ feature }' limit 5000 allow filtering`, cassandra: true })
                            .then (
                                function (events)
                                {
                                    // console.log (JSON.stringify (events, null, 4));
                                    events.forEach (
                                        event =>
                                        {
                                            const range = {
                                                from: event.start_time,
                                                to: event.end_time
                                            };
                                            self._theChart.xAxis.map (
                                                axis =>
                                                {
                                                    const band = axis.addPlotBand (range);
                                                    const color = {
                                                        "fill": event.severity === 1 ? "#FF0000" : "#FFA500"
                                                    };
                                                    band.svgElem.css (color);
                                                }
                                            );
                                        }
                                    );
                                }
                            );
                    }
                );
            }

            /**
             * Display the selected object data.
             */
            selection_change (current_feature, current_selection)
            {
                if (null != current_feature)
                    this.getDataFor (current_feature);
                else
                    this.clearChart ();
            }
        }

        return (CIMChart);
    }
);
