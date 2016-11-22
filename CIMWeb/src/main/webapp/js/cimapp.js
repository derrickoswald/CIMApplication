/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["es6-promise"],
    /**
     * @summary Main entry point for the application.
     * @description Performs application initialization as the first step in the RequireJS load sequence.
     * @see http://requirejs.org/docs/api.html#data-main
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (es6_promise)
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
         * The GeoJSON contents after load.
         */
        var JSON_Data = null;

        /**
         * The last selected feature.
         */
        var CURRENT_FEATURE = null;

        /**
         * The last selected features.
         */
        var CURRENT_SELECTION = null;

        // using Promise: backwards compatibility for older browsers
        es6_promise.polyfill ();

        /**
         * Create a circle layer object.
         * @param {String} id - the layer id
         * @param {Any[]} filter - the filter to apply to the points
         * @param {String} color - the symbol color to use (doesn't work)
         * @returns {Object} the layer
         * @function circle_layer
         * @memberOf module:cimapp
         */
        function circle_layer (id, filter, color)
        {
            var ret =
                {
                    id: id,
                    type: "circle",
                    source: "the points",
                    minzoom: 8,
                    maxzoom: 22,
                    paint:
                    {
                        "circle-radius": 5, // Optional number. Units in pixels. Defaults to 5.
                        "circle-blur": 0, // Optional number. Defaults to 0. 1 blurs the circle such that only the centerpoint is full opacity.
                        "circle-opacity": 1, // Optional number. Defaults to 1.
                        "circle-translate": [0, 0], // Optional array. Units in pixels. Defaults to 0,0. Values are [x, y] where negatives indicate left and up, respectively.
                        "circle-translate-anchor": "map", // Optional enum. One of map, viewport. Defaults to map. Requires circle-translate.
                    }
                };
            if (null != filter)
                ret.filter = filter;
            if (null != color)
                ret.paint["circle-color"] = color; // Optional color. Defaults to #000000.

            return (ret);
        }

        /**
         * Make a select option list of the files.
         * @param files The array of files on HDFS, each object has a name and details.
         */
        function make_file_list (files)
        {
            //    [
            //      {
            //        "path":"KS_Leistungen.csv",
            //        "length":403242,
            //        "modification_time":1479825255343,
            //        "access_time":1479825255110
            //      },
            //      {
            //        "path":"NIS_CIM_Export_sias_current_20160816_Kiental_V9",
            //        "length":14432564,
            //        "modification_time":1479825253185,
            //        "access_time":1479825252379
            //      }
            //    ]
            var options = ""
            files.forEach (function (s) { options += "<option value='" + s.path + "'>" + s.path + " " + s.length + "</option>\n" } );
            document.getElementById ("cim_file").innerHTML = options;
            document.getElementById ("cim_file2").innerHTML = options;
        }

        /**
         * Make a select option list of the transformers.
         * @param transformers The list transformers as an array of strings.
         */
        function make_list (transformers)
        {
            var select = document.getElementById ("transformer");
            var options = ""
            transformers.forEach (function (s) { options += "<option value='" + s + "'>" + s + "</option>\n" } );
            select.innerHTML = options + "<option value='all'>All</option>";
        }

        /**
         * Generate a map.
         * @param {Object} points - the points GeoJSON
         * @function make_map
         * @memberOf module:cimapp
         */
        function make_map (points)
        {
            JSON_Data = points;

            if (TheMap.getSource ("the points"))
            {
                TheMap.removeLayer ("fbad_invalid_house_connection");
                TheMap.removeLayer ("fbad_semivalid_house_connection");
                TheMap.removeLayer ("fbad_pseudovalid_house_connection");
                TheMap.removeLayer ("fbad_valid_house_connection");
                TheMap.removeLayer ("fok_invalid_house_connection");
                TheMap.removeLayer ("fok_semivalid_house_connection");
                TheMap.removeLayer ("fok_pseudovalid_house_connection");
                TheMap.removeLayer ("fok_valid_house_connection");
                TheMap.removeLayer ("circle_highlight");
                TheMap.removeSource ("the points");
            }

            TheMap.addSource
            (
                "the points",
                {
                    type: "geojson",
                    data: points,
                    maxzoom: 22
                }
            );

            // simple circle from zoom level 8 to 22
            TheMap.addLayer (circle_layer ("fbad_invalid_house_connection", ["all", ["==", "trafo_valid", false],["==", "wires_valid", false],["==", "fuse_valid", false]], "rgb(255, 0, 255)"));
            TheMap.addLayer (circle_layer ("fbad_semivalid_house_connection", ["all", ["==", "trafo_valid", false],["==", "wires_valid", true],["==", "fuse_valid", false]], "rgb(255, 165, 255)"));
            TheMap.addLayer (circle_layer ("fbad_pseudovalid_house_connection", ["all", ["==", "trafo_valid", true],["==", "wires_valid", false],["==", "fuse_valid", false]], "rgb(128, 128, 255)"));
            TheMap.addLayer (circle_layer ("fbad_valid_house_connection", ["all", ["==", "trafo_valid", true],["==", "wires_valid", true],["==", "fuse_valid", false]], "rgb(0, 255, 255)"));
            TheMap.addLayer (circle_layer ("fok_invalid_house_connection", ["all", ["==", "trafo_valid", false],["==", "wires_valid", false],["==", "fuse_valid", true]], "rgb(255, 0, 0)"));
            TheMap.addLayer (circle_layer ("fok_semivalid_house_connection", ["all", ["==", "trafo_valid", false],["==", "wires_valid", true],["==", "fuse_valid", true]], "rgb(255, 165, 0)"));
            TheMap.addLayer (circle_layer ("fok_pseudovalid_house_connection", ["all", ["==", "trafo_valid", true],["==", "wires_valid", false],["==", "fuse_valid", true]], "rgb(128, 128, 0)"));
            TheMap.addLayer (circle_layer ("fok_valid_house_connection", ["all", ["==", "trafo_valid", true],["==", "wires_valid", true],["==", "fuse_valid", true]], "rgb(0, 255, 0)"));
            TheMap.addLayer (circle_layer ("circle_highlight", ["==", "mRID", ""], "rgb(255, 255, 0)"));
        }

        /**
         * @summary Fetch some data.
         * @description Invoke the server-side function to get some data.
         * @param {object} event - optional, the click event
         * @function do_short_circuit
         * @memberOf module:cimapp
         */
        function do_short_circuit (event)
        {
            var file;
            var transformer;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file").value;
            transformer = document.getElementById ("transformer").value;
            url = window.location.origin + window.location.pathname + "cim/ShortCircuitCalculation/";
            url = url + encodeURIComponent (file);
            if ("" != transformer)
                url = url + "/" + transformer;
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "application/json");
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (Array.isArray (resp))
                            make_list (resp);
                        else
                            make_map (resp);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Connect to the server and read the list of files.
         * @description Invoke the server-side function to list files.
         * @param {object} event - optional, the click event
         * @function do_connect
         * @memberOf module:cimapp
         */
        function do_connect (event)
        {
            var url;
            var xmlhttp;

            url = window.location.origin + window.location.pathname + "cim/list/";
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "application/json");
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status != "OK")
                            alert (resp.message);
                        else
                            make_file_list (resp.result.files);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Fetch some data.
         * @description Invoke the server-side function to get some data.
         * @param {object} event - optional, the click event
         * @function connect
         * @memberOf module:cimapp
         */
        function connect (event)
        {
            var file;
            var transformer;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file").value;
            if ("" == file)
                do_connect (event);
            else
                do_short_circuit (event);
        }

        /**
         * @summary Export a GridLAB-D file.
         * @description Invoke the server-side function to export some data.
         * @param {object} event - optional, the click event
         * @function gridlab
         * @memberOf module:cimapp
         */
        function gridlab (event)
        {
            var file;
            var transformer;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file2").value;
            transformer = document.getElementById ("trafokreise").value;
            url = window.location.origin + window.location.pathname + "cim/GridLabExport/"
            if ("" != file)
                url = url + encodeURIComponent (file);
            else
                url = url + encodeURIComponent ("NIS_CIM_Export_sias_current_20160816_V8_Bruegg")
            if ("" != transformer)
                url = url + "/" + transformer;
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "text/plain");
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        alert (xmlhttp.responseText);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Make the details non-model dialog visible.
         * @description Uses jQuery to show the panel.
         * @function show_details
         * @memberOf module:cimapp
         */
        function show_details ()
        {
            $("#feature_details").show ();
        }

        /**
         * @summary Make the details non-model dialog invisible.
         * @description Uses jQuery to hide the panel.
         * @function hide_details
         * @memberOf module:cimapp
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
         * @memberOf module:cimapp
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
         * @memberOf module:cimapp
         */
        function glow (filter)
        {
            TheMap.setFilter ("circle_highlight", filter);
        }

        /**
         * @summary Display the current feature properties and highlight it on the map.
         * @description Shows a JSON properties sheet in the details window,
         * and highlights the current feature in the map.
         * Other features in the current selection are provided links in the details window
         * to make them the current feature.
         * @function highlight
         * @memberOf module:cimapp
         */
        function highlight ()
        {
            var feature;
            if (null != CURRENT_FEATURE)
            {
                var text = JSON.stringify (CURRENT_FEATURE.properties, null, 2);
                if (null != CURRENT_SELECTION)
                    for (var i = 0; i < CURRENT_SELECTION.length; i++)
                    {
                        if (CURRENT_SELECTION[i].properties.mRID != CURRENT_FEATURE.properties.mRID)
                            text = text + "\n<a href='#' onclick='require([\"cimapp\"], function(cimapp) {cimapp.select (\"" + CURRENT_SELECTION[i].properties.mRID + "\");})'>" + CURRENT_SELECTION[i].properties.mRID + "</a>";
                    }
                showDetails (text);
                glow (["in", "mRID", CURRENT_FEATURE.properties.mRID]);
            }
        }

        /**
         * @summary Clears the current feature and selection.
         * @description Hides the details non-modal dialog and reverts any highlighting in the map.
         * @function unhighlight
         * @memberOf module:cimapp
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
         * @memberOf module:cimapp
         */
        function select (mrid)
        {
            CURRENT_FEATURE = null;
            if (null != CURRENT_SELECTION)
                for (var i = 0; i < CURRENT_SELECTION.length; i++)
                    if (CURRENT_SELECTION[i].properties.mRID == mrid)
                    {
                        CURRENT_FEATURE = CURRENT_SELECTION[i];
                        break;
                    }
            if (null != CURRENT_FEATURE)
                highlight ();
            else
                unhighlight ();
        }

        /**
         * @summary Initialize the map.
         * @description Create the background map, centered on Bern and showing most of Switzerland.
         * @param {object} event - optional, the vector tile checkbox change event
         * @function init_map
         * @memberOf module:cimapp
         */
        function init_map (event)
        {
            document.getElementById ("map").innerHTML = "";
            mapboxgl.accessToken = TheToken;
            TheMap = new mapboxgl.Map
            (
                {
                    name: "TheMap",
                    version: 8,
                    container: "map",
                    center: [7.48634000000001, 46.93003],
                    zoom: 8,
                    maxZoom: 22,
                    //style: "mapbox://styles/mapbox/streets-v8",
                    style: "styles/streets-v8.json",
                    hash: true
                }
            );
            // add zoom and rotation controls to the map.
            TheMap.addControl (new mapboxgl.Navigation ());
            // handle mouse click - display details and highlight
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
                    CURRENT_SELECTION = null;
                    CURRENT_FEATURE = null;
                    if ((null != features) && (0 != features.length))
                    {
                        var list = [];
                        for (var i = 0; i < features.length; i++)
                            if (features[i].properties.mRID)
                                list.push (features[i]);
                        if (0 != list.length)
                        {
                            CURRENT_SELECTION = list;
                            CURRENT_FEATURE = list[0];
                        }
                    }
                    if (null != CURRENT_FEATURE)
                        highlight ();
                    else
                        unhighlight ();
                }
            );
            // handle mouse movement - display coordinates in the nav bar
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
        }

        return (
            {
                init_map: init_map,
                connect: connect,
                gridlab: gridlab,
                select: select,
                unhighlight: unhighlight
            }
        );
    }
);