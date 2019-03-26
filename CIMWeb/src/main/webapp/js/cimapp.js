/**
 * Functions for CIM Application
 */
"use strict";
define
(
    ["util", "cimmap", "cim", "cimfiles"],
    /**
     * @summary Application utilities.
     * @description Performs application checks.
     * @name cimapp
     * @exports cimapp
     * @version 1.0
     */
    function (util, cimmap, cim, cimfiles)
    {
        // the base name of the currently loaded file
        var TheCurrentName = null;

        // the rdf:about text for saving
        var TheCurrentAbout = null;

        // the md:description text for saving
        var TheCurrentDescription = null;

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

        function root_name (filename)
        {
            var ret;
            if (filename.startsWith ("hdfs://"))
            {
                var s = filename.substring ("hdfs://".length);
                ret = s.substring (s.indexOf ("/"));
                if (ret.endsWith (".rdf"))
                    ret = ret.substring (0, ret.length - 4);
            }
            else
                ret = filename;
            return (ret);
        }

        /**
         * @summary Event handler for Save.
         * @description Attached to the Save menu item, performs the CIM export and zipping.
         * @param {object} event - the click event - <em>not used</em>
         * @function save
         * @memberOf module:cimapp
         */
        function save (event)
        {
            event.preventDefault ();
            event.stopPropagation ();
            $("#save_modal").modal ("hide");

            TheCurrentName = document.getElementById ("save_name").value;
            TheCurrentAbout = document.getElementById ("rdf_about").value;
            TheCurrentDescription = document.getElementById ("md_description").value;
            var loaded = cimmap.get_loaded ();
            if (!TheCurrentName && loaded)
                TheCurrentName = root_name (loaded.files[0]);
            var name = TheCurrentName || "save";
            var full_model = document.getElementById ("full_model").checked;
            var difference_model = document.getElementById ("difference_model").checked;
            var only_new = document.getElementById ("only_new").checked;
            var suffix = "";
            if (difference_model)
                suffix = "_diff";
            else if (only_new)
                suffix = "_new";
            var name = TheCurrentName + suffix;
            var about = TheCurrentAbout;
            var description = TheCurrentDescription;

            var pending =
                (null == cimmap.get_data ()) ? Promise.resolve ("no data") :
                    new Promise (
                        function (resolve, reject)
                        {
                            var file = name + (difference_model ? "_diff" : "") + ".zip"
                            var begin = new Date ().getTime ();
                            console.log ("starting xml creation");
                            var text = cim.write_xml (cimmap.get_data ().Element, difference_model, only_new, about, description);
                            var start = new Date ().getTime ();
                            console.log ("finished xml creation (" + (Math.round (start - begin) / 1000) + " seconds)");
                            console.log ("starting zip");
                            require (
                                ["zip/zip", "zip/mime-types"],
                                function (zip, mimeTypes)
                                {
                                    //zip.workerScriptsPath = "js/zip/";
                                    zip.useWebWorkers = false;
                                    zip.createWriter (new zip.BlobWriter (),
                                        function (writer)
                                        {
                                            writer.add (name + ".rdf", new zip.TextReader (text),
                                                function ()
                                                {
                                                    writer.close (
                                                        function (blob) // blob contains the zip file as a Blob object
                                                        {
                                                            var end = new Date ().getTime ();
                                                            console.log ("finished zip (" + (Math.round (end - start) / 1000) + " seconds)");
                                                            console.log ("starting upload");
                                                            cimfiles.put (name + ".zip;unzip=true", blob).then (
                                                                function (json)
                                                                {
                                                                    console.log (JSON.stringify (json, null, 4));
                                                                    console.log ("finished upload (" + (Math.round (new Date ().getTime () - end) / 1000) + " seconds)");
                                                                    window.location.href = window.location.pathname + "#files";
                                                                    cimfiles.initialize ();
                                                                }
                                                            );
                                                        }
                                                    );
                                                }
                                            );
                                        },
                                        function (error)
                                        {
                                           console.log (error);
                                           reject (error);
                                        }
                                    );
                                }
                            );
                        }
                    );
            return (pending);
        }

        return (
            {
                ping: ping,
                pong: pong,
                initialize: initialize,
                save: save
            }
        );
    }
);
