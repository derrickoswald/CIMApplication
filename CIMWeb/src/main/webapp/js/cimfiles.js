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

        /**
         * @summary Read the list of files at the given path.
         * @description Invoke the server-side function to list files.
         * @function do_fetch
         * @memberOf module:cimfiles
         */
        function do_fetch (path)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("GET", url, false);
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
                            make_file_list (resp);
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
         * @memberOf module:cimfiles
         */
        function do_put (event)
        {
            var url;
            var xmlhttp;

            var file = document.getElementById ("file");
            if (file.value != "")
            {
                url = util.home () + "cim/file" + LAST_DIRECTORY + file.value.replace ("C:\\fakepath\\", "");
                var data = file.files[0];
                var reader = new FileReader ();
                reader.onload = function ()
                {
                    xmlhttp = util.createCORSRequest ("PUT", url, false);
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
                                    do_fetch (LAST_DIRECTORY);
                                else
                                    alert (resp.error);
                            }
                            else
                                alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                    };
                    xmlhttp.send (reader.result);
                };
                reader.onerror = function (event)
                {
                    alert (JSON.stringify (event, null, 4));
                }
                reader.readAsArrayBuffer (data)
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
            xmlhttp = util.createCORSRequest ("DELETE", url, false);
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
            url = util.home () + "cim/file" + path;
            xmlhttp = util.createCORSRequest ("GET", url, false);
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
                        cimmap.terminate ();
                        cimmap.initialize ();
                        cimmap.set_data (result.parsed);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
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
            xmlhttp = util.createCORSRequest ("GET", url, false);
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
                do_fetch: do_fetch,
                do_put: do_put,
                do_remove: do_remove,
                do_view, do_view,
                do_load, do_load
            }
        );
    }
);