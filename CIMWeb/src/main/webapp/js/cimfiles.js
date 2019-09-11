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
        let LAST_DIRECTORY = "/";

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
            if (response.status === "OK")
            {
                // render the file list
                const root = response.result.root.substring (response.result.filesystem.length);
                LAST_DIRECTORY = root;
                const parent = ("/" !== root) ? root.substring (0, root.substring (0, root.length - 1).lastIndexOf ("/")) + "/" : "/";
                const file_table_template =
`<div class="container-fluid">
  <div class="row justify-content-center">
    <div class="col-1">
    </div>
    <div class="col-10">
      <h1>{{response.result.filesystem}}</h1>
      <h2>{{dir}}</h2>
      <form id="upload" class="form-inline float-left" role="form">
          <input id="file" class="form-control" type="file" name="file"/>
          <button id="do_put" type="button" class="btn btn-primary">Upload</button>
      </form>
      <form id="sideload" class="form-inline float-left" style="margin-left: 20px;" role="form">
          <input id="url" class="form-control" type="text" name="url" placeholder="URL of RDF or ZIP"/>
          <button id="do_sideload" type="button" class="btn btn-primary">Sideload</button>
      </form>
      <form id="load" class="form-inline float-right" style="margin-right: 25px;" role="form">
          <button id="do_load" type="button" class="btn btn-primary">Load</button>
      </form>
      <div class="table-responsive">
        <table id="file_table" class="table table-striped table-hover">
          <thead>
            <tr><td class="text-center">Load</td><td class="text-center">View</td><td>Path</td><td>Owner:Group</td><td>Permission</td><td>Modified</td><td class="text-center">Size</td><td class="text-center">Remove</td></tr>
          </thead>
{{{dots}}}
{{#response.result.files}}
          <tr>
            <td style="text-align: center;">{{{load}}}</td>
            <td class="text-center">{{{view}}}</td>
            <td>{{{file}}}</td>
            <td>{{owner}}:{{group}}</td>
            <td>{{permission}}</td>
            <td>{{modified}}</td>
            <td class="text-right">{{size}}</td>
            <td class="text-center">{{{remove}}}</td>
          </tr>
{{/response.result.files}}
          <tfoot>
            <tr><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td id="total_size" class="right"></td><td class="center"></td></tr>
          </tfoot>
        </table>
      </div>
    </div>
    <div class="col-1">
    </div>
  </div>
</div>`;
                const text = mustache.render
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

                            return (("/" !== root) ?
                                "          <tr>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td><a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_fetch (\"" + parent + "\");}); return false;'><b>..</b></a></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "            <td></td>\n" +
                                "          </tr>\n"
                            :
                                "")
                        },
                        load: function ()
                        {
                            let text;
                            if (this.is_directory)
                                text = "";
                            else if (this.path.endsWith (".rdf") || this.path.endsWith (".xml") || this.path.endsWith (".csv"))
                                // tag these checkboxes with class 'filename'
                                text = `<input class="filename" type="checkbox" name="` + root + this.path + `">`;
                            else
                                text = "";
                            return (text);
                        },
                        view: function ()
                        {
                            let text;
                            if (this.is_directory)
                                text = "";
                            else if (this.path.endsWith (".rdf") || this.path.endsWith (".xml"))
                                text = "<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_view (\"" + root + this.path + "\");}); return false;'><i class='fa fa-eye'></i></a>";
                            else
                            {
                                const url = util.home () + "cim/file" + root + this.path;
                                text = "<a href='" + url + "'><i class='fa fa-cloud-download-alt'></i></span></a>";
                            }
                            return (text);
                        },
                        file: function ()
                        {
                            let text;
                            if (this.is_directory)
                                text = "<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_fetch (\"" + root + this.path + "/\");}); return false;'>" + this.path + "</a>";
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
                            return ("<a href='#' onclick='require([\"cimfiles\"], function(cimfiles) {cimfiles.do_remove (\"" + root + this.path + "\");}); return false;'><i class='fa fa-trash'></a>");
                        }
                    }
                );
                document.getElementById ("files").innerHTML = text;
                document.getElementById ("do_put").onclick = do_put;
                document.getElementById ("do_load").onclick = do_load;
            }
            else
                document.getElementById ("files").innerHTML = response.message;
        }

        /**
         * @summary Get the file at the given path.
         * @description Invoke the server-side function to get file data.
         * @param {string} path - the path to list (this is forced to start with a slash).
         * @return A Promise that resolves with the text of the file, or rejects with a status "FAIL".
         * @function get
         * @memberOf module:cimfiles
         */
        function get (path)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        path = path || "";
                        path = path.startsWith ("/") ? path : "/" + path;
                        const url = util.home () + "cim/file" + path;
                        const xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                    resolve (xmlhttp.responseText);
                                else
                                    if (null != err)
                                        reject ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
                        };
                        xmlhttp.send ();
                    }
                )
            );
        }

        /**
         * @summary Read the list of files at the given path.
         * @description Invoke the server-side function to list files.
         * @param {string} path - the path to list (this is forced to start and end with a slash).
         * @return a Promise that resolves with the server response or rejects with a status "FAIL" if the path does not exist.
         * @function fetch
         * @memberOf module:cimfiles
         */
        function fetch (path)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        path = path || "";
                        path = path.startsWith ("/") ? path : "/" + path;
                        path = path.endsWith ("/") ? path : path + "/";
                        const url = util.home () + "cim/file" + path;
                        const xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                    resolve (JSON.parse (xmlhttp.responseText));
                                else
                                    reject ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
                        };
                        xmlhttp.send ();
                    }
                )
            );
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
            fetch (path).then (make_file_list).catch (
                function (error)
                {
                    document.getElementById ("files").innerHTML = JSON.stringify (error, null, 4);
                }
            );
        }

        /**
         * @summary Put a file on HDFS.
         * @description Store data at URL.
         * @param {string} path - the HDFS path for the data
         * - forced to start with a slash
         * - if there is no data and the url ends in a slash, creates a directory.
         * @param {blob} data - the contents of the file
         * @return A Promise that resolves with the server response JSON, or rejects with a status "FAIL".
         * @function put
         * @memberOf module:cimfiles
         */
        function put (path, data)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        path = path.startsWith ("/") ? path : "/" + path;
                        const url = util.home () + "cim/file" + path;
                        const xmlhttp = util.createCORSRequest ("PUT", url);
                        xmlhttp.onreadystatechange = function ()
                        {
                            if (4 === xmlhttp.readyState)
                                if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                                    resolve (JSON.parse (xmlhttp.responseText));
                                else
                                    reject ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
                        };
                        xmlhttp.send (data);
                    }
                )
            );
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
            const file = document.getElementById ("file");
            if (file.value !== "")
            {
                const name = file.value.replace ("C:\\fakepath\\", ""); // file name
                let url = LAST_DIRECTORY + name;
                url = url.endsWith (".zip") ? url + ";unzip=true" : url;
                const data = file.files[0];
                const reader = new FileReader ();
                reader.onload = function () {
                    put (url, reader.result).then (
                        function (response)
                        {
                            if (response.status === "OK")
                                do_fetch (LAST_DIRECTORY);
                            else
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                        }
                    );
                };
                reader.onerror = function (event)
                {
                    alert (JSON.stringify (event, null, 4));
                };
                reader.readAsArrayBuffer (data);
            }
        }

        function do_load (event)
        {
            // get the file list
            const collection = document.getElementsByClassName ("filename");
            const files = [];
            for (let i = 0; i < collection.length; i++)
                if (collection[i].checked)
                    files.push (collection[i].getAttribute ("name"));
            if (0 !== files.length)
                load_files (files)
        }

        /**
         * @summary Remove the file or directory at the given path.
         * @description Invoke the server-side function to delete files.
         * @function do_remove
         * @memberOf module:cimfiles
         */
        function do_remove (path)
        {
            path = path.startsWith ("/") ? path : "/" + path;
            const url = util.home () + "cim/file" + path;
            const xmlhttp = util.createCORSRequest ("DELETE", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 === xmlhttp.readyState)
                    if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                    {
                        const resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status !== "OK")
                            alert (resp.message);
                        else
                        {
                            let parent = path.endsWith ("/") ? path.substring (0, path.length () - 1) : path;
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
         * @summary Parse a file.
         * @description Read in a CIM file.
         * @param {Blob} blob - the blob of CIM data
         * @function read_cim
         * @memberOf module:cimfiles
         */
        function read_cim (blob)
        {
            const start = new Date ().getTime ();
            console.log ("starting CIM read");
            cim.read_xml_blobs ([blob]).then (
                function (context)
                {
                    const end = new Date ().getTime ();
                    const elements = Object.keys (context.parsed.Element).length;
                    console.log ("finished CIM read (" + (Math.round (end - start) / 1000) + " seconds, " + elements + " elements)");
                    if (0 !== context.ignored)
                        console.log (context.ignored.toString () + " unrecognized element" + ((1 < context.ignored) ? "s" : ""));
                    cimmap.set_data (context.parsed);
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
            const start = new Date ().getTime ();
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
                                            const end = new Date ().getTime ();
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
         * @summary View the file contents.
         * @description Fetch the file and display in a cimmap.
         * @param {String} path the CIM RDF file path
         * @function do_view
         * @memberOf module:cimfiles
         */
        function do_view (path)
        {
            // switch to the map tab
            window.location.hash = "map";

            path = path.startsWith ("/") ? path : "/" + path;
            const url = util.home () + "cim/file" + path + ";zip=true";
            const xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.setRequestHeader ("Accept", "application/zip");
            xmlhttp.responseType = "blob";
            xmlhttp.onreadystatechange = function ()
            {
                if (4 === xmlhttp.readyState)
                    if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                        read_zip (xmlhttp.response, read_cim);
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Show what's loaded in Spark.
         * @description Fetch the full export and display in a cimmap.
         * @function do_show
         * @memberOf module:cimfiles
         */
        function do_show ()
        {
            // switch to the map tab
            window.location.hash = "map";

            const url = util.home () + "cim/view/spark;all=true;zip=true";
            const xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.setRequestHeader ("Accept", "application/zip");
            xmlhttp.responseType = "blob";
            xmlhttp.onreadystatechange = function ()
            {
                if (4 === xmlhttp.readyState)
                    if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                        read_zip (xmlhttp.response, read_cim);
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * Get the user's choice for storage level.
         * @returns {string} constant name for StorageLevel
         * @function storage_level
         * @memberOf module:cimfiles
         */
        function storage_level ()
        {
            return (document.getElementById ("storage_level").value);
        }

        /**
         * Get the user's choice for rdf:about processing.
         * @returns {boolean} <code>true</code> if rdf:about elements should be merged, <code>false</code> otherwise
         * @function do_about
         * @memberOf module:cimfiles
         */
        function do_about ()
        {
            return (document.getElementById ("do_about").checked);
        }

        /**
         * Get the user's choice for data model normalization.
         * @returns {boolean} <code>true</code> if 1:N relationships should be normalized, <code>false</code> otherwise
         * @function do_normalize
         * @memberOf module:cimfiles
         */
        function do_normalize ()
        {
            return (document.getElementById ("do_normalize").checked);
        }

        /**
         * Get the user's choice for deduplication.
         * @returns {boolean} <code>true</code> if duplicate elements should be removed, <code>false</code> otherwise
         * @function do_deduplication
         * @memberOf module:cimfiles
         */
        function do_deduplication ()
        {
            return (document.getElementById ("do_deduplication").checked);
        }

        /**
         * Get the user's choice for edge table creation.
         * @returns {boolean} <code>true</code> if the edges table is required, <code>false</code> otherwise
         * @function make_edges
         * @memberOf module:cimfiles
         */
        function make_edges ()
        {
            return (document.getElementById ("make_edges").checked);
        }

        /**
         * Get the user's choice for joining ServceLocation NIS-ISU.
         * @returns {boolean} <code>true</code> if joining is required, <code>false</code> otherwise
         * @function do_join
         * @memberOf module:cimfiles
         */
        function do_join ()
        {
            return (document.getElementById ("do_join").checked);
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
         * Get the user's choice for topology generation.
         * @returns {boolean} <code>true</code> if a topology should be created, <code>false</code> otherwise
         * @function do_topo
         * @memberOf module:cimfiles
         */
        function do_topo ()
        {
            return (document.getElementById ("do_topo").checked);
        }

        function force_retain_switches ()
        {
            return (document.getElementById ("force_retain_switches").value);
        }

        function force_retain_fuses ()
        {
            return (document.getElementById ("force_retain_fuses").value);
        }

        function force_switch_separate_islands ()
        {
            return (document.getElementById ("force_switch_separate_islands").value);
        }

        function force_fuse_separate_islands ()
        {
            return (document.getElementById ("force_fuse_separate_islands").value);
        }

        function default_switch_open_state ()
        {
            return (document.getElementById ("default_switch_open_state").checked);
        }

        function debug ()
        {
            return (document.getElementById ("debug").checked);
        }

        function cache ()
        {
            return (document.getElementById ("cache").value);
        }

        function split_maxsize ()
        {
            let splits = document.getElementById ("split_maxsize").value;
            if ("NaN" === Number (splits).toString ())
                 splits = "67108864";
            return (splits);
        }

        function header ()
        {
            return (document.getElementById ("header").checked);
        }

        function ignoreLeadingWhiteSpace ()
        {
            return (document.getElementById ("ignoreLeadingWhiteSpace").checked);
        }

        function ignoreTrailingWhiteSpace ()
        {
            return (document.getElementById ("ignoreTrailingWhiteSpace").checked);
        }

        function sep ()
        {
            return (document.getElementById ("sep").value);
        }

        function quote ()
        {
            return (document.getElementById ("quote").value);
        }

        function escape ()
        {
            return (document.getElementById ("escape").value);
        }

        function encoding ()
        {
            return (document.getElementById ("encoding").value);
        }

        function comment ()
        {
            return (document.getElementById ("comment").value);
        }

        function nullValue ()
        {
            return (document.getElementById ("nullValue").value);
        }

        function nanValue ()
        {
            return (document.getElementById ("nanValue").value);
        }

        function positiveInf ()
        {
            return (document.getElementById ("positiveInf").value);
        }

        function negativeInf ()
        {
            return (document.getElementById ("negativeInf").value);
        }

        function dateFormat ()
        {
            return (document.getElementById ("dateFormat").value);
        }

        function timestampFormat ()
        {
            return (document.getElementById ("timestampFormat").value);
        }

        function mode ()
        {
            if (document.getElementById ("permissive").checked)
                return ("PERMISSIVE");
            else if (document.getElementById ("dropmalformed").checked)
                return ("DROPMALFORMED");
            else if (document.getElementById ("failfast").checked)
                return ("FAILFAST");
            else
                return ("");
        }

        function inferSchema ()
        {
            return (document.getElementById ("inferSchema").checked);
        }

        /**
         * @summary Read the file contents in Spark.
         * @description Trigger CIMReader to read in the file.
         * @param {[string]} paths - the files to load
         * @function load_files
         * @memberOf module:cimfiles
         */
        function load_files (paths)
        {
            const csv = paths[0].toLowerCase ().endsWith (".csv");
            let path = paths.map (p => (p.startsWith ("/")) ? p : "/" + p).join ();
            path = path + ";StorageLevel=" + storage_level ();
            if (do_about ())
                path += ";do_about=true";
            if (do_normalize ())
                path += ";do_normalize=true";
            if (do_deduplication ())
                path += ";do_deduplication=true";
            if (make_edges ())
                path += ";make_edges=true";
            if (do_join ())
                path += ";do_join=true";
            if (do_topo_islands ())
                path += ";do_topo_islands=true";
            if (do_topo ())
                path += ";do_topo=true";
            if ("Unforced" !== force_retain_switches ())
                path += (";force_retain_switches=" + force_retain_switches ());
            if ("Unforced" !== force_retain_fuses ())
                path += (";force_retain_fuses=" + force_retain_fuses ());
            if ("Unforced" !== force_switch_separate_islands ())
                path += (";force_switch_separate_islands=" + force_switch_separate_islands ());
            if ("Unforced" !== force_fuse_separate_islands ())
                path += (";force_fuse_separate_islands=" + force_fuse_separate_islands ());
            if (default_switch_open_state ())
                path += ";default_switch_open_state=true";
            if (debug ())
                path += ";debug=true";
            if ("67108864" !== split_maxsize ())
                path += ";split_maxsize=" + split_maxsize ();
            if ("" !== cache ())
                path += (";cache=" + cache ());
            if (header ())
                path += ";header=true";
            if (ignoreLeadingWhiteSpace ())
                path += ";ignoreLeadingWhiteSpace=true";
            if (ignoreTrailingWhiteSpace ())
                path += ";ignoreTrailingWhiteSpace=true";
            if ("" !== sep ())
                path += ";sep=" + encodeURIComponent (sep ());
            if ("" !== quote ())
                path += ";quote=" + encodeURIComponent (quote ());
            if ("" !== escape ())
                path += ";escape=" + encodeURIComponent (escape ());
            if ("" !== encoding ())
                path += ";encoding=" + encodeURIComponent (encoding ());
            if ("" !== comment ())
                path += ";comment=" + encodeURIComponent (comment ());
            if ("" !== nullValue ())
                path += ";nullValue=" + encodeURIComponent (nullValue ());
            if ("" !== nanValue ())
                path += ";nanValue=" + encodeURIComponent (nanValue ());
            if ("" !== positiveInf ())
                path += ";positiveInf=" + encodeURIComponent (positiveInf ());
            if ("" !== negativeInf ())
                path += ";negativeInf=" + encodeURIComponent (negativeInf ());
            if ("" !== dateFormat ())
                path += ";dateFormat=" + encodeURIComponent (dateFormat ());
            if ("" !== timestampFormat ())
                path += ";timestampFormat=" + encodeURIComponent (timestampFormat ());
            if ("" !== mode ())
                path += ";mode=" + encodeURIComponent (mode ());
            if (inferSchema ())
                path += ";inferSchema=true";

            const url = util.home () + "cim/load" + path;
            const xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 === xmlhttp.readyState)
                    if (200 === xmlhttp.status || 201 === xmlhttp.status || 202 === xmlhttp.status)
                    {
                        const resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status === "OK")
                        {
                            console.log (JSON.stringify (resp, null, 4));
                            cimmap.set_loaded (resp.result);
                            if (!csv)
                                do_show ();
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
            document.getElementById ("files").innerHTML = "";
            do_fetch (LAST_DIRECTORY);
        }

        return (
            {
                initialize: initialize,
                get: get,
                fetch: fetch,
                put: put,
                do_put: do_put,
                do_remove: do_remove,
                read_cim: read_cim,
                read_zip: read_zip,
                do_view: do_view,
                do_load: do_load,
                do_fetch: do_fetch,
                load_files: load_files
            }
        );
    }
);