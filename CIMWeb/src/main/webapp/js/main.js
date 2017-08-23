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

        // populate the file list
        cimapp.do_connect ();
        cimapp.init_map ();
    }
);
