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
     * @summary Get instance types for master and workers.
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
            document.getElementById ("worker").innerHTML = options;
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

        function format (datum)
        {
            return (
                {
                    time: Date.parse (datum.Timestamp),
                    type: datum.InstanceType,
                    price: Number (datum.SpotPrice),
                    zone: datum.AvailabilityZone
                }
            );
        }

        function show_prices (data)
        {
            var raw = data.SpotPriceHistory.map (format);
            var text = JSON.stringify (raw, null, 4);
            document.getElementById ("price_data").innerHTML = text;
            function eu_west_1a (element)
            {
                return (element.zone == "eu-west-1a");
            }
            // need to make array of arrays: [date, open, high, low, close]
            function high (element)
            {
                var price = element.price;
                return ([element.time.valueOf (), price, price, price, price]);
            }
            var series =
            {
                type : 'line',
                name: raw[0].type,
                data: raw.filter (eu_west_1a).map (high),
                tooltip:
                {
                    pointFormat: "<span style='color:{series.color}'>\u25CF</span> {series.name}: <b>US${point.y}</b><br/>",
                    valueDecimals: 2,
                    valueSuffix: ''
                },
                dataGrouping:
                {
                    enabled: false
                }
            };
            var lastDate = series.data[series.data.length - 1][0];
            lastDate = new Date (lastDate.valueOf ());
            lastDate.setDate (lastDate.getDate () + 1);
            series.data.push ([lastDate, null, null, null, null]);

            var chart = $('#chart').highcharts ();
            chart.addSeries (series);
        }

        function get_price_history (instance_type)
        {
            var now = new Date ();
            var then = new Date (now.valueOf ());
            then.setDate (then.getDate () - 7); // one week of history
            now.setUTCHours (now.getUTCHours () - 1); // account for clock skew
            // Amazon doesn't like milliseconds
            var to = now.toISOString ().split ('.')[0] + "Z";
            var from = then.toISOString ().split ('.')[0] + "Z";

            var params =
            {
                //DryRun: true,
                StartTime: from,
                EndTime: to,
                InstanceTypes: [ instance_type ],
                ProductDescriptions: [ "Linux/UNIX" ],
                //Filters: [
                //    {
                //        Name: "",
                //        Values: [
                //            ""
                //        ]
                //    }
                //],
                //AvailabilityZone: "",
                //MaxResults: 0,
                //NextToken: ""
            };
            var chart = $('#chart').highcharts ();
            chart.showLoading ('Loading data from server...');
            var ec2 = new AWS.EC2 ();
            var accumulator = { SpotPriceHistory: [] };
            function next ()
            {
                ec2.describeSpotPriceHistory (params, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else
                    {
                        accumulator.SpotPriceHistory = accumulator.SpotPriceHistory.concat (data.SpotPriceHistory);
                        if (("undefined" == typeof (data.NextToken)) || (null == data.NextToken) || ("" == data.NextToken))
                        {
                            chart.hideLoading ();
                            show_prices (accumulator);
                        }
                        else
                        {
                            params.NextToken = data.NextToken;
                            next ();
                        }
                    }
                });
            }
            next ();
        }

        function change_instance (event)
        {
            var instance_type = event.target.value;
            get_price_history (instance_type);
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

                Highcharts.setOptions
                ({
                    global: { timezoneOffset: -60, useUTC: true },
                });

                // create the chart
                $('#chart').highcharts ('StockChart', {
                    chart:
                    {
                        zoomType: 'x'
                    },

                    scrollbar:
                    {
                        liveRedraw: false
                    },

                    xAxis :
                    {
                        minRange: 3600 * 1000 // one hour
                    },

                    yAxis:
                    {
                        title:
                        {
                            text: 'Price (US$)'
                        },
                        floor: 0
                    },

                    title:
                    {
                        text: 'Spot Price'
                    },

                });
            }
        }

        function term (event)
        {
            this.master = lookup_instance (document.getElementById ("master").value);
            this.worker = lookup_instance (document.getElementById ("worker").value);
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
                                { id: "master", event: "change", code: change_instance },
                                { id: "worker", event: "change", code: change_instance },
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