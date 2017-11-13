/**
 * @fileOverview Query CIM data in Spark memory.
 * @name cimquery
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache"],
    /**
     * @summary Functions to query CIM data in memory.
     * @name cimquery
     * @exports cimquery
     * @version 1.0
     */
    function (util, mustache)
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
         * @summary perform query.
         * @description Perform an SQL query on loaded CIM data.
         * @param {string} sql - the SQL to use
         * @param {string} table_name - the name of the temporary view to store the result DataFrame
         * @param {function} fn - the callback function with the data
         * @function query
         * @memberOf module:cimquery
         */
        function query (sql, table_name, fn)
        {
            var table = ("" != table_name) ? "table_name=" + encodeURIComponent (table_name) + "&": "";
            var url = util.home () + "cim/query?" + table + "sql=" + encodeURIComponent (sql);
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
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
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
                var table_name = document.getElementById ("table_name").value;
                query (sql, table_name, function (data) { document.getElementById ("results_table").innerHTML = "<pre>\n" + JSON.stringify (data, null, 4) + "</pre>"; });
            }
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
            var query_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8' style='margin-top: 40px;'>\n" +
                "      <form id='query_form' class='waves-effect waves-light' role='form' style='width: 100%'>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='sql'>SQL query</label>\n" +
                "          <textarea id='sql' class='form-control' aria-describedby='sqlHelp' name='sql' rows='8' placeholder='select * from ACLineSegment' style='width: 80%'>{{sql}}</textarea>\n" +
                "          <small id='sqlHelp' class='form-text text-muted'>A spark SQL query against the <a href='https://derrickoswald.github.io/CIMReader/doc/scaladocs/index.html#ch.ninecode.model.package' target='_blank'>CIMReader schema</a>.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='table_name'>Save as table</label>\n" +
                "          <input  id='table_name' type='text' class='form-control' aria-describedby='nameHelp' placeholder='table name' value='{{table}}'>\n" +
                "          <small id='nameHelp' class='form-text text-muted'>Enter a name for a temporary view to hold the results of the query.</small>\n" +
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

            var text = mustache.render
            (
                query_template,
                {
                    sql: function () { return ((null != TheQuery) ? TheQuery : ""); },
                    table: function () { return ((null != TheTable) ? TheTable : ""); }
                }
            );
            document.getElementById ("main").innerHTML = text;
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