/**
 * Short Circuit theme.
 */
"use strict";

define
(
    ["mustache", "./default_theme"],
    /**
     * @summary Theme on short circuit output.
     * @description Theme class to add popups to conducting equipment with short circuit results.
     * @exports shortcircuit_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme)
    {
        class ShortCircuitTheme extends DefaultTheme
        {
            constructor (shortcircuit)
            {
                super ();
                this._shortcircuit = shortcircuit;
                this._popup = new mapboxgl.Popup (
                    {
                        closeButton: false,
                        closeOnClick: false
                    }
                );
                this._items =
                    [
                        {
                            id: "fuseOK",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 255, 0);'>&nbsp;&nbsp;&nbsp;</span> Fuse OK",
                            checked: true,
                            color: "rgb(0, 255, 0)"
                        },
                        {
                            id: "fuseBAD",
                            description: "<span style='width: 15px; height: 15px; background: rgb(255, 0, 0);'>&nbsp;&nbsp;&nbsp;</span> Fuse NOT OK",
                            checked: true,
                            color: "rgb(255, 0, 0)"
                        },
                        {
                            id: "failure",
                            description: "<span style='width: 15px; height: 15px; background: rgb(143, 0, 255);'>&nbsp;&nbsp;&nbsp;</span> Failure",
                            checked: true,
                            color: "rgb(143, 0, 255)"
                        },
                        {
                            id: "unknown",
                            description: "<span style='width: 15px; height: 15px; background: rgb(128, 128, 128);'>&nbsp;&nbsp;&nbsp;</span> Status unknown",
                            checked: true,
                            color: "rgb(128, 128, 128)"
                        }
                    ];

            }

            getName ()
            {
                return ("ShortCircuitTheme");
            }

            getTitle ()
            {
                return ("Short Circuit results");
            }

            getDescription ()
            {
                return ("Popup added to ConductingEquipment with short-circuit and maximum starting current values.");
            }

            /**
             * Item list for the legend.
             */
            getItems ()
            {
                return (this._items);
            }

            /**
             * Add short circuit information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             */
            process_spatial_objects_again (data, options)
            {
                const equipment = data.ConductingEquipment;
                for (let i = 0; i < this._shortcircuit.length; i++)
                {
                    const anal = this._shortcircuit[i];
                    const id = anal["equipment"];
                    const object = equipment[id];
                    if (object)
                        object["shortcircuit"] = anal;
                }
                for (let id in equipment)
                    if (equipment.hasOwnProperty(id))
                    {
                        let color = "rgb(128, 128, 128)";
                        const anal = equipment[id].shortcircuit;
                        if ("undefined" != typeof (anal))
                        {
                            if (anal.errors && anal.errors.find (x => x.startsWith ("FATAL")))
                                color = "rgb(143, 0, 255)";
                            else
                                if (anal.fuses)
                                    color = anal.fuseok ? "rgb(0, 255, 0)" : "rgb(255, 0, 0)";
                        }
                        equipment[id].color = color;
                    }
            }

            dvalue (r)
            {
                let ret = "";

                const nr = Number (r);
                if (!isNaN (nr))
                    ret = nr.toPrecision (3);
                else
                    ret = r;

                return (ret);
            }

            mvalue (r)
            {
                let ret = "";

                const nr = Number (r);
                if (!isNaN (nr))
                    if (nr > 1e6)
                        ret = (nr / 1e6).toPrecision (5) + "M";
                    else
                        ret = nr.toPrecision (5);
                else
                    ret = r;

                return (ret);
            }

            impedance (r, x)
            {
                let ret = "";

                const nr = Number (r);
                const nx = Number (x);
                if (!isNaN (nr) && !isNaN (nx))
                    if ((nr < 1.0) && (nx < 1.0))
                        ret = (nr * 1000).toPrecision (3) + " + " + (nx * 1000).toPrecision (3) + "jm&#x2126;";
                    else
                        ret = nr.toPrecision (3) + " + " + nx.toPrecision (3) + "j&#x2126;";
                else
                    ret = r.toString () + " + " + x + "j&#x2126;";

                return (ret);
            }

            glyph (bool)
            {
                return (bool ? "<span style='color: #00ff00'>&#x2713;</span>" : "<span style='color: #ff0000'>&#x2717;</span>");
            }

            fuses (sc)
            {
                let ret = "";
                if (sc.fuses)
                    ret = "<div>Fuses = " + sc.fuses + " recommended: " + sc.fusemax + "A " + this.glyph (sc.fuseok) + "</div>"
                return (ret);
            }

            errors (sc)
            {
                let ret = "";
                if (sc.errors)
                    sc.errors.forEach (function (error) { ret = ret + "<div style='color: " + (error.startsWith ("FATAL") ? "red" : "orange") + ";'>" + error + "</div>"; });
                return (ret);
            }

            toHTML (cimobject)
            {
                let ret = "";

                // check for short circuit results
                let sc = cimobject.shortcircuit;
                if (sc)
                {
                    // not sure why, but the object is serialized as JSON when it comes from MapBox
                    if ("string" == typeof (sc))
                        sc = JSON.parse (sc);
                    ret =
                        "<strong>" + sc.equipment + " (" + sc.trafo + ")</strong>" +
                        "<p>" +
                        "<div>S<sub>sc</sub> = " + this.mvalue (sc.low_sk) + "VA</div>" +
                        "<div>Z<sub>11</sub> = " + this.impedance (sc.low_r, sc.low_x) + " Z<sub>00</sub> = " + this.impedance (sc.low_r0, sc.low_x0) + "</div>" +
                        "<div>I<sub>p</sub> = " + this.dvalue (sc.low_ip) + "A</div>" +
                        "<table class='analysis-table'>" +
                        "  <tr>" +
                        "    <th></th><th>3&#x0278; (A)</th><th>1&#x0278; (A)</th><th>2&#x0278;<sub>L-L</sub> (A)</th>" +
                        "  </tr>" +
                        "  <tr>" +
                        "    <th>I<sub>sc</sub></th><td>" + this.dvalue (sc.low_ik3pol) + "</td><td>" + this.dvalue (sc.low_ik) + "</td><td></td>" +
                        "  </tr>" +
                        "  <tr>" +
                        "    <th>I<sub>max</sub> @6%</th><td>" + this.dvalue (sc.imax_3ph_low) + "</td><td>" + this.dvalue (sc.imax_1ph_low) + "</td><td>" + this.dvalue (sc.imax_2ph_low) + "</td>" +
                        "  </tr>" +
                        "  <tr>" +
                        "    <th>I<sub>max</sub> @3%</th><td>" + this.dvalue (sc.imax_3ph_med) + "</td><td>" + this.dvalue (sc.imax_1ph_med) + "</td><td>" + this.dvalue (sc.imax_2ph_med) + "</td>" +
                        "  </tr>" +
                        "</table>" +
                        this.fuses (sc) +
                        this.errors (sc) +
                        "</p>";
                }

                return (ret);
            }

            popon (event)
            {
                // change the cursor style as a UI indicator
                this._map.getCanvas ().style.cursor = "pointer";
                const html = this.toHTML (event.features[0].properties);
                if ("" !== html)
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
             * @param {Object} cimmap - the CIM map object
             * @param {Object} options - object with rendering options, e.g.
             *   show_internal_features flag - render internal features
             */
            make_theme (cimmap, options)
            {
                super.make_theme (cimmap, options);
                this._map = cimmap.get_map ();
                this._popon = this.popon.bind (this);
                this._popoff = this.popoff.bind (this);
                this._map.on ("mouseenter", "symbol", this._popon);
                this._map.on ("mouseleave", "symbol", this._popoff);
                this._map.on ("mouseenter", "circle", this._popon);
                this._map.on ("mouseleave", "circle", this._popoff);
            }

            /**
             * Remove layers and sources from the map.
             */
            remove_theme ()
            {
                if (this._map)
                {
                    this._map.off ("mouseenter", "symbol", this._popon);
                    this._map.off ("mouseleave", "symbol", this._popoff);
                    this._map.off ("mouseenter", "circle", this._popon);
                    this._map.off ("mouseleave", "circle", this._popoff);
                }
                super.remove_theme ();
            }
        }

        return (ShortCircuitTheme);
    }
);