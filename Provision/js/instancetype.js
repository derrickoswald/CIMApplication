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
//            if ("withCredentials" in ret) // "withCredentials" only exists on XMLHTTPRequest2 objects
//            {
//                ret.open (method, url, synchronous);
//                ret.withCredentials = true;
//            }
//            else if (typeof XDomainRequest != "undefined") // IE
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
                var numbers = characters.filter (function (c) { return (c >= "0" && c <= "9"); });
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

            function big (instance)
            {
                return (instance.storage >= 30)
            }
            var biggies = instances.filter (big);
            function wrap (instance)
            {
                return ("<option value=\"" + instance.type + "\">" + instance.type + " - CPU: " + instance.cpu + " Memory: " + instance.memory + " Storage: " + instance.storage + "GB (" + (instance.storage - 30) + "GB available)</option>")
            }
            var options = biggies.map (wrap).join ("\n");
            document.getElementById ("master").innerHTML = options;
            document.getElementById ("worker").innerHTML = options;

            // initialize the charts
            get_price_history (["master_chart", "worker_chart"], biggies[0].type);
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

        function update_chart (chart, name, seriess)
        {
            var ch = $("#" + chart).highcharts ();

            // remove the old
            function remove (series)
            {
                var remove = !series.name.startsWith ("Navigator");
                if (remove)
                    series.remove (false);
                return (!remove);
            }
            function check (acc, val)
            {
                return (acc && val);
            }
            do
            {}
            while (!ch.series.map (remove).reduce (check, true));

            // add the new
            seriess[0].showInNavigator = true;
            function add (series)
            {
                ch.addSeries (series, false);
            }
            seriess.map (add);
            ch.subtitle.update ({ text: name });

            // refresh
            ch.reflow ();
            ch.redraw ();
        }

        function show_prices (chart, data, now)
        {
            var raw = data.SpotPriceHistory.map (format);
            var name = raw[0].type;
            function zone (zones, element)
            {
                if (!zones.includes (element.zone))
                    zones.push (element.zone);
                return (zones);
            }
            var zones = raw.reduce (zone, []);
            function make_series (zone)
            {
                function only_zone (element)
                {
                    return (element.zone == zone);
                }
                function bytime (a, b)
                {
                    return (a.time.valueOf () - b.time.valueOf ());
                }
                function high (element)
                {
                    var price = element.price;
                    return ([element.time.valueOf (), price, price, price, price]);
                }
                var series =
                {
                        type : "line",
                        name: zone,
                        description: "Amazon EC2 spot instance price for " + name + " instance type in zone " + zone,
                        data: raw.filter (only_zone).sort (bytime).map (high),
                        step: true,
                        tooltip: { },
                        turboThreshold: 0
                }
                function tooltip ()
                {
                    var value;
                    if (this.y >= 1.0)
                        value = '$' + this.y.toFixed(2)
                    else
                        value = (100*this.y).toFixed(2) + '¢'
                    return ("<span style='color:" + this.color + "'>\u25CF</span> " + series.name + ": <b>" + value + "</b><br/>");
                }
                var price = series.data[series.data.length -1][1];
                series.data.push ([now.valueOf (), price, price, price, price])
                series.tooltip.pointFormatter = tooltip;
                return (series);
            }

            var seriess = zones.map (make_series);
            update_chart (chart, name, seriess);

            // update the estimated cost
            estimate_costs ();
        }

        function get_price_history (charts, instance_type)
        {
            var now = new Date ();
            var then = new Date (now.valueOf ());
            then.setDate (then.getDate () - 10); // ten days of history
            now.setUTCHours (now.getUTCHours () - 1); // account for clock skew
            // Amazon doesn't like milliseconds
            var to = now.toISOString ().split (".")[0] + "Z";
            var from = then.toISOString ().split (".")[0] + "Z";

            var params =
            {
                StartTime: from,
                EndTime: to,
                InstanceTypes: [ instance_type ],
                ProductDescriptions: [ "Linux/UNIX" ],
            };
            function loading (chart)
            {
                $("#" + chart).highcharts ().showLoading ("Loading price data from AWS...");
            }
            function hide_loading (chart)
            {
                $("#" + chart).highcharts ().hideLoading ();
            }
            charts.map (loading);
            var ec2 = new AWS.EC2 ();
            var prices = { SpotPriceHistory: [] };
            function next ()
            {
                ec2.describeSpotPriceHistory (params, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else
                    {
                        prices.SpotPriceHistory = prices.SpotPriceHistory.concat (data.SpotPriceHistory);
                        if (("undefined" == typeof (data.NextToken)) || (null == data.NextToken) || ("" == data.NextToken))
                        {
                            charts.map (hide_loading);
                            function show (chart)
                            {
                                show_prices (chart, prices, now);
                            }
                            charts.map (show);
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

//        function label_minimum (chart, point, text)
//        {
//            var x = point.plotX + chart.plotLeft;
//            var y = point.plotY + chart.plotTop;
//            var style =
//            {
//                color: "#FFFFFF"
//            };
//            var attributes =
//            {
//                fill: "rgba(0, 0, 0, 0.75)",
//                padding: 8,
//                r: 5,
//                zIndex: 6
//            };
//            chart.renderer.label (text, x - 100, y - 50, "callout", x, y).css (style).attr (attributes).add ();
//        }

        function dollars_cents (cost)
        {
            var value;
            if (cost >= 1.0)
                value = "$" + cost.toFixed (2)
            else
                value = (100 * cost).toFixed (2) + "¢";
            return (value);
        }

        function get_minimum_latest_cost (chart)
        {
            function not_navigator (series)
            {
                return (!series.name.startsWith ("Navigator"));
            }
            function costs (series)
            {
                return (series.points[series.points.length - 1]); // use undocumented points array because data array is zero length
            }
            function miner (acc, val)
            {
                var cost = (val.y < acc.cost) ? val.y : acc.cost;
                return ({ cost: cost, count: acc.count + 1, point: val});
            }
            var cost = chart.series.filter (not_navigator).map (costs).reduce (miner, { cost: Number.MAX_VALUE, count: 0, point: null });
            return (cost);
        }

        function estimate_costs ()
        {
            var master = $("#master_chart").highcharts ();
            var worker = $("#worker_chart").highcharts ();
            var master_costs = get_minimum_latest_cost (master);
            var worker_costs = get_minimum_latest_cost (worker);
            var worker_count = Number (document.getElementById ("worker_count").value);
            if ((master_costs.count > 0) && (worker_costs.count > 0))
            {
//                label_minimum (master, master_costs.point, "last: " + dollars_cents (master_costs.cost));
//                label_minimum (worker, worker_costs.point, "last: " + dollars_cents (worker_costs.cost));
                var cost = master_costs.cost + (worker_costs.cost * worker_count);
                var formula = dollars_cents (master_costs.cost) + " + " + worker_count + " × " + dollars_cents (worker_costs.cost) + " = " + dollars_cents (cost);
                document.getElementById ("cost_estimate").innerHTML = "<h1>Estimated cost: " + formula + "/hr</h1>";
            }
        }

        function change_instance (event)
        {
            var chart = event.target.id + "_chart";
            var instance_type = event.target.value;
            get_price_history ([chart], instance_type);
        }

        function create_chart (container, title)
        {
            $("#" + container).highcharts (
                "StockChart",
                {
                    chart:
                    {
                        zoomType: "x",
                        description: "Amazon EC2 spot instance pricing"
                    },
                    navigator:
                    {
                        name: "Navigator_" + container,
                        height: 20
                    },
                    rangeSelector:
                    {
                        buttons:
                        [
                            {
                                type: "hour",
                                count: 1,
                                text: "Hour"
                            },
                            {
                                type: "hour",
                                count: 4,
                                text: "4hr"
                            },
                            {
                                type: 'day',
                                count: 1,
                                text: 'Day'
                            },
                            {
                                type: 'day',
                                count: 3,
                                text: '3d'
                            },
                            {
                                type: 'day',
                                count: 7,
                                text: 'Week'
                            },
                            {
                                type: 'all',
                                text: 'All'
                            }
                        ],
                        inputEnabled: false
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
                            text: "Price (US$/hr)"
                        },
                        floor: 0,
                        opposite: false
                    },
                    title:
                    {
                        text: title
                    },
                    subtitle:
                    {
                        text: "instance type"
                    },
                    legend:
                    {
                        enabled: true,
                        floating: true,
                        align: "left",
                        verticalAlign: "top"
                    }
                }
            );
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
                Highcharts.setOptions
                ({
                    global: { timezoneOffset: -60, useUTC: true },
                });

                // create the chart
                create_chart ("master_chart", "Master Spot Price");
                create_chart ("worker_chart", "Worker Spot Price");

                get_instances (function (err, data) {
                    if (err) console.log (err); // an error occurred
                    else     show_types (data); // successful response
                  });
            }
        }

        function term (event)
        {
            this.master = lookup_instance (document.getElementById ("master").value);
            this.worker = lookup_instance (document.getElementById ("worker").value);
            this.costs =
            {
                master: get_minimum_latest_cost ($("#master_chart").highcharts ()).cost,
                worker: get_minimum_latest_cost ($("#worker_chart").highcharts ()).cost
            };
            this.worker_count = Number (document.getElementById ("worker_count").value);
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
                                { id: "worker_count", event: "change", code: estimate_costs },
                                { id: "worker_count", event: "input", code: estimate_costs },
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