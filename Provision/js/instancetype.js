/**
 * @fileOverview Choose instance type step of the ECS provisioning wizard.
 * @name instancetype
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache"],
    /**
     * @summary Get instance types for master and slaves.
     * @description Gets the instance type the user has and chosen for master and slave.
     * @name instancetype
     * @exports instancetype
     * @version 1.0
     */
    function (mustache)
    {
        var instances = null;

// Cross-Origin Request Blocked: The Same Origin Policy disallows reading the remote resource at https://aws.amazon.com/ec2/instance-types. (Reason: CORS header ‘Access-Control-Allow-Origin’ missing).  (unknown)
//
//        /**
//         * @summary Browser independent CORS setup.
//         * @description Creates the CORS request and opens it.
//         * @param {string} method The method type, e.g. "GET" or "POST"
//         * @param {string} url the URL to open the request on
//         * @param {boolean} synchronous optional parameter for open() call, default <em>true</em>
//         * @returns {object} the request object or <code>null</code> if CORS isn't supported
//         * @memberOf module:instancetype
//         */
//        function createCORSRequest (method, url, synchronous)
//        {
//            var ret;
//
//            if ("undefined" == typeof (synchronous))
//                synchronous = true;
//            ret = new XMLHttpRequest ();
//            if ('withCredentials' in ret) // "withCredentials" only exists on XMLHTTPRequest2 objects
//            {
//                ret.open (method, url, synchronous);
//                ret.withCredentials = true;
//            }
//            else if (typeof XDomainRequest != 'undefined') // IE
//            {
//                ret = new XDomainRequest ();
//                ret.open (method, url);
//            }
//            else
//                ret = null; // CORS is not supported by the browser
//
//            return (ret);
//        }
//
//        /**
//         * @summary Fetch the instance type page from Amazon.
//         * @description TBD
//         * @param {function} callback - the function to call back when the fetch is complete
//         */
//        function get_instances (callback)
//        {
//            var xmlhttp;
//
//            xmlhttp = createCORSRequest ("GET", "https://aws.amazon.com/ec2/instance-types");
//            xmlhttp.onreadystatechange = function ()
//            {
//                if (4 == xmlhttp.readyState)
//                {
//                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
//                        callback (null, xmlhttp.responseText);
//                    else
//                        callback ("status: " + xmlhttp.status, null);
//                }
//            };
//            xmlhttp.send ();
//        }

        function show_types (data)
        {
            var el = document.createElement ("html");
            el.innerHTML = data;
            var table = el.getElementsByTagName ("table")[0];
            var rows = table.getElementsByTagName ("tr");
            // row zero has the column headers
            var header = rows.item (0).cells;
            var headers = [];
            for (var i = 0; i < header.length; i++)
                headers.push (header.item (i).innerHTML.toString ());

            function s (element) { return (-1 != element.indexOf (this)); }
            var type = headers.findIndex (s.bind ("Instance Type"));
            var cpu = headers.findIndex (s.bind ("CPU"));
            var memory = headers.findIndex (s.bind ("Memory"));
            var storage = headers.findIndex (s.bind ("Storage"));
            function disk (string)
            {
                var index;
                var count;
                var size;

                if (-1 == (index = string.indexOf ("x")))
                    count = 1;
                else
                {
                    count = Number (string.substring (0, index).trim ());
                    string = string.substring (index + 1).trim ();
                }
                var characters = string.split ("");
                var numbers = characters.filter (function (c) { return (c >= '0' && c <= '9'); });
                size = count * Number (numbers.join (""));

                return (size);
            }
            instances = [];
            for (var j = 1; j < rows.length; j++)
            {
                var cells = rows.item (j).cells;
                instances.push (
                    {
                        type: cells.item (type).textContent.toString (),
                        cpu: Number (cells.item (cpu).textContent.toString ().trim ()),
                        memory: Number (cells.item (memory).textContent.toString ().trim ()),
                        storage: disk (cells.item (storage).textContent.toString ().trim ()),
                    }
                );
            }

            function wrap (instance)
            {
                return ("<option value=\"" + instance.type + "\">" + instance.type + " - CPU: " + instance.cpu + " Memory: " + instance.memory + " Strorage: " + instance.storage + "</option>")
            }
            function big (instance)
            {
                return (instance.storage >= 30)
            }
            var options = instances.filter (big).map (wrap).join ("\n");
            document.getElementById ("master").innerHTML = options;
            document.getElementById ("slaves").innerHTML = options;
        }

        function get_instances (callback)
        {
            var xmlhttp = new XMLHttpRequest ();
            xmlhttp.open ("GET", "templates/amazon.mst", true);
            xmlhttp.setRequestHeader ("Accept", "text/html");
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                {
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status || (0 == xmlhttp.status))
                        callback (null, xmlhttp.responseText);
                    else
                        callback ("status: " + xmlhttp.status, null);
                }
            };
            xmlhttp.send ();
        }

        function lookup_instance (type)
        {
            var found = null;
            function find (instance)
            {
                if (instance.type == type)
                    found = instance;
            }
            if (null != instances)
                instances.forEach (find);
            return (found);
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:instancetype
         */
        function init (event)
        {
            if (null == instances)
            {
                get_instances (function (err, data) {
                    if (err) console.log (err); // an error occurred
                    else     show_types (data); // successful response
                  });
            }
        }

        function term (event)
        {
            this.master = lookup_instance (document.getElementById ("master").value);
            this.slaves = lookup_instance (document.getElementById ("slaves").value);
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "instancetypes",
                            title: "Instance Type",
                            template: "templates/instancetype.mst",
                            hooks:
                            [
                            ],
                            transitions:
                            {
                                enter: init,
                                leave: term
                            }
                        }
                    );
                }
            }
        );
    }
);