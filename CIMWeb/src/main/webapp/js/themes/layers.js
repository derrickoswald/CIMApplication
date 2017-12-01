/**
 * Layer definitions for CIM Application
 */
"use strict";

define
(
    [],
    /**
     * @summary Standard layer definitions.
     * @description Mapbox layer defintions.
     * @name layers
     * @exports layers
     * @version 1.0
     */
    function ()
    {
        /**
         * Create a line layer object.
         * @param {String} id - the layer id
         * @param {String} source - the data source
         * @param {String} color - the line color
         * @param {Any[]} filter - optional filter to apply to the lines
         * @returns {Object} the layer
         * @function line_layer
         * @memberOf module:layers
         */
        function line_layer (id, source, color, filter)
        {
            var ret =
                {
                    id: id,
                    type: "line",
                    source: source,
                    layout:
                    {
                        "line-join": "round",
                        "line-cap": "round"
                    },
                    paint:
                    {
                        "line-color": color,
                        "line-width": 3
                    }
                };
            if ("undefined" != typeof (filter) && (null != filter))
                ret.filter = filter;

            return (ret);
        }

        /**
         * Create a circle layer object.
         * @param {String} id - the layer id
         * @param {String} source - the data source
         * @param {String} color - the symbol color
         * @param {Any[]} filter - optional filter to apply to the points
         * @returns {Object} the layer
         * @function circle_layer
         * @memberOf module:layers
         */
        function circle_layer (id, source, color, filter)
        {
            var ret =
                {
                    id: id,
                    type: "circle",
                    source: source,
                    minzoom: 14,
                    maxzoom: 17,
                    paint:
                    {
                        "circle-radius": 5, // Optional number. Units in pixels. Defaults to 5.
                        "circle-color": color, // Optional color. Defaults to #000000.
                        "circle-blur": 0, // Optional number. Defaults to 0. 1 blurs the circle such that only the centerpoint is full opacity.
                        "circle-opacity": 1, // Optional number. Defaults to 1.
                        "circle-translate": [0, 0], // Optional array. Units in pixels. Defaults to 0,0. Values are [x, y] where negatives indicate left and up, respectively.
                        "circle-translate-anchor": "map", // Optional enum. One of map, viewport. Defaults to map. Requires circle-translate.
                    }
                };
            if ("undefined" != typeof (filter) && (null != filter))
                ret.filter = filter;

            return (ret);
        }

        /**
         * Create a symbol layer object.
         * @param {String} id - the layer id
         * @param {String} source - the data source
         * @param {String} color - the symbol color
         * @param {Any[]} filter - optional filter to apply to the points
         * @returns {Object} the layer
         * @function symbol_layer
         * @memberOf module:layers
         */
        function symbol_layer (id, source, color, filter)
        {
            var ret =
                {
                    id: id,
                    type: "symbol",
                    source: source,
                    minzoom: 17,
                    interactive: true,
                    layout:
                    {
                        "icon-image": "{symbol}",
                        "icon-allow-overlap": true,
                        "icon-size":
                        {
                            stops: [[17, 0.1875], [18, 0.25], [19, 0.3], [20, 0.45], [21, 0.9], [22, 1.8], [23, 3.75], [24, 7.5], [25, 10.0]]
                        },
                        "icon-rotate": 0.0,
                        "icon-offset": [0, 0],
                        "text-field": "{name}",
                        "text-font": ["Open Sans Semibold", "Arial Unicode MS Bold"],
                        "text-offset":
                        {
                            stops: [[18, [0, 1.0]], [20, [0, 2.0]], [21, [0, 3.0]], [22, [0, 4.0]], [23, [0, 5.0]], [24, [0, 6.0]], [25, [0, 7.0]]]
                        },
                        "text-anchor": "top",
                        "text-allow-overlap": true,
                        "text-size":
                        {
                            stops: [[17, 4], [18, 8], [19, 12], [20, 14], [21, 18], [22, 24], [23, 30], [24, 38], [25, 48]]
                        }
                    },
                    paint:
                    {
                        "icon-color": color,
                        "text-color": color
                    }
                };
            if ("undefined" != typeof (filter) && (null != filter))
                ret.filter = filter;

            return (ret);
        }

        return (
            {
                line_layer: line_layer,
                circle_layer: circle_layer,
                symbol_layer: symbol_layer
            }
        );
    }
);