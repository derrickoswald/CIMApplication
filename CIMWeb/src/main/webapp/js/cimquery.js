/**
 * @fileOverview Query CIM data in Spark memory.
 * @name cimquery
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap"],
    /**
     * @summary Functions to query CIM data in memory.
     * @name cimquery
     * @exports cimquery
     * @version 1.0
     */
    function (util, mustache, cim, cimmap)
    {
        /**
         * The current query.
         */
        var TheQuery;

        /**
         * The current view name for the results.
         */
        var TheTable;

        /**
         * The Cassandra table for the results.
         */
        var TheCassandraTable;

        /**
         * Direct queries to Cassandra.
         */
        var QueryCassandra = false;

        /**
         * @summary perform query.
         * @description Perform an SQL query on loaded CIM data.
         * @param {string} sql - the SQL to use
         * @param {boolean} cassandra - if <code>true</code> query Cassandra rather than Spark
         * @param {string} table_name - the name of the temporary view to store the result DataFrame, "" for none
         * @param {string} cassandra_table_name - the name of the name of the Cassandra table to store the result DataFrame, "" for none
         * @param {function} fn - the callback function with the data
         * @param {function} error - the error callback function with an error string if possible
         * @function query
         * @memberOf module:cimquery
         */
        function query (sql, cassandra, table_name, cassandra_table_name, fn, error)
        {
            error = error || function (s) { alert (s); };
            var target = (cassandra) ? "cassandra=true&": "";
            var table = ("" != table_name) ? "table_name=" + encodeURIComponent (table_name) + "&": "";
            var cassandra_table = ("" != cassandra_table_name) ? "cassandra_table_name=" + encodeURIComponent (cassandra_table_name) + "&": "";
            var url = util.home () + "cim/query?" + target + table + cassandra_table + "sql=" + encodeURIComponent (sql);
            var xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                            fn (resp.result);
                        else
                            error (resp.message);
                    }
                    else
                        error ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        function queryPromise (options)
        {
            return (
                new Promise (
                    function (resolve, reject)
                    {
                        var target = (options.cassandra) ? "cassandra=true&": "";
                        var table = (options.table) ? "table_name=" + encodeURIComponent (options.table) + "&": "";
                        var cassandra_table = (options.cassandra_table) ? "cassandra_table_name=" + encodeURIComponent (options.cassandra_table) + "&": "";
                        var url = util.home () + "cim/query?" + target + table + cassandra_table + "sql=" + encodeURIComponent (options.sql);
                        var xmlhttp = util.createCORSRequest ("GET", url);
                        xmlhttp.onload = function ()
                        {
                            if (xmlhttp.status >= 200 && xmlhttp.status < 300)
                            {
                                var resp = JSON.parse (xmlhttp.responseText);
                                if (resp.status == "OK")
                                    resolve (resp.result);
                                else
                                    reject (resp.message);
                            }
                            else
                                reject (
                                    {
                                        status: xmlhttp.status,
                                        statusText: xmlhttp.statusText
                                    }
                                );
                        };
                        xmlhttp.onerror = function ()
                        {
                            reject (
                                {
                                    status: xmlhttp.status,
                                    statusText: xmlhttp.statusText
                                }
                            );
                        };
                        xmlhttp.send ();
                    }
                )
            );
        }

        function create_from (proto)
        {
            proto.EditDisposition = "new";
            var dummy_data = {};
            var cls = cim.class_map (proto);
            var obj = new cls (proto, dummy_data);
            if (dummy_data.IdentifiedObject)
                proto.mRID = proto.id;
            obj = new cls (proto, cimmap.get_data ());
        }

        /**
         * @summary Query loaded file.
         * @description Perform an SQL query on loaded CIM data.
         * @param {object} event - optional, the click event
         * @function do_query
         * @memberOf module:cimquery
         */
        function do_query (event)
        {
            var sql = document.getElementById ("sql").value;
            if (sql != "")
            {
                TheQuery = sql;
                TheTable = document.getElementById ("table_name").value;
                TheCassandraTable = document.getElementById ("cassandra_table_name").value;
                QueryCassandra = document.getElementById ("query_cassandra").checked;
                var class_name = document.getElementById ("create_class_name").value;
                function fn (data)
                {
                    document.getElementById ("results_table").innerHTML = "<pre>\n" + JSON.stringify (data, null, 4) + "</pre>";
                    if (class_name != "")
                    {
                        for (var i = 0; i < data.length; i++)
                        {
                            var proto = data[i];
                            proto.cls = class_name;
                            if (!proto.id)
                                proto.id = class_name + (~~(1e6 * Math.random ())).toString ();
                            create_from (proto);
                        }
                    }
                }
                query (TheQuery, QueryCassandra, TheTable, TheCassandraTable, fn);
            }
        }

        /**
         * @summary Render the query page.
         * @description Uses mustache to create HTML DOM elements that comprise the query form.
         * @function initialize
         * @memberOf module:cimquery
         */
        function initialize ()
        {
            document.getElementById ("query").innerHTML = "";
            var query_template =
                `
                <div class="container">
                  <div class="row justify-content-center">
                    <div class="col-12" style="margin-top: 40px;">
                      <form id="query_form" role="form" style="width: 100%">
                        <div class="form-group">
                          <label for="sql">SQL query</label>
                          <textarea id="sql" class="form-control" aria-describedby="sqlHelp" name="sql" rows="8" placeholder="select * from ACLineSegment" style="width: 80%">{{sql}}</textarea>
                          <small id="sqlHelp" class="form-text text-muted">A Spark SQL query against the <a href="https://derrickoswald.github.io/CIMReader/doc/scaladocs/index.html#ch.ninecode.model.package" target="_blank">CIMReader schema</a>.</small>
                        </div>
                        <div class="form-group row">
                          <div class="col-sm-2 col-form-label">Cassandra</div>
                          <div class="col-sm-10">
                            <div class="form-check">
                              <label class="form-check-label">
                                <input id="query_cassandra" class="form-check-input" type="checkbox" value=""{{cassandra}}>
                                Query Cassandra rather than Spark.
                              </label>
                            </div>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="table_name">Save as table</label>
                          <input id="table_name" type="text" class="form-control" aria-describedby="nameHelp" placeholder="table name" value="{{table}}">
                          <small id="nameHelp" class="form-text text-muted">Enter a name for a temporary view to hold the results of the query.</small>
                        </div>
                        <div class="form-group">
                          <label for="cassandra_table_name">Save in Cassandra</label>
                          <input id="cassandra_table_name" type="text" class="form-control" aria-describedby="cassandraHelp" placeholder="cassandra table name, e.g. measured_value" value="{{ctable}}">
                          <small id="cassandraHelp" class="form-text text-muted">Enter the name of the Cassandra table to store the results of the query.</small>
                        </div>
                        <div class="form-group">
                          <label for="create_elements">Create CIM elements in browser memory</label>
                          <select id="create_class_name" class="form-control custom-select" aria-describedby="createElementsHelp">
                {{#classes}}
                              <option value="{{.}}">{{.}}</option>
                {{/classes}}
                          </select>
                          <small id="createElementsHelp" class="form-text text-muted">Select the CIM class for an object to be created (in browser memory) for each row of query results.</small>
                        </div>
                        <div class="form-group">
                          <button id="do_query" type="button" class="btn btn-primary">Query</button>
                        </div>
                      </form>
                      <div id="results_table">
                      </div>
                    </div>
                  </div>
                </div>
                `;

            var cls_map = cim.classes ();
            var classes = [];
            for (var property in cls_map)
                if (cls_map.hasOwnProperty (property))
                    classes.push (property);
            classes.sort ();
            classes.unshift ("");
            var text = mustache.render
            (
                query_template,
                {
                    sql: function () { return ((null != TheQuery) ? TheQuery : ""); },
                    table: function () { return ((null != TheTable) ? TheTable : ""); },
                    ctable: function () { return ((null != TheCassandraTable) ? TheCassandraTable : ""); },
                    cassandra: function () { return ((QueryCassandra) ? " checked" : ""); },
                    classes: classes
                }
            );
            document.getElementById ("query").innerHTML = text;
            document.getElementById ("do_query").onclick = do_query;
        }

        return (
            {
                initialize: initialize,
                query: query,
                queryPromise: queryPromise
            }
        );
    }
)