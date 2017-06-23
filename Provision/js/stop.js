/**
 * @fileOverview Stop cluster step of the ECS provisioning wizard.
 * @name stop
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Stop master and worker instances.
     * @description Executes terminateInstances for master and workers.
     * @name stop
     * @exports stop
     * @version 1.0
     */
    function ()
    {
        function stop ()
        {
            var details = this;
            var params =
            {
                cluster: details.cluster.clusterName
            };
            var ecs = new AWS.ECS ();
            ecs.listContainerInstances (params, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else
                {
                    var arns = data.containerInstanceArns;
                    if (0 < arns.length)
                    {
                        var params =
                        {
                            cluster: details.cluster.clusterName,
                            containerInstances: arns
                        };
                        ecs.describeContainerInstances (params, function (err, data) {
                            if (err) console.log (err, err.stack); // an error occurred
                            else
                            {
                                var instances = data.containerInstances.map (function (x) { return (x.ec2InstanceId); });
                                var params =
                                {
                                    InstanceIds: instances
                                };
                                var ec2 = new AWS.EC2 ();
                                ec2.terminateInstances (params, function (err, data) {
                                    if (err) console.log (err, err.stack); // an error occurred
                                    else
                                    {
                                        // ?? disable button or wait for instances to shut down?
                                    }
                                });
                            }
                        });
                    }
                }
            });
        }

        /**
         * Form initialization function.
         * 
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:stop
         */
        function init (event)
        {
        }

        function term (event)
        {
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "stop",
                            title: "Stop cluster",
                            template: "templates/stop.mst",
                            hooks:
                            [
                                { id : "stop_cluster", event : "click", code : stop }
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