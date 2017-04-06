/**
 * @fileOverview Extension of {@link module:wizard} for creating ECS clusters.
 * @name ecswizard
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["wizard", "mustache", "overview", "cluster", "image", "instancetype"],
    /**
     * @summary Create an ECS cluster by specifying the AMI, instances and containers.
     * @exports ecswizard
     * @version 1.0
     */
    function (wiz, mustache, overview, cluster, image, instancetype)
    {
        var left;
        var content;
        var right;

        /**
         * @summary Wizard data.
         * @description The object passed around to maintain state.
         * @memberOf ecswizard
         */
        var data =
        {
        };

        /**
         * @summary Wizard steps.
         * @description The steps in the wizard sequence
         * @memberOf ecswizard
         */
        var steps =
            [
                overview.getStep (),
                cluster.getStep (),
                image.getStep (),
                instancetype.getStep ()
            ];

        /**
         * @summary Perform the standard layout for the main page.
         * @return {object} containing { left, middle, right } elements for
         * the left quarter, middle half and right quarter respectively.
         * @function layout
         * @memberOf module:page
         */
        function layout ()
        {
            var target;

            var template =
                "<div id='main_area' class='row'>" +
                    "<div class='col-sm-6 col-md-3' id='left'>" +
                    "</div>" +
                    "<div class='col-sm-6 col-md-3 col-md-push-6' id='right'>" +
                    "</div>" +
                    "<div class='col-md-6 col-md-pull-3 tab-content' id='content'>" +
                    "</div>" +
                "</div>";

            target = document.getElementById ("main");
            target.innerHTML = mustache.render (template);

            left = document.getElementById ("left");
            content = document.getElementById ("content");
            right = document.getElementById ("right");

            return ({ left: left, content: content, right: right });
        }

        /**
         * @summary Return the layout for the main page.
         * @return {object} containing { left, middle, right } elements for
         * the left quarter, middle half and right quarter respectively.
         * @function get_layout
         * @memberOf module:page
         */
        function get_layout ()
        {
            return ({ left: left, content: content, right: right });
        }

        /**
         * @summary Create the wizard.
         *
         * @description Builds on the generic wizard module and adds specific html id values and
         * method steps to create a functioning thing wizard.
         * @param {number} start - the initial step number
         * @function initialize
         * @memberOf ecswizard
         */
        function initialize (start)
        {
            var areas;

            areas = layout ();
            wiz.wizard (areas.left, areas.content, steps, data, start);
        }

        return (
            {
                "data": data,
                "initialize": initialize
            }
        );
    }
);
