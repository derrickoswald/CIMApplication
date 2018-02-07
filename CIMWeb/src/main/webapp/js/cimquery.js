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
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-12' style='margin-top: 40px;'>\n" +
                "      <form id='query_form' role='form' style='width: 100%'>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='sql'>SQL query</label>\n" +
                "          <textarea id='sql' class='form-control' aria-describedby='sqlHelp' name='sql' rows='8' placeholder='select * from ACLineSegment' style='width: 80%'>{{sql}}</textarea>\n" +
                "          <small id='sqlHelp' class='form-text text-muted'>A Spark SQL query against the <a href='https://derrickoswald.github.io/CIMReader/doc/scaladocs/index.html#ch.ninecode.model.package' target='_blank'>CIMReader schema</a>.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-2 col-form-label'>Cassandra</div>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <div class='form-check'>\n" +
                "              <label class='form-check-label'>\n" +
                "                <input id='query_cassandra' class='form-check-input' type='checkbox' value=''{{cassandra}}>\n" +
                "                Query Cassandra rather than Spark.\n" +
                "              </label>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='table_name'>Save as table</label>\n" +
                "          <input id='table_name' type='text' class='form-control' aria-describedby='nameHelp' placeholder='table name' value='{{table}}'>\n" +
                "          <small id='nameHelp' class='form-text text-muted'>Enter a name for a temporary view to hold the results of the query.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='cassandra_table_name'>Save in Cassandra</label>\n" +
                "          <input id='cassandra_table_name' type='text' class='form-control' aria-describedby='cassandraHelp' placeholder='cassandra table name, e.g. measured_value_by_day' value='{{ctable}}'>\n" +
                "          <small id='cassandraHelp' class='form-text text-muted'>Enter the name of the Cassandra table to store the results of the query.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='create_elements'>Create CIM elements in browser memory</label>\n" +
                "          <select id='create_class_name' class='form-control' aria-describedby='createElementsHelp'>\n" +
                "{{#classes}}\n" +
                "              <option value='{{.}}'>{{.}}</option>\n" +
                "{{/classes}}\n" +
                "          </select>\n" +
                "          <small id='createElementsHelp' class='form-text text-muted'>Select the CIM class for an object to be created (in browser memory) for each row of query results.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <button id='do_query' type='button' class='btn btn-primary'>Query</button>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "      <div id='results_table'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

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
                query: query
            }
        );
    }
)