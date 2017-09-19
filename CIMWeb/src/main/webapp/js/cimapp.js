/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["es6-promise", "cim", "cimmap"],
    /**
     * @summary Main entry point for the application.
     * @description Performs application initialization as the first step in the RequireJS load sequence.
     * @see http://requirejs.org/docs/api.html#data-main
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (es6_promise, cim, cimmap)
    {
        // using Promise: backwards compatibility for older browsers
        es6_promise.polyfill ();

        /**
         * Make a select option list of the files.
         * @param response The response with the array of files on HDFS, each object has a name and details.
         */
        function make_file_list (response)
        {
            // {
            //    filesystem: "hdfs://server:8020",
            //    root: "hdfs://server:8020/",
            //    files:
            //    [
            //      {
            //        "path":"KS_Leistungen.csv",
            //        "length":403242,
            //        "modification_time":1479825255343,
            //        "access_time":1479825255110
            //      },
            //      {
            //        "path":"NIS_CIM_Export_sias_current_20160816_Kiental_V9.rdf",
            //        "length":14432564,
            //        "modification_time":1479825253185,
            //        "access_time":1479825252379
            //      }
            //    ]
            // }
            var options = ""
            var root = response.root;
            response.files.forEach (function (s) { if (!s.is_directory) options += "<option value='" + s.path + "'>" + s.path + " " + s.size + "</option>\n" } );
            document.getElementById ("cim_file").innerHTML = options;
            document.getElementById ("cim_file2").innerHTML = options;
        }

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
         * @summary Browser independent CORS setup.
         * @description Creates the CORS request and opens it.
         * @param {string} method The method type, e.g. "GET" or "POST"
         * @param {string} url the URL to open the request on
         * @param {boolean} synchronous optional parameter for open() call, default <em>true</em>
         * @returns {object} the request object or <code>null</code> if CORS isn't supported
         * @memberOf module:instancetype
         */
        function createCORSRequest (method, url, synchronous)
        {
            var ret;

            if ("undefined" == typeof (synchronous))
                synchronous = true;
            ret = new XMLHttpRequest ();
            if ("withCredentials" in ret) // "withCredentials" only exists on XMLHTTPRequest2 objects
            {
                ret.open (method, url, synchronous);
                ret.withCredentials = true;
            }
            else if (typeof XDomainRequest != "undefined") // IE
            {
                ret = new XDomainRequest ();
                ret.open (method, url);
            }
            else
                ret = null; // CORS is not supported by the browser

            return (ret);
        }

        function running_local ()
        {
            return (
                ("null" == window.location.origin) // Firefox
             || ("file://" == window.location.origin) // chromium
                )
        }

        /**
         * @summary Connect to the server and read the list of files.
         * @description Invoke the server-side function to list files.
         * @param {object} event - optional, the click event
         * @function do_connect
         * @memberOf module:cimapp
         */
        function do_connect (event)
        {
            var url;
            var xmlhttp;

            if (running_local ())
                url = "http://localhost:9080/cimweb/cim/list/"
            else
                url = window.location.origin + window.location.pathname + "cim/list/";
            xmlhttp = createCORSRequest ("GET", url, false);
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
                        else
                            make_file_list (resp.result);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Put a file on HDFS.
         * @description Store a user selected file.
         * @param {object} event - optional, the click event
         * @function do_put
         * @memberOf module:cimapp
         */
        function do_put (event)
        {
            var url;
            var xmlhttp;

            if (running_local ())
                url = "http://localhost:9080/cimweb/cim/put/"
            else
                url = window.location.origin + window.location.pathname + "cim/put/";
            var file = document.getElementById ("file");
            if (file.value != "")
            {
                url = url + file.value.replace ("C:\\fakepath\\", "");
                var data = file.files[0];
                var reader = new FileReader ();
                reader.onload = function ()
                {
                    xmlhttp = createCORSRequest ("PUT", url, false);
                    xmlhttp.onreadystatechange = function ()
                    {
                        var resp;
                        var msg;
                        var reason;

                        if (4 == xmlhttp.readyState)
                            if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                            {
                                resp = JSON.parse (xmlhttp.responseText);
                                if (resp.status == "OK")
                                    alert (JSON.stringify (resp.result, null, 4));
                                else
                                   alert (resp.error);
                            }
                            else
                                alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                    };
                    xmlhttp.send (reader.result);
                };
                reader.readAsArrayBuffer (data)
            }
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
                if (running_local ())
                    url = "http://localhost:9080/cimweb/cim/get/"
                else
                    url = window.location.origin + window.location.pathname + "cim/get/";
                url += file;
                xmlhttp = createCORSRequest ("GET", url, false);
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
//                        else
//                            do_connect ();
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        return (
            {
                do_connect: do_connect,
                do_put: do_put,
                connect: connect,
                gridlab: gridlab,
            }
        );
    }
);
