/**
 * @fileOverview IAM setup step of the ECS provisioning wizard.
 * @name iam
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get/create IAM security groups.
     * @description Gets or creates the necessary the security groups.
     * @name iam
     * @exports iam
     * @version 1.0
     */
    function ()
    {
        var roles = null;

        function show_roles (data)
        {
            roles = data.Roles
            function wrap (role)
            {
                return ("<option value=\"" + role.RoleName + "\">" + role.RoleName + " (" + role.Arn + ")" + "</option>")
            }
            var options = roles.map (wrap).join ("\n");
            document.getElementById ("role_list").innerHTML = options;
        }

        function change_role (event)
        {
            var role = lookup_role ();
            var name = document.getElementById ("role").value;
            var creatable = ((null != role) && ("" != name))
            document.getElementById ("create_role").disabled = creatable;
        }

        function add_role_to_instance_profile (name)
        {
            var iam = new AWS.IAM ();
            var params =
            {
                InstanceProfileName: name, 
                RoleName: name
            };
            iam.addRoleToInstanceProfile (params, function(err, data) {
                if (err) console.log(err, err.stack); // an error occurred
                else     init (null); // refresh
            });
        }

        function create_instance_profile (name)
        {
            var iam = new AWS.IAM ();
            var params =
            {
                InstanceProfileName: name,
                Path: "/"
            };
            iam.createInstanceProfile (params, function(err, data) {
                if (err) console.log(err, err.stack); // an error occurred
                else     add_role_to_instance_profile (name);
            });
        }

        function attach_custom_policy (name, custom_policy)
        {
            var iam = new AWS.IAM ();
            var params =
            {
                RoleName: name,
                PolicyName: name + "-instance-discovery-and-start",
                PolicyDocument: JSON.stringify (custom_policy, null, 4)
            };
            iam.putRolePolicy (params, function(err, data) {
                if (err) console.log(err, err.stack); // an error occurred
                else create_instance_profile (name);
            });
        }

        function attach_policies (name, policies, custom_policy)
        {
            var MAX_INT = 1000; // doesn't like big number like 4294967295 => validation error detected: Value '100000' at 'maxItems' failed to satisfy constraint: Member must have value less than or equal to 1000
            var iam = new AWS.IAM ();
            iam.listPolicies ({ MaxItems: MAX_INT }, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else
                {
                    function needed (policy)
                    {
                        return (-1 != policies.indexOf (policy.PolicyName));
                    }
                    function arn (policy)
                    {
                        return (policy.Arn);
                    }
                    var arns = data.Policies.filter (needed).map (arn);

                    var count = 0;
                    function gather (data)
                    {
                        count++;
                        if (count >= arns.length)
                            attach_custom_policy (name, custom_policy);
                    }
                    for (var i = 0; i < arns.length; i++)
                    {
                        var params =
                        {
                            PolicyArn: arns[i], 
                            RoleName: name
                        };
                        iam.attachRolePolicy (params, function (err, data) {
                            if (err) console.log(err, err.stack); // an error occurred
                            else     gather (data);           // successful response
                        });
                    }
                }
            });
        }

        function create_role (event)
        {
            var name = document.getElementById ("role").value;
            var role_document =
            {
                Statement:
                [
                    {
                        Action: "sts:AssumeRole", 
                        Principal:
                        {
                            Service: "spotfleet.amazonaws.com"
                        }, 
                        Effect: "Allow", 
                        Sid: ""
                    },
                    {
                        Action: "sts:AssumeRole", 
                        Principal:
                        {
                            Service: "ec2.amazonaws.com"
                        }, 
                        Effect: "Allow", 
                        Sid: ""
                    }, 
                    {
                        Action: "sts:AssumeRole", 
                        Principal:
                        {
                            Service: "ecs.amazonaws.com"
                        }, 
                        Effect: "Allow", 
                        Sid: ""
                    }
              ]
            };
            var policies =
            [
                "AmazonEC2ContainerServiceforEC2Role",
                "AmazonEC2ContainerServiceRole",
                "AmazonEC2SpotFleetRole"
            ];
            var custom_policy =
            {
                Statement:
                [
                    {
                        Action:
                        [
                            "ecs:DescribeContainerInstances",
                            "ecs:ListContainerInstances"
                        ],
                        Resource:
                        [
                            "*"
                        ],
                        Effect: "Allow",
                        Sid: ""
                    },
                    {
                        Action:
                        [
                            "ec2:DescribeInstances"
                        ],
                        Resource:
                        [
                            "*"
                        ],
                        Effect: "Allow",
                        Sid: ""
                    },
                    {
                        Action:
                        [
                            "ecs:StartTask"
                        ],
                        Resource: "*",
                        Effect: "Allow"
                    }
                ]
            };
            var iam = new AWS.IAM ();
            var params =
            {
                RoleName: name,
                Path: "/", 
                AssumeRolePolicyDocument: JSON.stringify (role_document, null, 4)
            };
            iam.createRole (params, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else attach_policies (name, policies, custom_policy);
            });
        }

        function lookup_role ()
        {
            var name = document.getElementById ("role").value;
            var found = null;
            function find (role)
            {
                if (role.RoleName == name)
                    found = role;
            }
            if (null != roles)
                roles.forEach (find);
            return (found);
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
            if ((null == roles) || (null == event))
            {
                // because of CORS, this will only work if you disable web security:
                // chromium-browser --disable-web-security --user-data-dir .
                var iam = new AWS.IAM ();
                iam.listRoles ({}, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else     show_roles (data);     // successful response
                });
            }
        }

        function term (event)
        {
            var name = document.getElementById ("role").value;
            if ("" != name)
                if (null == roles)
                    this.role = { RoleName: name }
                else
                    this.role = lookup_role ();
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "iam",
                            title: "IAM ",
                            template: "templates/iam.mst",
                            hooks:
                            [
                                { id: "role", event: "change", code: change_role },
                                { id: "role", event: "input", code: change_role },
                                { id : "create_role", event : "click", code : create_role }
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