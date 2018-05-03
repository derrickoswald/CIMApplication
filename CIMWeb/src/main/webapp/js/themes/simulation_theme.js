/**
 * Simulation theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./simulation_legend", "./layers", "../cimquery"],
    /**
     * @summary Theme on simulation output.
     * @description Theme class for colorizing by (eventually percent maximum) cable current and (eventually) deviation from nominal voltage.
     * @name simulation_theme
     * @exports simulation_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, SimulationLegend, layers, cimquery)
    {
        class SimulationTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
                this._legend = new SimulationLegend (this);
                this._legend.legend_change_listener (this.slider_changed.bind (this));
                this._simulation_points = null;
                this._simulation_lines = null;
                this._simulation_polygons = null;
                this._extents = { xmin: 0.0, ymin: 0.0, xmax: 0.0, ymax: 0.0 };
                this._render_listener = null;
            }

            getName ()
            {
                return ("SimulationTheme");
            }

            getTitle ()
            {
                return ("Simulation results");
            }

            getDescription ()
            {
                return ("Nodes and edges colored by simulation values.");
            }

            getExtents ()
            {
                return (this._extents);
            }

            getLegend ()
            {
                return (this._legend);
            }

            slider_changed (value)
            {
//                var val = "T" + value;
//                this._TheMap.setPaintProperty ("lines", "line-color", { type: "exponential", property: val, stops: [ [0.0, "RGB(102, 255, 0)"], [10.0, "RGB(355,0,127)"] ],});
            }

            /**
             * Remove layers and sources from the map.
             * @function remove_theme
             * @memberOf module:default_theme
             */
            remove_theme ()
            {
                if ((null != this._TheMap) && this._TheMap.getSource ("nodes"))
                {
                    this._TheMap.removeLayer ("points");
                    this._TheMap.removeLayer ("lines");
                    this._TheMap.removeLayer ("polygons");
                    this._TheMap.removeSource ("nodes");
                    this._TheMap.removeSource ("edges");
                    this._TheMap.removeSource ("areas");
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
                var start = new Date ().getTime ();
                console.log ("rendering simulation data");

                var map = cimmap.get_map ();
                this._TheMap = map; // to be able to remove it later

                // update the map
                map.addSource
                (
                    "nodes",
                    {
                        type: "geojson",
                        data: this._simulation_points,
                        maxzoom: 25
                    }
                );

                map.addSource
                (
                    "edges",
                    {
                        type: "geojson",
                        data: this._simulation_lines,
                        maxzoom: 25
                    }
                );

                map.addSource
                (
                    "areas",
                    {
                        type: "geojson",
                        data: this._simulation_polygons,
                        maxzoom: 25
                    }
                );

                // simple circle from 14 to 25
                map.addLayer (layers.full_circle_layer ("points", "nodes", "#000000"))

                // lines 3 pixels wide
                map.addLayer (layers.line_layer ("lines", "edges", "#000000"));

                // blue with border
                map.addLayer (layers.polygon_layer ("polygons", "areas", "#0000ff", "#000000"))

                // set the current filter
                this.legend_changed ();

                var end = new Date ().getTime ();
                console.log ("finished rendering simulation data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();
            }

            fixup (raw)
            {
                var feature = JSON.parse (raw["[json]"]);
                delete feature.simulation;
                var mrid = feature.mrid;
                delete feature.mrid;
                if (!feature.properties)
                    feature.properties = {};
                feature.properties.mRID = mrid;
                var transformer = feature.transformer;
                if (transformer)
                {
                    feature.properties.transformer = transformer;
                    delete feature.transformer;
                }
                return (feature);
            }

            setSimulationGeoJSON_Points (data)
            {
                // [ {"simulation": "e780ca29-1e69-4748-959a-79461707100d", "mrid": "MUI200057", "geometry": {"type": "Point", "coordinates": [9.50617, 47.0154]}, "type": "Feature"}, ...
                var features = data.map (this.fixup);
                // the points GeoJSON
                this._simulation_points =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };
                var extents = { xmin: Number.MAX_VALUE, ymin: Number.MAX_VALUE, xmax: -Number.MAX_VALUE, ymax: -Number.MAX_VALUE };
                features.forEach (
                    point =>
                    {
                        var x = point.geometry.coordinates[0];
                        var y = point.geometry.coordinates[1];
                        if (x < extents.xmin)
                            extents.xmin = x;
                        if (x > extents.xmax)
                            extents.xmax = x;
                        if (y < extents.ymin)
                            extents.ymin = y;
                        if (y > extents.ymax)
                            extents.ymax = y;
                    }
                );
                this._extents = extents;
                var geojson = cimquery.queryPromise (
                    { sql: "select json * from cimapplication.geojson_lines where simulation='" + this._simulation + "'", cassandra: true }
                ).then (this.setSimulationGeoJSON_Lines.bind (this));
                return (geojson);
            }

            setSimulationGeoJSON_Lines (data)
            {
                // [ {"simulation": "e780ca29-1e69-4748-959a-79461707100d", "mrid": "KLE2632", "geometry": {"type": "LineString", "coordinates": [[9.491, 47.0138], [9.491, 47.0138], [9.49105, 47.0139], [9.49109, 47.0139], [9.49107, 47.0139], [9.49108, 47.014]]}, "type": "Feature"}, ...
                var features = data.map (this.fixup);
                // the lines GeoJSON
                this._simulation_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };
                var geojson = cimquery.queryPromise (
                    { sql: "select json * from cimapplication.geojson_polygons where simulation='" + this._simulation + "'", cassandra: true }
                ).then (this.setSimulationGeoJSON_Polygons.bind (this));
                return (geojson);
            }

            setSimulationGeoJSON_Polygons (data)
            {
                // [ {"simulation": "e780ca29-1e69-4748-959a-79461707100d", "mrid": "TRA3215", "geometry": {"type": "Polygon", "coordinates": [[[9.50617, 47.0154], [9.50617, 47.0154]]]}, "type": "Feature"}, ...
                var features = data.map (this.fixup);
                // the lines GeoJSON
                this._simulation_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };
            }

            setSimulationJSON (data)
            {
                this._simulation_json = JSON.parse (data[0]["[json]"]);
                this._simulation = this._simulation_json.id;
                // "interval": {"end": "2017-07-18 23:00:00.000Z", "start": "2017-07-17 23:00:00.000Z"}
                this._legend.setTimes (
                    {
                        start: new Date (this._simulation_json.interval.start),
                        end: new Date (this._simulation_json.interval.end)
                    }
                );
                var geojson = cimquery.queryPromise (
                    { sql: "select json * from cimapplication.geojson_points where simulation='" + this._simulation + "'", cassandra: true }
                ).then (this.setSimulationGeoJSON_Points.bind (this));
                return (geojson);
            }

            setSimulation (id)
            {
                this._simulation = id;
                var promise = cimquery.queryPromise (
                    { sql: "select json * from cimapplication.simulation where id='" + id + "'", cassandra: true }
                ).then (this.setSimulationJSON.bind (this));
                return (promise);
            }

            setRenderListener (fn)
            {
                this._render_listener = fn;
            }
        }

        return (SimulationTheme);
    }
)