/**
 * @fileOverview Manage CIM files on HDFS.
 * @name cimfiles
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap"],
    /**
     * @summary Functions to manage CIM files on HDFS.
     * @name cimfiles
     * @exports cimfiles
     * @version 1.0
     */
    function (util, mustache, cim, cimmap)
    {
        var LAST_DIRECTORY = "/";

        /**
         * Make a select option list of the files.
         * @param response The response with the array of files on HDFS, each object has a name and details.
         */
        function make_file_list (response)
        {
            //{
            //  "status":"OK",
            //  "message":"",
            //  "result":{
            //    "filesystem":"hdfs://6797428d567e:8020",
            //    "root":"hdfs://6797428d567e:8020/data/",
            //    "files":[
            //      {
            //        "path":"TRA4110.rdf",
            //        "size":3265954,
            //        "modification_time":1505910788382,
            //        "access_time":1505910787601,
            //        "group":"spark",
            //        "owner":"root",
            //        "permission":"rw-r--r--",
            //        "replication":1,
            //        "block_size":134217728,
            //        "is_directory":false,
            //        "is_sym_link":false
            //      }
            //    ]
            //  }
            //}
            if (response.status == "OK")
            {
                // render the file list
                var root = response.result.root.substring (response.result.filesystem.length);
                LAST_DIRECTORY = root;
                var parent = ("/" != root) ? root.substring (0, root.substring (0, root.length - 1).lastIndexOf ("/")) + "/" : "/";
                var file_table_template =
                    "<div class='container'>\n" +
                    "  <div class='row justify-content-center'>\n" +
                    "    <div class='col-8'>\n" +
                    "      <h1>{{response.result.filesystem}}</h1>\n" +
                    "      <h2>{{dir}}</h2>\n" +
                    "      <form id='upload' class='form-inline navbar-right waves-effect waves-light' role='form'>\n" +
                    "          <input id='file' class='form-control' type='file' name='file'/>\n" +
                    "          <button id='do_put' type='button' class='btn btn-primary'>Upload</button>\n" +
                    "      </form>\n" +
                    "      <form id='sideload' class='form-inline navbar-right waves-effect waves-light' role='form' style='margin-right:10em'>\n" +
                    "          <input id='url' class='form-control' type='text' name='url' placeholder='URL of RDF or ZIP'/>\n" +
                    "          <button id='do_sideload' type='button' class='btn btn-primary'>Sideload</button>\n" +
                    "      </form>\n" +
                    "      <table id='file_table' class='table table-striped table-hover'>\n" +
                    "        <thead>\n" +
                    "          <tr><td class='center'>Load</td><td class='center'>View</td><td>Path</td><td>Owner:Group</td><td>Permission</td><td>Modified</td><td class='right'>Size</td><td class='center'>Remove</td></tr>\n" +
                    "        </thead>\n" +
                    "{{{dots}}}\n" +
                    "{{#response.result.files}}\n" +
                    "          <tr>\n" +
                    "            <td class='center'>{{{load}}}</td>\n" +
                    "            <td class='center'>{{{view}}}</td>\n" +
                    "            <td>{{{file}}}</td>\n" +
                    "            <td>{{owner}}:{{group}}</td>\n" +
                    "            <td>{{permission}}</td>\n" +
                    "            <td>{{modified}}</td>\n" +
                    "            <td class='right'>{{size}}</td>\n" +
                    "            <td class='center'>{{{remove}}}</td>\n" +
                    "          </tr>\n" +
                    "{{/response.result.files}}\n" +
                    "        <tfoot>\n" +
                    "          <tr><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td id='total_size' class='right'></td><td class='center'></td></tr>\n" +
                    "        </tfoot>\n" +
                    "      </table>\n" +
                    "    </div>\n" +
                    "  </div>\n" +
                    "</div>\n";
                var text = mustache.render
                (
                    file_table_template,
                    {
                        response: response,
                        dir: function ()
                        {
                            return (root);
                        },
                        dots: function ()
                        {

                            return (("/" != root) ?
                                "          <tr>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td><a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_fetch (\"" + parent + "\");})'><b>..</b></a></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td class='right'></td>\n" +
                                "            <td class='center'></td>\n" +
                                "          </tr>\n"
                            :
                                "")
                        },
                        load: function ()
                        {
                            var text;
                            if (this.is_directory || !this.path.endsWith (".rdf"))
                                text = ""
                            else
                                text = "<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_load (\"" + root + this.path + "\");})'><span class='glyphicon glyphicon-open'></span></a>";;
                            return (text);
                        },
                        view: function ()
                        {
                            var text;
                            if (this.is_directory || !this.path.endsWith (".rdf"))
                                text = ""
                            else
                                text = "<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_view (\"" + root + this.path + "\");})'><span class='glyphicon glyphicon-eye-open'></span></a>";;
                            return (text);
                        },
                        file: function ()
                        {
                            var text;
                            if (this.is_directory)
                                text = "<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_fetch (\"" + root + this.path + "/\");})'>" + this.path + "</a>";
                            else
                                text = this.path;
                            return (text);
                        },
                        modified: function ()
                        {
                            return (new Date (this.modification_time).toString ());
                        },
                        remove: function ()
                        {
                            return ("<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_remove (\"" + root + this.path + "\");})'><span class='glyphicon glyphicon-remove'></span></a>");
                        }
                    }
                );
                document.getElementById ("main").innerHTML = text;
                document.getElementById ("do_put").onclick = do_put;
            }
            else
                document.getElementById ("main").innerHTML = response.message;
        }

        /**
         * @summary Get the file at the given path.
         * @description Invoke the server-side function to get file gata execute the callback.
         * @param {string} path - the path to list (this is forced to start with a slash).
         * @param {function} fn - the callback function with signature fn (text).
         * @param {function} err - the JSON callback function for an error with signature fn (response).
         * @function get
         * @memberOf module:cimfiles
         */
        function get (path, fn, err)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        fn (xmlhttp.responseText);
                    else
                        if (null != err)
                            err ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
            };
            xmlhttp.send ();
        }

        /**
         * @summary Read the list of files at the given path.
         * @description Invoke the server-side function to list files and execute the callback.
         * The response will have a status "FAIL" if the path does not exist
         * @param {string} path - the path to list (this is forced to start and end with a slash).
         * @param {function} fn - the callback function for returned JSON with signature fn (response).
         * @function fetch
         * @memberOf module:cimfiles
         */
        function fetch (path, fn)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            path = path.endsWith ("/") ? path : path + "/";
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        fn (JSON.parse (xmlhttp.responseText));
                    else
                        fn ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
            };
            xmlhttp.send ();
        }

        /**
         * @summary Read the list of files at the given path.
         * @description Displays the list of files.
         * @param {string} path - the path to list (this is forced to start and end with a slash).
         * @function do_fetch
         * @memberOf module:cimfiles
         */
        function do_fetch (path)
        {
            fetch (path, make_file_list);
        }

        /**
         * @summary Put a file on HDFS.
         * @description Store data at URL and callback.
         * @param {string} path - the HDFS path for the data
         * - forced to start with a slash
         * - if there is no data and the url ends in a slash, creates a directory.
         * @param {blob} data - the contents of the file
         * @param {function} - callback for JSON response with signature fn (response)
         * @function do_put
         * @memberOf module:cimfiles
         */
        function put (path, data, fn)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("PUT", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        fn (JSON.parse (xmlhttp.responseText));
                    else
                        fn ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
            };
            xmlhttp.send (data);
        }

        /**
         * @summary Put a file on HDFS.
         * @description Store a user selected file.
         * @param {object} event - optional, the click event
         * @function do_put
         * @memberOf module:cimfiles
         */
        function do_put (event)
        {
            var url;
            var file;
            var name; // file name
            var zip; // boolean zip flag

            function callback (response)
            {
                if (response.status == "OK")
                    do_fetch (LAST_DIRECTORY);
                else
                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
            }

            file = document.getElementById ("file");
            if (file.value != "")
            {
                name = file.value.replace ("C:\\fakepath\\", "");
                url = LAST_DIRECTORY + name;
                url = url.endsWith (".zip") ? url + ";unzip=true" : url;
                var data = file.files[0];
                var reader = new FileReader ();
                reader.onload = function () { put (url, reader.result, callback); };
                reader.onerror = function (event)
                {
                    alert (JSON.stringify (event, null, 4));
                }
                reader.readAsArrayBuffer (data);
            }
        }

        /**
         * @summary Remove the file or directory at the given path.
         * @description Invoke the server-side function to delete files.
         * @function do_remove
         * @memberOf module:cimfiles
         */
        function do_remove (path)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("DELETE", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status != "OK")
                            alert (resp.message);
                        else
                        {
                            var parent = path.endsWith ("/") ? path.substring (0, path.length () - 1) : path;
                            parent = parent.substring (0, parent.lastIndexOf ("/")) + "/";
                            do_fetch (parent);
                        }
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Parse a zip file.
         * @description Read in a CIM file.
         * @param {Blob} blob - the blob of CIM data
         * @function read_cim
         * @memberOf module:cimspace
         */
        function read_cim (blob)
        {
            var start = new Date ().getTime ();
            console.log ("starting CIM read");
            cim.read_xml_blob
            (
                blob,
                function (result)
                {
                    var end = new Date ().getTime ();
                    console.log ("finished CIM read (" + (Math.round (end - start) / 1000) + " seconds)");
                    // display the results on the map
                    cimmap.terminate ();
                    cimmap.initialize ();
                    cimmap.set_data (result.parsed);
                }
            );
        }

        /**
         * @summary Uncompress a zip file and then parse it.
         * @description Use AMD wrapped zip.js (see https://github.com/MeltingMosaic/zip-amd) to read in a CIM file.
         * @param {Blob} blob - the blob of zipped data
         * @param {function} fn - the function to handle the unzipped data signature: function fn (data)
         * @function read_zip
         * @memberOf module:cimfiles
         */
        function read_zip (blob, fn)
        {
            var start = new Date ().getTime ();
            console.log ("starting unzip");
            require (
                ["zip/zip", "zip/mime-types"],
                function (zip, mimeTypes)
                {
                    //zip.workerScriptsPath = "js/zip/";
                    zip.useWebWorkers = false;
                    zip.createReader (new zip.BlobReader (blob),
                        function (zipReader)
                        {
                            zipReader.getEntries (
                                function (entries) {
                                    entries[0].getData (
                                        new zip.BlobWriter (mimeTypes.getMimeType (entries[0].filename)),
                                        function (data)
                                        {
                                            zipReader.close ();
                                            var end = new Date ().getTime ();
                                            console.log ("finished unzip (" + (Math.round (end - start) / 1000) + " seconds)");
                                            fn (data);
                                        }
                                );
                            })
                        }
                    );
                }
            );
        }

        /**
         * @summary Show the file contents.
         * @description Fetch the file and display in a cimmap.
         * @function do_view
         * @memberOf module:cimfiles
         */
        function do_view (path)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/file" + path + ";zip=true";
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.setRequestHeader ("Accept", "application/zip");
            xmlhttp.responseType = "blob";
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        read_zip (xmlhttp.response, read_cim);
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * Get the user's choice for edge table creation.
         * @returns {boolean} <code>true</code> if the edges tale is required, <code>false</code> otherwise
         * @function make_edges
         * @memberOf module:cimfiles
         */
        function make_edges ()
        {
            return (document.getElementById ("make_edges").checked);
        }

        /**
         * Get the user's choice for topology generation.
         * @returns {boolean} <code>true</code> if a topology should be created, <code>false</code> otherwise
         * @function do_topo
         * @memberOf module:cimfiles
         */
        function do_topo ()
        {
            return (document.getElementById ("do_topo").checked);
        }

        /**
         * Get the user's choice for topological island generation.
         * @returns {boolean} <code>true</code> if a topological islands should be created, <code>false</code> otherwise
         * @function do_topo_islands
         * @memberOf module:cimfiles
         */
        function do_topo_islands ()
        {
            return (document.getElementById ("do_topo_islands").checked);
        }

        /**
         * @summary Read the file contents in Spark.
         * @description Trigger CIMReader to read in the file.
         * @param {string} path - the file to load
         * @function do_load
         * @memberOf module:cimfiles
         */
        function do_load (path)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            if (make_edges ())
                path += ";make_edges=true";
            if (do_topo ())
                path += ";do_topo=true";
            if (do_topo_islands ())
                path += ";do_topo_islands=true";
            url = util.home () + "cim/load" + path;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                        {
                            alert (JSON.stringify (resp, null, 4));
                        }
                        else
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Render the file page.
         * @description Uses mustache to create HTML DOM elements that display the HDFS contents.
         * @function initialize
         * @memberOf module:cimfiles
         */
        function initialize ()
        {
            document.getElementById ("main").innerHTML = "";
            do_fetch (LAST_DIRECTORY);
        }

        return (
            {
                initialize: initialize,
                get: get,
                fetch: fetch,
                do_fetch: do_fetch,
                put: put,
                do_put: do_put,
                do_remove: do_remove,
                do_view, do_view,
                do_load, do_load
            }
        );
    }
);