/**
 * Digitizer for CIM Application
 */
"use strict";

define
(
    ["cancelablepromise"],
    /**
     * @summary Point and line digitizer.
     * @description functions to digitize a point or a line
     * @name digitizer
     * @exports digitizer
     * @version 1.0
     */
    function (CancelablePromise)
    {
        class Digitizer
        {
            constructor (map, cimmap)
            {
                this._map = map;
                this._cimmap = cimmap;
                this._mrid = null;
            }

            popup (html, position)
            {
                var lnglat = position || this._map.getCenter ();
                var popup = new mapboxgl.Popup ();
                popup.setLngLat (lnglat)
                popup.setHTML (html)
                popup.addTo (this._map);
                return (popup);
            }

            distance (a, b)
            {
                var dx = a.lng - b.lng;
                var dy = a.lat - b.lat;
                return (dx * dx + dy * dy);
            }

            snap (event)
            {
                var ret = event.lngLat
                var width = 4;
                var height = 4;
                var features = this._map.queryRenderedFeatures
                (
                    [
                      [event.point.x - width / 2, event.point.y - height / 2],
                      [event.point.x + width / 2, event.point.y + height / 2]
                    ],
                    {}
                );
                if ((null != features) && (0 != features.length))
                {
                    var mrid = this._mrid;
                    var best_lnglat = null;
                    var best_feature = null;
                    var dist = this.distance.bind (this);
                    function assign_best (lnglat, feature)
                    {
                        best_lnglat = lnglat;
                        best_feature = feature;
                        console.log ("snap " + feature.properties.cls + ":" + feature.properties.mRID + " " + dist (ret, lnglat) + " [" + lnglat.lng + "," + lnglat.lat + "]");
                    }
                    for (var i = 0; i < features.length; i++)
                    {
                        if (features[i].properties.mRID && (mrid != features[i].properties.mRID)) // only our features and not the current one
                        {
                            if ("Point" == features[i].geometry.type)
                            {
                                var candidate = new mapboxgl.LngLat.convert (features[i].geometry.coordinates);
                                if (null == best_lnglat)
                                    assign_best (candidate, features[i]);
                                else if (this.distance (ret, candidate) < this.distance (ret, best_lnglat))
                                    assign_best (candidate, features[i]);
                            }
                            else if ("LineString" == features[i].geometry.type)
                            {
                                for (var j = 0; j < features[i].geometry.coordinates.length; j++)
                                {
                                    var candidate = new mapboxgl.LngLat.convert (features[i].geometry.coordinates[j]);
                                    if (null == best_lnglat)
                                        assign_best (candidate, features[i]);
                                    else if (this.distance (ret, candidate) < this.distance (ret, best_lnglat))
                                        assign_best (candidate, features[i]);
                                }
                            }
                        }
                    }
                    if (null != best_lnglat)
                        ret = best_lnglat;
                }

                return (ret);
            }

            digitize_point_mousedown_listener (points, callback_success, callback_failure, event)
            {
                event.originalEvent.preventDefault ();
                event.originalEvent.stopPropagation ();
                var buttons = event.originalEvent.buttons;
                var leftbutton = 0 != (buttons & 1);
                if (leftbutton)
                {
                    var lnglat = this.snap (event);
                    var feature = points.features[points.features.length - 1];
                    feature.geometry.coordinates = [lnglat.lng, lnglat.lat];
                    this._map.getSource ("edit points").setData (points);
                    callback_success (feature);
                }
                else
                    callback_failure ();
            }

            set_point_listeners ()
            {
                if (this._mousedown)
                {
                    // set up our listeners
                    this._map.dragPan.disable ();
                    this._map.dragRotate.disable ();
                    this._cimmap.remove_listeners ();
                    this._map.on ("mousedown", this._mousedown);
                }
            }

            reset_point_listeners ()
            {
                if (this._mousedown)
                {
                    this._map.dragPan.enable ();
                    this._map.dragRotate.enable ();
                    this._map.off ("mousedown", this._mousedown);
                    delete this._mousedown;
                    this._cimmap.add_listeners ();
                }
            }

            digitize_point (obj, features, text, callback_success, callback_failure)
            {
                this._mrid = obj.mRID;

                // get the current GeoJSON
                var options =
                    {
                        show_internal_features: this._cimmap.show_internal_features ()
                    };
                var geo = this._cimmap.get_themer ().getTheme ().make_geojson (features, options);
                var points = geo.points;
                points.features.push
                (
                    {
                        type: "Feature",
                        geometry:
                        {
                            type: "Point",
                            coordinates: []
                        },
                        properties: obj
                    }
                );

                function cb_success (feature)
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_point_listeners ();
                    callback_success (feature);
                }
                function cb_failure ()
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_point_listeners ();
                    callback_failure ({canceled: true});
                }
                this._mousedown = this.digitize_point_mousedown_listener.bind (this, points, cb_success.bind (this), cb_failure.bind (this));

                this.set_point_listeners ();

                // pop up a prompt and wait
                this._popup = this.popup (text);
            }

            async digitize_point_wait (obj, features, text, callback_success, callback_failure)
            {
                var status = null;
                function cb_success (feature)
                {
                    status = "success";
                    callback_success (feature);
                }
                function cb_failure (error)
                {
                    status = "fail";
                    callback_failure (error);
                }
                function sleep (ms)
                {
                    return (new Promise (resolve => setTimeout (resolve, ms)));
                }
                this.digitize_point (obj, features, text, cb_success, cb_failure)
                do
                    await sleep (500);
                while (null == status);
            }

            point (obj, features, prompt)
            {
                var text = prompt || "<h1>Digitize point geometry</h1>";
                function abort ()
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_point_listeners ();
                }
                return (new CancelablePromise (new Promise (this.digitize_point_wait.bind (this, obj, features, text)), abort.bind (this)));
            }

            digitize_line_mousedown_listener (lines, callback_success, callback_failure, event)
            {
                event.originalEvent.preventDefault ();
                event.originalEvent.stopPropagation ();
                var feature = lines.features[lines.features.length - 1];
                var coordinates = feature.geometry.coordinates;
                var lnglat = this.snap (event);
                var buttons = event.originalEvent.buttons;
                var leftbutton = 0 != (buttons & 1);
                var rightbutton = 0 != (buttons & 2);

                if (leftbutton)
                {
                    coordinates.push ([lnglat.lng, lnglat.lat]);
                    if (coordinates.length > 2)
                        this._map.getSource ("edit lines").setData (lines);
                }
                else if (rightbutton)
                {
                    lines.features.length = lines.features.length - 1;
                    if (coordinates.length > 1)
                        callback_success (feature);
                    else
                        callback_failure ()
                }
            }

            digitize_line_mousemove_listener (lines, event)
            {
                event.originalEvent.preventDefault ();
                event.originalEvent.stopPropagation ();
                var lnglat = event.lngLat;
                var feature = lines.features[lines.features.length - 1];
                // ToDo: snap to point or end of line
                feature.transient = [lnglat.lng, lnglat.lat];
            }

            animate_line (lines, timestamp)
            {
                var feature = lines.features[lines.features.length - 1];
                if (null != feature.transient)
                {
                    var coordinates = feature.geometry.coordinates;
                    coordinates.push (feature.transient);
                    if (coordinates.length >= 2)
                        this._map.getSource ("edit lines").setData (lines);
                    coordinates.length = coordinates.length - 1;
                    feature.transient = null;
                }
                // trigger next animation
                this._animation = requestAnimationFrame (this._animate);
            }

            set_line_listeners ()
            {
                if (this._mousedown)
                {
                    // set up our listeners
                    this._cimmap.remove_listeners ();
                    this._map.dragPan.disable ();
                    this._map.dragRotate.disable ();
                    this._map.on ("mousedown", this._mousedown);
                    this._map.on ("mousemove", this._mousemove);
                    // start animation
                    this._animation = requestAnimationFrame (this._animate);
                }
            }

            reset_line_listeners ()
            {
                if (this._mousedown)
                {
                    cancelAnimationFrame (this._animation);
                    delete this._animation;
                    this._map.dragPan.enable ();
                    this._map.dragRotate.enable ();
                    this._map.off ("mousedown", this._mousedown);
                    delete this._mousedown;
                    this._map.off ("mousemove", this._mousemove);
                    delete this._mousemove;
                    this._cimmap.add_listeners ();
                }
            }

            digitize_line (obj, features, text, callback_success, callback_failure)
            {
                this._mrid = obj.mRID;

                // get the current GeoJSON
                var options =
                    {
                        show_internal_features: this._cimmap.show_internal_features ()
                    };
                var geo = this._cimmap.get_themer ().getTheme ().make_geojson (features, options);
                var lines = geo.lines;

                // add an empty line
                lines.features.push
                (
                    {
                        type: "Feature",
                        geometry:
                        {
                            type: "LineString",
                            coordinates: []
                        },
                        properties: obj,
                        transient: null
                    }
                );
                function cb_success (feature)
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_line_listeners ();
                    callback_success (feature);
                }
                function cb_failure ()
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_line_listeners ();
                    callback_failure ({canceled: true});
                }
                this._mousedown = this.digitize_line_mousedown_listener.bind (this, lines, cb_success.bind (this), cb_failure.bind (this));
                this._mousemove = this.digitize_line_mousemove_listener.bind (this, lines);
                this._animate = this.animate_line.bind (this, lines);

                this.set_line_listeners ();
                // pop up a prompt
                this._popup = this.popup (text);
            }

            async digitize_line_wait (obj, features, text, callback_success, callback_failure)
            {
                var status = null;
                function cb_success (feature)
                {
                    status = "success";
                    callback_success (feature);
                }
                function cb_failure (error)
                {
                    status = "fail";
                    callback_failure (error);
                }
                function sleep (ms)
                {
                    return (new Promise (resolve => setTimeout (resolve, ms)));
                }
                this.digitize_line (obj, features, text, cb_success, cb_failure)
                do
                    await sleep (500);
                while (null == status);
            }

            line (obj, features, prompt)
            {
                var text = prompt || "<h1>Digitize linear geometry<br>Right-click to finish</h1>";
                function abort ()
                {
                    if (this._popup)
                    {
                        this._popup.remove ();
                        delete this._popup;
                    }
                    this.reset_line_listeners ();
                }
                return (new CancelablePromise (new Promise (this.digitize_line_wait.bind (this, obj, features, text)), abort.bind (this)));
            }

        }

        return (Digitizer);
    }
)