/**
 * @fileOverview Export data.
 * @name cimexport
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cimquery"],
    /**
     * @summary Functions to export CIM data in memory.
     * @name cimexport
     * @exports cimexport
     * @version 1.0
     */
    function (util, mustache, cimquery)
    {
        /**
         * @summary Call the gridlab RESTful service.
         * @description Invokes the server side gridlab function.
         * @param {string} island - the island name from the topology.
         * @function exportIsland
         * @memberOf module:cimexport
         */
        function exportIsland (island)
        {
            var url;
            var xmlhttp;

            url = util.home () + "cim/gridlab/" + island;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        document.getElementById ("glm").innerHTML = "<pre>\n" +  xmlhttp.responseText + "</pre>";
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Execute an export.
         * @description Gets the user's input and generates the .glm for the island.
         * @function do_export
         * @memberOf module:cimexport
         */
        function do_export ()
        {
            var island = document.getElementById ("island").value;
            if (("undefined" != typeof (island)) && ("" != island))
                exportIsland (island);
        }

        /**
         * @summary Render the export page.
         * @description Uses mustache to create HTML DOM elements that display the export options.
         * @function initialize
         * @memberOf module:cimexport
         */
        function initialize ()
        {
            document.getElementById ("main").innerHTML = "";
            var export_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Export GridLAB-D .glm</h1>\n" +
                "      <form>\n" +
                "        <div class='form-group row'>\n" +
                "          <label class='col-sm-2 col-form-label' for='island'>Island</label>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <select id='island' class='form-control' name='island'>\n" +
                "{{#data}}\n" +
                "              <option value='{{mRID}}'>{{mRID}}</option>\n" +
                "{{/data}}\n" +
                "            </select>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class='form-group row'>\n" +
                "          <div class='col-sm-10'>\n" +
                "            <button id='do_export' type='button' class='btn btn-primary'>Export</button>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "      <div id='glm'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            cimquery.query ("select i.IdentifiedObject.mRID from TopologicalIsland i",
                function (data)
                {
                    var text = mustache.render
                    (
                        export_template,
                        { data: data }
                    );
                    document.getElementById ("main").innerHTML = text;
                    document.getElementById ("do_export").onclick = do_export;
                }
            );
        }

        return (
            {
                initialize: initialize,
                exportIsland: exportIsland
            }
        );
    }
)
