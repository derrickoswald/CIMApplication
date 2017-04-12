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
     * @summary .
     * @description .
     * @name start
     * @exports start
     * @version 1.0
     */
    function ()
    {
        var details;

        function adjust_disk ()
        {
            // for now just use the default layout
            return (details.image.BlockDeviceMappings);
        }

        function start_master ()
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

            var disk = adjust_disk ();
            var master_spot_fleet_request =
            {
                IamFleetRole: details.iam.roleArn,
                AllocationStrategy: "lowestPrice",
                TargetCapacity: 1,
                SpotPrice: "1.912",
                ValidFrom: "2017-03-28T06:28:10Z",
                ValidUntil: "2018-03-28T06:28:10Z",
                TerminateInstancesWithExpiration: true,
                LaunchSpecifications: [ {
                    ImageId: details.image.ImageId,
                    InstanceType: details.master.type,
                    KeyName: details.keypair.KeyName,
                    SpotPrice: "1.912",
                    IamInstanceProfile:
                    {
                        Arn : details.iam.profileArn
                    },
                    BlockDeviceMappings: disk,
                    SecurityGroups: [ {
                        GroupId: "sg-5a25d623"
                    } ],
                    UserData: btoa(master_startup_script)
                } ],
                Type: "request"
            };
            var text = JSON.stringify (master_spot_fleet_request, null, 4);
            document.getElementById ("wizard_data").innerHTML = text;
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
                                { id : "start_master_button", event : "click", code : start_master }
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