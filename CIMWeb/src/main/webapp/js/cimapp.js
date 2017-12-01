/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["util"],
    /**
     * @summary Application utilities.
     * @description Performs application checks.
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (util)
    {
        /**
         * @summary Connect to the server to see if it's alive.
         * @description Invoke the server-side ping function.
         * @param {function} fn Optional function to handle the received data, signature fn (result)
         * @function ping
         * @memberOf module:cimapp
         */
        function ping (fn)
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
                        if (resp.status == "OK")
                            if (fn)
                                fn (resp.result)
                            else
                                alert (resp.result);
                        else
                            alert (resp);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Connect to the server to see if it's alive and connected to Spark.
         * @description Invoke the server-side pong function.
         * @param {function} fn Optional function to handle the received data, signature fn (result)
         * @function pong
         * @memberOf module:cimapp
         */
        function pong (fn)
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
                        if (resp.status == "OK")
                            if (fn)
                                fn (resp.result)
                            else
                                alert (resp.result);
                        else
                            alert (resp);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Fix up the Spark and Hadoop URLs.
         * @description Gets the server status and updates the URL's accordingly.
         * @function initialize
         * @memberOf module:cimapp
         */
        function initialize ()
        {
            pong (
                function (result)
                {
                    if (result.properties["SparkConnectionFactory.ServerName"] != "localhost")
                    {
                        var namenode = result.environment.NAMENODE;
                        var ui = result.spark_instance.spark_application_ui_url;
                        document.getElementById ("spark_master").setAttribute ("href", "http://" + namenode + ":8080")
                        document.getElementById ("spark_job").setAttribute ("href", ui)
                        document.getElementById ("hadoop_hdfs").setAttribute ("href", "http://" + namenode + ":50070")
                    }
                    else
                        document.getElementById ("spark_master").style.display = "none";
                }
            );
        }

        return (
            {
                ping: ping,
                pong: pong,
                initialize: initialize
            }
        );
    }
);
