/**
 * @fileOverview Cluster creation step of the ECS provisioning wizard.
 * @name cluster
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get/create a cluster.
     * @description Gets the clusters the user has and choose or create a cluster.
     * @name cluster
     * @exports cluster
     * @version 1.0
     */
    function ()
    {
        var clusters = null;

        function show_clusters (data)
        {
            function active (cluster)
            {
                return (cluster.status == "ACTIVE");
            }
            function extract (cluster)
            {
                return ({"clusterArn": cluster.clusterArn, "clusterName": cluster.clusterName, "registeredContainerInstancesCount": cluster.registeredContainerInstancesCount });
            }
            clusters = data.clusters.filter (active).map (extract)
            function wrap (cluster)
            {
                return ("<option value=\"" + cluster.clusterName + "\">" + cluster.clusterName + " (" + cluster.clusterArn + ")" + "</option>")
            }
            var options = clusters.map (wrap).join ("\n");
            document.getElementById ("cluster_list").innerHTML = options;
            // if there is only one cluster, select it
            if (1 == clusters.length)
            {
                document.getElementById ("cluster").value = clusters[0].clusterName;
                change_cluster (null);
                this.cluster = clusters[0];
            }
        }

        function describe_clusters (data)
        {
            var ecs = new AWS.ECS ();
            ecs.describeClusters ({ clusters: data.clusterArns }, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     show_clusters (data);           // successful response
            });
        }

        function lookup_cluster ()
        {
            var name = document.getElementById ("cluster").value;
            var found = null;
            function find (cluster)
            {
                if (cluster.clusterName == name)
                    found = cluster;
            }
            if (null != clusters)
                clusters.forEach (find);
            return (found);
        }

        function change_cluster (event)
        {
            var cluster = lookup_cluster ();
            var name = document.getElementById ("cluster").value;
            var creatable = ((null != cluster) && ("" != name));
            document.getElementById ("create_cluster").disabled = creatable;
        }

        function create_cluster (event)
        {
            var name = document.getElementById ("cluster").value;
            var ecs = new AWS.ECS ();
            ecs.createCluster ({ clusterName: name }, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     init (null); // refresh
            });
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:cluster
         */
        function init (event)
        {
            if ((null == clusters) || (null == event))
            {
                var ecs = new AWS.ECS ();
                ecs.listClusters ({}, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else     describe_clusters (data);     // successful response
                });
            }
        }

        function term (event)
        {
            this.cluster = lookup_cluster ();
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "clusters",
                            title: "Cluster",
                            template: "templates/cluster.mst",
                            hooks:
                            [
                                { id: "cluster", event: "change", code: change_cluster },
                                { id: "cluster", event: "input", code: change_cluster },
                                { id : "create_cluster", event : "click", code : create_cluster }
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