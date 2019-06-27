/**
 * Project theme.
 */
"use strict";

define
(
    ["../mustache", "./diagram", "./project_legend", "./layers", "../cimquery", "../cim", "../cimproject"],
    /**
     * @summary Theme of projects.
     * @exports project_theme
     * @version 1.0
     */
    function (mustache, DiagramTheme, ProjectLegend, layers, cimquery, cim, CIMProject)
    {
        class ProjectTheme extends DiagramTheme
        {
            static getInfoBox ()
            {
                return (ProjectTheme.InfoBox);
            }

            static setInfoBox (box)
            {
                ProjectTheme.InfoBox = box;
            }

            constructor ()
            {
                super ();
                // ToDo: UI mechanism to choose input keyspace
                this._input_keyspace = "cimapplication";
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
                this._loading = null;
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
            }

            /**
             * Remove layers and sources from the map.
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
                if (this._mousedown_listener)
                {
                    this._cimmap.pop_listeners ();
                    delete this._mousedown_listener;
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
                function defer_mousedown (event)
                {
                    const listeners = this._cimmap.peek_listeners ();
                    if (listeners["mousedown"])
                        listeners["mousedown"] (event);
                }
                if ((null != features) && (0 !== features.length))
                {
                    let trafo = null;
                    let boundaryswitch = null;
                    for (let i = 0; i < features.length; i++)
                    {
                        if (features[i].layer.id === "areas")
                            trafo = features[i].properties.name;
                        if (features[i].layer.id === "edge_labels" || features[i].layer.id === "edges")
                            boundaryswitch = features[i].properties.mrid;
                    }
                    if (boundaryswitch)
                    {
                        // check if it's in memory
                        const data = this._cimmap.get_data ();
                        if (data)
                        {
                            const sw = data["Element"][boundaryswitch];
                            if (sw)
                            {
                                // select the switch
                                this._cimmap.select (boundaryswitch, [boundaryswitch]);
                                // make the editor visible
                                const editor = this._cimmap.get_editor ();
                                if (!editor.visible ())
                                {
                                    this._cimmap.get_map ().addControl (editor);
                                    editor.initialize ();
                                }
                            }
                        }
                    }
                    else if (trafo)
                    {
                        // check if it's already loaded
                        const loaded = this._cimmap.get_loaded ();
                        const existing = loaded && loaded.files && loaded.files.includes (trafo);
                        if (!existing && (this._loading !== trafo))
                            this.load_trafo (trafo);
                        else
                            defer_mousedown.call (this, event);
                    }
                    else
                        defer_mousedown.call (this, event);
                }
                else
                    defer_mousedown.call (this, event);
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

            /**
             * For subclasses to override stylization information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             */
            process_spatial_objects_again (data, options)
            {
                super.process_spatial_objects_again (data, options);

                // all black, except for the ones that have data
                const with_data = {};
                for (let trafo in this._consumers_with_data)
                    if (this._consumers_with_data.hasOwnProperty (trafo))
                        this._consumers_with_data[trafo].forEach (mrid => with_data[mrid] = 1);
                this._cimmap.forAll ("EnergyConsumer", consumer => consumer.color = (with_data[consumer.id] ? "#00ff00" : "#000000"));
            }

            color_energy_consumers (transformer)
            {
                // find the polygon in the GeoJSON set and get the time at which to check for data
                const polygon = this._project_polygons.features.find (x => x.properties.name === transformer);
                if (polygon)
                {
                    const time = polygon.properties.time;
                    if (time)
                    {
                        const self = this;
                        cimquery.queryPromise ({ sql: `select elements from ${ self._keyspace }.transformers where id='${ self._project }' and name='${ transformer }'`, cassandra: true })
                        .then (
                            function (data)
                            {
                                const elements = data[0].elements;
                                // make a list of EnergyConsumer
                                const houses = [];
                                for (let id in elements)
                                    if (elements.hasOwnProperty (id))
                                        if (elements[id] === "EnergyConsumer")
                                            houses.push(id);
                                // now see if any of the houses has meter data at that time
                                const inclause = "mrid in (" + houses.map (x => "'" + x + "'").join (",") + ")";
                                if (houses.length !== 0)
                                    cimquery.queryPromise ({ sql: `select mrid from ${ self._input_keyspace }.measured_value where ${ inclause } and type='energy' and time=${ time }`, cassandra: true })
                                        .then (
                                            function (data)
                                            {
                                                self._consumers_with_data[transformer] = data.map (x => x.mrid);
                                                self._cimmap.make_map ();
                                            }
                                        );
                            }
                        );
                    }
                }
            }

            load_trafo (name)
            {
                this._loading = name;
                console.log ("load trafo " + name);
                const self = this;
                const promise = cimquery.queryPromise ({ sql: `select cim from ${ self._keyspace }.transformers where id='${ self._project }' and name='${ name }' allow filtering`, cassandra: true })
                .then (
                    function (data)
                    {
                        const zip = atob (data[0].cim);
                        const bytes = new Array (zip.length);
                        for (let i = 0; i < zip.length; i++)
                            bytes[i] = zip.charCodeAt (i);
                        const blob = new Blob([new Uint8Array (bytes)], {type : 'application/zip'});
                        const start = new Date ().getTime ();
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
                                                        const end = new Date ().getTime ();
                                                        console.log ("finished unzip (" + (Math.round (end - start) / 1000) + " seconds)");

                                                        console.log ("starting CIM read");
                                                        cim.read_xml_blobs ([data]).then (
                                                            function (context)
                                                            {
                                                                const elements = Object.keys (context.parsed.Element).length;
                                                                console.log ("finished CIM read (" + (Math.round (new Date ().getTime () - end) / 1000) + " seconds, " + elements + " elements)");
                                                                if (0 !== context.ignored)
                                                                    console.log (context.ignored.toString () + " unrecognized element" + ((1 < context.ignored) ? "s" : ""));
                                                                const original = self._cimmap.get_data ();
                                                                if (original)
                                                                {
                                                                    // combine the data
                                                                    for (let property in context.parsed)
                                                                        if (context.parsed.hasOwnProperty (property))
                                                                        {
                                                                            let bucket = original[property];
                                                                            if (!bucket)
                                                                                original[property] = bucket = {};
                                                                            const c = context.parsed[property];
                                                                            for (let p in c)
                                                                                if (c.hasOwnProperty (p))
                                                                                    bucket[p] = c[p];
                                                                        }
                                                                    self._cimmap.set_data (original, true);
                                                                    const loaded = self._cimmap.get_loaded ();
                                                                    loaded.files.push (name);
                                                                    loaded.elements = loaded.elements + elements;
                                                                    self._cimmap.set_loaded (loaded);
                                                                    self._loading = null;
                                                                }
                                                                else
                                                                {
                                                                    self._cimmap.set_data (context.parsed, true);
                                                                    self._cimmap.set_loaded ({ files: [name], options: {}, elements: elements });
                                                                    self._loading = null;
                                                                }
                                                                // set the color of EnergyConsumer based on whether there is data at the time retrieved when the polygon was loaded
                                                                self.color_energy_consumers.call (self, name);
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
                map.addLayer (layers.label_layer ("nodes", "nodes", "point", "{name}", "#0000ff"));

                // yellow lines 3 pixels wide with a label
                map.addLayer (layers.line_layer ("edges", "edges", "#ffff00"));
                map.addLayer (layers.label_layer ("edge_labels", "edges", "line-center", "{mrid}: {island1}-{island2}", "#000000"));

                // blue with border
                map.addLayer (layers.polygon_layer ("areas", "areas", { type: "identity", property: "color" }, "#000000"));

                const end = new Date ().getTime ();
                console.log ("finished rendering project data (" + (Math.round (end - start) / 1000) + " seconds)");

                if (this._render_listener)
                    this._render_listener ();

                this._mousedown_listener = this.mousedown_listener.bind (this);
                this._cimmap.push_listeners ({ "mousedown": this._mousedown_listener });

                // add the project info box
                if (!ProjectTheme.getInfoBox ())
                {
                    ProjectTheme.setInfoBox (new CIMProject (this._cimmap, this));
                    this._cimmap.get_map ().addControl (ProjectTheme.getInfoBox ());
                    ProjectTheme.getInfoBox ().initialize ();
                }
                else if (!ProjectTheme.getInfoBox ().visible ())
                {
                    this._cimmap.get_map ().addControl (ProjectTheme.getInfoBox ());
                    ProjectTheme.getInfoBox ().initialize ();
                }
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
                        const coordinates_island1 = this._project_points.features.find (x => x.properties.name === row.island1).geometry.coordinates;
                        const coordinates_island2 = this._project_points.features.find (x => x.properties.name === row.island2).geometry.coordinates;
                        // shorten the lines by 8% on each end using the parametric form of the line x = x0 + at; y = y0 + bt
                        const offset = 8. / 100.0;
                        const x0 = coordinates_island1[0];
                        const y0 = coordinates_island1[1];
                        const a = (coordinates_island2[0] - x0);
                        const b = (coordinates_island2[1] - y0);
                        const p1 = [x0 + a * offset, y0 + b * offset];
                        const p2 = [x0 + a * (1.0 - offset), y0 + b * (1.0 - offset)];
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

            setProjectGeoJSON_Polygons (data)
            {
                // {"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [[[5.270025681883567, 51.471759093742094], [5.269886154431646, 51.47177654841522], [5.269554541950953, 51.471945318378914], [5.269122160971165, 51.47240697336926], [5.269002467393875, 51.47254885425582], [5.269002467393875, 51.47254888036082], [5.269022391821153, 51.47278951418596], [5.269132702340698, 51.47281786323444], [5.269132722169161, 51.47281786547049], [5.269623965172592, 51.472633014284845], [5.269901236980701, 51.47206227547255], [5.270012366281179, 51.471814907323335], [5.270025681883567, 51.471759093742094]]]}, "properties": {"name": "TX0001"}}
                const features = data.map ((raw) => JSON.parse (raw["[json]"]));

                // color them blue until we get information about whether they have data or not
                features.forEach ((feature) => feature.properties.color = "#0000ff");

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
                        const position = this.centroid (polygon.geometry.coordinates);
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

            // color the polygons based on whether they have data or not
            checkForData ()
            {
                const self = this;
                cimquery.queryPromise ({ sql: `select name, elements from ${ self._keyspace }.transformers`, cassandra: true })
                    .then (
                        function (trafos)
                        {
                            Promise.all (
                                trafos.map (
                                    function (trafo)
                                    {
                                        const transformer = trafo.name;
                                        const elements = trafo.elements;
                                        // make a list of EnergyConsumer
                                        const houses = [];
                                        for (let id in elements)
                                            if (elements.hasOwnProperty (id))
                                                if (elements[id] === "EnergyConsumer")
                                                    houses.push(id);
                                        // now see if any of the houses has meter data
                                        const inclause = "mrid in (" + houses.map (x => "'" + x + "'").join (",") + ")";
                                        if (houses.length === 0)
                                            return (Promise.resolve ());
                                        else
                                            return (
                                                cimquery.queryPromise ({ sql: `select TOUNIXTIMESTAMP(time) as time from ${ self._input_keyspace }.measured_value where ${ inclause } and type='energy' limit 1`, cassandra: true })
                                                    .then (
                                                        function (data)
                                                        {
                                                            if (data.length > 0)
                                                            {
                                                                // find the polygon in the GeoJSON set and update it's color and a time at which to check for data
                                                                const polygon = self._project_polygons.features.find (x => x.properties.name === transformer);
                                                                if (polygon)
                                                                {
                                                                    polygon.properties.color = "#00ff00";
                                                                    polygon.properties.time = data[0].time;
                                                                }
                                                            }
                                                        }
                                                    )
                                                );
                                    }
                                )
                            ).then (
                                function ()
                                {
                                    self._TheMap.getSource ("areas").setData (self._project_polygons);
                                }
                            );
                        }
                    );
            }

            setProject (keyspace, id)
            {
                this._keyspace = keyspace;
                this._project = id;
                this._consumers_with_data = {};
                const self = this;
                const promise = cimquery.queryPromise ({ sql: `select json type, geometry, properties from ${ keyspace }.transformer_service_area where id='${ id }'`, cassandra: true })
                    .then (data => self.setProjectGeoJSON_Polygons.call (self, data))
                    .then (() =>
                        {
                            self._TheMap.getSource ("areas").setData (self._project_polygons);
                            self._TheMap.getSource ("nodes").setData (self._project_points);
                        }
                    )
                    .then (() => cimquery.queryPromise ({ sql: `select * from ${ keyspace }.boundary_switches where id='${ id }'`, cassandra: true }))
                    .then (data => self.setProjectGeoJSON_Lines.call (self, data))
                    .then (() => self._TheMap.getSource ("edges").setData (self._project_lines))
                    .then (() => self._cimmap.set_data (null))
                    .then (() => self.checkForData ());

                return (promise);
            }

            setRenderListener (fn)
            {
                this._render_listener = fn;
            }
        }

        return (ProjectTheme);
    }
);
