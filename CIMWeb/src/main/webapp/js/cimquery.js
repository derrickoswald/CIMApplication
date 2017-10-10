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
         * @summary perform query.
         * @description Perform an SQL query on loaded CIM data.
         * @param {string} sql - the SQL to use
         * @function query
         * @memberOf module:cimquery
         */
        function query (sql, fn)
        {
            url = util.home () + "cim/query?sql=" + encodeURIComponent (sql);
            xmlhttp = util.createCORSRequest ("GET", url);
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
            var url;
            var xmlhttp;

            var sql = document.getElementById ("sql").value;
            if (sql != "")
                query (sql, function (data) { document.getElementById ("results_table").innerHTML = "<pre>\n" + JSON.stringify (data, null, 4) + "</pre>"; });
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
                "      <form id='query_form' class='form-inline waves-effect waves-light' role='form' style='width: 100%'>\n" +
                "          <textarea id='sql' class='form-control' name='sql' rows='8' placeholder='SQL query' style='width: 80%'></textarea>\n" +
                "          <button id='do_query' type='button' class='btn btn-primary'>Query</button>\n" +
                "      </form>\n" +
                "      <div id='results_table'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            var text = mustache.render
            (
                query_template
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