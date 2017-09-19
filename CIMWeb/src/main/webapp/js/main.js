/**
 * Main javascript file for CIM Application
 */
"use strict";
requirejs
(
    ["cimapp", "cimmap"],
    function (cimapp, cimmap)
    {
        // initialize material design for bootstrap (https://github.com/FezVrasta/bootstrap-material-design)
        $.material.init ();

        // initialize button
        document.getElementById ("connect").onclick = cimapp.connect;
        document.getElementById ("export").onclick = cimapp.gridlab;
        //document.getElementById ("do_put").onclick = cimapp.do_put;

        document.getElementById ("show_internal_features").onchange = cimmap.redraw;
        document.getElementById ("trace").onclick = cimmap.trace;
        document.getElementById ("unhighlight").onclick = cimmap.unhighlight;
        document.getElementById ("search").onsubmit = cimmap.search;

        cimapp.do_connect (); // populate the file list
        cimmap.init_map ();
    }
);
