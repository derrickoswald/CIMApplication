/**
 * Map functions for CIM Application
 */
"use strict";
define
(
    [],
    /**
     * @summary Main entry point for the application.
     * @description Performs application initialization as the first step in the RequireJS load sequence.
     * @see http://requirejs.org/docs/api.html#data-main
     * @name cimmap
     * @exports cimmap
     * @version 1.0
     */
    function ()
    {
        /**
         * The map object.
         * @see https://www.mapbox.com
         */
        var TheMap = null;

        /**
         * The user specific token to access mapbox tiles.
         */
        var TheToken = "pk.eyJ1IjoiZGVycmlja29zd2FsZCIsImEiOiJjaWV6b2szd3MwMHFidDRtNDZoejMyc3hsIn0.wnEkePEuhYiNcXDLACSxVw";

        /**
         * The CIM file contents after load.
         */
        var CIM_Data = null;

        /**
         * The last selected feature.
         */
        var CURRENT_FEATURE = null;

        /**
         * The last selected features.
         */
        var CURRENT_SELECTION = null;

        /**
         * Set the CIM data for the map to draw.
         * @param {JSON} Data parsed from the cim module.
         * @function set_data
         * @memberOf module:cimmap
         */
        function set_data (data)
        {
            CIM_Data = data;
            redraw ();
        }

        /**
         * Get the CIM data the map is displaying.
         * @return {JSON} Data parsed from the cim module.
         * @function get_data
         * @memberOf module:cimmap
         */
        function get_data ()
        {
            return (CIM_Data);
        }

        /**
         * Get the user's choice for showing internal features.
         * @returns {boolean} <code>true</code> if internal features should be shown, <code>false</code> otherwise
         * @function show_internal_features
         * @memberOf module:cimmap
         */
        function show_internal_features ()
        {
            return (document.getElementById ("show_internal_features").checked && mapboxgl.supported ());
        }

        /**
         * Create a circle layer object.
         * @param {String} id - the layer id
         * @param {Any[]} filter - the filter to apply to the points
         * @param {String} color - the symbol color to use (doesn't work)
         * @returns {Object} the layer
         * @function circle_layer
         * @memberOf module:cimmap
         */
        function circle_layer (id, filter, color)
        {
            return (
                {
                    id: id,
                    type: "circle",
                    source: "the cim points",
                    minzoom: 14,
                    maxzoom: 17,
                    filter: filter,
                    paint:
                    {
                        "circle-radius": 5, // Optional number. Units in pixels. Defaults to 5.
                        "circle-color": color, // Optional color. Defaults to #000000.
                        "circle-blur": 0, // Optional number. Defaults to 0. 1 blurs the circle such that only the centerpoint is full opacity.
                        "circle-opacity": 1, // Optional number. Defaults to 1.
                        "circle-translate": [0, 0], // Optional array. Units in pixels. Defaults to 0,0. Values are [x, y] where negatives indicate left and up, respectively.
                        "circle-translate-anchor": "map", // Optional enum. One of map, viewport. Defaults to map. Requires circle-translate.
                    }
                }
            );
        }

        /**
         * Create a symbol layer object.
         * @param {String} id - the layer id
         * @param {Any[]} filter - the filter to apply to the points
         * @param {String} symbol - the symbol name
         * @param {Number} orientation - the symbol orientation
         * @param {Number[]} offset - the symbol offset
         * @param {String} color - the symbol color (doesn't work)
         * @returns {Object} the layer
         * @function symbol_layer
         * @memberOf module:cimmap
         */
        function symbol_layer (id, filter, symbol, orientation, offset, color)
        {
            //console.log (id + " " + JSON.stringify (filter, null, 4));
            return (
                {
                    id: id,
                    type: "symbol",
                    source: "the cim points",
                    minzoom: 17,
                    filter: filter,
                    interactive: true,
                    layout:
                    {
                        "icon-image": symbol,
                        "icon-allow-overlap": true,
                        "icon-size":
                        {
                            stops: [[17, 1], [18, 1], [19, 1.2], [20, 1.4], [21, 1.6], [22, 1.8], [23, 2], [24, 2.2], [25, 2.4]]
                        },
                        "icon-rotate": orientation,
                        "icon-offset": offset,
                        "text-field": "{name}",
                        "text-font": ["Open Sans Semibold", "Arial Unicode MS Bold"],
                        "text-offset": [0, 1],
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
                }
            );

        }

        /**
         * Add stylization information to elements and make a list of point and linear features.
         * @param {Object} psr - the hash table object with properties that are (PowerSystemResource) elements keyed by mRID.
         * @param {Object} locations - the hash table object with properties that are locations with arrays of coordinates.
         * @param {Object[]} points - the resultant list of point GeoJSON objects.
         * @param {Object} psr - the resultant list of linear GeoJSON objects.
         * @function process_spatial_objects
         * @memberOf module:cimmap
         */
        function process_spatial_objects (psr, locations, points, lines)
        {
            var coordinates;
            var location;
            for (var id in psr)
            {
                if (null != (location = psr[id].Location))
                {
                    if (null != (coordinates = locations[location]))
                    {
                        if (2 == coordinates.length)
                        {
                            points.features.push
                            (
                                {
                                    type : "Feature",
                                    geometry :
                                    {
                                        type : "Point",
                                        coordinates : [ coordinates[0], coordinates[1] ]
                                    },
                                    properties : psr[id]
                                }
                            );
                            psr[id].id = id;
                            psr[id].orientation = 0.0;
                            // assign the symbol
                            if (0 == id.indexOf ("TRA"))
                            {
                                psr[id].symbol = "transformer";
                                psr[id].color = "rgb(0, 255, 0)";
                            }
                            else if (0 == id.indexOf ("TEI"))
                            {
                                psr[id].symbol = "switch";
                                psr[id].color = "rgb(0, 0, 255)";
                            }
                            else if (0 == id.indexOf ("HAS"))
                            {
                                psr[id].symbol = "house_connection";
                                psr[id].color = "rgb(255, 0, 0)";
                            }
                            else
                            {
                                psr[id].symbol = "monument-24";
                                psr[id].color = "rgb(255, 255, 255)";
                            }
                        }
                        else
                        {
                            lines.features.push
                            (
                                {
                                    type : "Feature",
                                    geometry :
                                    {
                                        type : "LineString",
                                        coordinates : coordinates.reduce
                                        (
                                            function (ret, item)
                                            {
                                                var next;

                                                next = ret[ret.length - 1];
                                                if (!next || (2 <= next.length))
                                                {
                                                    next = [];
                                                    ret.push (next);
                                                }
                                                next.push (item);

                                                return (ret);
                                            },
                                            []
                                        )
                                    },
                                    properties : psr[id]
                                }
                            );
                            psr[id].id = id;
                        }
                    }
                }
            }
        }

        /**
         * @summary Gather position points into locations.
         * @description Convert sequences of position points into locations with coordinate array.
         * @function get_locations
         * @memberOf module:cimmap
         */
        function get_locations (points)
        {
            // the parsed PositionPoint set
            var points = CIM_Data.PositionPoint;
            // the parsed Location set
            var locations = CIM_Data.Location;
            // list of locations to exclude
            var blacklist = {};
            var ret = {};

            if (!show_internal_features ())
            {
                for (var location in locations)
                {
                    var l = locations[location];
                    if (l.CoordinateSystem != "wgs84")
                        blacklist[location] = true;
                }
            }
            for (var point in points)
            {
                var p = points[point];
                var location = p.Location;
                if ((null != location) && ("undefined" == typeof (blacklist[location])))
                {
                    if (null == ret[location])
                        ret[location] = [];
                    var seq = p.sequenceNumber;
                    if (null != seq)
                    {
                        ret[location][seq * 2] = p.xPosition;
                        ret[location][seq * 2 + 1] = p.yPosition;
                    }
                }
            }

            return (ret);
        }

        /**
         * Generate a map.
         * @function make_map
         * @memberOf module:cimmap
         */
        async function make_map ()
        {
            function sleep(ms)
            {
                return new Promise(resolve => setTimeout(resolve, ms));
            }
            do
                await sleep (2000);
            while (!TheMap.loaded ())

            // index of position point data by location
            var locations = get_locations ();
            // the lines GeoJSON
            var lines =
            {
                "type" : "FeatureCollection",
                "features" : []
            };
            // the points GeoJSON
            var points =
            {
                "type" : "FeatureCollection",
                "features" : []
            };
            process_spatial_objects (CIM_Data.PowerSystemResource, locations, points, lines);

            // remove previous layer data
            if (TheMap.getSource ("the cim lines"))
            {
                TheMap.removeLayer ("lines");
                TheMap.removeLayer ("lines_highlight");
                TheMap.removeLayer ("circle_transformer");
                TheMap.removeLayer ("circle_switch");
                TheMap.removeLayer ("circle_house_connection");
                TheMap.removeLayer ("circle_other");
                TheMap.removeLayer ("circle_highlight");
                TheMap.removeLayer ("symbol");
                TheMap.removeLayer ("symbol_highlight");
                TheMap.removeSource ("the cim lines");
                TheMap.removeSource ("the cim points");
            }

            // update the map
            TheMap.addSource
            (
                "the cim lines",
                {
                    type: "geojson",
                    data: lines,
                    maxzoom: 25
                }
            );

            TheMap.addSource
            (
                "the cim points",
                {
                    type: "geojson",
                    data: points,
                    maxzoom: 25
                }
            );

            TheMap.addLayer
            (
                {
                    id: "lines",
                    type: "line",
                    source: "the cim lines",
                    layout:
                    {
                        "line-join": "round",
                        "line-cap": "round"
                    },
                    paint:
                    {
                        "line-color": "#000",
                        "line-width": 3
                    }
                }
            );

            TheMap.addLayer
            (
                {
                    id: "lines_highlight",
                    type: "line",
                    source: "the cim lines",
                    filter: ["==", "mRID", ""],
                    layout:
                    {
                        "line-join": "round",
                        "line-cap": "round"
                    },
                    paint:
                    {
                        "line-color": "#ffff00",
                        "line-width": 3
                    }
                }
            );

            // simple circle from 14 to 17
            TheMap.addLayer (circle_layer ("circle_transformer", ["==", "symbol", "transformer"], "rgb(0, 255, 0)"));
            TheMap.addLayer (circle_layer ("circle_switch", ["==", "symbol", "switch"], "rgb(0, 0, 255)"));
            TheMap.addLayer (circle_layer ("circle_house_connection", ["==", "symbol", "house_connection"], "rgb(255, 0, 0)"));
            TheMap.addLayer (circle_layer ("circle_other", ["==", "symbol", "monument-24"], "black"));

            TheMap.addLayer (circle_layer ("circle_highlight", ["==", "mRID", ""], "rgb(255, 255, 0)"));

            // symbol icon from 17 and deeper
            // ToDo: color the icons according to color on the object
            TheMap.addLayer (symbol_layer ("symbol", ["!=", "mRID", ""], "{symbol}", 0.0, [0, 0], "rgb(0, 0, 0)"));

            TheMap.addLayer (symbol_layer ("symbol_highlight", ["==", "mRID", ""], "{symbol}", 0.0, [0, 0], "rgb(255, 255, 0)"));

        }

        /**
         * @summary Make the details non-model dialog visible.
         * @description Uses jQuery to show the panel.
         * @function show_details
         * @memberOf module:cimmap
         */
        function show_details ()
        {
            $("#feature_details").show ();
        }

        /**
         * @summary Make the details non-model dialog invisible.
         * @description Uses jQuery to hide the panel.
         * @function hide_details
         * @memberOf module:cimmap
         */
        function hide_details ()
        {
            $("#feature_details").hide (200);
        }

        /**
         * Show the content in a window.
         * @description Raise a popup window and populate it with the preformatted text provided.
         * @param {string} content - the detail content to display
         * @function showDetails
         * @memberOf module:cimmap
         */
        function showDetails (content)
        {
            var text =
                 "        <pre>" +
                 content +
                 "        </pre>";
            document.getElementById ("feature_detail_contents").innerHTML = text;
            show_details ();
        }

        /**
         * @summary Change the filter for the glow layers.
         * @description Applies the given filter to the highlight layers.
         * These layers are copies of the similarly named layers, but with a yellow color.
         * When a filter matches a feature, the yeloow layer is drawn on top of
         * the original layer creating a cheezy 'glow' effect.
         * Setting the filter to something that never matches effectively turns off the layer.
         * @param {string} filter - the filter to apply to the highlight layers
         * @function glow
         * @memberOf module:cimmap
         */
        function glow (filter)
        {
            TheMap.setFilter ("lines_highlight", filter);
            TheMap.setFilter ("circle_highlight", filter);
            TheMap.setFilter ("symbol_highlight", filter);
        }

        /**
         * @summary JSON replacer function for element details display.
         * @description Makes links out of strings that are also element id values.
         * @function detail_helper
         * @memberOf module:cimmap
         */
        function detail_helper (key, value)
        {
            var feature;
            if ((key == "orientation") || (key == "symbol") || (key == "color"))
                return undefined;
            if (typeof value === "string")
                if (null != (feature = CIM_Data.Element[value]))
                {
                    value = "<a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;" + value + "&quot;);})'>" + value + "</a>"
                }
            return (value);
        }

        /**
         * @summary Display the current feature properties and highlight it on the map.
         * @description Shows a JSON properties sheet in the details window,
         * and highlights the current feature in the map.
         * Other features in the current selection are provided links in the details window
         * to make them the current feature.
         * @function highlight
         * @memberOf module:cimmap
         */
        function highlight ()
        {
            var feature;
            if ((null != CIM_Data) && (null != CURRENT_FEATURE))
                if (null != (feature = CIM_Data.Element[CURRENT_FEATURE]))
                {
                    var text = JSON.stringify (feature, detail_helper, 2);
                    if (null != CURRENT_SELECTION)
                        for (var i = 0; i < CURRENT_SELECTION.length; i++)
                        {
                            if (CURRENT_SELECTION[i] != CURRENT_FEATURE)
                                text = text + "\n<a href='#' onclick='require([\"cimmap\"], function(cimmap) {cimmap.select (\"" + CURRENT_SELECTION[i] + "\");})'>" + CURRENT_SELECTION[i] + "</a>";
                        }
                    showDetails (text);
                    glow (["in", "mRID", CURRENT_FEATURE]);
                }
        }

        /**
         * @summary Clears the current feature and selection.
         * @description Hides the details non-modal dialog and reverts any highlighting in the map.
         * @function unhighlight
         * @memberOf module:cimmap
         */
        function unhighlight ()
        {
            glow (["==", "mRID", ""]);
            CURRENT_FEATURE = null;
            CURRENT_SELECTION = null;
            document.getElementById ("feature_detail_contents").innerHTML = "";
            hide_details ();
        }

        /**
         * @summary Handler for a current feature link click.
         * @description Sets the current feature and redisplay the details window and highlighting appropriately.
         * @function select
         * @memberOf module:cimmap
         */
        function select (mrid)
        {
            if (mrid != CURRENT_SELECTION)
            {
                CURRENT_FEATURE = mrid;
                if (!CURRENT_SELECTION.includes (mrid))
                    CURRENT_SELECTION = [mrid];
                highlight ();
            }
        }

        /**
         * Get the user's choice for through switch tracing.
         * @returns {boolean} <code>true</code> a trace through open switches should be done, <code>false</code> otherwise
         * @function trace_through_open_switches
         * @memberOf module:cimmap
         */
        function trace_through_open_switches ()
        {
            return (document.getElementById ("trace_through_open_switches").checked);
        }

        /**
         * Get the user's choice for through transformer tracing.
         * @returns {boolean} <code>true</code> a trace across voltage level changes should be done, <code>false</code> otherwise
         * @function trace_though_voltage_level_changes
         * @memberOf module:cimmap
         */
        function trace_though_voltage_level_changes ()
        {
            return (document.getElementById ("trace_though_voltage_level_changes").checked);
        }

        /**
         * Get voltage level boundary transformers.
         * @description Get a list of tranformers with ends where voltages differ.
         * @function get_transformers
         * @memberOf module:cimmap
         */
        function get_transformers ()
        {
            var transformers = {};
            var ret = {};

            for (var end in CIM_Data.PowerTransformerEnd)
            {
                var e = CIM_Data.PowerTransformerEnd[end]
                var transformer = e.PowerTransformer;
                if (null != transformer)
                {
                    if (null == transformers[transformer])
                        transformers[transformer] = [];
                    var no = e.endNumber;
                    if (null != no)
                        transformers[transformer][Number(no)] = e
                }
            }
            // eliminate step-down
            for (var trans in transformers)
            {
                var ends = transformers[trans];
                if (ends && ends.length > 1)
                {
                    var high_voltage = Number (CIM_Data.BaseVoltage[ends[1].BaseVoltage].nominalVoltage);
                    var unequal = false;
                    for (var i = 2; "undefined" != typeof (ends[i]); i++)
                        if (high_voltage != Number (CIM_Data.BaseVoltage[ends[i].BaseVoltage].nominalVoltage))
                            unequal = true;
                    if (unequal)
                        ret[trans] = ends; // could also store something else here - the test is just for non-null
                }
            }
            //alert(JSON.stringify(ret, null, 4));

            return (ret);
        }

        /**
         * Trace the currently selected object and highlight the results.
         * @description Traverse through the ConnectivityNode, Terminal and ConductingEquipment
         * to make a list of connected devices and wires. Then highlight them on screen.
         * @function trace
         * @memberOf module:cimmap
         */
        function trace ()
        {
            // the source feature
            var source;
            // the type of switch trace
            var through_opens = trace_through_open_switches ();
            // the type of transformer trace
            var through_voltages = trace_though_voltage_level_changes ();

            if (null == CIM_Data)
                alert ("no CIM data loaded");
            else if (null == CURRENT_FEATURE)
                alert ("no feature selected");
            else
            {
                // organize terminals by connectivity node and equipment
                var terminals_by_node = {};
                var terminals_by_equp = {};
                for (var t in CIM_Data.Terminal)
                {
                    var terminal = CIM_Data.Terminal[t];
                    var node = terminal.ConnectivityNode;
                    if (null != node)
                    {
                        if (null == terminals_by_node[node])
                            terminals_by_node[node] = [];
                        if (!terminals_by_node[node].includes (terminal.mRID))
                            terminals_by_node[node].push (terminal.mRID);
                    }
                    var equp = terminal.ConductingEquipment;
                    if (null != equp)
                    {
                        if (null == terminals_by_equp[equp])
                            terminals_by_equp[equp] = [];
                        if (!terminals_by_equp[equp].includes (terminal.mRID))
                            terminals_by_equp[equp].push (terminal.mRID);
                    }
                }

                // get the source equipment
                source = CIM_Data.ConductingEquipment[CURRENT_FEATURE];
                if (null == source)
                {
                    // try for a terminal
                    var term = CIM_Data.Terminal[CURRENT_FEATURE];
                    source = term ? CIM_Data.ConductingEquipment[term.ConductingEquipment] : null;
                }
                if (null == source)
                {
                    // try for a node
                    var terms = terminals_by_node[CURRENT_FEATURE];
                    if (terms)
                        for (var i = 0; (i < terms.length) && (null == source); i++)
                        {
                            var term = CIM_Data.Terminal[terms[i]];
                            source = term ? CIM_Data.ConductingEquipment[term.ConductingEquipment] : null;
                        }
                }
                if (null == source)
                    alert ("feature is not part of the topology");
                else
                {
                    // the list of traced conducting equipment
                    var equipment = [];
                    // the list of things to trace
                    var todo = [];
                    todo.push (source.mRID);
                    var transformers = (!through_voltages) ? get_transformers () : {};
                    // iterate until done
                    while ("undefined" != typeof (source = todo.pop ())) // if you call pop() on an empty array, it returns undefined
                    {
                        equipment.push (source);
                        var ce = CIM_Data.ConductingEquipment[source];
                        if (!through_opens && ((null == ce) || (ce.normalOpen == "true")))
                            continue;
                        var terms = terminals_by_equp[source];
                        if (!through_voltages && (null != transformers[source]))
                            continue;
                        if (null != terms)
                            for (var i = 0; i < terms.length; i++)
                            {
                                var terminal = CIM_Data.Terminal[terms[i]];
                                if (null != terminal)
                                {
                                    var equp = terminal.ConductingEquipment;
                                    if (null != equp)
                                        if (!equipment.includes (equp) && !todo.includes (equp))
                                            todo.push (equp); // this should never happen
                                    var node = terminal.ConnectivityNode;
                                    if (null != node)
                                    {
                                        var next = terminals_by_node[node];
                                        if (null != next)
                                            for (var j = 0; j < next.length; j++)
                                            {
                                                if (next[j] != terms[i]) // don't trace back the way we came
                                                {
                                                    var t = CIM_Data.Terminal[next[j]];
                                                    if (null != t)
                                                    {
                                                        var e = t.ConductingEquipment;
                                                        if (null != e)
                                                            if (!equipment.includes (e) && !todo.includes (e))
                                                                todo.push (e);
                                                    }
                                                }
                                            }
                                    }
                                }

                            }
                    }
                    // sort the list to make it easy to find an element
                    equipment.sort ();
                    // create the text to show in the details window
                    var text = JSON.stringify (CIM_Data.Element[CURRENT_FEATURE], detail_helper, 2) +
                        "\n" +
                        equipment.join (', ');
                    if (null != CURRENT_SELECTION)
                        for (var i = 0; i < CURRENT_SELECTION.length; i++)
                        {
                            if (CURRENT_SELECTION[i] != CURRENT_FEATURE)
                                text = text + "\n<a href='#' onclick='require([\"cimmap\"], function(cimmap) {cimmap.select (\"" + CURRENT_SELECTION[i] + "\");})'>" + CURRENT_SELECTION[i] + "</a>";
                        }
                    // post the text
                    showDetails (text);
                    // highlight the elements on screen
                    equipment.unshift ("in", "mRID");
                    glow (equipment);
                }
            }
        }

        /**
         * Compute the bounding box for the currentls selected element.
         * @description Look up all PositionPoint elements associated with elemen's location,
         * and compute the minimum bounding box that would enclose it.
         * @parm {string} id - the id of the element to process
         * @function get_bounding_box
         * @memberOf module:cimmap
         */
        function get_bounding_box (id)
        {
            var ret = null;

            var feature;
            var location;
            if (null != (feature = CIM_Data.Element[id]))
            {
                if (null != (location = feature.Location))
                {
                    var minx = Number.MAX_VALUE;
                    var maxx = Number.MIN_VALUE;
                    var miny = Number.MAX_VALUE;
                    var maxy = Number.MIN_VALUE;
                    var pp = CIM_Data.PositionPoint;
                    var valid = false;
                    for (var point in pp)
                    {
                        var p = pp[point];
                        if (location == p.Location)
                        {
                            var x = Number (p.xPosition);
                            var y = Number (p.yPosition);
                            if (minx > x)
                                minx = x;
                            if (maxx < x)
                                maxx = x;
                            if (miny > y)
                                miny = y;
                            if (maxy < y)
                                maxy = y;
                            valid = true;
                        }
                    }
                    if (valid)
                        ret = [[minx, miny], [maxx, maxy]];
                }
            }
            return (ret);
        }

        /**
         * Search the element identifiers to find those that match the search text.
         * @description Scan through all elements and make a list of those
         * where the id, mRID, name or aliasName match the user enetered text.
         * If some are found, highlight the first and zoom to the area that
         * contains the element.
         * @function search
         * @memberOf module:cimmap
         */
        function search ()
        {
            var text;
            if (null != CIM_Data)
                if ("" != (text = document.getElementById ("search_text").value))
                {
                    var match = [];
                    for (var id in CIM_Data.Element)
                        if (CIM_Data.Element[id].id == text)
                            match.push (id);
                        else if (CIM_Data.Element[id].mRID == text)
                            match.push (id);
                        else if (CIM_Data.Element[id].name == text)
                            match.push (id);
                        else if (CIM_Data.Element[id].aliasName == text)
                            match.push (id);
                    if (match.length > 0)
                    {
                        CURRENT_SELECTION = match;
                        CURRENT_FEATURE = match[0];
                        var current = null;
                        var bb = null;
                        for (var i = 0; i < CURRENT_SELECTION.length; i++)
                        {
                            bb = get_bounding_box (match[i]);
                            if (null != bb)
                            {
                                current = match[i];
                                break;
                            }
                        }
                        if (null != current)
                        {
                            CURRENT_FEATURE = current;
                            var x = (bb[1][0] - bb[0][0]) / 2.0 + bb[0][0];
                            var y = (bb[1][1] - bb[0][1]) / 2.0 + bb[0][1];
                            TheMap.easeTo
                            (
                                {
                                    center: [x, y],
                                    zoom: 17
                                }
                            );
                        }
                        else
                            alert ("No geometry for selected object(s)");
                        highlight ();
                    }
                    else
                        alert ("No matches found for '" + text + "'");
                }
                else
                    alert ("No search text");
            else
                alert ("No CIM data loaded");
        }

        /**
         * @summary Redraw the map.
         * @description Given some CIM datra has been loaded, redraws the map.
         * @param {object} event - optional, the vector tile checkbox change event
         * @function redraw
         * @memberOf module:cimmap
         */
        function redraw (event)
        {
            if (null != CIM_Data)
                make_map ();
        }

        /**
         * @summary Initialize the map.
         * @description Create the background map, centered on Bern and showing most of Switzerland.
         * @param {object} event - <em>not used</em>
         * @function initialize
         * @memberOf module:cimmap
         */
        function initialize (event)
        {
            // make the map
            document.getElementById ("main").innerHTML = "";
            mapboxgl.accessToken = TheToken;
            TheMap = new mapboxgl.Map
            (
                {
                    name: "TheMap",
                    version: 8,
                    container: "main",
                    center: [7.48634000000001, 46.93003],
                    zoom: 8,
                    maxZoom: 25,
                    // style: "mapbox://styles/mapbox/streets-v9",
                    style: "styles/streets-v8.json",
                    // style: "https://rawgit.com/derrickoswald/CIMApplication/master/CIMWeb/src/main/webapp/styles/streets-v8.json",
                    hash: true
                }
            );
            // add zoom and rotation controls to the map.
            TheMap.addControl (new mapboxgl.Navigation ());
            // handle mouse click
            TheMap.on
            (
                'mousedown',
                function (event)
                {
                    var features = TheMap.queryRenderedFeatures
                    (
                        event.point,
                        {}
                    );
                    if ((null != features) && (0 != features.length))
                    {
                        var selection = [];
                        for (var i = 0; i < features.length; i++)
                        {
                            var mrid = features[i].properties.mRID;
                            if (null != mrid)
                                selection[selection.length] = mrid;
                        }
                        if (selection.length > 0)
                        {
                            if (selection[0] != CURRENT_FEATURE)
                            {
                                CURRENT_FEATURE = selection[0];
                                CURRENT_SELECTION = selection;
                                highlight ();
                            }
                        }
                        else
                            unhighlight ();
                    }
                    else
                        unhighlight ();
                }
            );
            // handle mouse movement
            TheMap.on
            (
                'mousemove',
                function (event)
                {
                    var lng = event.lngLat.lng;
                    var lat = event.lngLat.lat;
                    lng = Math.round (lng * 1000000) / 1000000;
                    lat = Math.round (lat * 1000000) / 1000000;
                    document.getElementById ("coordinates").innerHTML = "" + lng + "," + lat;
                }
            );
            // display any existing data
            redraw ();
        }

        /**
         * @summary Shut down the map.
         * @description Clean up and close the map.
         * @param {object} event - <em>not used</em>
         * @function terminate
         * @memberOf module:cimmap
         */
        function terminate (event)
        {
            if (null != TheMap)
            {
                var map = TheMap;
                map.remove ();
                document.getElementById ("main").innerHTML = "";
            }
        }

        return (
            {
                set_data: set_data,
                get_data: get_data,
                redraw: redraw,
                initialize: initialize,
                trace: trace,
                unhighlight: unhighlight,
                select: select,
                search: search,
                terminate: terminate
            }
        );
    }
);
