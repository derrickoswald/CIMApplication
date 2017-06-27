/**
 * @fileOverview Wait for the cluster step of the ECS provisioning wizard.
 * @name wait
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Wait for master and worker instances.
     * @description Waits for master and worker instances to initialize and shows ssh commands and updates Web UI links.
     * @name wait
     * @exports wait
     * @version 1.0
     */
    function ()
    {
        var details = null;
        var attempts = 0;
        var SLEEP_TIME = 2000; // milliseconds between attempts
        var MAX_ATTEMPTS = 30; // at 2 seconds per attempt, this is one minute

        function sleep (ms)
        {
            return new Promise ((resolve) => setTimeout (resolve, ms));
        }

        function node_type (instance)
        {
            function get_type (element)
            {
                return (element.name == "node_type");
            };
            var typ = instance.attributes.find (get_type);
            return (typeof typ === 'undefined' ? "" : typ.value);
        }

        function started ()
        {
            var master = details.master_dns_name;
            var link = document.getElementById ("spark_standalone_ui");
            link.setAttribute ("href", "http://" + master + ":8080");
            var link = document.getElementById ("spark_master_ui");
            link.setAttribute ("href", "http://" + master + ":4040");
            var link = document.getElementById ("hdfs_namenode_ui");
            link.setAttribute ("href", "http://" + master + ":50070");
            var link = document.getElementById ("hdfs_backup_ui");
            link.setAttribute ("href", "http://" + master + ":50105");
            document.getElementById ("spark_shell").innerHTML = "spark-shell --master spark://" + master + ":7077";
            document.getElementById ("spark_submit").innerHTML = "spark-submit --master spark://" + master + ":7077";
            document.getElementById ("sparkR").innerHTML = "sparkR --master spark://" + master + ":7077";
            document.getElementById ("spark_sql").innerHTML = "spark-sql --master spark://" + master + ":7077";
            document.getElementById ("pyspark").innerHTML = "pyspark --master spark://" + master + ":7077";
            document.getElementById ("started").innerHTML = "<strong>Cluster successfully started.</strong>";
        }

        function wait_for_instances ()
        {
            attempts += 1;
            var workers = details.worker_count
            if ((0 == workers) || isNaN (workers))
                workers = 1;
            var waiting_on = workers + 1;
            var ecs = new AWS.ECS ();
            var params =
            {
                cluster: details.cluster.clusterName
            };
            sleep (SLEEP_TIME).then (() =>
            {
                ecs.listContainerInstances (params, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else
                    {
                        var arns = data.containerInstanceArns;
                        var count = arns.length;
                        if (0 < count)
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
                                    ec2.describeInstances (params, function (err, data2) {
                                        if (err) console.log (err, err.stack); // an error occurred
                                        else
                                        {
                                            var text = "";
                                            data2.Reservations.forEach (
                                                function (y)
                                                {
                                                    y.Instances.forEach (
                                                        function (x)
                                                        {
                                                            var ecs_instance = data.containerInstances.find (function (y) { return (x.InstanceId == y.ec2InstanceId); });
                                                            var type = node_type (ecs_instance);
                                                            var dns_name = x.PublicDnsName;
                                                            if (type == "master")
                                                                details.master_dns_name = dns_name;
                                                            text = text + "<p>" + type + ": ssh -i \"~/.ssh/" + details.keypair.KeyName + ".pem\" ec2-user@" + dns_name + "</p>";
                                                        }
                                                    );
                                                }
                                            );
                                            document.getElementById ("ssh_command").innerHTML = text;
                                            if (waiting_on >= count)
                                                started ();
                                            else
                                                if (attempts >= MAX_ATTEMPTS)
                                                    document.getElementById ("started").innerHTML = "<strong>Cluster failed to start.</strong>";
                                                else
                                                    wait_for_instances ();
                                        }
                                    });
                                }
                            });
                        }
                        else
                        {
                            var dots = document.getElementById ("ssh_command").innerHTML + ".";
                            document.getElementById ("ssh_command").innerHTML = dots;
                            if (attempts >= MAX_ATTEMPTS)
                                document.getElementById ("started").innerHTML = "<strong>Cluster failed to start.</strong>";
                            else
                                wait_for_instances ();
                        }
                    }
                });
            });
        }

        /**
         * Form initialization function.
         * 
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:wait
         */
        function init (event)
        {
            details = this;
            attempts = 0;
            wait_for_instances ();
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
                            id: "wait",
                            title: "Wait for cluster",
                            template: "templates/wait.mst",
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