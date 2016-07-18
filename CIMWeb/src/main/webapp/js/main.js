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
        document.getElementById ("unhighlight").onclick = cimapp.unhighlight;

        cimapp.init_map ();
    }
);
