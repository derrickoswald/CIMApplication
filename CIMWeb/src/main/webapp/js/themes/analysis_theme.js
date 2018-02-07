/**
 * Analysis theme.
 */
"use strict";

define
(
    ["../mustache", "./default_theme"],
    /**
     * @summary Theme on analysis output.
     * @description Theme class to add popups to conducting equipment with analysis results.
     * @name analysis_theme
     * @exports analysis_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme)
    {
        class AnalysisTheme extends DefaultTheme
        {
            constructor (analysis)
            {
                super ();
                this._analysis = analysis;
                this._popup = new mapboxgl.Popup (
                    {
                        closeButton: false,
                        closeOnClick: false
                    }
                );
            }

            getName ()
            {
                return ("AnalysisTheme");
            }

            getTitle ()
            {
                return ("Analysis results");
            }

            getDescription ()
            {
                return ("Popup added to ConductingEquipment with short-circuit and maximum starting current values.");
            }

            /**
             * Add analysis information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @function process_spatial_objects_again
             * @memberOf module:analysis_theme
             */
            process_spatial_objects_again (data)
            {
                var equipment = data.ConductingEquipment;
                for (var i = 0; i < this._analysis.length; i++)
                {
                    var anal = this._analysis[i];
                    var id = anal["equipment"];
                    var object = equipment[id];
                    if (object)
                        object["analysis"] = anal;
                }
            }

            dvalue (r)
            {
                var ret = "";

                var nr = Number (r);
                if (!isNaN (nr))
                    ret = nr.toPrecision (3);
                else
                    ret = r;

                return (ret);
            }

            mvalue (r)
            {
                var ret = "";

                var nr = Number (r);
                if (!isNaN (nr))
                    if (nr > 1e6)
                        ret = (nr / 1e6).toPrecision (5) + "M";
                    else
                        nr.toPrecision (5);
                else
                    ret = r;

                return (ret);
            }

            impedance (r, x)
            {
                var ret = "";

                var nr = Number (r);
                var nx = Number (x);
                if (!isNaN (nr) && !isNaN (nx))
                    if ((nr < 0.1) && (nx < 0.1))
                        ret = (nr / 1000).toPrecision (3) + " + " + (nx / 1000).toPrecision (3) + "jm&#x2126;";
                    else
                        ret = nr.toPrecision (3) + " + " + nx.toPrecision (3) + "j&#x2126;";
                else
                    ret = r.toString () + " + " + x + "j&#x2126;";

                return (ret);
            }

            contents (cimobject)
            {
                var ret = "";

                // check for analysis results
                var analysis = cimobject.analysis;
                if (analysis)
                {
                    // not sure why, but the object is serialized as JSON
                    analysis = JSON.parse (analysis)
                    ret =
                    "<strong>" + analysis.equipment + " (" + analysis.tx + ")</strong>" +
                    "<p>" +
                    "<div>S<sub>k</sub> = " + this.dvalue (analysis.sk) + "VA</div>" +
                    "<div>Z<sub>11</sub> = " + this.impedance (analysis.r, analysis.x) + " Z<sub>00</sub> = " + this.impedance (analysis.r0, analysis.x0) + "</div>" +
                    "<div>I<sub>p</sub> = " + this.dvalue (analysis.ip) + "A</div>" +
                    "<table class='analysis-table'>" +
                    "  <tr>" +
                    "    <th></th><th>3&#x0278; (A)</th><th>1&#x0278; (A)</th><th>2&#x0278;<sub>L-L</sub> (A)</th>" +
                    "  </tr>" +
                    "  <tr>" +
                    "    <th>I<sub>sc</sub></th><td>" + this.dvalue (analysis.ik3pol) + "</td><td>" + this.dvalue (analysis.ik) + "</td><td></td>" +
                    "  </tr>" +
                    "  <tr>" +
                    "    <th>I<sub>max</sub> @6%</th><td>" + this.dvalue (analysis.motor_3ph_max_low / 400.0) + "</td><td>" + this.dvalue (analysis.motor_1ph_max_low / 400.0) + "</td><td>" + this.dvalue (analysis.motor_l_l_max_low) + "</td>" +
                    "  </tr>" +
                    "  <tr>" +
                    "    <th>I<sub>max</sub> @3%</th><td>" + this.dvalue (analysis.motor_3ph_max_med / 400.0) + "</td><td>" + this.dvalue (analysis.motor_1ph_max_med / 400.0) + "</td><td>" + this.dvalue (analysis.motor_l_l_max_med) + "</td>" +
                    "  </tr>" +
                    "</table>" +
                    "</p>";
                }

                return (ret);
            }

            popon (event)
            {
                // change the cursor style as a UI indicator
                this._map.getCanvas ().style.cursor = "pointer";
                var html = this.contents (event.features[0].properties)
                if ("" != html)
                {
                    // set the popup coordinates
                    this._popup.setLngLat (event.features[0].geometry.coordinates);
                    // set the popup contents
                    this._popup.setHTML (html);
                    // add the popup to the map
                    this._popup.addTo (this._map);
                }
            }

            popoff ()
            {
                // change the cursor style back
                this._map.getCanvas ().style.cursor = "";
                // remove the popup
                // if (this._popup.isOpen ())
                    this._popup.remove ();
            }

            /**
             * Add sources and layers to the map.
             * @param {Object} map - the Mapbox map object
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             * @function make_theme
             * @memberOf module:analysis_theme
             */
            make_theme (map, data, options)
            {
                super.make_theme (map, data, options);
                this._map = map;
                this._popon = this.popon.bind (this);
                this._popoff = this.popoff.bind (this);
                this._map.on ("mouseenter", "symbol", this._popon);
                this._map.on ("mouseleave", "symbol", this._popoff);
            }

            /**
             * Remove layers and sources from the map.
             * @function remove_theme
             * @memberOf module:analysis_theme
             */
            remove_theme ()
            {
                if (this._map)
                {
                    this._map.off ("mouseenter", "symbol", this._popon);
                    this._map.off ("mouseleave", "symbol", this._popoff);
                }
                super.remove_theme ();
            }
        }

        return (AnalysisTheme);
    }
)