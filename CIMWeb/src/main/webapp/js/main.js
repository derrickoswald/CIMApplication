/**
 * Main javascript file for CIM Application
 */
"use strict";
requirejs
(
    ["cimapp", "cimfiles", "cimmap", "cimquery", "cimexport", "cimsimulate"],
    function (cimapp, cimfiles, cimmap, cimquery, cimexport, cimsimulate)
    {
        // initialize material design for bootstrap (https://github.com/FezVrasta/bootstrap-material-design)
        $.material.init ();

        /**
         * Page activation function generator.
         */
        function activate (fn)
        {
            return (
                /**
                 * Click event handler.
                 * @param {object} event - the click event
                 */
                function (event)
                {
                    var nav;
                    var parent;
                    var active;

                    // stop the normal link action
                    event.preventDefault ();
                    event.stopPropagation ();
                    // switch class active
                    nav = document.getElementById ("functions");
                    active = nav.getElementsByClassName ("active")[0];
                    if (active)
                        active.classList.remove ("active");
                    parent = event.target.parentElement;
                    parent.classList.add ("active");
                    // close the menu (for cell phones)
                    nav.classList.remove ("in");
                    // initialize the new nav
                    fn ();
                }
            );
        }

        document.getElementById ("files").onclick = activate (cimfiles.initialize);
        document.getElementById ("map").onclick = activate (cimmap.initialize);
        document.getElementById ("simulate").onclick = activate (cimsimulate.initialize);
        document.getElementById ("query").onclick = activate (cimquery.initialize);
        document.getElementById ("export").onclick = activate (cimexport.initialize);

        document.getElementById ("show_internal_features").onchange = cimmap.redraw;
        document.getElementById ("buildings_3d").onchange = cimmap.buildings_3d;
        document.getElementById ("trace").onclick = cimmap.trace;
        document.getElementById ("unhighlight").onclick = cimmap.unhighlight;
        document.getElementById ("search").onsubmit = cimmap.search;

        cimfiles.initialize (); // populate the file list
        // cimmap.initialize ();
    }
);
