/**
 * Main javascript file for CIM Application
 */
"use strict";
requirejs
(
    ["cimapp"],
    function (cimapp)
    {
        // initialize material design for bootstrap (https://github.com/FezVrasta/bootstrap-material-design)
        $.material.init ();

        // initialize button
        document.getElementById ("connect").onclick = cimapp.connect;
        document.getElementById ("export").onclick = cimapp.gridlab;
        document.getElementById ("unhighlight").onclick = cimapp.unhighlight;
        document.getElementById ("do_put").onclick = cimapp.do_put;

        cimapp.do_connect (); // populate the file list
        cimapp.init_map ();
    }
);
