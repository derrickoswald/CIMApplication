/**
 * Project theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./project_legend", "./layers", "../cimquery", "../cim"],
    /**
     * @summary Theme of projects.
     * @name project_theme
     * @exports project_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, ProjectLegend, layers, cimquery, cim)
    {
        class ProjectTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
                this._legend = new ProjectLegend (this);
                this._project_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._project_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._project_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._extents = { xmin: 0.0, ymin: 0.0, xmax: 0.0, ymax: 0.0 };
                this._render_listener = null;
                this.getLegend ().legend_change_listener (this.legend_changed.bind (this));
                this._Trafo = null;
            }

            getName ()
            {
                return ("ProjectTheme");
            }

            getTitle ()
            {
                return ("Project builder");
            }

            getDescription ()
            {
                return ("Create a project by assembling transformer service areas.");
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
                if (value)
                    this.setProject (value.keyspace, value.id);
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
                if ((null != this._TheMap) && this._TheMap.getSource ("nodes"))
                {
                    this._TheMap.removeLayer ("nodes");
                    this._TheMap.removeLayer ("edges");
                    this._TheMap.removeLayer ("edge_labels");
                    this._TheMap.removeLayer ("areas");
                    this._TheMap.removeSource ("nodes");
                    this._TheMap.removeSource ("edges");
                    this._TheMap.removeSource ("areas");
                }
                if (this._TheMap)
                {
                    this._TheMap.off ("mousedown", this._mousedown_listener);
                    this._cimmap.add_listeners ();
                }
                super.remove_theme ();
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
                    for (var i = 0; i < features.length; i++)
                        if (features[i].layer.id == "areas")
                            trafo = features[i].properties.name;
                    if (((null == this._Trafo) && (null != trafo)) || (trafo != this._Trafo))
                        this.load_trafo (trafo);
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

            clear ()
            {
                this._project_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._project_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._project_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._TheMap.getSource ("nodes").setData (this._project_points);
                this._TheMap.getSource ("edges").setData (this._project_lines);
                this._TheMap.getSource ("edge_labels").setData (this._project_lines);
                this._TheMap.getSource ("areas").setData (this._project_polygons);
            }

            load_trafo (name)
            {
                var self = this;
                var promise = cimquery.queryPromise ({ sql: "select cim from " + self._keyspace + ".transformers where id='" + self._project + "' and name='" + name + "' allow filtering", cassandra: true })
                .then (
                    function (data)
                    {
                        var zip = atob (data[0].cim);
                        var bytes = new Array (zip.length);
                        for (var i = 0; i < zip.length; i++)
                            bytes[i] = zip.charCodeAt (i);
                        var blob = new Blob([new Uint8Array (bytes)], {type : 'application/zip'});
                        var start = new Date ().getTime ();
                        console.log ("starting unzip");
                        require (
                            ["zip/zip", "zip/mime-types"],
                            function (zip, mimeTypes)
                            {
                                //zip.workerScriptsPath = "js/zip/";
                                zip.useWebWorkers = false;
                                zip.createReader (new zip.BlobReader (blob),
                                    function (zipReader)
                                    {
                                        zipReader.getEntries (
                                            function (entries) {
                                                entries[0].getData (
                                                    new zip.BlobWriter (mimeTypes.getMimeType (entries[0].filename)),
                                                    function (data)
                                                    {
                                                        zipReader.close ();
                                                        var end = new Date ().getTime ();
                                                        console.log ("finished unzip (" + (Math.round (end - start) / 1000) + " seconds)");

                                                        console.log ("starting CIM read");
                                                        cim.read_xml_blobs ([data]).then (
                                                            function (context)
                                                            {
                                                                var elements = Object.keys (context.parsed.Element).length;
                                                                console.log ("finished CIM read (" + (Math.round (new Date ().getTime () - end) / 1000) + " seconds, " + elements + " elements)");
                                                                if (0 != context.ignored)
                                                                    console.log (context.ignored.toString () + " unrecognized element" + ((1 < context.ignored) ? "s" : ""));
                                                                var original = self._cimmap.get_data ();
                                                                if (original)
                                                                {
                                                                    // combine the data
                                                                    for (var property in context.parsed)
                                                                        if (context.parsed.hasOwnProperty (property))
                                                                        {
                                                                            var bucket = original[property];
                                                                            if (!bucket)
                                                                                original[property] = bucket = {};
                                                                            var c = context.parsed[property];
                                                                            for (var p in c)
                                                                                bucket[p] = c[p];
                                                                        }
                                                                    self._cimmap.set_data (original, true);
                                                                    var loaded = self._cimmap.get_loaded ();
                                                                    loaded.files.push (name);
                                                                    loaded.elements = loaded.elements + elements;
                                                                    self._cimmap.set_loaded (loaded);
                                                                }
                                                                else
                                                                {
                                                                    self._cimmap.set_data (context.parsed, true);
                                                                    self._cimmap.set_loaded ({ files: [name], options: {}, elements: elements });
                                                                }
                                                            }
                                                        );
                                                    }
                                            );
                                        })
                                    }
                                );
                            }
                        );
                    }
                );

                return (promise);
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             * @memberOf module:project_theme
             */
            make_theme (cimmap, options)
            {
                super.make_theme (cimmap, options);

                var start = new Date ().getTime ();
                console.log ("rendering project data");

                this._cimmap = cimmap;
                var map = cimmap.get_map ();
                this._TheMap = map; // to be able to remove it later

                // update the map
                map.addSource
                (
                    "nodes",
                    {
                        type: "geojson",
                        data: this._project_points,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "edges",
                    {
                        type: "geojson",
                        data: this._project_lines,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "areas",
                    {
                        type: "geojson",
                        data: this._project_polygons,
                        maxzoom: 24
                    }
                );

                // label the polygons
                map.addLayer (layers.label_layer ("nodes", "nodes", "point", "{name}", "#0000ff"))

                // yellow lines 3 pixels wide with a label
                map.addLayer (layers.line_layer ("edges", "edges", "#ffff00"));
                map.addLayer (layers.label_layer ("edge_labels", "edges", "line-center", "{mrid}: {island1}-{island2}", "#000000"))

                // blue with border
                map.addLayer (layers.polygon_layer ("areas", "areas", "#0000ff", "#000000"))

                var end = new Date ().getTime ();
                console.log ("finished rendering project data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();

                this._cimmap.remove_listeners ();
                this._mousedown_listener = this.mousedown_listener.bind (this);
                this._TheMap.on ("mousedown", this._mousedown_listener);
            }

            fixup (raw)
            {
                var feature = JSON.parse (raw["[json]"]);
                delete feature.id;
                var name = feature.name;
                delete feature.name;
                if (!feature.properties)
                    feature.properties = {};
                feature.properties.name = name;
                return (feature);
            }

            setProjectGeoJSON_Points (data)
            {
                // {"id": "2f956deb-75ba-426a-99be-a29684ab8428", "mrid": "JPR00001", "island1": "TX0002", "island2": "TX0001"}
                var features = data.map (this.fixup);
                // the points GeoJSON
                this._project_points =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };
            }

            setProjectGeoJSON_Lines (data)
            {
                // id                                   | mrid     | island1 | island2
                //--------------------------------------+----------+---------+---------
                // d0d17f07-7dcf-4208-9a45-f16eab441739 | JPR00001 |  TX0002 |  TX0001

                // generate the link lines JSON
                this._project_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                data.forEach ((row) =>
                    {
                        var coordinates_island1 = this._project_points.features.find ((x) => x.properties.name == row.island1).geometry.coordinates;
                        var coordinates_island2 = this._project_points.features.find ((x) => x.properties.name == row.island2).geometry.coordinates;
                        // shorten the lines by 8% on each end using the parametric form of the line x = x0 + at; y = y0 + bt
                        var offset = 8. / 100.0;
                        var x0 = coordinates_island1[0];
                        var y0 = coordinates_island1[1];
                        var a = (coordinates_island2[0] - x0);
                        var b = (coordinates_island2[1] - y0);
                        var p1 = [x0 + a * offset, y0 + b * offset];
                        var p2 = [x0 + a * (1.0 - offset), y0 + b * (1.0 - offset)];
                        delete row.id;
                        this._project_lines.features.push
                        (
                            {
                                type : "Feature",
                                geometry :
                                {
                                    type : "LineString",
                                    coordinates : [p1, p2]
                                },
                                properties : row
                            }
                        );
                    }
                );
            }

            centroid (coordinates)
            {
                var centroid = [0.0, 0.0];
                var area = 0.0;
                var x0 = 0.0; // current vertex X
                var y0 = 0.0; // current vertex Y
                var x1 = 0.0; // next vertex X
                var y1 = 0.0; // next vertex Y
                var a = 0.0;  // partial signed area

                coordinates.forEach (
                    perimeter =>
                    {
                        for (var i = 0; i < perimeter.length - 1; ++i)
                        {
                            x0 = perimeter[i][0];
                            y0 = perimeter[i][1];
                            if (i == perimeter.length - 1)
                            {
                                x1 = perimeter[0][0];
                                y1 = perimeter[0][1];
                            }
                            else
                            {
                                x1 = perimeter[i+1][0];
                                y1 = perimeter[i+1][1];
                            }
                            a = x0 * y1 - x1 * y0;
                            area += a;
                            centroid[0] += (x0 + x1) * a;
                            centroid[1] += (y0 + y1) * a;
                        }
                    }
                );
                centroid[0] /= (3.0 * area);
                centroid[1] /= (3.0 * area);

                return (centroid);
            }

            setProjectGeoJSON_Polygons (data)
            {
                //  {"id": "2f956deb-75ba-426a-99be-a29684ab8428", "name": "TX0001", "geometry": {"type": "Polygon", "coordinates": [[[5.270025681883567, 51.471759093742094], [5.269886154431646, 51.47177654841522], [5.269554541950953, 51.471945318378914], [5.269122160971165, 51.47240697336926], [5.269002467393875, 51.47254885425582], [5.269002467393875, 51.47254888036082], [5.269022391821153, 51.47278951418596], [5.269132702340698, 51.47281786323444], [5.269132722169161, 51.47281786547049], [5.269623965172592, 51.472633014284845], [5.269901236980701, 51.47206227547255], [5.270012366281179, 51.471814907323335], [5.270025681883567, 51.471759093742094]]]}, "properties": {"name": "TX0001"}, "type": "Feature"}
                var features = data.map (this.fixup);
                // the polygons GeoJSON
                this._project_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };

                // generate the labels JSON
                this._project_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                features.forEach (
                    polygon =>
                    {
                        var position = this.centroid (polygon.geometry.coordinates);
                        this._project_points.features.push
                        (
                            {
                                type : "Feature",
                                geometry :
                                {
                                    type : "Point",
                                    coordinates : position
                                },
                                properties : polygon.properties
                            }
                        );
                    }
                )

                // get the extents of the theme
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

            setProjectJSON (data)
            {
                this._project_json = JSON.parse (data[0]["[json]"]);
                this._project = this._project_json.id;
            }

            setProject (keyspace, id)
            {
                this._keyspace = keyspace;
                this._project = id;
                var self = this;
                var promise = cimquery.queryPromise ({ sql: "select json * from " + keyspace + ".transformer_service_area where id='" + id + "'", cassandra: true })
                    .then (data => self.setProjectGeoJSON_Polygons.call (self, data))
                    .then (() =>
                        {
                            self._TheMap.getSource ("areas").setData (self._project_polygons);
                            self._TheMap.getSource ("nodes").setData (self._project_points);
                        }
                    )
                    .then (() => cimquery.queryPromise ({ sql: "select * from " + keyspace + ".boundary_switches where id='" + id + "'", cassandra: true }))
                    .then (data => self.setProjectGeoJSON_Lines.call (self, data))
                    .then (() => self._TheMap.getSource ("edges").setData (self._project_lines))
                    .then (() => self._cimmap.set_data (null));

                return (promise);
            }

            setRenderListener (fn)
            {
                this._render_listener = fn;
            }
        }

        return (ProjectTheme);
    }
)
