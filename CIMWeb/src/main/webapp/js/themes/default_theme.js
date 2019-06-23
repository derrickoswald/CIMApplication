/**
 * Default theme.
 */
"use strict";

define
(
    ["../mustache", "./legend", "./layers"],
    /**
     * @summary Base class for themes.
     * @description Theme class for colorizing by equipment type.
     * @name default_theme
     * @exports default_theme
     * @version 1.0
     */
    function (mustache, Legend, layers)
    {
        /**
         * symbology
         */
        const junction_symbol = "alternate_junction";
        const connector_symbol = "connector";
        const distribution_box_symbol = "distribution_box";
        const energy_consumer_symbol = "energy_consumer";
        const fuse_symbol = "fuse";
        const other_symbol = "junction";
        const street_light_symbol = "street_light";
        const substation_symbol = "substation";
        const switch_symbol = "switch";
        const transformer_station_symbol = "transformer_station";
        const transformer_symbol = "transformer";
        const feeder_symbol = "feeder";

        var TheExtents;

        /**
         * @summary Gather position points into locations.
         * @description Convert sequences of position points into locations with coordinate array.
         * As a side effect, computes the minimum bounding rectangle and stores it in TheExtents.
         * @param {object} data - the hash table object of CIM classes by class name
         * @param {object} options layer options, e.g. show_internal_features
         * @return {object} object of arrays stored by Location.id
         * @function get_locations
         * @memberOf module:default_theme
         */
        function get_locations (data, options)
        {
            const ret = {};

            const points = data.PositionPoint;
            const locations = data.Location;
            // list of locations to exclude
            const blacklist = {};
            const extents = { xmin: Number.MAX_VALUE, ymin: Number.MAX_VALUE, xmax: -Number.MAX_VALUE, ymax: -Number.MAX_VALUE };

            if (!options.show_internal_features)
                for (let location in locations)
                    if (locations.hasOwnProperty (location) && (locations[location].CoordinateSystem === "pseudo_wgs84"))
                        blacklist[location] = true;

            for (let point in points)
                if (points.hasOwnProperty (point))
                {
                    const p = points[point];
                    if (!p.EditDisposition || (p.EditDisposition !== "delete"))
                    {
                        const location = p.Location;
                        if ((null != location) && ("undefined" === typeof (blacklist[location])))
                        {
                            if (!ret[location])
                            {
                                const array = [];
                                array.isInternal = (locations[location].CoordinateSystem === "pseudo_wgs84") ? function () { return (true); }: function () { return (false); };
                                ret[location] = array;
                            }
                            const seq = Number (p.sequenceNumber);
                            if (null != seq)
                            {
                                const x = Number (p.xPosition);
                                const y = Number (p.yPosition);
                                ret[location][seq * 2] = x;
                                ret[location][seq * 2 + 1] = y;
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

            // fix non-zero based sequence numbers
            for (let property in ret)
                if (ret.hasOwnProperty (property))
                {
                    const a = ret[property];
                    if (("undefined" === typeof (a[0])) && ("undefined" === typeof (a[1])))
                    {
                        ret[property] = a.slice (2);
                        ret[property].isInternal = a.isInternal;
                    }
                }

            TheExtents = extents;
            return (ret);
        }

        class DefaultTheme
        {
            constructor()
            {
                this._items =
                    [
                        {
                            id: "consumers",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 139, 139);'>&nbsp;&nbsp;&nbsp;</span> energy consumer",
                            checked: true,
                            color: "rgb(0, 139, 139)"
                        },
                        {
                            id: "connectors",
                            description: "<span style='width: 15px; height: 15px; background: rgb(139, 0, 0);'>&nbsp;&nbsp;&nbsp;</span> connector",
                            checked: true,
                            color: "rgb(139, 0, 0)"
                        },
                        {
                            id: "containers",
                            description: "<span style='width: 15px; height: 15px; background: rgb(255, 0, 255);'>&nbsp;&nbsp;&nbsp;</span> container",
                            checked: true,
                            color: "rgb(255, 0, 255)"
                        },
                        {
                            id: "switches",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 0, 139);'>&nbsp;&nbsp;&nbsp;</span> switch",
                            checked: true,
                            color: "rgb(0, 0, 139)"
                        },
                        {
                            id: "transformers",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 100, 0);'>&nbsp;&nbsp;&nbsp;</span> transformer",
                            checked: true,
                            color: "rgb(0, 100, 0)"
                        },
                        {
                            id: "cables",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 0, 0);'>&nbsp;&nbsp;&nbsp;</span> cable",
                            checked: true,
                            color: "rgb(0, 0, 0)"
                        }
                    ];
                this._legend = new Legend (this);
                this._legend.legend_change_listener (this.legend_changed.bind (this));
            }

            getName ()
            {
                return ("DefaultTheme");
            }

            getTitle ()
            {
                return ("Default");
            }

            getDescription ()
            {
                return ("Equipment colored by function.");
            }

            getExtents ()
            {
                return (TheExtents);
            }

            /**
             * The legend for this theme.
             */
            getLegend ()
            {
                return (this._legend);
            }

            /**
             * Item list for the legend.
             */
            getItems ()
            {
                return (this._items);
            }

            legend_changed ()
            {
                if ((null != this._TheMap) && this._TheMap.getSource ("cim lines") && this._TheMap.getSource ("cim points"))
                {
                    const colors = this._items.filter (function (item) { return (item.checked); }).map (function (item) { return (item.color); });
                    colors.unshift ("in", "color");
                    this._TheMap.setFilter ("lines", colors);
                    this._TheMap.setFilter ("circle", colors);
                    this._TheMap.setFilter ("symbol", colors);
                }
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
                const points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                // the special points GeoJSON
                const special_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                // the lines GeoJSON
                const lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                // the polygons GeoJSON
                const polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };

                let coordinates;
                const psr = data.PowerSystemResource;

                function symbolize (id)
                {
                    if (2 === coordinates.length)
                    {
                        var target_array = points;
                        psr[id].id = id;
                        psr[id].rotation = 0.0;

                        // assign the symbol and color
                        if ("PowerTransformer" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = transformer_symbol;
                            psr[id].color = "rgb(0, 100, 0)";
                        }
                        else if ("Fuse" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = fuse_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Switch" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Cut" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Disconnector" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("GroundDisconnector" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Jumper" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("MktSwitch" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("ProtectedSwitch" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Breaker" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("LoadBreakSwitch" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Recloser" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("Sectionaliser" === psr[id].cls)
                        {
                            if (coordinates.isInternal ()) target_array = special_points;
                            psr[id].symbol = switch_symbol;
                            psr[id].color = "rgb(0, 0, 139)";
                        }
                        else if ("EnergyConsumer" === psr[id].cls)
                        {
                            if (psr[id].PSRType === "PSRType_StreetLight")
                                psr[id].symbol = street_light_symbol;
                            else
                                psr[id].symbol = energy_consumer_symbol;
                            psr[id].color = "rgb(0, 139, 139)";
                        }
                        else if ("Connector" === psr[id].cls)
                        {
                            psr[id].symbol = feeder_symbol;
                            psr[id].color = "rgb(139, 0, 0)";
                        }
                        else if ("Junction" === psr[id].cls)
                        {
                            psr[id].symbol = other_symbol;
                            psr[id].color = "rgb(139, 0, 0)";
                        }
                        else if ("BusbarSection" === psr[id].cls)
                        {
                            psr[id].symbol = junction_symbol;
                            psr[id].color = "rgb(139, 0, 0)";
                        }
                        else
                        {
                            if (data.Substation && ("undefined" != typeof (data.Substation[id])))
                            {
                                if (psr[id].PSRType === "PSRType_DistributionBox")
                                    psr[id].symbol = distribution_box_symbol;
                                else if (psr[id].PSRType === "PSRType_Substation")
                                    psr[id].symbol = substation_symbol;
                                else if (psr[id].PSRType === "PSRType_TransformerStation")
                                    psr[id].symbol = transformer_station_symbol;
                                else
                                    psr[id].symbol = substation_symbol;
                                psr[id].color = "rgb(255, 0, 255)";
                            }
                            else
                            {
                                psr[id].symbol = other_symbol;
                                psr[id].color = "rgb(0, 0, 0)";
                            }
                        }
                        target_array.features.push
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
                    }
                    else
                    {
                        const coords = coordinates.reduce
                        (
                            function (ret, item)
                            {
                                let next;

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
                        lines.features.push
                        (
                            {
                                type : "Feature",
                                geometry :
                                    {
                                        type : "LineString",
                                        coordinates : coords
                                    },
                                properties : psr[id]
                            }
                        );
                        psr[id].id = id;
                        psr[id].color = "rgb(0, 0, 0)";
                    }
                }

                for (let id in psr)
                {
                    if (psr.hasOwnProperty (id))
                    {
                        let obj = psr[id];
                        coordinates = locations[obj.Location];
                        if (coordinates)
                        {
                            // don't show deleted elements
                            if (obj.EditDisposition && ("delete" === obj.EditDisposition))
                                continue;

                            symbolize (id);
                        }
                    }
                }

                return ({ points: points, special_points: special_points, lines: lines, polygons: polygons });
            }

            /**
             * For subclasses to override stylization information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             * @function process_spatial_objects_again
             * @memberOf module:default_theme
             */
            process_spatial_objects_again (data, options)
            {
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
                let ret;
                if (null != data)
                {
                    const locations = get_locations (data, options);
                    ret = this.process_spatial_objects (data, locations, options);
                    this.process_spatial_objects_again (data, options);
                }
                else
                {
                    const fc = { "type" : "FeatureCollection", "features" : [] };
                    ret = { points: fc, special_points: fc, lines: fc, polygons: fc };
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
                if ((null != this._TheMap) && this._TheMap.getSource ("cim lines"))
                {
                    this._TheMap.removeLayer ("lines");
                    this._TheMap.removeLayer ("lines_highlight");
                    this._TheMap.removeLayer ("circle");
                    this._TheMap.removeLayer ("circle_highlight");
                    this._TheMap.removeLayer ("symbol");
                    this._TheMap.removeLayer ("symbol_highlight");
                    this._TheMap.removeSource ("cim points");
                    this._TheMap.removeSource ("cim special points");
                    this._TheMap.removeSource ("cim lines");
                    this._TheMap.removeSource ("cim polygons");
                }
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             * @memberOf module:default_theme
             */
            make_theme (cimmap, options)
            {
                const start = new Date ().getTime ();
                console.log ("rendering CIM data");

                const map = cimmap.get_map ();
                this._TheMap = map; // to be able to remove it later

                const geo = this.make_geojson (cimmap.get_data (), options);

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
                    "cim special points",
                    {
                        type: "geojson",
                        data: geo.special_points,
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

                const not_deleted = ["any", ["!", ["has", "EditDisposition"]], ["!=", ["get", "EditDisposition"], "delete"]];

                // lines 3 pixels wide
                map.addLayer (layers.line_layer ("lines", "cim lines", { type: "identity", property: "color" }, not_deleted));
                map.addLayer (layers.line_layer ("lines_highlight", "cim lines", "rgb(255, 255, 0)", ["==", "mRID", ""]));

                // simple circles
                map.addLayer (layers.full_circle_layer ("circle", "cim points", { type: "identity", property: "color" }, not_deleted));
                map.addLayer (layers.full_circle_layer ("circle_highlight", "cim points", "rgb(255, 255, 0)", ["==", "mRID", ""]));

                // symbol icon from 17 and deeper
                map.addLayer (layers.symbol_layer ("symbol", "cim special points", { type: "identity", property: "color" }, not_deleted, true));
                map.addLayer (layers.symbol_layer ("symbol_highlight", "cim special points", "rgb(255, 255, 0)", ["==", "mRID", ""], true));

                // set the current filter
                this.legend_changed ();

                const end = new Date ().getTime ();
                console.log ("finished rendering CIM data (" + (Math.round (end - start) / 1000) + " seconds)");
            }
        }

        return (DefaultTheme);
    }
);
