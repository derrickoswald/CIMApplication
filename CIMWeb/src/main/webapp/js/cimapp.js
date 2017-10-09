/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["es6-promise", "util"],
    /**
     * @summary Application utilities.
     * @description Performs application checks.
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (es6_promise, util)
    {
        // using Promise: backwards compatibility for older browsers
        es6_promise.polyfill ();

        /**
         * @summary Connect to the server to see if it's alive.
         * @description Invoke the server-side ping function.
         * @function ping
         * @memberOf module:cimapp
         */
        function ping ()
        {
            var url;
            var xmlhttp;

            url = util.home () + "cim/ping;debug=true";
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status != "OK")
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Connect to the server to see if it's alive and connected to Spark.
         * @description Invoke the server-side pong function.
         * @function pong
         * @memberOf module:cimapp
         */
        function pong ()
        {
            var url;
            var xmlhttp;

            url = util.home () + "cim/pong;debug=true";
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status != "OK")
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        return (
            {
                ping: ping,
                pong: pong
            }
        );
    }
);
