/**
 * Project theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme", "./event_legend", "./layers", "../cimquery", "../cim"],
    /**
     * @summary Theme of events (overvoltage, overcurrent, overpower).
     * @exports event_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme, EventLegend, layers, cimquery, cim)
    {
        class EventTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
                this._legend = new EventLegend (this);
                this._event_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_polygons_labels =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._extents = { xmin: 0.0, ymin: 0.0, xmax: 0.0, ymax: 0.0 };
                this._render_listener = null;
                this.getLegend ().legend_change_listener (this.legend_changed.bind (this));
            }

            getName ()
            {
                return ("EventTheme");
            }

            getTitle ()
            {
                return ("Events");
            }

            getDescription ()
            {
                return ("Show anomolous events (overvoltage, overcurrent, overpower).");
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
                    this.setSimulation (value);
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
                    this._TheMap.removeLayer ("areas");
                    this._TheMap.removeLayer ("labels");
                    this._TheMap.removeSource ("nodes");
                    this._TheMap.removeSource ("edges");
                    this._TheMap.removeSource ("areas");
                    this._TheMap.removeSource ("labels");
                }
                if (this._TheMap)
                {
                    this._TheMap.off ("mousedown", this._mousedown_listener);
                    this._cimmap.add_listeners ();
                }
                super.remove_theme ();
            }

            click (event)
            {
                const x = event.point.x;
                const y = event.point.y;
                const width = 4;
                const height = 4;
                const features = this._TheMap.queryRenderedFeatures
                (
                    [
                      [x - width / 2, y - height / 2],
                      [x + width / 2, y + height / 2]
                    ],
                    {}
                );
                if ((null != features) && (0 !== features.length))
                {
                    let trafo = null;
                    let feature = null;
                    for (let i = 0; i < features.length; i++)
                        if (features[i].layer.id === "areas")
                            trafo = features[i].properties.mrid;
                        else if ((features[i].layer.id === "nodes") || (features[i].layer.id === "edges"))
                            feature = features[i].properties.mrid;
                    if (trafo && !this._loaded.includes (trafo))
                        this.load_trafo (trafo);
                    else if (feature)
                        this._cimmap.select (feature, [feature]);
                    else
                        this._cimmap.default_mousedown_listener (event);
                }
                else
                    this._cimmap.default_mousedown_listener (event);
            }

            // handle mouse click
            mousedown_listener (event)
            {
                // only do something if no key is pressed
                const key = event.originalEvent.ctrlKey || event.originalEvent.shiftKey || event.originalEvent.altKey || event.originalEvent.metaKey;
                if (!key)
                {
                    const buttons = event.originalEvent.buttons;
                    //    0  : No button or un-initialized
                    //    1  : Primary button (usually left)
                    //    2  : Secondary button (usually right)
                    const leftbutton = 0 !== (buttons & 1);
                    //const rightbutton = 0 !== (buttons & 2);
                    if (leftbutton)
                        this.click (event);
                }
            }

            clear ()
            {
                this._event_points =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._event_polygons_labels =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                this._TheMap.getSource ("nodes").setData (this._event_points);
                this._TheMap.getSource ("edges").setData (this._event_lines);
                this._TheMap.getSource ("areas").setData (this._event_polygons);
                this._TheMap.getSource ("labels").setData (this._event_polygons_labels);
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
                const consumers = data.EnergyConsumer;
                // all black
                for (let id in consumers)
                    if (consumers.hasOwnProperty (id))
                        consumers[id].color = "#0000000";
                // except for the ones that have data
                for (let trafo in this._consumers_with_data)
                    if (this._consumers_with_data.hasOwnProperty (trafo))
                    {
                        const consumerlist = this._consumers_with_data[trafo];
                        consumerlist.forEach (
                            mrid =>
                            {
                                const consumer = consumers[mrid];
                                if (consumer)
                                    consumer.color = "#00ff00";
                            }
                        );
                    }
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             */
            make_theme (cimmap, options)
            {
                super.make_theme (cimmap, options);

                const start = new Date ().getTime ();
                console.log ("rendering project data");

                this._cimmap = cimmap;
                const map = cimmap.get_map ();
                this._TheMap = map; // to be able to remove it later

                // update the map
                map.addSource
                (
                    "nodes",
                    {
                        type: "geojson",
                        data: this._event_points,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "edges",
                    {
                        type: "geojson",
                        data: this._event_lines,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "areas",
                    {
                        type: "geojson",
                        data: this._event_polygons,
                        maxzoom: 24
                    }
                );

                map.addSource
                (
                    "labels",
                    {
                        type: "geojson",
                        data: this._event_polygons_labels,
                        maxzoom: 24
                    }
                );

                // energy consumers
                map.addLayer (layers.full_circle_layer ("nodes", "nodes", { type: "identity", property: "color" }));

                // cables
                map.addLayer (layers.line_layer ("edges", "edges", { type: "identity", property: "color" }));

                // transformer service areas
                map.addLayer (layers.polygon_layer ("areas", "areas", { type: "identity", property: "color" }, "#000000", null, { type: "identity", property: "pattern" }));

                // label the transformer service areas
                map.addLayer (layers.label_layer ("labels", "labels", "point", "{name}", "#000000"));

                const end = new Date ().getTime ();
                console.log ("finished rendering event data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();

                this._cimmap.remove_listeners ();
                this._mousedown_listener = this.mousedown_listener.bind (this);
                this._TheMap.on ("mousedown", this._mousedown_listener);
            }

            setProjectGeoJSON_Lines (data)
            {
                // id                                   | mrid     | island1 | island2
                //--------------------------------------+----------+---------+---------
                // d0d17f07-7dcf-4208-9a45-f16eab441739 | JPR00001 |  TX0002 |  TX0001

                // generate the link lines JSON
                this._event_lines =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                data.forEach ((row) =>
                    {
                        const coordinates_island1 = this._event_points.features.find (x => x.properties.name === row.island1).geometry.coordinates;
                        const coordinates_island2 = this._event_points.features.find (x => x.properties.name === row.island2).geometry.coordinates;
                        // shorten the lines by 8% on each end using the parametric form of the line x = x0 + at; y = y0 + bt
                        const offset = 8. / 100.0;
                        const x0 = coordinates_island1[0];
                        const y0 = coordinates_island1[1];
                        const a = (coordinates_island2[0] - x0);
                        const b = (coordinates_island2[1] - y0);
                        const p1 = [x0 + a * offset, y0 + b * offset];
                        const p2 = [x0 + a * (1.0 - offset), y0 + b * (1.0 - offset)];
                        delete row.id;
                        this._event_lines.features.push
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
                const centroid = [0.0, 0.0];
                let area = 0.0;
                let x0 = 0.0; // current vertex X
                let y0 = 0.0; // current vertex Y
                let x1 = 0.0; // next vertex X
                let y1 = 0.0; // next vertex Y
                let a = 0.0;  // partial signed area

                coordinates.forEach (
                    perimeter =>
                    {
                        for (let i = 0; i < perimeter.length - 1; ++i)
                        {
                            x0 = perimeter[i][0];
                            y0 = perimeter[i][1];
                            if (i === perimeter.length - 1)
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

            setEventGeoJSON_Polygons (data)
            {
                // {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [[[5.270025681883567, 51.471759093742094], [5.269886154431646, 51.47177654841522], [5.269554541950953, 51.471945318378914], [5.269122160971165, 51.47240697336926], [5.269002467393875, 51.47254885425582], [5.269002467393875, 51.47254888036082], [5.269022391821153, 51.47278951418596], [5.269132702340698, 51.47281786323444], [5.269132722169161, 51.47281786547049], [5.269623965172592, 51.472633014284845], [5.269901236980701, 51.47206227547255], [5.270012366281179, 51.471814907323335], [5.270025681883567, 51.471759093742094]]]}, "properties": {"name": "TX0001"}}
                const features = data.map ((raw) => JSON.parse (raw["[json]"]));

                // move the mrid into the properties
                features.forEach ((feature) => { if (!feature.properties) feature.properties = {}; feature.properties.mrid = feature.mrid; delete feature.mrid; });

                // color them green until we get information about whether they have events or not
                features.forEach ((feature) => feature.properties.pattern = "pat_0_0_0");

                // the polygons GeoJSON
                this._event_polygons =
                {
                    "type" : "FeatureCollection",
                    "features" : features
                };

                // generate the labels JSON
                this._event_polygons_labels =
                {
                    "type" : "FeatureCollection",
                    "features" : []
                };
                features.forEach (
                    polygon =>
                    {
                        const position = this.centroid (polygon.geometry.coordinates);
                        this._event_polygons_labels.features.push
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
                );

                // get the extents of the theme
                const extents = { xmin: Number.MAX_VALUE, ymin: Number.MAX_VALUE, xmax: -Number.MAX_VALUE, ymax: -Number.MAX_VALUE };
                features.forEach (
                    polygon =>
                    {
                        polygon.geometry.coordinates.forEach (
                            perimeter =>
                            {
                                perimeter.forEach (
                                    point =>
                                    {
                                        const x = point[0];
                                        const y = point[1];
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

            // color the polygons based on whether they have events or not and their severity
            checkForTransformerServiceAreaEvents ()
            {
                // ToDo: can we take all events for a simulation rather than just 5000?
                const self = this;
                cimquery.queryPromise ({ sql: `select mrid, type, severity, TOUNIXTIMESTAMP(start_time), TOUNIXTIMESTAMP(end_time) from ${ self._simulation.output_keyspace }.simulation_event where simulation='${ self._simulation.id }' limit 5000 allow filtering`, cassandra: true })
                    .then (
                        function (events)
                        {
                            // mrid    | type                             | severity | start_time                      | end_time
                            // ---------+----------------------------------+----------+---------------------------------+---------------------------------
                            // HAS2596 | voltage subceeds 10.0% threshold |        2 | 2018-02-13 22:30:00.000000+0000 | 2018-02-13 22:45:00.000000+0000
                            // HAS2596 | voltage subceeds 10.0% threshold |        2 | 2018-03-19 22:15:00.000000+0000 | 2018-03-19 22:30:00.000000+0000
                            self._events = events;

                            // create the mapping table between mrid and transformer service area
                            let mapping = {};
                            self._simulation.recorders.forEach (x => mapping[x.mrid] = x.transformer);

                            // now scoot through the events and generate the pattern discriminate for each transformer
                            let patterns = {};
                            self._events.forEach (
                                x =>
                                {
                                    const trafo = mapping[x.mrid];
                                    let discriminate = patterns[trafo];
                                    if (!discriminate)
                                        discriminate = patterns[trafo] = [0, 0, 0];
                                    const index = x.type.startsWith ("power") ? 0 : x.type.startsWith ("voltage") ? 1 : 2;
                                    discriminate[index] = Math.max (discriminate[index], x.severity);
                                }
                            );

                            self._event_polygons.features.forEach (
                                x =>
                                {
                                    const discriminate = patterns[x.properties.mrid];
                                    if (discriminate)
                                        x.properties.pattern = "pat_" + discriminate.join ("_");
                                }
                            );

                            self._TheMap.getSource ("areas").setData (self._event_polygons);
                        }
                    );
            }

            setSimulation (simulation)
            {
                this._simulation = simulation;
                this._loaded = [];
                this.clear ();
                const self = this;
                const promise = cimquery.queryPromise ({ sql: `select json mrid, type, geometry, properties from ${ self._simulation.output_keyspace }.geojson_polygons where simulation='${ self._simulation.id }'`, cassandra: true })
                    .then (data => self.setEventGeoJSON_Polygons.call (self, data))
                    .then (() =>
                        {
                            self._TheMap.getSource ("areas").setData (self._event_polygons);
                            self._TheMap.getSource ("labels").setData (self._event_polygons_labels);
                        }
                    )
                    .then (() => self._cimmap.set_data (null))
                    .then (() => self.checkForTransformerServiceAreaEvents.call (self));

                return (promise);
            }

            setEventGeoJSON_Points (data)
            {
                // {"mrid": "ABG1066", "type": "Feature", "geometry": {"type": "Point", "coordinates": [8.76929328008, 47.0414525751]}, "properties": null}
                const features = data.map ((raw) => JSON.parse (raw["[json]"]));

                // move the mrid into the properties
                features.forEach ((feature) => { if (!feature.properties) feature.properties = {}; feature.properties.mrid = feature.mrid; delete feature.mrid; });

                // color them green until we get information about whether they have events or not
                features.forEach ((feature) => feature.properties.color = "#00ff00");

                // the points GeoJSON
                this._event_points =
                    {
                        "type" : "FeatureCollection",
                        "features" : features
                    };
            }

            setEventGeoJSON_Lines (data)
            {
                // {"mrid": "FLE1005", "type": "Feature", "geometry": {"type": "LineString", "coordinates": [[8.77861991238, 47.053758741], [8.77827516523, 47.0539704543], [8.77794293792, 47.0541760109], [8.77749911987, 47.0543045381], [8.77703878787, 47.0544391942], [8.77660127174, 47.0545667]]}, "properties": {"ratedCurrent": "205.0"}}
                const features = data.map ((raw) => JSON.parse (raw["[json]"]));

                // move the mrid into the properties
                features.forEach ((feature) => { if (!feature.properties) feature.properties = {}; feature.properties.mrid = feature.mrid; delete feature.mrid; });

                // color them green until we get information about whether they have events or not
                features.forEach ((feature) => feature.properties.color = "#00ff00");

                // the lines GeoJSON
                this._event_lines =
                    {
                        "type" : "FeatureCollection",
                        "features" : features
                    };
            }

            // assume _events has already been loaded
            checkForVoltageEvents ()
            {
                // if any events are voltage events mark them
                let lookup = {};
                this._event_points.features.forEach (x => lookup[x.properties.mrid] = x);
                // now scoot through the events and label any nodes they refer to
                this._events.forEach (
                    x =>
                    {
                        const node = lookup[x.mrid];
                        if (node)
                        {
                            // ToDo: should do Math.max here
                            const color = (x.severity === 1) ? "#ffa500" : "#ff0000";
                            const current_color = node.properties.color;
                            node.properties.color = (current_color && (current_color === "#ff0000")) ? current_color : color;
                        }
                    }
                );
                this._TheMap.getSource ("nodes").setData (this._event_points);

            }

            // assume _events has already been loaded
            checkForCurrentEvents ()
            {
                // if any events are current events mark them
                let lookup = {};
                this._event_lines.features.forEach (x => lookup[x.properties.mrid] = x);
                // now scoot through the events and label any nodes they refer to
                this._events.forEach (
                    x =>
                    {
                        const edge = lookup[x.mrid];
                        if (edge)
                        {
                            // ToDo: should do Math.max here
                            const color = (x.severity === 1) ? "#ffa500" : "#ff0000";
                            const current_color = edge.properties.color;
                            edge.properties.color = (current_color && (current_color === "#ff0000")) ? current_color : color;
                        }
                    }
                );
                this._TheMap.getSource ("edges").setData (this._event_lines);

            }

            load_trafo (transformer)
            {
                const self = this;
                this._loaded.push (transformer);
                const promise = cimquery.queryPromise ({ sql: `select json mrid, type, geometry, properties from ${ self._simulation.output_keyspace }.geojson_points where simulation='${ self._simulation.id }' and transformer='${ transformer }' allow filtering`, cassandra: true })
                    .then (data => self.setEventGeoJSON_Points.call (self, data))
                    .then (() => self._TheMap.getSource ("nodes").setData (self._event_points))
                    .then (() => self._cimmap.set_data (null))
                    .then (() => self.checkForVoltageEvents.call (self))
                    .then (() => cimquery.queryPromise ({ sql: `select json mrid, type, geometry, properties from ${ self._simulation.output_keyspace }.geojson_lines where simulation='${ self._simulation.id }' and transformer='${ transformer }' allow filtering`, cassandra: true }))
                    .then (data => self.setEventGeoJSON_Lines.call (self, data))
                    .then (() => self.checkForCurrentEvents.call (self));
            }

            setRenderListener (fn)
            {
                this._render_listener = fn;
            }
        }

        return (EventTheme);
    }
);
