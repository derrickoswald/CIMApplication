/**
 * Main javascript file for CIM Application
 */
"use strict";
requirejs
(
    ["cimapp", "cimfiles", "cimmap", "cimanalysis", "cimquery", "cimsimulate", "themes/project_theme"],
    function (cimapp, cimfiles, cimmap, cimanalysis, cimquery, cimsimulate, ProjectTheme)
    {
        /**
         * Get the hash portion of the url.
         * @param {String} url the full URL to get the hash from
         * @return {String} just the hash portion
         * @function get_hash
         * @memberOf module:main
         */
        function get_hash (url)
        {
            var parser = document.createElement('a'); // see https://www.joezimjs.com/javascript/the-lazy-mans-url-parsing/
            parser.href = url;
            var ret = parser.hash;
            if (0 != ret.length)
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
         * @memberOf module:main
         */
        function hide (id)
        {
            if ("" != id)
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
         * @memberOf module:main
         */
        function show (id)
        {
            if ("" != id)
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
                if ("" == next)
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
                            cimmap.initialize (event);
                            var theme = new ProjectTheme ();
                            cimmap.get_themer ().removeTheme (theme);
                            cimmap.get_themer ().addTheme (theme);
                            break;
                        case "analysis": cimanalysis.initialize (event); break;
                        case "simulate": cimsimulate.initialize (event); break;
                        case "query": cimquery.initialize (event); break;
                    }
                    initialized[next] = true;
                }
            }

        document.getElementById ("internal_features").onchange = cimmap.redraw;
        document.getElementById ("buildings_3d").onchange = cimmap.buildings_3d;
        document.getElementById ("scale_bar").onchange = cimmap.scale_bar;
        document.getElementById ("coordinate").onchange = cimmap.coordinates;
        document.getElementById ("trace").onclick = cimmap.trace;
        document.getElementById ("unhighlight").onclick = cimmap.unhighlight;
        document.getElementById ("search").onsubmit = cimmap.search;
        document.getElementById ("search_button").onclick = cimmap.search;
        document.getElementById ("save").onclick = cimapp.save;

        // set URLs in the external links
        cimapp.initialize ();

        // manually trigger a hashchange to start the app.
        window.location.hash = "files";
    }
);
