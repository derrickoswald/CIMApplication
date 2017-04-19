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
     * @summary Start master and slave instances.
     * @description Executes .
     * @name start
     * @exports start
     * @version 1.0
     */
    function ()
    {
        var details;

        function adjust_master_disk ()
        {
            var mappings = details.image.BlockDeviceMappings;
            var master = details.master;
            function sum (size, disk)
            {
                return (size + disk.Ebs.VolumeSize);
            }
            var needed = mappings.reduce (sum, 0);
            var extra = master.storage - needed;
            function add (disk)
            {
                if (disk.DeviceName == "/dev/xvdcz")
                    disk.Ebs.VolumeSize = disk.Ebs.VolumeSize + extra;
                return (disk);
            }
            mappings = mappings.map (add);
            return (mappings);
        }

        function adjust_worker_disk ()
        {
            var mappings = details.image.BlockDeviceMappings;
            var worker = details.worker;
            function sum (size, disk)
            {
                return (size + disk.Ebs.VolumeSize);
            }
            var needed = mappings.reduce (sum, 0);
            var extra = worker.storage - needed;
            function add (disk)
            {
                if (disk.DeviceName == "/dev/xvdcz")
                    disk.Ebs.VolumeSize = disk.Ebs.VolumeSize + extra;
                return (disk);
            }
            mappings = mappings.map (add);
            return (mappings);
        }

        function start ()
        {
            var master_startup_script = "\
Content-Type: multipart/mixed; boundary=\"==BOUNDARY==\"\n\
MIME-Version: 1.0\n\
\n\
--==BOUNDARY==\n\
Content-Type: text/text/x-shellscript; charset=\"us-ascii\"\n\
\n\
#!/bin/bash\n\
# Specify the cluster that the container instance should register into\n\
cluster=" + details.cluster.clusterName + "\n\
\n\
# Write the cluster configuration variables to the ecs.config file\n\
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config\n\
echo ECS_INSTANCE_ATTRIBUTES={\\\"node_type\\\": \\\"master\\\"} >> /etc/ecs/ecs.config\n\
\n\
# Install the ftp, unzip and the jq JSON parser\n\
yum install -y ftp unzip jq\n\
\n\
# Install a modern version of the aws-cli package (yum installs an old one)\n\
pushd /tmp\n\
curl \"https://s3.amazonaws.com/aws-cli/awscli-bundle.zip\" -o \"awscli-bundle.zip\"\n\
unzip awscli-bundle.zip\n\
rm  awscli-bundle.zip\n\
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws\n\
rm -rf awscli-bundle\n\
popd\n\
\n\
--==BOUNDARY==\n\
Content-Type: text/text/upstart-job; charset=\"us-ascii\"\n\
\n\
#upstart-job\n\
description \"Amazon EC2 Container Service (start task on instance boot)\"\n\
author \"Derrick Oswald\"\n\
start on started ecs\n\
\n\
script\n\
    exec 2>>/var/log/ecs/ecs-start-task.log\n\
    set -x\n\
    until curl -s http://localhost:51678/v1/metadata\n\
    do\n\
        sleep 1\n\
    done\n\
\n\
    # Grab the container instance ARN, cluster name and AWS region from instance metadata\n\
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )\n\
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )\n\
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')\n\
\n\
    # Get the master public DNS name\n\
    master_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'\"' '{print $2}')\n\
    master_dns_name=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')  \n\
\n\
    # make the overrides JSON file\n\
    cat <<-EOF >/tmp/overrides.json\n\
	{\n\
		\"containerOverrides\": [\n\
			{\n\
				\"name\": \"sandbox\",\n\
                \"command\": [\"start-spark\", \"master\"],\n\
				\"environment\": [\n\
				{\n\
					\"name\": \"SPARK_PUBLIC_DNS\",\n\
					\"value\": $master_dns_name\n\
				}\n\
			]\n\
			}\n\
		]\n\
	}\n\
	EOF\n\
\n\
    # Specify the task definition to run at launch\n\
    task_definition=" + details.taskdefinition.family + "\n\
\n\
    # Run the AWS CLI start-task command to start your task on this container instance\n\
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json\n\
end script\n\
--==BOUNDARY==--";

            var worker_startup_script = "\
Content-Type: multipart/mixed; boundary=\"==BOUNDARY==\"\n\
MIME-Version: 1.0\n\
\n\
--==BOUNDARY==\n\
Content-Type: text/text/x-shellscript; charset=\"us-ascii\"\n\
\n\
#!/bin/bash\n\
# Specify the cluster that the container instance should register into\n\
cluster=" + details.cluster.clusterName + "\n\
\n\
# Write the cluster configuration variables to the ecs.config file\n\
echo ECS_CLUSTER=$cluster >> /etc/ecs/ecs.config\n\
echo ECS_INSTANCE_ATTRIBUTES={\\\"node_type\\\": \\\"slave\\\"} >> /etc/ecs/ecs.config\n\
\n\
# Install the ftp, unzip and the jq JSON parser\n\
yum install -y ftp unzip jq\n\
\n\
# Install a modern version of the aws-cli package (yum installs an old one)\n\
pushd /tmp\n\
curl \"https://s3.amazonaws.com/aws-cli/awscli-bundle.zip\" -o \"awscli-bundle.zip\"\n\
unzip awscli-bundle.zip\n\
rm  awscli-bundle.zip\n\
./awscli-bundle/install -i /usr/local/aws -b /usr/bin/aws\n\
rm -rf awscli-bundle\n\
popd\n\
\n\
--==BOUNDARY==\n\
Content-Type: text/text/upstart-job; charset=\"us-ascii\"\n\
\n\
#upstart-job\n\
description \"Amazon EC2 Container Service (start task on instance boot)\"\n\
author \"Derrick Oswald\"\n\
start on started ecs\n\
\n\
script\n\
    exec 2>>/var/log/ecs/ecs-start-task.log\n\
    set -x\n\
    until curl -s http://localhost:51678/v1/metadata\n\
    do\n\
        sleep 1\n\
    done\n\
\n\
    # Grab the container instance ARN, cluster name and AWS region from instance metadata\n\
    instance_arn=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F/ '{print $NF}' )\n\
    cluster=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .Cluster' | awk -F/ '{print $NF}' )\n\
    region=$(curl -s http://localhost:51678/v1/metadata | jq -r '. | .ContainerInstanceArn' | awk -F: '{print $4}')\n\
\n\
    # Get the master internal DNS name\n\
    until [ -n '$master_ecs_arn' ]; master_ecs_arn=$(aws ecs list-container-instances --cluster $cluster --region $region --filter 'attribute:node_type == master' | jq '.containerInstanceArns|.[0]' | awk -F'\"' '{print $2}' | awk -F/ '{print $NF}'); sleep 1; done;\n\
    master_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $master_ecs_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'\"' '{print $2}')\n\
    master_dns_name=$(aws ec2 describe-instances --region $region --instance-id $master_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PrivateDnsName')\n\
\n\
    # Get the slave public DNS name\n\
    slave_ec2_arn=$(aws ecs describe-container-instances --cluster $cluster --region $region --container-instances $instance_arn | jq '.containerInstances|.[0]|.ec2InstanceId' | awk -F'\"' '{print $2}')\n\
    slave_dns_name=$(aws ec2 describe-instances --region $region --instance-id $slave_ec2_arn | jq '.Reservations|.[0]|.Instances|.[0]|.PublicDnsName')\n\
\n\
        # make the overrides JSON file\n\
        cat <<-EOF >/tmp/overrides.json\n\
        {\n\
            \"containerOverrides\": [\n\
                {\n\
                    \"name\": \"sandbox\",\n\
                    \"command\": [\"start-spark\", \"worker\", $master_dns_name],\n\
                    \"environment\": [\n\
                    {\n\
                        \"name\": \"SPARK_PUBLIC_DNS\",\n\
                        \"value\": $slave_dns_name\n\
                    }\n\
                ]\n\
                }\n\
            ]\n\
        }\n\
        EOF\n\
\n\
    # Specify the task definition to run at launch\n\
    task_definition=" + details.taskdefinition.family + "\n\
\n\
    # Run the AWS CLI start-task command to start your task on this container instance\n\
    aws ecs start-task --cluster $cluster --task-definition $task_definition --container-instances $instance_arn --started-by $instance_arn --region $region --overrides file:///tmp/overrides.json\n\
end script\n\
--==BOUNDARY==--";

            var master_price = document.getElementById ("master_price").value;
            if ("" == master_price)
                master_price = "0.25";
            var worker_price = document.getElementById ("worker_price").value;
            if ("" == worker_price)
                worker_price = "0.10";
            var worker_count = Number (document.getElementById ("worker_count").value);
            if ((0 == worker_count) || isNaN (worker_count))
                worker_count = 1;

            var master_disk = adjust_master_disk ();
            var worker_disk = adjust_worker_disk ();

            var now = new Date ();
            var then = new Date (now.valueOf ());
            then.setDate (then.getDate () + 1); // one day should be OK

            var spot_feet_request =
            {
                IamFleetRole: details.iam.roleArn,
                AllocationStrategy: "lowestPrice",
                TargetCapacity: 1,
                SpotPrice: master_price,
                ValidFrom: now.toISOString (),
                ValidUntil: then.toISOString (),
                TerminateInstancesWithExpiration: true,
                Type: "request",
                LaunchSpecifications:
                [
                    {
                        ImageId: details.image.ImageId,
                        InstanceType: details.master.type,
                        KeyName: details.keypair.KeyName,
                        SpotPrice: master_price,
                        IamInstanceProfile: { Arn : details.iam.profileArn },
                        BlockDeviceMappings: master_disk,
                        SecurityGroups:
                        [
                            { GroupId: details.master_security_group.GroupId }
                        ],
                        UserData: btoa (master_startup_script)
                    }
                ]
            };
            var worker =
            {
                ImageId: details.image.ImageId,
                InstanceType: details.worker.type,
                KeyName: details.keypair.KeyName,
                SpotPrice: worker_price,
                IamInstanceProfile: { Arn : details.iam.profileArn },
                BlockDeviceMappings: worker_disk,
                SecurityGroups:
                [
                    { GroupId: details.worker_security_group.GroupId }
                ],
                UserData: btoa (worker_startup_script)
            };
            for (var i = 0; i < worker_count; i++)
                spot_feet_request.LaunchSpecifications.push (worker);

            var text = JSON.stringify (spot_feet_request, null, 4);
            document.getElementById ("wizard_data").innerHTML = text;

            var ec2 = new AWS.EC2 ();
            var params =
            {
                DryRun: true, 
                SpotFleetRequestConfig: spot_feet_request
            };
            ec2.requestSpotFleet (params, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else console.log (JSON.stringify (data, null, 4));
            });
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