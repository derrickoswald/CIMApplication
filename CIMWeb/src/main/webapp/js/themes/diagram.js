/**
 * Diagram theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./layers"],
    /**
     * @summary Theme of diagrams.
     * @name diagram_theme
     * @exports diagram_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, layers)
    {
        /**
         * symbology
         */
        var junction_symbol = "alternate_junction";
        var connector_symbol = "connector";
        var distribution_box_symbol = "distribution_box";
        var energy_consumer_symbol = "energy_consumer";
        var fuse_symbol = "fuse";
        var other_symbol = "junction";
        var street_light_symbol = "street_light";
        var substation_symbol = "substation";
        var switch_symbol = "switch";
        var transformer_station_symbol = "transformer_station";
        var transformer_symbol = "transformer";
        var feeder_symbol = "feeder";

        var TheExtents;

        /**
         * @summary Gather diagram object points into diagram objects.
         * @description Convert sequences of diagram object points into locations with coordinate array.
         * As a side effect, computes the minimum bounding rectangle and stores it in TheExtents.
         * @param {object} data - the hash table object of CIM classes by class name
         * @param {object} options layer options, e.g. show_internal_features
         * @return {object} object of arrays stored by Location.id
         * @function get_locations
         * @memberOf module:default_theme
         */
        function get_locations (data, options)
        {
            var ret = {};

            var points = data.DiagramObjectPoint;
            var objects = data.DiagramObject;
            var extents = { xmin: Number.MAX_VALUE, ymin: Number.MAX_VALUE, xmax: -Number.MAX_VALUE, ymax: -Number.MAX_VALUE };
            if (points && objects)
                for (var point in points)
                {
                    var p = points[point];
                    var obj = p.DiagramObject;
                    if (null != obj)
                    {
                        var object = objects[obj];
                        if (null != object)
                        {
                            var id = object.IdentifiedObject;
                            if (null != id)
                            {
                                if (null == ret[id])
                                {
                                    var array = [];
                                    var polygon = object.isPolygon;
                                    array.isPolygon = polygon ? function () { return (true); } : function () { return (false); }
                                    ret[id] = array;
                                }
                                var seq = Number (p.sequenceNumber);
                                if (null != seq)
                                {
                                    var x = Number (p.xPosition);
                                    var y = Number (p.yPosition);
                                    ret[id][seq * 2] = x;
                                    ret[id][seq * 2 + 1] = y;
                                    if ((x >= -180.0) && (x <= 180.0) && (y >= -90.0) && (y <= 90.0)) // eliminate fucked up coordinates
                                    {
                                        if (x < extents.xmin)
                                            extents.xmin = x;
                                        if (x > extents.xmax)
                                            extents.xmax = x;
                                        if (y < extents.ymin)
                                            extents.ymin = y;
                                        if (y > extents.ymax)
                                            extents.ymax = y;
                                    }
                                }
                            }
                        }
                    }
                }

            // fix non-zero based sequence numbers
            for (var property in ret)
                if (ret.hasOwnProperty (property))
                {
                    var a = ret[property];
                    if (("undefined" == typeof (a[0])) && ("undefined" == typeof (a[1])))
                    {
                        ret[property] = a.slice (2);
                        ret[property].isPolygon = a.isPolygon;
                    }
                }

            TheExtents = extents;
            return (ret);
        }

        class DiagramTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
            }

            getName ()
            {
                return ("DiagramTheme");
            }

            getTitle ()
            {
                return ("Diagram");
            }

            getDescription ()
            {
                return ("Show the diagram layout objects on the map.");
            }

            getExtents ()
            {
                return (TheExtents);
            }

            /**
             * Add stylization information to elements and make a list of point and linear features.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} locations - the hash table object with properties that are locations with arrays of coordinates
             * @param {Object} options - options for processing
             * @return {Object} with points, lines and polygons feature collections
             * @function process_spatial_objects
             * @memberOf module:default_theme
             */
            process_spatial_objects (data, locations, options)
            {
                // the points GeoJSON
                var points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                // the lines GeoJSON
                var lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                // the polygons GeoJSON
                var polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };

                var coordinates;
                var location;
                var objects = data.IdentifiedObject
                for (var id in objects)
                {
                    if (null != (coordinates = locations[id]))
                    {
                        // don't show deleted elements
                        if (objects[id].EditDisposition && ("delete" == objects[id].EditDisposition))
                            continue;

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
                                    properties : objects[id]
                                }
                            );
                            objects[id].id = id;
                            objects[id].rotation = 0.0;

                            // assign the symbol and color
                            if ("PowerTransformer" == objects[id].cls)
                            {
                                objects[id].symbol = transformer_symbol;
                                objects[id].color = "rgb(0, 100, 0)";
                            }
                            else if ("Fuse" == objects[id].cls)
                            {
                                objects[id].symbol = fuse_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Switch" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Cut" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Disconnector" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("GroundDisconnector" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Jumper" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("MktSwitch" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("ProtectedSwitch" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Breaker" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("LoadBreakSwitch" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Recloser" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("Sectionaliser" == objects[id].cls)
                            {
                                objects[id].symbol = switch_symbol;
                                objects[id].color = "rgb(0, 0, 139)";
                            }
                            else if ("EnergyConsumer" == objects[id].cls)
                            {
                                if (objects[id].PSRType == "PSRType_StreetLight")
                                    objects[id].symbol = street_light_symbol;
                                else
                                    objects[id].symbol = energy_consumer_symbol;
                                objects[id].color = "rgb(0, 139, 139)";
                            }
                            else if ("Connector" == objects[id].cls)
                            {
                                objects[id].symbol = feeder_symbol;
                                objects[id].color = "rgb(139, 0, 0)";
                            }
                            else if ("Junction" == objects[id].cls)
                            {
                                objects[id].symbol = other_symbol;
                                objects[id].color = "rgb(139, 0, 0)";
                            }
                            else if ("BusbarSection" == objects[id].cls)
                            {
                                objects[id].symbol = junction_symbol;
                                objects[id].color = "rgb(139, 0, 0)";
                            }
                            else
                            {
                                if (data.Substation && ("undefined" != typeof (data.Substation[id])))
                                {
                                    if (objects[id].PSRType == "PSRType_DistributionBox")
                                        objects[id].symbol = distribution_box_symbol;
                                    else if (objects[id].PSRType == "PSRType_Substation")
                                        objects[id].symbol = substation_symbol;
                                    else if (objects[id].PSRType == "PSRType_TransformerStation")
                                        objects[id].symbol = transformer_station_symbol;
                                    else
                                        objects[id].symbol = substation_symbol;
                                    objects[id].color = "rgb(255, 0, 255)";
                                }
                                else
                                {
                                    objects[id].symbol = other_symbol;
                                    objects[id].color = "rgb(0, 0, 0)";
                                }
                            }
                        }
                        else
                        {
                            var coords = coordinates.reduce
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
                            );
                            if (coordinates.isPolygon ())
                            {
                                polygons.features.push
                                (
                                    {
                                        type : "Feature",
                                        geometry :
                                        {
                                            type : "Polygon",
                                            coordinates : [ coords ]
                                        },
                                        properties : objects[id]
                                    }
                                );
                                objects[id].id = id;
                                objects[id].kolour = "rgb(0, 0, 255)";
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
                                            coordinates : coords
                                        },
                                        properties : objects[id]
                                    }
                                );
                                objects[id].id = id;
                                objects[id].color = "rgb(0, 0, 0)";
                            }
                        }
                    }
                }


                return ({ points: points, lines: lines, polygons: polygons });
            }

            /**
             * Override stylization information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             * @function process_spatial_objects_again
             * @memberOf module:diagram
             */
            process_spatial_objects_again (data, options)
            {
                var diagram_object = data.DiagramObject;
                for (var id in diagram_object)
                {
                    var obj = diagram_object[id];
                    if (obj.rotation != 0.0)
                    {
                        var id = obj.IdentifiedObject;
                        if (null != id)
                        {
                            var element = data.Element[id];
                            if (null != element)
                                // -90.0: fix symbol, zero is east & not down
                                // value is in degrees clockwise, not counter-clockwise
                                // +180: symbol points in to station
                                element.rotation = -90.0 + (-obj.rotation * 180.0 / Math.PI) + 180.0;
                        }
                    }
                    if (null != obj.DiagramObjectStyle)
                    {
                        var id = obj.IdentifiedObject;
                        if (null != id)
                        {
                            var element = data.Element[id];
                            if (null != element)
                            {
                                if ("feeder_internally_fed_style" == obj.DiagramObjectStyle)
                                    element.color = "rgb(139, 0, 0)";
                                else if ("feeder_externally_feed_style" == obj.DiagramObjectStyle)
                                    element.color = "rgb(255, 0, 255)";
                                else
                                {
                                    var style = data.DiagramObjectStyle[obj.DiagramObjectStyle];
                                    if (style)
                                    {
                                        var color_name = style.name; // e.g. yellowgreen
                                        element.kolour = color_name;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /**
             * Create the GeoJSON for the data with the given options.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             * @return {Object} with points, lines and polygons feature collections
             * @function make_geojson
             * @memberOf module:default_theme
             */
            make_geojson (data, options)
            {
                var ret;
                if (null != data)
                {
                    var locations = get_locations (data, options);
                    ret = this.process_spatial_objects (data, locations, options);
                    this.process_spatial_objects_again (data, options);
                }
                else
                {
                    var fc = { "type" : "FeatureCollection", "features" : [] };
                    ret = { points: fc, lines: fc, polygons: fc };
                }
                return (ret);
            }


            /**
             * Remove layers and sources from the map.
             * @function remove_theme
             * @memberOf module:default_theme
             */
            remove_theme ()
            {
                if (this._TheChart)
                {
                    this._TheMap.removeControl (this._TheChart);
                    delete this._TheChart;
                }
                if ((null != this._TheMap) && this._TheMap.getSource ("cim points"))
                {
                    this._TheMap.removeLayer ("lines");
                    this._TheMap.removeLayer ("lines_highlight");
                    this._TheMap.removeLayer ("circle");
                    this._TheMap.removeLayer ("circle_highlight");
                    this._TheMap.removeLayer ("symbol");
                    this._TheMap.removeLayer ("symbol_highlight");
                    this._TheMap.removeLayer ("polygons");
                    this._TheMap.removeSource ("cim points");
                    this._TheMap.removeSource ("cim lines");
                    this._TheMap.removeSource ("cim polygons");
                }
                if (this._TheMap)
                {
                    this._TheMap.off ("mousedown", this._mousedown_listener);
                    this._cimmap.add_listeners ();
                }
                super.remove_theme ();
            }

            // handle mouse click
            mousedown_listener (event)
            {
                // only do something if no key is pressed
                var key = event.originalEvent.ctrlKey || event.originalEvent.shiftKey || event.originalEvent.altKey || event.originalEvent.metaKey;
                if (!key)
                {
                    var buttons = event.originalEvent.buttons;
                    //    0  : No button or un-initialized
                    //    1  : Primary button (usually left)
                    //    2  : Secondary button (usually right)
                    var leftbutton = 0 != (buttons & 1);
                    var rightbutton = 0 != (buttons & 2);
                    if (leftbutton)
                        this._cimmap.default_mousedown_listener (event);
                }
            }

            clear ()
            {
                var fc = { type: "FeatureCollection", features: [] };
                this._TheMap.getSource ("cim points").setData (fc);
                this._TheMap.getSource ("cim lines").setData (fc);
                this._TheMap.getSource ("cim polygons").setData (fc);
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             * @memberOf module:diagram_theme
             */
            make_theme (cimmap, options)
            {
                var start = new Date ().getTime ();
                console.log ("rendering diagram data");

                this._cimmap = cimmap;
                var map = cimmap.get_map ();
                this._TheMap = map; // to be able to remove it later

                var geo = this.make_geojson (cimmap.get_data (), options);

                // update the map
                map.addSource
                (
                    "cim points",
                    {
                        type: "geojson",
                        data: geo.points,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "cim lines",
                    {
                        type: "geojson",
                        data: geo.lines,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "cim polygons",
                    {
                        type: "geojson",
                        data: geo.polygons,
                        maxzoom: 24
                    }
                );

                // lines 3 pixels wide
                // note: really weird shit starts happening if the property name is "color", so use "kolour" instead
                map.addLayer (layers.line_layer ("lines", "cim lines", { type: "identity", property: "kolour" }, ["!has", "EditDisposition"]));
                map.addLayer (layers.line_layer ("lines_highlight", "cim lines", "rgb(255, 255, 0)", ["==", "mRID", ""]));

                // simple circle from 14 to 17
                map.addLayer (layers.circle_layer ("circle", "cim points", { type: "identity", property: "color" }, ["!has", "EditDisposition"]))
                map.addLayer (layers.circle_layer ("circle_highlight", "cim points", "rgb(255, 255, 0)", ["==", "mRID", ""]))

                // symbol icon from 17 and deeper
                map.addLayer (layers.symbol_layer ("symbol", "cim points", { type: "identity", property: "color" }, ["!has", "EditDisposition"]));
                map.addLayer (layers.symbol_layer ("symbol_highlight", "cim points", "rgb(255, 255, 0)", ["==", "mRID", ""]));

                map.addLayer (layers.polygon_layer ("polygons", "cim polygons", { type: "identity", property: "kolour" }, "#000000"))

                // set the current filter
                this.legend_changed ();

                var end = new Date ().getTime ();
                console.log ("finished rendering diagram data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();

                this._cimmap.remove_listeners ();
                this._mousedown_listener = this.mousedown_listener.bind (this);
                this._TheMap.on ("mousedown", this._mousedown_listener);
            }

            setRenderListener (fn)
            {
                this._render_listener = fn;
            }
        }

        return (DiagramTheme);
    }
)
