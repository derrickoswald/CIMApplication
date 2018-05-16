/**
 * Chart control for CIM Application
 */
"use strict";

define
(
    ["highstock"],
    /**
     * @summary Chart control.
     * @description UI element for displaying measured, simulated and summarized data.
     * @name cimchart
     * @exports cimchart
     * @version 1.0
     */
    function (notaAMDmodule)
    {
        class CIMChart
        {
            constructor ()
            {
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl card";
                var text = document.createElement ("div");
                text.id = "chart";
                text.className = "card-body";
                text.setAttribute ("style", "min-width: 600px; height: 400px; margin: 0 auto;");
                text.innerHTML = "";
                this._container.appendChild (text);
                return (this._container);
            }

            onRemove ()
            {
                // destroy the container
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            getDefaultPosition ()
            {
                return ("bottom-left");
            }

            addChart (title, name, data)
            {
                // Create the chart
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

                        series:
                        [
                            {
                                name: name,
                                data: data,
                                step: true,
                                tooltip:
                                {
                                    valueDecimals: 2
                                }
                            }
                        ]
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


        }

        return (CIMChart);
    }
)
