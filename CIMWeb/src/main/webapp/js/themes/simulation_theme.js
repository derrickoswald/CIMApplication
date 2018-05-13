/**
 * Simulation theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./simulation_legend", "./layers", "../cimquery", "../cimchart"],
    /**
     * @summary Theme on simulation output.
     * @description Theme class for colorizing by (eventually percent maximum) cable current and (eventually) deviation from nominal voltage.
     * @name simulation_theme
     * @exports simulation_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, SimulationLegend, layers, cimquery, CIMChart)
    {
        class SimulationTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
                this._legend = new SimulationLegend (this);
                this._simulation_points = null;
                this._simulation_lines = null;
                this._simulation_polygons = null;
                this._extents = { xmin: 0.0, ymin: 0.0, xmax: 0.0, ymax: 0.0 };
                this._render_listener = null;
                this.getLegend ().legend_change_listener (this.legend_changed.bind (this));
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

            legend_changed (value)
            {
                var date = new Date (value).toISOString ();
                var val = "T" + date.substring (0, date.indexOf ("T")) + "max";
                var current = this._TheMap.getPaintProperty ("polygons", "fill-color");
                if (current.val != val)
                    this._TheMap.setPaintProperty ("polygons", "fill-color", { type: "exponential", property: val, stops: [ [0.0, "RGB(0, 255, 0)"], [100.0, "RGB(255,0,0)"] ] });
                val = "T" + date.replace ("T", " ");
                this._TheMap.setPaintProperty ("lines", "line-color", { type: "exponential", property: val, stops: [ [0.0, "RGB(0, 255, 0)"], [100.0, "RGB(255,0,0)"] ] });
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
                    this._TheChart = null;
                }
                if ((null != this._TheMap) && this._TheMap.getSource ("nodes"))
                {
                    this._TheMap.removeLayer ("points");
                    this._TheMap.removeLayer ("lines");
                    this._TheMap.removeLayer ("polygons");
                    this._TheMap.removeSource ("nodes");
                    this._TheMap.removeSource ("edges");
                    this._TheMap.removeSource ("areas");
                }
                if (this._TheMap)
                {
                    this._TheMap.off ("mousedown", this._mousedown_listener);
                    this._cimmap.add_listeners ();
                }
            }

            // load trafokreis
            load_trafo (trafo)
            {
                var self = this;
                self._Trafo = trafo;
                cimquery.queryPromise ({ sql: "select json * from cimapplication.geojson_lines where simulation='" + self._simulation + "' and transformer ='" + self._Trafo + "' allow filtering", cassandra: true })
                .then (data => self.setSimulationGeoJSON_Lines.call (self, data))
                .then (() => cimquery.queryPromise ({ sql: "select json * from cimapplication.geojson_points where simulation='" + self._simulation + "' and transformer ='" + self._Trafo + "' allow filtering", cassandra: true }))
                .then (data => self.setSimulationGeoJSON_Points.call (self, data))
                .then (() =>
                    {
                        self._TheMap.getSource ("nodes").setData (self._simulation_points);
                        self._TheMap.getSource ("edges").setData (self._simulation_lines);
                    }
                )
                .then (() => cimquery.queryPromise ({ sql: "select json * from cimapplication.utilization_summary_by_day where transformer ='" + self._Trafo + "' allow filtering", cassandra: true }))
                .then (data => self.setSimulationSummary_for_Polygon.call (self, data))
                .then (() => cimquery.queryPromise ({ sql: "select json * from cimapplication.utilization_by_day where interval = 900000 and transformer ='" + self._Trafo + "' allow filtering", cassandra: true }))
                .then (data => self.setUtilization_for_Lines.call (self, data))
                .then (() =>
                    {
                        self._TheMap.getSource ("edges").setData (self._simulation_lines);
                    }
                );
            }

            // load cable data
            load_cable (cable)
            {
                var self = this;
                cimquery.queryPromise ({ sql: "select json * from cimapplication.utilization_by_day where interval = 900000 and mrid ='" + cable + "' allow filtering", cassandra: true })
                .then (data => self.setCableUtilization.call (self, data));
            }

            click (x, y)
            {
                var width = 4;
                var height = 4;
                var features = this._TheMap.queryRenderedFeatures
                (
                    [
                      [x - width / 2, y - height / 2],
                      [x + width / 2, y + height / 2]
                    ],
                    {}
                );
                if ((null != features) && (0 != features.length))
                {
                    var trafo = null;
                    var cable = null;
                    for (var i = 0; i < features.length; i++)
                        if (features[i].layer.id == "polygons")
                            trafo = features[i].properties.mRID;
                        else
                            if (features[i].properties.mRID && features[i].properties.ratedCurrent)
                                cable = features[i].properties.mRID;
                    if (!this._Trafo || (trafo != this._Trafo) || (trafo && !cable))
                        this.load_trafo (trafo)
                    else if (cable)
                        this.load_cable (cable);
                }
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
                        this.click (event.point.x, event.point.y);
                }
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             * @memberOf module:simulation_theme
             */
            make_theme (cimmap, options)
            {
                var start = new Date ().getTime ();
                console.log ("rendering simulation data");

                this._cimmap = cimmap;
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

                var end = new Date ().getTime ();
                console.log ("finished rendering simulation data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();

                // set the current filter
                this.legend_changed (new Date (this.getLegend ().getTimes ().start));

                this._cimmap.remove_listeners ();
                this._mousedown_listener = this.mousedown_listener.bind (this);
                this._TheMap.on ("mousedown", this._mousedown_listener);
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
            }

            setSimulationSummary_for_Polygons (data)
            {
                var default_data = {};
                data.forEach (
                    row =>
                    {
                        var utilization = JSON.parse (row["[json]"]);
                        //    {
                        //        avg: 10.277724814899273
                        //        date: "2017-07-17"
                        //        max: 66.69010587510391
                        //        min: 0
                        //        transformer: "TRA2755"
                        //    }
                        var polygon = this._simulation_polygons.features.filter (polygon => polygon.properties.mRID == utilization.transformer);
                        if (polygon.length > 0)
                        {
                            polygon = polygon[0].properties;
                            var date = utilization.date;
                            var item = "T" + date + "min";
                            polygon[item] = utilization.min;
                            default_data[item] = 0.0;
                            var item = "T" + date + "avg";
                            polygon[item] = utilization.avg;
                            default_data[item] = 0.0;
                            var item = "T" + date + "max";
                            polygon[item] = utilization.max;
                            default_data[item] = 0.0;
                        }
                    }
                );
                this._simulation_polygons.features.forEach (
                    polygon =>
                    {
                        for (var x in default_data)
                            if ("undefined" == typeof (polygon.properties[x]))
                                polygon.properties[x] = default_data[x];
                    }
                );
            }

            setSimulationSummary_for_Polygon (data)
            {
                if (null != this._TheChart)
                {
                    this._TheMap.removeControl (this._TheChart);
                    this._TheChart = null;
                }

                var transformer = "";
                var values = data.map (
                    row =>
                    {
                        var utilization = JSON.parse (row["[json]"]);
                        transformer = utilization.transformer;
                        return ([(new Date (utilization.date)).getTime (), utilization.max]);
                    }
                )
                .sort ((a, b) => a[0] - b[0]);
                this._TheChart = new CIMChart ()
                this._TheMap.addControl (this._TheChart);
                this._TheChart.addChart ("Utilization", transformer, values)
            }

            setUtilization_for_Lines (data)
            {
                var index = {};
                this._simulation_lines.features.forEach (line => index[line.properties.mRID] = line);
                var default_data = {};
                data.forEach (
                    row =>
                    {
                        var utilization = JSON.parse (row["[json]"]);
                        var line = index[utilization.mrid];
                        if (line)
                        {
                            var time = utilization.time;
                            var item = "T" + time;
                            line.properties[item] = utilization.percent;
                            default_data[item] = 0.0;
                        }
                    }
                );
                this._simulation_lines.features.forEach (
                    line =>
                    {
                        for (var x in default_data)
                            if ("undefined" == typeof (line.properties[x]))
                                line.properties[x] = default_data[x];
                    }
                );
            }

            setCableUtilization (data)
            {
                if (null != this._TheChart)
                {
                    this._TheMap.removeControl (this._TheChart);
                    this._TheChart = null;
                }

                var cable = "";
                var values = data.map (
                    row =>
                    {
                        var utilization = JSON.parse (row["[json]"]);
                        cable = utilization.mrid;
                        return ([(new Date (utilization.time)).getTime (), utilization.percent]);
                    }
                )
                .sort ((a, b) => a[0] - b[0]); // If compareFunction(a, b) is less than 0, sort a to an index lower than b, i.e. a comes first.
                this._TheChart = new CIMChart ()
                this._TheMap.addControl (this._TheChart);
                this._TheChart.addChart (cable + " Cable Utilization", cable, values)
            }

            setSimulationGeoJSON_Polygons (data)
            {
                // [ {"simulation": "e780ca29-1e69-4748-959a-79461707100d", "mrid": "TRA3215", "geometry": {"type": "Polygon", "coordinates": [[[9.50617, 47.0154], [9.50617, 47.0154]]]}, "type": "Feature"}, ...
                var features = data.map (this.fixup);
                //    {
                //        geometry: { type: "Polygon", coordinates: […] }
                //        properties: { mRID: "TRA2755" }
                //        type: "Feature"
                //    }
                // the polygons GeoJSON
                this._simulation_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };
                this._simulation_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._simulation_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                var extents = { xmin: Number.MAX_VALUE, ymin: Number.MAX_VALUE, xmax: -Number.MAX_VALUE, ymax: -Number.MAX_VALUE };
                features.forEach (
                    polygon =>
                    {
                        polygon.geometry.coordinates.forEach (
                            perimeter =>
                            {
                                perimeter.forEach (
                                    point =>
                                    {
                                        var x = point[0];
                                        var y = point[1];
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
                            }
                        );
                    }
                );
                this._extents = extents;
            }

            setSimulationJSON (data)
            {
                this._simulation_json = JSON.parse (data[0]["[json]"]);
                //    {
                //        cim: "hdfs://sandbox:8020/SAK_sta117_sta206.rdf"
                //        cimreaderoptions: { StorageLevel: "MEMORY_AND_DISK_SER", "ch.ninecode.cim.do_about": "false", "ch.ninecode.cim.do_deduplication": "false", … }
                //        description: "sample simulation"
                //        id: "c01a6f2f-48bf-4a8f-bc13-298e16b5cb21"
                //        interval: { end: "2017-07-18 23:00:00.000Z", start: "2017-07-17 23:00:00.000Z" }
                //        name: "Sample"
                //        players: [ {…}, {…}, {…}, … ]
                //        recorders: [ {…}, {…}, {…}, … ]
                //        transformers: [ "TRA2755", "TRA2769" ]
                //    }
                this._simulation = this._simulation_json.id;
                var times =
                    {
                        start: new Date (this._simulation_json.interval.start).getTime (),
                        end: new Date (this._simulation_json.interval.end).getTime ()
                    };
                this.getLegend ().setTimes (times);
            }

            setSimulation (id)
            {
                this._simulation = id;
                var self = this;
                var promise = cimquery.queryPromise ({ sql: "select json * from cimapplication.simulation where id='" + id + "'", cassandra: true })
                .then (data => self.setSimulationJSON.call (self, data))
                // query the polygons
                .then (() => cimquery.queryPromise ({ sql: "select json * from cimapplication.geojson_polygons where simulation='" + this._simulation + "'", cassandra: true }))
                .then (data => self.setSimulationGeoJSON_Polygons.call (self, data))
                // query the summary results
                .then (() => cimquery.queryPromise ({ sql: "select json * from cimapplication.utilization_summary_by_day", cassandra: true }))
                .then (data => self.setSimulationSummary_for_Polygons.call (self, data));
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