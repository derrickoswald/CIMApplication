/**
 * MaximumFeedIn theme.
 */
"use strict";

define
(
    ["mustache", "./default_theme"],
    /**
     * @summary Theme on maximum feed-in output.
     * @description Theme class to add popups to conducting equipment with maximum feed-in results.
     * @exports maximumfeedin_theme
     * @version 1.0
     */
    function (mustache, DefaultTheme)
    {
        class MaximumFeedInTheme extends DefaultTheme
        {
            constructor ()
            {
                super ();
                this._popup = new mapboxgl.Popup (
                    {
                        closeButton: false,
                        closeOnClick: false
                    }
                );
                this._items =
                    [
                        {
                            id: "FeedInOK",
                            description: "<span style='width: 15px; height: 15px; background: rgb(0, 255, 0);'>&nbsp;&nbsp;&nbsp;</span> Feed-In OK",
                            checked: true,
                            color: "rgb(0, 255, 0)"
                        },
                        {
                            id: "FeedInBad",
                            description: "<span style='width: 15px; height: 15px; background: rgb(255, 0, 0);'>&nbsp;&nbsp;&nbsp;</span> Feed-In NOT OK",
                            checked: true,
                            color: "rgb(255, 0, 0)"
                        },
                        {
                            id: "unknown",
                            description: "<span style='width: 15px; height: 15px; background: rgb(128, 128, 128);'>&nbsp;&nbsp;&nbsp;</span> Status unknown",
                            checked: true,
                            color: "rgb(128, 128, 128)"
                        }
                    ];

            }

            setAnalysis (maximumfeedin)
            {
                this._maximumfeedin = maximumfeedin;
            }

            getName ()
            {
                return ("MaximumFeedInTheme");
            }

            getTitle ()
            {
                return ("Maximum Feed-In results");
            }

            getDescription ()
            {
                return ("Popup added to ConductingEquipment with maximum feed-in values.");
            }

            /**
             * Item list for the legend.
             */
            getItems ()
            {
                return (this._items);
            }

            /**
             * Add maximum feed-in information.
             * @param {Object} data - the hash table object of CIM classes by class name
             * @param {Object} options - options for processing
             */
            process_spatial_objects_again (data, options)
            {
                const equipment = data.ConductingEquipment;
                for (let i = 0; i < this._maximumfeedin.length; i++)
                {
                    const anal = this._maximumfeedin[i];
                    const id = anal["house"];
                    const object = equipment[id];
                    if (object)
                        object["maximumfeedin"] = anal;
                }
                for (let id in equipment)
                    if (equipment.hasOwnProperty(id))
                    {
                        let color = "rgb(128, 128, 128)";
                        const anal = equipment[id].maximumfeedin;
                        if ("undefined" != typeof (anal))
                        {
                            color = anal.maximum >= 50000 ? "rgb(0, 255, 0)" : "rgb(255, 0, 0)";
                        }
                        equipment[id].color = color;
                    }
            }

            glyph (bool)
            {
                return (bool ? "<span style='color: #00ff00'>&#x2713;</span>" : "<span style='color: #ff0000'>&#x2717;</span>");
            }

            toHTML (cimobject)
            {
                let ret = "";

                // check for maximum feed-in results
                let mfi = cimobject.maximumfeedin;
                if (mfi)
                {
                    // not sure why, but the object is serialized as JSON when it comes from MapBox
                    if ("string" == typeof (mfi))
                        mfi = JSON.parse (mfi);
                    ret =
                        "<strong>" + mfi.house + " (" + mfi.trafo + ((mfi.feeder) ? ":" + mfi.feeder : "") + ")</strong>" +
                        "<p>" +
                        "<div>Maximum = " + mfi.maximum + "W</div>" +
                        "<div>Reason = " + mfi.reason + "</div>" +
                        "<div>Details = " + mfi.details + "</div>" +
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

        return (MaximumFeedInTheme);
    }
);