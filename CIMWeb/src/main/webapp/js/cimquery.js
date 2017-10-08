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
         * @summary Query loaded file.
         * @description Perform an SQL query on loaded CIM data.
         * @param {object} event - optional, the click event
         * @function do_query
         * @memberOf module:cimfiles
         */
        function do_query (event)
        {
            var url;
            var xmlhttp;

            var sql = document.getElementById ("sql").value;
            if (sql != "")
            {
                url = util.home () + "cim/query?sql=" + encodeURIComponent (sql);
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
                                document.getElementById ("results_table").innerHTML = "<pre>\n" + JSON.stringify (resp.result, null, 4) + "</pre>";
                            else
                                alert (resp.message);
                        }
                        else
                            alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
                };
                xmlhttp.send ();
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
                "    <div class='col-8'>\n" +
                "      <form id='query_form' class='form-inline waves-effect waves-light' role='form' style='width: 100%'>\n" +
                "          <input id='sql' class='form-control' type='text' name='sql' placeholder='SQL query' style='width: 80%'/>\n" +
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
                do_query: do_query
            }
        );
    }
)