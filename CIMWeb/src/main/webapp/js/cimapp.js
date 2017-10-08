/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["es6-promise", "util", "cim", "cimmap"],
    /**
     * @summary Main entry point for the application.
     * @description Performs application initialization as the first step in the RequireJS load sequence.
     * @see http://requirejs.org/docs/api.html#data-main
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (es6_promise, util, cim, cimmap)
    {
        // using Promise: backwards compatibility for older browsers
        es6_promise.polyfill ();

        /**
         * Make a select option list of the transformers.
         * @param transformers The list transformers as an array of strings.
         */
        function make_list (transformers)
        {
            var select = document.getElementById ("transformer");
            var options = ""
            transformers.forEach (function (s) { options += "<option value='" + s + "'>" + s + "</option>\n" } );
            select.innerHTML = options + "<option value='all'>All</option>";
        }

        /**
         * @summary Fetch some data.
         * @description Invoke the server-side function to get some data.
         * @param {object} event - optional, the click event
         * @function do_short_circuit
         * @memberOf module:cimapp
         */
        function do_short_circuit (event)
        {
            var file;
            var transformer;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file").value;
            transformer = document.getElementById ("transformer").value;
            url = window.location.origin + window.location.pathname + "cim/ShortCircuitCalculation/";
            url = url + encodeURIComponent (file);
            if ("" != transformer)
                url = url + "/" + transformer;
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "application/json");
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (Array.isArray (resp))
                            make_list (resp);
                        else
                            make_map (resp);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Fetch some data.
         * @description Invoke the server-side function to get some data.
         * @param {object} event - optional, the click event
         * @function connect
         * @memberOf module:cimapp
         */
        function connect (event)
        {
            var file;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file").value;
            if ("" != file)
            {
                url = util.home () + "cim/file/" + file;
                xmlhttp = createCORSRequest ("GET", url);
                xmlhttp.onreadystatechange = function ()
                {
                    var resp;
                    var msg;
                    var reason;

                    if (4 == xmlhttp.readyState)
                        if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        {
                            var start = new Date ().getTime ();
                            console.log ("starting XML read");
                            var result = cim.read_full_xml (xmlhttp.responseText, 0, null, null)
                            var end = new Date ().getTime ();
                            console.log ("finished XML read (" + (Math.round (end - start) / 1000) + " seconds)");

                            // display the results on the map
                            cimmap.set_data (result.parsed);
                        }
                        else
                            alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                };
                xmlhttp.send ();
            }
        }

        /**
         * @summary Export a GridLAB-D file.
         * @description Invoke the server-side function to export some data.
         * @param {object} event - optional, the click event
         * @function gridlab
         * @memberOf module:cimapp
         */
        function gridlab (event)
        {
            var file;
            var transformer;
            var url;
            var xmlhttp;

            file = document.getElementById ("cim_file2").value;
            transformer = document.getElementById ("trafokreise").value;
            url = window.location.origin + window.location.pathname + "cim/GridLabExport/"
            if ("" != file)
                url = url + encodeURIComponent (file);
            else
                url = url + encodeURIComponent ("NIS_CIM_Export_sias_current_20160816_V8_Bruegg")
            if ("" != transformer)
                url = url + "/" + transformer;
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "text/plain");
            xmlhttp.onreadystatechange = function ()
            {
                var resp;
                var msg;
                var reason;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        alert (xmlhttp.responseText);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

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

            url = window.location.origin + window.location.pathname + "cim/ping";
            xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", url, true);
            xmlhttp.setRequestHeader ("Accept", "application/json");
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
                        {
                            alert (resp.message);
                            document.getElementById ("functions").style.display = "none";
                            document.getElementById ("search").style.display = "none";
                        }
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        return (
            {
                connect: connect,
                gridlab: gridlab,
            }
        );
    }
);
