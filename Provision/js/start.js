/**
 * @fileOverview Start cluster step of the ECS provisioning wizard.
 * @name start
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Start master, worker and Cassandra instances.
     * @description Executes requestSpotInstances for Spark master, workers and Cassandra.
     * @name start
     * @exports start
     * @version 1.0
     */
    function ()
    {
        var details;

        function adjust_disk (details, instance)
        {
            var mappings = JSON.parse (JSON.stringify (details.image.BlockDeviceMappings));
            function sum (size, disk)
            {
                return (size + disk.Ebs.VolumeSize);
            }
            var needed = mappings.reduce (sum, 0);
            var extra = instance.storage - needed;
            function add (disk)
            {
                if (disk.DeviceName == "/dev/xvdcz")
                    disk.Ebs.VolumeSize = disk.Ebs.VolumeSize + extra;
                return (disk);
            }
            mappings = mappings.map (add);
            // remove Encrypted parameter to avoid: failed: Parameter encrypted is invalid. You cannot specify the encrypted flag if specifying a snapshot id in a block device mapping.
            mappings = mappings.map (disk => { if (disk.Ebs.SnapshotId) delete disk.Ebs.Encrypted; return (disk); } );
            return (mappings);
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

        function requestSpotInstances (request)
        {
            var ret = new Promise (
                function (resolve, reject)
                {
                    var ec2 = new AWS.EC2 ();
                    ec2.requestSpotInstances (request,
                        function (err, data)
                        {
                            if (err)
                                reject (err); // an error occurred
                            else
                                resolve (data);
                        }
                    );
                }
            );
            return (ret);
        }

        function listContainerInstances ()
        {
            var ret = new Promise (
                function (resolve, reject)
                {
                    var ecs = new AWS.ECS ();
                    ecs.listContainerInstances ({ "cluster": details.cluster.clusterName },
                        function (err, data)
                        {
                            if (err)
                                reject (err); // an error occurred
                            else
                                resolve (data);
                        }
                    );
                }
            );
            return (ret);
        }

        function describeContainerInstances (data)
        {
            var arns = data.containerInstanceArns;
            var ret = (0 == arns.length) ? Promise.resolve ({ "containerInstances": [] } ) :
                new Promise (
                    function (resolve, reject)
                    {
                        var ecs = new AWS.ECS ();
                        ecs.describeContainerInstances ({ "cluster": details.cluster.clusterName, "containerInstances": arns },
                            function (err, data)
                            {
                                if (err)
                                    reject (err); // an error occurred
                                else
                                    resolve (data);
                            }
                        );
                    }
                );
            return (ret);
        }

        function count_seeds (data)
        {
            return (data.containerInstances.filter (ecs_instance => node_type (ecs_instance) == "cassandra_seed").length);
        }

        function sleep (ms)
        {
            return new Promise ((resolve) => setTimeout (resolve, ms));
        }

        function waitForSeeds (count, max_times)
        {
            return (
                listContainerInstances ()
                .then (describeContainerInstances)
                .then (count_seeds)
                .then ((number) =>
                    {
                        if (count != number)
                        {
                            if (max_times > 0)
                                return (sleep (1000.0).then (waitForSeeds (count, max_times - 1)));
                            else
                                return (Promise.reject ("maximum iterations reached while waiting for Cassandra seed nodes"));
                        }
                        else
                            return (number);
                    }
                )
            );
        }

        function start ()
        {
            var master_startup_script =
`Content-Type: multipart/mixed; boundary="==BOUNDARY=="
MIME-Version: 1.0

--==BOUNDARY==
Content-Type: text/text/x-shellscript; charset="us-ascii"

#!/bin/bash
# specify the cluster that the container instance should register into
cluster=${ details.cluster.clusterName }

# write the cluster configuration variables to the ecs.config file
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config
echo ECS_INSTANCE_ATTRIBUTES={\\"node_type\\": \\"master\\"} >> /etc/ecs/ecs.config

# install the ftp, unzip and the jq JSON parser
yum install -y ftp unzip jq

# install a modern version of the aws-cli package (yum installs an old one)
pushd /tmp
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
unzip awscli-bundle.zip
rm  awscli-bundle.zip
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws
rm -rf awscli-bundle
popd

--==BOUNDARY==
Content-Type: text/text/upstart-job; charset="us-ascii"

#upstart-job
description "Amazon EC2 Container Service (start task on instance boot)"
author "Derrick Oswald"
start on started ecs

script
    exec 2>>/var/log/ecs/ecs-start-task.log
    set -x
    until curl -s http://localhost:51678/v1/metadata
    do
        sleep 1
    done

    # get the container instance ARN, cluster name and AWS region from instance metadata
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')

    # get the master public DNS name
    master_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'"' '{print $2}')
    master_dns_name=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')
    master_ip_address=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateIpAddress' | tr -d '"')

    # get the seed internal DNS names
    until [ -n "$seed_ecs_arns" ]; do seed_ecs_arns=$(aws ecs list-container-instances --cluster $cluster --region $region --filter 'attribute:node_type == cassandra_seed' | jq --compact-output '.containerInstanceArns|.[]' | awk -F'"' '{print $2 }' | awk -F/ '{printf "%s ",$NF}'); sleep 1; done;
    seed_ec2_arns=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $seed_ecs_arns | jq '.containerInstances|.[]|.ec2InstanceId' | awk -F'"' '{printf "%s ",$2}')
    seed_dns_names=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateDnsName')
    seed_ip_addresses=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateIpAddress' | tr -d '"')

    # make the overrides JSON file
    cat <<-EOF >/tmp/overrides.json
	{
		"containerOverrides":
		[
			{
				"name": "master",
				"command": ["start-spark", "master"],
				"environment":
				[
					{
						"name": "SPARK_PUBLIC_DNS",
						"value": $master_dns_name
					}
				]
			}
		]
	}
	EOF

    # add the local IP address for master and seeds to /etc/hosts
    echo $master_ip_address	master >> /etc/hosts
    readarray -t a < <( echo $seed_ip_addresses )
    for i in "$\{!a[@]}"; do echo "$\{a[$i]}" cassandra_seed_$i >> /etc/hosts; done

    # specify the task definition to run at launch
    task_definition=${ details.master_taskdefinition.family }

    # run the AWS CLI start-task command to start your task on this container instance
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json
end script
--==BOUNDARY==--`;

        var worker_startup_script =
`Content-Type: multipart/mixed; boundary="==BOUNDARY=="
MIME-Version: 1.0

--==BOUNDARY==
Content-Type: text/text/x-shellscript; charset="us-ascii"

#!/bin/bash
# specify the cluster that the container instance should register into
cluster=${ details.cluster.clusterName }

# write the cluster configuration variables to the ecs.config file
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config
echo ECS_INSTANCE_ATTRIBUTES={\\"node_type\\": \\"worker\\"} >> /etc/ecs/ecs.config

# install the ftp, unzip and the jq JSON parser
yum install -y ftp unzip jq

# install a modern version of the aws-cli package (yum installs an old one)
pushd /tmp
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
unzip awscli-bundle.zip
rm  awscli-bundle.zip
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws
rm -rf awscli-bundle
popd

--==BOUNDARY==
Content-Type: text/text/upstart-job; charset="us-ascii"

#upstart-job
description "Amazon EC2 Container Service (start task on instance boot)"
author "Derrick Oswald"
start on started ecs

script
    exec 2>>/var/log/ecs/ecs-start-task.log
    set -x
    until curl -s http://localhost:51678/v1/metadata
    do
        sleep 1
    done

    # get the container instance ARN, cluster name and AWS region from instance metadata
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')

    # get the master internal DNS name
    until [ -n "$master_ecs_arn" ]; do master_ecs_arn=$(aws ecs list-container-instances --cluster $cluster --region $region --filter 'attribute:node_type == master' | jq '.containerInstanceArns|.[0]' | awk -F'"' '{print $2}' | awk -F/ '{print $NF}'); sleep 1; done;
    master_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $master_ecs_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'"' '{print $2}')
    master_dns_name=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateDnsName')
    master_ip_address=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateIpAddress' | tr -d '"')

    # get the worker public DNS name
    worker_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'"' '{print $2}')
    worker_dns_name=$(aws ec2 describe-instances --region $region --instance-id $worker_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')
    worker_ip_address=$(aws ec2 describe-instances --region $region --instance-id $worker_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateIpAddress' | tr -d '"')

    # get the seed internal DNS names
    until [ -n "$seed_ecs_arns" ]; do seed_ecs_arns=$(aws ecs list-container-instances --cluster $cluster --region $region --filter 'attribute:node_type == cassandra_seed' | jq --compact-output '.containerInstanceArns|.[]' | awk -F'"' '{print $2 }' | awk -F/ '{printf "%s ",$NF}'); sleep 1; done;
    seed_ec2_arns=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $seed_ecs_arns | jq '.containerInstances|.[]|.ec2InstanceId' | awk -F'"' '{printf "%s ",$2}')
    seed_dns_names=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateDnsName')
    seed_ip_addresses=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateIpAddress' | tr -d '"')

    # make the overrides JSON file
    cat <<-EOF >/tmp/overrides.json
	{
		"containerOverrides":
		[
			{
				"name": "worker",
				"command": ["start-spark", "worker", $master_dns_name],
				"environment":
				[
					{
						"name": "SPARK_PUBLIC_DNS",
						"value": $worker_dns_name
					}
				]
			}
		]
	}
	EOF

    # add the local IP addresses for master, worker and seeds to /etc/hosts
    echo $master_ip_address	master >> /etc/hosts
    echo $worker_ip_address	worker >> /etc/hosts
    readarray -t a < <( echo $seed_ip_addresses )
    for i in "$\{!a[@]}"; do echo "$\{a[$i]}" cassandra_seed_$i >> /etc/hosts; done

    # specify the task definition to run at launch
    task_definition=${ details.worker_taskdefinition.family }

    # run the AWS CLI start-task command to start your task on this container instance
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json
end script
--==BOUNDARY==--`;

        var cassandra_seed_startup_script =
`Content-Type: multipart/mixed; boundary="==BOUNDARY=="
MIME-Version: 1.0

--==BOUNDARY==
Content-Type: text/text/x-shellscript; charset="us-ascii"

#!/bin/bash
# specify the cluster that the container instance should register into
cluster=${ details.cluster.clusterName }

# write the cluster configuration variables to the ecs.config file
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config
echo ECS_INSTANCE_ATTRIBUTES={\\"node_type\\": \\"cassandra_seed\\"} >> /etc/ecs/ecs.config

# install the ftp, unzip and the jq JSON parser
yum install -y ftp unzip jq

# install a modern version of the aws-cli package (yum installs an old one)
pushd /tmp
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
unzip awscli-bundle.zip
rm  awscli-bundle.zip
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws
rm -rf awscli-bundle
popd

--==BOUNDARY==
Content-Type: text/text/upstart-job; charset="us-ascii"

#upstart-job
description "Amazon EC2 Container Service (start task on instance boot)"
author "Derrick Oswald"
start on started ecs

script
    exec 2>>/var/log/ecs/ecs-start-task.log
    set -x
    until curl -s http://localhost:51678/v1/metadata
    do
        sleep 1
    done

    # get the container instance ARN, cluster name and AWS region from instance metadata
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')

    # get the seed ip address
    node_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'"' '{print $2}')
    node_dns_name=$(aws ec2 describe-instances --region $region --instance-id $node_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')
    node_ip_address_string=$(aws ec2 describe-instances --region $region --instance-id $node_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateIpAddress')
    node_ip_address=$(echo $node_ip_address_string | tr -d '"')

    # make the overrides JSON file
    cat <<-EOF >/tmp/overrides.json
	{
		"containerOverrides":
		[
			{
				"name": "cassandra",
				"command": ["cassandra", "-f"],
				"environment":
				[
					{
						"name": "CASSANDRA_CLUSTER_NAME",
						"value": "cassandra"
					},
					{
						"name": "CASSANDRA_LISTEN_ADDRESS",
						"value": $node_ip_address_string
					},
					{
						"name": "CASSANDRA_BROADCAST_ADDRESS",
						"value": $node_ip_address_string
					},
					{
						"name": "CASSANDRA_BROADCAST_RPC_ADDRESS",
						"value": $node_ip_address_string
					}
				]
			}
		]
	}
	EOF

    # add the local IP address for the seed to /etc/hosts
    echo $node_ip_address	cassandra >> /etc/hosts

    # specify the task definition to run at launch
    task_definition=${ details.cassandra_taskdefinition.family }

    # run the AWS CLI start-task command to start your task on this container instance
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json
end script
--==BOUNDARY==--`;

        var cassandra_node_startup_script =
`Content-Type: multipart/mixed; boundary="==BOUNDARY=="
MIME-Version: 1.0

--==BOUNDARY==
Content-Type: text/text/x-shellscript; charset="us-ascii"

#!/bin/bash
# specify the cluster that the container instance should register into
cluster=${ details.cluster.clusterName }

# write the cluster configuration variables to the ecs.config file
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config
echo ECS_INSTANCE_ATTRIBUTES={\\"node_type\\": \\"cassandra_node\\"} >> /etc/ecs/ecs.config

# install the ftp, unzip and the jq JSON parser
yum install -y ftp unzip jq

# install a modern version of the aws-cli package (yum installs an old one)
pushd /tmp
curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
unzip awscli-bundle.zip
rm  awscli-bundle.zip
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws
rm -rf awscli-bundle
popd

--==BOUNDARY==
Content-Type: text/text/upstart-job; charset="us-ascii"

#upstart-job
description "Amazon EC2 Container Service (start task on instance boot)"
author "Derrick Oswald"
start on started ecs

script
    exec 2>>/var/log/ecs/ecs-start-task.log
    set -x
    until curl -s http://localhost:51678/v1/metadata
    do
        sleep 1
    done

    # get the container instance ARN, cluster name and AWS region from instance metadata
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')

    # get the seed internal DNS names
    until [ -n "$seed_ecs_arns" ]; do seed_ecs_arns=$(aws ecs list-container-instances --cluster $cluster --region $region --filter 'attribute:node_type == cassandra_seed' | jq --compact-output '.containerInstanceArns|.[]' | awk -F'"' '{print $2 }' | awk -F/ '{printf "%s ",$NF}'); sleep 1; done;
    seed_ec2_arns=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $seed_ecs_arns | jq '.containerInstances|.[]|.ec2InstanceId' | awk -F'"' '{printf "%s ",$2}')
    seed_dns_names=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateDnsName')
    seed_ip_addresses_string=$(aws ec2 describe-instances --region $region --instance-id $seed_ec2_arns | jq '.Reservations|.[0]|.Instances|.[]|.PrivateIpAddress')
    seed_ip_addresses=$(echo $seed_ip_addresses_string | tr -d '"')

    # get the node public DNS name
    node_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'"' '{print $2}')
    node_dns_name=$(aws ec2 describe-instances --region $region --instance-id $node_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')
    node_ip_address_string=$(aws ec2 describe-instances --region $region --instance-id $node_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateIpAddress')
    node_ip_address=$(echo $node_ip_address_string | tr -d '"')

    # make the overrides JSON file
    cat <<-EOF >/tmp/overrides.json
	{
		"containerOverrides":
		[
			{
				"name": "cassandra",
				"command": ["cassandra", "-f"],
				"environment":
				[
					{
						"name": "CASSANDRA_CLUSTER_NAME",
						"value": "cassandra"
					},
					{
						"name": "CASSANDRA_SEEDS",
						"value": $seed_ip_addresses_string
					},
					{
						"name": "CASSANDRA_LISTEN_ADDRESS",
						"value": $node_ip_address_string
					},
					{
						"name": "CASSANDRA_BROADCAST_ADDRESS",
						"value": $node_ip_address_string
					},
					{
						"name": "CASSANDRA_BROADCAST_RPC_ADDRESS",
						"value": $node_ip_address_string
					}
				]
			}
		]
	}
	EOF

    # add the local IP address for seeds and this node to /etc/hosts
    readarray -t a < <( echo $seed_ip_addresses )
    for i in "$\{!a[@]}"; do echo "$\{a[$i]}" cassandra_seed_$i >> /etc/hosts; done
    echo $node_ip_address	cassandra >> /etc/hosts

    # specify the task definition to run at launch
    task_definition=${ details.cassandra_taskdefinition.family }

    # run the AWS CLI start-task command to start your task on this container instance
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json
end script
--==BOUNDARY==--`;

            var master_price = document.getElementById ("master_price").value;
            if ("" == master_price)
                master_price = "0.25";
            var worker_price = document.getElementById ("worker_price").value;
            if ("" == worker_price)
                worker_price = "0.10";
            var workers = Number (document.getElementById ("workers").value);
            if ((0 == workers) || isNaN (workers))
                workers = 1;
            details.worker_count = workers;
            var cassandra_price = document.getElementById ("cassandra_price").value;
            if ("" == cassandra_price)
                cassandra_price = "0.10";
            var cassandras = Number (document.getElementById ("cassandras").value);
            var cassandra_seeds = Number (document.getElementById ("cassandra_seeds").value);
            if (cassandra_seeds >= cassandras)
                cassandra_seeds = cassandras - 1;

            var master_disk = adjust_disk (details, details.master);
            var worker_disk = adjust_disk (details, details.worker);
            var cassandra_disk = adjust_disk (details, details.cassandra);

            var now = new Date ();
            var then = new Date (now.valueOf ());
            then.setDate (then.getDate () + 1); // one day should be OK
            now.setUTCHours (now.getUTCHours () - 1); // account for clock skew
            // Amazon doesn't like milliseconds
            var from = now.toISOString ().split ('.')[0] + "Z";
            var until = then.toISOString ().split ('.')[0] + "Z";

            var master_request =
            {
                //DryRun: true,
                SpotPrice: master_price,
                //ClientToken; "",
                InstanceCount: 1,
                Type: "one-time",
                //ValidFrom: from,
                ValidUntil: until,
                //LaunchGroup: "", 
                //AvailabilityZoneGroup: "", 
                //BlockDurationMinutes: 0, 
                LaunchSpecification:
                {
                    ImageId: details.image.ImageId,
                    KeyName: details.keypair.KeyName,
                    SecurityGroups: [ details.master_security_group.GroupName ],
                    UserData: btoa (master_startup_script),
                    //AddressingType: "",
                    InstanceType: details.master.type,
                    //Placement: {
                    //    AvailabilityZone: "",
                    //    GroupName: "",
                    //    Tenancy: ""
                    //},
                    //KernelId: "",
                    //RamdiskId: "",
                    BlockDeviceMappings: master_disk,
                    //SubnetId: "",
                    //NetworkInterfaces: []
                    IamInstanceProfile: { Arn : details.iam.profileArn },
                    //EbsOptimized: true
                    //Monitoring: { enabled: true }
                    SecurityGroupIds: [ details.master_security_group.GroupId ]
                }
            };

            var worker_request =
            {
                //DryRun: true,
                SpotPrice: worker_price,
                //ClientToken; "",
                InstanceCount: workers,
                Type: "one-time",
                //ValidFrom: from,
                ValidUntil: until,
                //LaunchGroup: "", 
                //AvailabilityZoneGroup: "", 
                //BlockDurationMinutes: 0, 
                LaunchSpecification:
                {
                    ImageId: details.image.ImageId,
                    KeyName: details.keypair.KeyName,
                    SecurityGroups: [ details.worker_security_group.GroupName ],
                    UserData: btoa (worker_startup_script),
                    //AddressingType: "",
                    InstanceType: details.worker.type,
                    //Placement: {
                    //    AvailabilityZone: "",
                    //    GroupName: "",
                    //    Tenancy: ""
                    //},
                    //KernelId: "",
                    //RamdiskId: "",
                    BlockDeviceMappings: worker_disk,
                    //SubnetId: "",
                    //NetworkInterfaces: []
                    IamInstanceProfile: { Arn : details.iam.profileArn },
                    //EbsOptimized: true
                    //Monitoring: { enabled: true }
                    SecurityGroupIds: [ details.worker_security_group.GroupId ]
                }
            };

            var cassandra_seed_request =
            {
                //DryRun: true,
                SpotPrice: cassandra_price,
                //ClientToken; "",
                InstanceCount: cassandra_seeds,
                Type: "one-time",
                //ValidFrom: from,
                ValidUntil: until,
                //LaunchGroup: "",
                //AvailabilityZoneGroup: "",
                //BlockDurationMinutes: 0,
                LaunchSpecification:
                {
                    ImageId: details.image.ImageId,
                    KeyName: details.keypair.KeyName,
                    SecurityGroups: [ details.cassandra_security_group.GroupName ],
                    UserData: btoa (cassandra_seed_startup_script),
                    //AddressingType: "",
                    InstanceType: details.cassandra.type,
                    //Placement: {
                    //    AvailabilityZone: "",
                    //    GroupName: "",
                    //    Tenancy: ""
                    //},
                    //KernelId: "",
                    //RamdiskId: "",
                    BlockDeviceMappings: cassandra_disk,
                    //SubnetId: "",
                    //NetworkInterfaces: []
                    IamInstanceProfile: { Arn : details.iam.profileArn },
                    //EbsOptimized: true
                    //Monitoring: { enabled: true }
                    SecurityGroupIds: [ details.cassandra_security_group.GroupId ]
                }
            };

            var cassandra_node_request =
            {
                //DryRun: true,
                SpotPrice: cassandra_price,
                //ClientToken; "",
                InstanceCount: cassandras - cassandra_seeds,
                Type: "one-time",
                //ValidFrom: from,
                ValidUntil: until,
                //LaunchGroup: "",
                //AvailabilityZoneGroup: "",
                //BlockDurationMinutes: 0,
                LaunchSpecification:
                {
                    ImageId: details.image.ImageId,
                    KeyName: details.keypair.KeyName,
                    SecurityGroups: [ details.cassandra_security_group.GroupName ],
                    UserData: btoa (cassandra_node_startup_script),
                    //AddressingType: "",
                    InstanceType: details.cassandra.type,
                    //Placement: {
                    //    AvailabilityZone: "",
                    //    GroupName: "",
                    //    Tenancy: ""
                    //},
                    //KernelId: "",
                    //RamdiskId: "",
                    BlockDeviceMappings: cassandra_disk,
                    //SubnetId: "",
                    //NetworkInterfaces: []
                    IamInstanceProfile: { Arn : details.iam.profileArn },
                    //EbsOptimized: true
                    //Monitoring: { enabled: true }
                    SecurityGroupIds: [ details.cassandra_security_group.GroupId ]
                }
            };

            var text =
                "Master\n" + JSON.stringify (master_request, null, 4) +
                "\nWorkers\n" + JSON.stringify (worker_request, null, 4) +
                "\nCassandra seed\n" + JSON.stringify (cassandra_seed_request, null, 4) +
                "\nCassandra node\n" + JSON.stringify (cassandra_node_request, null, 4);
            document.getElementById ("wizard_data").innerHTML = text;

            requestSpotInstances (cassandra_seed_request)
                .then ((data) => { details.cassandra_seed_request = data.SpotInstanceRequests; })
                .then (waitForSeeds (cassandra_seeds, 120), )
                .then (requestSpotInstances (cassandra_node_request))
                .then ((data) => { details.cassandra_node_request = data.SpotInstanceRequests; })
                .then (requestSpotInstances (master_request))
                .then ((data) => { details.master_request = data.SpotInstanceRequests; })
                .then (requestSpotInstances (worker_request))
                .then ((data) => { details.worker_request = data.SpotInstanceRequests; });
        }

        /**
         * Form initialization function.
         * 
         * @param {object}
         *            event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:start
         */
        function init (event)
        {
            details = this;
            var text = JSON.stringify (this, null, 4);
            document.getElementById ("wizard_data").innerHTML = text;
            if ("undefined" != typeof (details.costs))
            {
                var margin = 1.5;
                var master = (details.costs.master * margin).toFixed (3);
                var worker = (details.costs.worker * margin).toFixed (3);
                var cassandra = (details.costs.cassandra * margin).toFixed (3);
                document.getElementById ("master_price").value = master.toString ();
                document.getElementById ("worker_price").value = worker.toString ();
                document.getElementById ("cassandra_price").value = cassandra.toString ();
            }
            if ("undefined" != typeof (details.worker_count))
                document.getElementById ("workers").value = details.worker_count.toString ();
            if ("undefined" != typeof (details.cassandra_count))
                document.getElementById ("cassandras").value = details.cassandra_count.toString ();
            document.getElementById ("cassandra_seeds").value = "1";
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
                            id: "start",
                            title: "Start cluster",
                            template: "templates/start.mst",
                            hooks:
                            [
                                { id : "start_cluster", event : "click", code : start }
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