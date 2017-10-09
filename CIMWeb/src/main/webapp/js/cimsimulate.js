/**
 * @fileOverview Simulate with gridlabd.
 * @name cimexport
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache"],
    /**
     * @summary Functions to simulate using CIM data in memory.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache)
    {
        /**
         * @summary Render the simulations page.
         * @description Uses mustache to create HTML DOM elements that display the simulation options.
         * @function initialize
         * @memberOf module:cimexport
         */
        function initialize ()
        {
            document.getElementById ("main").innerHTML = "";
            var simulate_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Simulate using GridLAB-D</h1>\n" +
                "      <h2>TBD</h2>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            var text = mustache.render
            (
                simulate_template
            );
            document.getElementById ("main").innerHTML = text;
        }

        return (
            {
                initialize: initialize
            }
        );
    }
)
