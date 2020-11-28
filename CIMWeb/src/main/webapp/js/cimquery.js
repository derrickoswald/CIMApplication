/**
 * @fileOverview Query CIM data in Spark memory.
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap"],
    /**
     * @summary Functions to query CIM data in memory.
     * @exports cimquery
     * @version 1.0
     */
    function (util, mustache, cim, cimmap)
    {
        /**
         * The current query.
         */
        let TheQuery;

        /**
         * The current view name for the results.
         */
        let TheTable;

        /**
         * The Cassandra table for the results.
         */
        let TheCassandraTable;

        /**
         * Direct queries to Cassandra.
         */
        let QueryCassandra = false;

        function queryPromise (options)
        {
            const target = (options.cassandra) ? "cassandra=true&": "";
            const table = (options.table) ? "table_name=" + encodeURIComponent (options.table) + "&": "";
            const cassandra_table = (options.cassandra_table) ? "cassandra_table_name=" + encodeURIComponent (options.cassandra_table) + "&": "";
            const url = util.home () + "cim/query?" + target + table + cassandra_table + "sql=" + encodeURIComponent (options.sql);
            return (
                util.makeRequest ("GET", url).then (
                    (xmlhttp) =>
                    {
                        return (
                            new Promise (
                                function (resolve, reject)
                                {
                                    try
                                    {
                                        const resp = JSON.parse (xmlhttp.responseText);
                                        if (resp.status === "OK")
                                            resolve (resp.result);
                                        else
                                            reject (resp.message);
                                    }
                                    catch (exception)
                                    {
                                        reject (exception.toString ());
                                    }
                                }
                            )
                        );
                    }
                )
            );
        }

        function create_from (proto)
        {
            proto.EditDisposition = "new";
            const dummy_data = {};
            const cls = cim.class_map (proto);
            let obj = new cls (proto, dummy_data);
            if (dummy_data.IdentifiedObject)
                proto.mRID = proto.id;
            obj = new cls (proto, cimmap.get_data ());
        }

        /**
         * @summary Query loaded file.
         * @description Perform an SQL query on loaded CIM data.
         * @param {object} event - optional, the click event
         * @function do_query
         */
        function do_query (event)
        {
            const sql = document.getElementById ("sql").value;
            if (sql !== "")
            {
                TheQuery = sql;
                TheTable = document.getElementById ("table_name").value;
                TheCassandraTable = document.getElementById ("cassandra_table_name").value;
                QueryCassandra = document.getElementById ("query_cassandra").checked;
                const class_name = document.getElementById ("create_class_name").value;
                function fn (data)
                {
                    document.getElementById ("results_table").innerHTML = "<pre>\n" + JSON.stringify (data, null, 4) + "</pre>";
                    if (class_name !== "")
                    {
                        for (let i = 0; i < data.length; i++)
                        {
                            const proto = data[i];
                            proto.cls = class_name;
                            if (!proto.id)
                                proto.id = class_name + (~~(1e6 * Math.random ())).toString ();
                            create_from (proto);
                        }
                    }
                }
                queryPromise ({ sql: TheQuery, cassandra: QueryCassandra, table: TheTable, cassandra_table: TheCassandraTable }).then (fn);
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
            const query_template =
                `
                <div class="container">
                  <div class="row justify-content-center">
                    <div class="col-12" style="margin-top: 40px;">
                      <form id="query_form" role="form" style="width: 100%">
                        <div class="form-group">
                          <label for="sql">SQL query</label>
                          <textarea id="sql" class="form-control" aria-describedby="sqlHelp" name="sql" rows="8" placeholder="select * from ACLineSegment" style="width: 80%">{{sql}}</textarea>
                          <small id="sqlHelp" class="form-text text-muted">A Spark SQL query against the <a href="https://derrickoswald.github.io/CIMSpark" target="_blank">CIMReader schema</a>.</small>
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

            const cls_map = cim.classes ();
            const classes = [];
            for (let property in cls_map)
                if (cls_map.hasOwnProperty (property))
                    classes.push (property);
            classes.sort ();
            classes.unshift ("");
            document.getElementById ("query").innerHTML = mustache.render
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
            document.getElementById ("do_query").onclick = do_query;
        }

        /**
         * @summary Update the page.
         * @description Called if the page is already initialized and the page is again being shown.
         * @function focus
         * @memberOf module:cimquery
         */
        function focus ()
        {
        }

        /**
         * @summary Close down the page.
         * @description Called if the page is being hidden.
         * @function blur
         * @memberOf module:cimquery
         */
        function blur ()
        {
        }

        return (
            {
                queryPromise: queryPromise,
                initialize: initialize,
                focus: focus,
                blur: blur
            }
        );
    }
);