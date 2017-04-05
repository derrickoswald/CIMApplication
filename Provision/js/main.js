/*

So far, custom configuration is not needed.

requirejs.config
(
    {
        // by default load any module IDs from js/lib
        baseUrl: 'js/lib',

        // except, if the module ID starts with "app",
        // load it from the js/app directory. paths
        // config is relative to the baseUrl, and
        // never includes a ".js" extension since
        // the paths config could be for a directory.
        paths:
        {
            app: '../app'
        }
    }
);

 */

/**
 * @fileOverview Application initialization.
 * @name main
 * @author Derrick Oswald
 * @version 1.0
 */
requirejs
(
    ["ecswizard"],
    /**
     * @summary Main entry point for the application.
     * @description Performs application initialization as the first step in the RequireJS load sequence.
     * @see http://requirejs.org/docs/api.html#data-main
     * @name main
     * @exports main
     * @version 1.0
     */
    function (ecswizard)
    {
        ecswizard.initialize ();
    }
);
