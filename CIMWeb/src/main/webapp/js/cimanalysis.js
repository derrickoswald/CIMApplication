/**
 * @fileOverview Calculate short circuit values.
 * @name cimanalysis
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cimmap", "cimquery", "cimcassandra", "analyzers/maximumfeedin", "analyzers/shortcircuit"],
    /**
     * @summary Functions to perform short circuit calculations.
     * @name cimanalysis
     * @exports cimanalysis
     * @version 1.0
     */
    function (util, mustache, cimmap, cimquery, cimcassandra, MaximumFeedIn, ShortCircuit)
    {
        let TheMaximumFeedIn;
        let TheShortCircuit;

        /**
         * @summary Initialize the short circuit page.
         * @description Get the keyspaces then render the short circuit page.
         * @function initialize
         * @memberOf module:cimanalysis
         */
        function initialize ()
        {
            const analysis_tabs_template =
`
<ul id="analysisTabs" class="nav nav-tabs justify-content-center bg-light" role="tablist">
  <li class="nav-item" role="presentation">
    <a id="maximumfeedin-tab" class="nav-link active" data-toggle="tab" href="#maximumfeedin_content" role="tab" aria-controls="maximumfeedin_content" aria-selected="false">Maximum Feed-In</a>
  </li>
  <li class="nav-item" role="presentation">
    <a id="shortcircuit-tab" class="nav-link" data-toggle="tab" href="#shortcircuit_content" role="tab" aria-controls="shortcircuit_content" aria-selected="true">Short Circuit</a>
  </li>
</ul>
<div class="tab-content" id="analysisTabContents">
  <div id="maximumfeedin_content" class="tab-pane fade show active" role="tabpanel" aria-labelledby="maximumfeeedin-tab"></div>
  <div id="shortcircuit_content" class="tab-pane fade show" role="tabpanel" aria-labelledby="shortcircuit-tab"></div>
</div>
`;
            document.getElementById ("analysis").innerHTML = mustache.render (analysis_tabs_template);
            TheMaximumFeedIn = new MaximumFeedIn (document.getElementById ("maximumfeedin_content"));
            TheMaximumFeedIn.initialize ();
            TheShortCircuit = new ShortCircuit (document.getElementById ("shortcircuit_content"));
            TheShortCircuit.initialize ();
        }

        /**
         * @summary Update the page.
         * @description Called if the page is already initialized and the page is again being shown.
         * @function focus
         * @memberOf module:cimanalysis
         */
        function focus ()
        {
            TheMaximumFeedIn.focus ();
            TheShortCircuit.focus ();
        }

        /**
         * @summary Close down the page.
         * @description Called if the page is being hidden.
         * @function blur
         * @memberOf module:cimanalysis
         */
        function blur ()
        {
            TheMaximumFeedIn.blur ();
            TheShortCircuit.blur ();
        }

        return (
            {
                initialize: initialize,
                focus: focus,
                blur: blur
            }
        );
    }
);