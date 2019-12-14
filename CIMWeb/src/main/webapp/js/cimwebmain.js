/**
 * Main javascript file for CIM Application
 */
"use strict";

requirejs.config({
    "paths": {
      "jquery": "lib/jquery.min",
      "moment": "lib/moment.min",
      "mustache": "lib/mustache.min"
    }
});

requirejs
(
    ["cimapp", "cimfiles", "cimmap", "cimdetails", "cimedit", "cimconnectivity", "cimdiagram", "cimchart", "cimingest", "cimanalysis", "cimquery", "cimsimulate",
     "themes/cimthemes", "themes/default_theme", "themes/icon_theme", "themes/voltage", "themes/island", "themes/inservice", "themes/diagram", "themes/project_theme", "themes/event_theme",
     "nav/cimnav", "nav/zoominnav", "nav/zoomoutnav", "nav/rotationnav", "nav/zoomnav", "nav/infonav", "nav/themenav", "nav/legendnav", "nav/editnav", "nav/connectivitynav", "nav/diagramnav", "nav/chartnav"],
    function (cimapp, cimfiles, cimmap, CIMDetails, CIMEdit, CIMConnectivity, CIMDiagram, CIMChart, cimingest, cimanalysis, cimquery, cimsimulate,
     ThemeControl, DefaultTheme, IconTheme, VoltageTheme, IslandTheme, InServiceTheme, DiagramTheme, ProjectTheme, EventTheme,
     NavigationControl, ZoomInNav, ZoomOutNav, RotationNav, ZoomNav, InfoNav, ThemeNav, LegendNav, EditNav, ConnectivityNav, DiagramNav, ChartNav)
    {
        /**
         * Get the hash portion of the url.
         * @param {String} url the full URL to get the hash from
         * @return {String} just the hash portion
         * @function get_hash
         */
        function get_hash (url)
        {
            var parser = document.createElement('a'); // see https://www.joezimjs.com/javascript/the-lazy-mans-url-parsing/
            parser.href = url;
            var ret = parser.hash;
            if (0 !== ret.length)
            {
                ret = ret.substring (1);
                if (!isNaN (Number (ret.charAt (0)))) // test for MapBox being greedy with their hash
                    ret = "map";
            }
            return (ret)
        }

        /**
         * Hide the given element.
         * Sets the display style to "none".
         * @param {String} id the element id
         * @function hide
         */
        function hide (id)
        {
            if ("" !== id)
            {
                var element = document.getElementById (id);
                if (null != element)
                    element.style.display = "none";
            }
        }

        /**
         * Show the given element.
         * Sets the display style to "block".
         * @param {String} id the element id
         * @function show
         */
        function show (id)
        {
            if ("" !== id)
            {
                var element = document.getElementById (id);
                if (null != element)
                    element.style.display = "block";
            }
        }

        // switch tabs on a URL hash change
        var initialized = {};
        window.onhashchange =
            function (event)
            {
                // make the correct tab visible
                var prev = get_hash (event.oldURL);
                var next = get_hash (event.newURL);
                if ("" === next)
                    next = "files"; // default tab
                hide (prev);
                show (next);

                // switch class active
                var nav = document.getElementById ("functions");
                var actives = nav.getElementsByClassName ("active");
                for (var i = 0; i < actives.length; i++)
                    actives.item (i).classList.remove ("active");
                var active = document.getElementById (next + "_link");
                if (active)
                    active.parentElement.classList.add ("active");

                // close the menu (for cell phones)
                nav.classList.remove ("in");

                // initialize the tab if necessary
                if (!initialized[next])
                {
                    switch (next)
                    {
                        case "files": cimfiles.initialize (event); break;
                        case "map":
                            cimmap.initialize (TheNavigator, TheThemer, TheEditor);
                            var theme = new ProjectTheme ();
                            cimmap.get_themer ().removeTheme (theme);
                            cimmap.get_themer ().addTheme (theme);
                            break;
                        case "ingest": cimingest.initialize (event); break;
                        case "analysis": cimanalysis.initialize (event); break;
                        case "simulate": cimsimulate.initialize (event); break;
                        case "query": cimquery.initialize (event); break;
                    }
                    initialized[next] = true;
                }
            };

        document.getElementById ("internal_features").onchange = cimmap.redraw;
        document.getElementById ("buildings_3d").onchange = cimmap.buildings_3d;
        document.getElementById ("scale_bar").onchange = cimmap.scale_bar;
        document.getElementById ("coordinate").onchange = cimmap.coordinates;
        document.getElementById ("trace").onclick = cimmap.trace;
        document.getElementById ("unhighlight").onclick = cimmap.unhighlight;
        document.getElementById ("search").onsubmit = cimmap.search;
        document.getElementById ("search_button").onclick = cimmap.search;
        document.getElementById ("save").onclick = cimapp.save;

        /**
         * The detail view control object.
         */
        const TheDetails = new CIMDetails (cimmap);

        /**
         * The editor control object.
         */
        const TheEditor = new CIMEdit (cimmap);

        /**
         * The connectivity control object.
         */
        const TheConnectivity = new CIMConnectivity (cimmap, TheEditor);

        /**
         * The diagram control object.
         */
        const TheDiagram = new CIMDiagram (cimmap);

        /**
         * The chart control object.
         */
        const TheChart = new CIMChart (cimmap);

        /**
         * The theme setting control object.
         */
        const TheThemer = new ThemeControl ();
        TheThemer.addTheme (new DefaultTheme ());
        TheThemer.addTheme (new IconTheme ());
        TheThemer.addTheme (new VoltageTheme ());
        TheThemer.addTheme (new IslandTheme ());
        TheThemer.addTheme (new InServiceTheme ());
        TheThemer.addTheme (new DiagramTheme ());
        TheThemer.addTheme (new EventTheme ());

        function toggle (control_or_function)
        {
            return (
                function (event)
                {
                    const control = ("function" == typeof (control_or_function)) ? control_or_function () : control_or_function;
                    if (control.visible ())
                        cimmap.get_map ().removeControl (control);
                    else
                    {
                        cimmap.get_map ().addControl (control);
                        control.initialize ();
                    }
                }
            );
        }

        const zoom = document.createElement ("button", { is: "zoom-nav-button" });
        const info = document.createElement ("button", { is: "info-nav-button" });
        const theme = document.createElement ("button", { is: "theme-nav-button" });
        const legend = document.createElement ("button", { is: "legend-nav-button" });
        const edit = document.createElement ("button", { is: "edit-nav-button" });
        const connectivity = document.createElement ("button", { is: "connectivity-nav-button" });
        const diagram = document.createElement ("button", { is: "diagram-nav-button" });
        const chart = document.createElement ("button", { is: "chart-nav-button" });

        const TheNavigator =  new NavigationControl ();
        TheNavigator.addButton (document.createElement ("button", { is: "zoomin-nav-button" }));
        TheNavigator.addButton (document.createElement ("button", { is: "zoomout-nav-button" }));
        TheNavigator.addButton (document.createElement ("button", { is: "rotation-nav-button" }));
        TheNavigator.addButton (zoom);
        TheNavigator.addButton (info);
        TheNavigator.addButton (theme);
        TheNavigator.addButton (legend);
        TheNavigator.addButton (edit);
        TheNavigator.addButton (connectivity);
        TheNavigator.addButton (diagram);
        TheNavigator.addButton (chart);

        // set URLs in the external links
        cimapp.initialize ();

        zoom.addEventListener ("click", cimmap.zoom_extents);
        info.addEventListener ("click", toggle (TheDetails));
        theme.addEventListener ("click", toggle (TheThemer));
        legend.addEventListener ("click", toggle (function () { return (TheThemer.getTheme ().getLegend ()); }));
        edit.addEventListener ("click", toggle (TheEditor));
        connectivity.addEventListener ("click", toggle (TheConnectivity));
        diagram.addEventListener ("click", toggle (TheDiagram));
        chart.addEventListener ("click", toggle (TheChart));

        // manually trigger a hashchange to start the app.
        window.location.hash = "files";
    }
);
