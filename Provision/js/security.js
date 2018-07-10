/**
 * @fileOverview Security group creation step of the ECS provisioning wizard.
 * @name security
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get/create security groups for master and worker.
     * @description Gets the security groups the user has and choose or create one each for master and worker instances.
     * @name security
     * @exports security
     * @version 1.0
     */
    function ()
    {
        var groups = null;

        function show_groups (data)
        {
            function wrap (group)
            {
                return ("<option value=\"" + group.GroupName + "\">" + group.GroupName + " (" + group.GroupId + " " + group.Description +")" + "</option>")
            }
            groups = data.SecurityGroups;
            var options = groups.map (wrap).join ("\n");
            document.getElementById ("master_security_group_list").innerHTML = options;
            document.getElementById ("worker_security_group_list").innerHTML = options;
            document.getElementById ("cassandra_security_group_list").innerHTML = options;
            change_group (null)
        }

        function lookup_group (name)
        {
            var found = null;
            function find (group)
            {
                if (group.GroupName == name)
                    found = group;
            }
            if (null != groups)
                groups.forEach (find);
            return (found);
        }

        function change_group (event)
        {
            var master_group_name = document.getElementById ("master_security_group").value;
            var worker_group_name = document.getElementById ("worker_security_group").value;
            var cassandra_group_name = document.getElementById ("cassandra_security_group").value;
            var master_group = lookup_group (master_group_name)
            var worker_group = lookup_group (worker_group_name)
            var cassandra_group = lookup_group (cassandra_group_name)
            var needed = ((null == master_group) || (null == worker_group) || (null == cassandra_group));
            var valid_names = (("" != master_group_name) && ("" != worker_group_name) && ("" != cassandra_group_name));
            var creatable = (needed && valid_names)
            document.getElementById ("create_security_groups").disabled = !creatable;
        }

        function add_rules (master_group, worker_group, cassandra_group, authorized_ip)
        {
            authorized_ip = authorized_ip + "/32"
            var pairs = [
                {
                    GroupName: master_group.GroupName,
                    GroupId: master_group.GroupId
                },
                {
                    GroupName: worker_group.GroupName,
                    GroupId: worker_group.GroupId
                },
                {
                    GroupName: cassandra_group.GroupName,
                    GroupId: cassandra_group.GroupId
                }
            ];
            var master_rules =
            {
                GroupName: master_group.GroupName,
                GroupId: master_group.GroupId,
                IpPermissions:
                [
                    // see https://spark.apache.org/docs/latest/security.html#configuring-ports-for-network-security
                    { IpProtocol: "icmp", FromPort:    -1, ToPort:    -1, UserIdGroupPairs: pairs }, // all icmp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all tcp traffic between master, workers and cassandra
                    { IpProtocol:  "udp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all udp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:    22, ToPort:    22, IpRanges: [ { CidrIp: authorized_ip } ] }, // ssh
                    { IpProtocol:  "tcp", FromPort:  8080, ToPort:  8080, IpRanges: [ { CidrIp: authorized_ip } ] }, // Standalone Master Web UI
                    { IpProtocol:  "tcp", FromPort:  7077, ToPort:  7077, IpRanges: [ { CidrIp: authorized_ip } ] }, // Driver to Standalone Master
                    { IpProtocol:  "tcp", FromPort:  6066, ToPort:  6066, IpRanges: [ { CidrIp: authorized_ip } ] }, // Standalone Master REST port (spark.master.rest.port)
                    { IpProtocol:  "tcp", FromPort:  4040, ToPort:  4040, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cluster Manager Web UI
                    { IpProtocol:  "tcp", FromPort: 18080, ToPort: 18080, IpRanges: [ { CidrIp: authorized_ip } ] }, // History Server
                    { IpProtocol:  "tcp", FromPort:  8787, ToPort:  8787, IpRanges: [ { CidrIp: authorized_ip } ] }, // Rstudio
                    { IpProtocol:  "tcp", FromPort:  8088, ToPort:  8088, IpRanges: [ { CidrIp: authorized_ip } ] }, // Yarn Resource Manager

                    // https://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-hdfs/hdfs-default.xml
                    { IpProtocol:  "tcp", FromPort: 50070, ToPort: 50070, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Namenode Web UI
                    { IpProtocol:  "tcp", FromPort: 50075, ToPort: 50075, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Datanode Web UI
                    { IpProtocol:  "tcp", FromPort: 50090, ToPort: 50090, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Secondary Namenode Web UI
                    { IpProtocol:  "tcp", FromPort: 50105, ToPort: 50105, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Backup Node Web UI
                    { IpProtocol:  "tcp", FromPort:  8020, ToPort:  8020, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Namenode IPC
                    { IpProtocol:  "tcp", FromPort: 50010, ToPort: 50010, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Datanode data transfer
                    { IpProtocol:  "tcp", FromPort: 50020, ToPort: 50020, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Datanode IPC
                    { IpProtocol:  "tcp", FromPort: 50100, ToPort: 50100, IpRanges: [ { CidrIp: authorized_ip } ] }  // DFS Backup Node data transfer
                ]
            };

            var worker_rules =
            {
                GroupName: worker_group.GroupName,
                GroupId: worker_group.GroupId,
                IpPermissions:
                [
                    // see https://spark.apache.org/docs/latest/security.html#configuring-ports-for-network-security
                    { IpProtocol: "icmp", FromPort:    -1, ToPort:    -1, UserIdGroupPairs: pairs }, // all icmp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all tcp traffic between master, workers and cassandra
                    { IpProtocol:  "udp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all udp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:    22, ToPort:    22, IpRanges: [ { CidrIp: authorized_ip } ] }, // ssh
                    { IpProtocol:  "tcp", FromPort:  8081, ToPort:  8081, IpRanges: [ { CidrIp: authorized_ip } ] }, // Standalone Worker Web UI

                    // https://hadoop.apache.org/docs/r2.7.3/hadoop-project-dist/hadoop-hdfs/hdfs-default.xml
                    { IpProtocol:  "tcp", FromPort:  8020, ToPort:  8020, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Namenode IPC
                    { IpProtocol:  "tcp", FromPort: 50010, ToPort: 50010, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Datanode data transfer
                    { IpProtocol:  "tcp", FromPort: 50020, ToPort: 50020, IpRanges: [ { CidrIp: authorized_ip } ] }, // DFS Datanode IPC
                    { IpProtocol:  "tcp", FromPort: 50100, ToPort: 50100, IpRanges: [ { CidrIp: authorized_ip } ] }  // DFS Backup Node data transfer
                ]
            };

            var cassandra_rules =
            {
                GroupName: cassandra_group.GroupName,
                GroupId: cassandra_group.GroupId,
                IpPermissions:
                [
                    // see https://spark.apache.org/docs/latest/security.html#configuring-ports-for-network-security
                    { IpProtocol: "icmp", FromPort:    -1, ToPort:    -1, UserIdGroupPairs: pairs }, // all icmp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all tcp traffic between master, workers and cassandra
                    { IpProtocol:  "udp", FromPort:     0, ToPort: 65535, UserIdGroupPairs: pairs }, // all udp traffic between master, workers and cassandra
                    { IpProtocol:  "tcp", FromPort:    22, ToPort:    22, IpRanges: [ { CidrIp: authorized_ip } ] }, // ssh
                    // Cassandra inter-node ports:
                    { IpProtocol:  "tcp", FromPort:  7000, ToPort:  7000, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cassandra inter-node cluster communication
                    { IpProtocol:  "tcp", FromPort:  7001, ToPort:  7001, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cassandra SSL inter-node cluster communication
                    { IpProtocol:  "tcp", FromPort:  7199, ToPort:  7199, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cassandra JMX monitoring port
                    // Cassandra client ports
                    { IpProtocol:  "tcp", FromPort:  9042, ToPort:  9042, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cassandra client port
                    { IpProtocol:  "tcp", FromPort:  9160, ToPort:  9160, IpRanges: [ { CidrIp: authorized_ip } ] }, // Cassandra client port (Thrift)
                    { IpProtocol:  "tcp", FromPort:  9142, ToPort:  9142, IpRanges: [ { CidrIp: authorized_ip } ] }  // Default for native_transport_port_ssl, useful when both encrypted and unencrypted connections are required
                ]
            };

            var ec2 = new AWS.EC2 ();
            var count = 0;
            function gather (data)
            {
                count++;
                if (count >= 3)
                    init (null); // refresh
            }
            ec2. authorizeSecurityGroupIngress (master_rules, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     gather (data);
            });
            ec2. authorizeSecurityGroupIngress (worker_rules, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     gather (data);
            });
            ec2. authorizeSecurityGroupIngress (cassandra_rules, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     gather (data);
            });
        }

        function create_security_groups (event)
        {
            var master_group_name = document.getElementById ("master_security_group").value;
            var worker_group_name = document.getElementById ("worker_security_group").value;
            var cassandra_group_name = document.getElementById ("cassandra_security_group").value;
            var authorized_ip = document.getElementById ("authorized_ip").value.trim ();
            if ("" == authorized_ip)
                authorized_ip = "0.0.0.0/0";

            var master_group = lookup_group (master_group_name)
            var worker_group = lookup_group (worker_group_name)
            var cassandra_group = lookup_group (cassandra_group_name)
            var needed = [];
            if (null == master_group)
                needed.push (master_group_name);
            if (null == worker_group)
                needed.push (worker_group_name);
            if (null == cassandra_group)
                needed.push (cassandra_group_name);
            var count = 0;
            var ec2 = new AWS.EC2 ();
            if (needed.length != 0)
            {
                function gather (data, group)
                {
                    count++;
                    group.GroupId = data.GroupId;
                    if (count == needed.length)
                        add_rules (master_group, worker_group, cassandra_group, authorized_ip);
                }
                if (null == master_group)
                {
                    var params =
                    {
                        GroupName: master_group_name, 
                        Description: "Security group for master instances", 
                    };
                    master_group =
                    {
                        GroupName: params.GroupName, 
                        Description: params.Description, 
                        IpPermissionsEgress: [],
                        IpPermissions: []
                    }
                    ec2.createSecurityGroup (params, function (err, data) {
                        if (err) console.log (err, err.stack); // an error occurred
                        else     gather (data, master_group);
                    });
                }
                if (null == worker_group)
                {
                    var params =
                    {
                        GroupName: worker_group_name, 
                        Description: "Security group for worker instances", 
                    };
                    worker_group =
                    {
                        GroupName: params.GroupName, 
                        Description: params.Description, 
                        IpPermissionsEgress: [],
                        IpPermissions: []
                    }
                    ec2.createSecurityGroup (params, function (err, data) {
                        if (err) console.log (err, err.stack); // an error occurred
                        else     gather (data, worker_group);
                    });
                }
                if (null == cassandra_group)
                {
                    var params =
                    {
                        GroupName: cassandra_group_name,
                        Description: "Security group for cassandra instances",
                    };
                    cassandra_group =
                    {
                        GroupName: params.GroupName,
                        Description: params.Description,
                        IpPermissionsEgress: [],
                        IpPermissions: []
                    }
                    ec2.createSecurityGroup (params, function (err, data) {
                        if (err) console.log (err, err.stack); // an error occurred
                        else     gather (data, cassandra_group);
                    });
                }
            }

        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:security
         */
        function init (event)
        {
            if ((null == groups) || (null == event))
            {
                var ec2 = new AWS.EC2 ();
                ec2.describeSecurityGroups ({}, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else     show_groups (data);     // successful response
                });
            }
        }

        function term (event)
        {
            this.master_security_group = lookup_group (document.getElementById ("master_security_group").value);
            this.worker_security_group = lookup_group (document.getElementById ("worker_security_group").value);
            this.cassandra_security_group = lookup_group (document.getElementById ("cassandra_security_group").value);
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "security",
                            title: "Security Groups",
                            template: "templates/security.mst",
                            hooks:
                            [
                                { id: "master_security_group", event: "change", code: change_group },
                                { id: "master_security_group", event: "input", code: change_group },
                                { id: "worker_security_group", event: "change", code: change_group },
                                { id: "worker_security_group", event: "input", code: change_group },
                                { id: "cassandra_security_group", event: "change", code: change_group },
                                { id: "cassandra_security_group", event: "input", code: change_group },
                                { id: "create_security_groups", event : "click", code : create_security_groups }
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