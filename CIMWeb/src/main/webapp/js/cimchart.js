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
        }

        return (CIMChart);
    }
)