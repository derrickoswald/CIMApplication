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
     * @exports cimapp
     * @version 1.0
     */
    function (util, cimmap, cim, cimfiles)
    {
        // the base name of the currently loaded file
        let TheCurrentName = null;

        // the rdf:about text for saving
        let TheCurrentAbout = null;

        // the md:description text for saving
        let TheCurrentDescription = null;

        /**
         * @summary Connect to the server to see if it's alive.
         * @description Invoke the server-side ping function.
         * @param debug add the 'debug' matrix parameter to request if <code>true</code>
         * @return a promise that resolves to the the received data, signature fn (result), or rejects with the failed result or the error message
         */
        function ping (debug)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        const url = util.home () + "cim/ping" + ((debug) ? ";debug=true" : "");
                        const xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                {
                                    const resp = JSON.parse (xmlhttp.responseText);
                                    if ("OK" === resp.status)
                                        resolve (resp.result);
                                    else
                                        reject (resp);
                                }
                                else
                                    reject ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                        };
                        xmlhttp.send ();
                    }
                )
            );
        }

        /**
         * @summary Connect to the server to see if it's alive and connected to Spark.
         * @description Invoke the server-side pong function.
         * @param debug add the 'debug' matrix parameter to request if <code>true</code>
         * @return a promise that resolves to the the received data, signature fn (result), or rejects with the failed result or the error message
         */
        function pong (debug)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        const url = util.home () + "cim/pong" + ((debug) ? ";debug=true" : "");
                        const xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                {
                                    const resp = JSON.parse (xmlhttp.responseText);
                                    if ("OK" === resp.status)
                                        resolve (resp.result);
                                    else
                                        reject (resp);
                                }
                                else
                                    reject ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                        };
                        xmlhttp.send ();
                    }
                )
            );
        }

        /**
         * @summary Fix up the Spark and Hadoop URLs.
         * @description Gets the server status and updates the URL's accordingly.
         * @function initialize
         * @memberOf module:cimapp
         */
        function initialize ()
        {
            pong (true).then (
                function (result)
                {
                    if ("localhost" !== result.properties["SparkConnectionFactory.ServerName"])
                    {
                        const namenode = result.environment.NAMENODE;
                        const ui = result.spark_instance.spark_application_ui_url;
                        document.getElementById ("spark_master").setAttribute ("href", "http://" + namenode + ":8080");
                        document.getElementById ("spark_job").setAttribute ("href", ui);
                        document.getElementById ("hadoop_hdfs").setAttribute ("href", "http://" + namenode + ":50070")
                    }
                    else
                        document.getElementById ("spark_master").style.display = "none";
                }
            ).catch (
                function (error)
                {
                    document.getElementById ("spark_master").style.display = "none";
                }
            );
        }

        function root_name (filename)
        {
            let ret = filename;

            if (filename.startsWith ("hdfs://"))
            {
                const s = filename.substring ("hdfs://".length);
                ret = s.substring (s.indexOf ("/"));
                if (ret.endsWith (".rdf"))
                    ret = ret.substring (0, ret.length - 4);
            }

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
            const loaded = cimmap.get_loaded ();
            if (!TheCurrentName && loaded)
                TheCurrentName = root_name (loaded.files[0]);
            let name = TheCurrentName || "save";
            const difference_model = document.getElementById ("difference_model").checked;
            const only_new = document.getElementById ("only_new").checked;
            let suffix = "";
            if (difference_model)
                suffix = "_diff";
            else if (only_new)
                suffix = "_new";
            name = name + suffix;
            const about = TheCurrentAbout;
            const description = TheCurrentDescription;

            const pending =
                (null == cimmap.get_data ()) ? Promise.resolve ("no data") :
                    new Promise (
                        function (resolve, reject)
                        {
                            const begin = new Date ().getTime ();
                            console.log ("starting xml creation");
                            const text = cim.write_xml (cimmap.get_data ().Element, difference_model, only_new, about, description);
                            const start = new Date ().getTime ();
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
                                                            const end = new Date ().getTime ();
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