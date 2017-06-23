/**
 * @fileOverview Task definition creation step of the ECS provisioning wizard.
 * @name task
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get/create the Spark task.
     * @description Gets the task called Spark that the user has and choose or create a new task.
     * @name task
     * @exports task
     * @version 1.0
     */
    function ()
    {
        var taskdefinitions = null;

        function show_tasks ()
        {
            function wrap (taskdef)
            {
                return ("<option value=\"" + taskdef.family + "\">" + taskdef.family + " (" + taskdef.taskDefinitionArn + ")" + "</option>")
            }
            var options = taskdefinitions.map (wrap).join ("\n");
            document.getElementById ("taskdefinition_list").innerHTML = options;
            // if there is only one task definition, select it
            if (1 == taskdefinitions.length)
            {
                document.getElementById ("taskdefinition").value = taskdefinitions[0].family;
                change_taskdefinition (null);
                this.taskdefinition = taskdefinitions[0];
            }
        }

        function describe_tasks (data)
        {
            function best (accumulator, element)
            {
                var parts = element.split (":");
                var family = parts[parts.length - 2].split ("/")[1];
                var version = parts[parts.length - 1];
                if ("undefined" == typeof (accumulator[family]))
                    accumulator[family] = element;
                else
                {
                    parts = accumulator[family].split (":")
                    if (parts[parts.length - 1] < version)
                        accumulator[family] = element;
                }
                return (accumulator);
            }
            var taskdefs = data.taskDefinitionArns.reduce (best, {});

            function donep (l1, l2)
            {
                var done = true;
                for (x in l1)
                    if ("undefined" == typeof (l2[x]))
                        done = false;
                return (done);
            }
            function gather (data)
            {
                var taskdef = data.taskDefinition;
                var family = taskdef.family;
                this[family] = taskdef;
                if (donep (taskdefs, this))
                {
                    var taskdefs = [];
                    for (x in this)
                        taskdefs.push (this[x])
                    taskdefinitions = taskdefs;
                    show_tasks ();
                }
            }
            var ecs = new AWS.ECS ();
            var responses = {};
            var fn = gather.bind (responses);
            for (x in taskdefs)
            {
                var params =
                {
                    taskDefinition: taskdefs[x]
                };
                ecs.describeTaskDefinition (params, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else     fn (data);                    // successful response
                });
            }
        }

        function lookup_task ()
        {
            var name = document.getElementById ("taskdefinition").value;
            var found = null;
            function find (taskdef)
            {
                if (taskdef.family == name)
                    found = taskdef;
            }
            if (null != taskdefinitions)
                taskdefinitions.forEach (find);
            return (found);
        }

        function change_taskdefinition (event)
        {
            var task = lookup_task ();
            var image = document.getElementById ("dockerimage").value;
            var creatable = ((null != task) && (image != ""))
            document.getElementById ("create_taskdefinition").disabled = creatable;
            if (null != task)
                document.getElementById ("dockerimage").value = task.containerDefinitions[0].image
        }

        function create_taskdefinition (event)
        {
            var name = document.getElementById ("taskdefinition").value;
            var image = document.getElementById ("dockerimage").value;
            var params =
            {
                "family": name, 
                "networkMode": "host", 
                "containerDefinitions":
                [
                    {
                        "name": "sandbox", 
                        "image": image, 
                        "memoryReservation": 4096, 
                        "essential": true,
                        "mountPoints":
                        [
                            {
                                "sourceVolume": "tmp", 
                                "containerPath": "/host_tmp", 
                                "readOnly": false
                            }
                        ], 
                        "hostname": "sandbox", 
                        "disableNetworking": false, 
                        "privileged": true, 
                        "readonlyRootFilesystem": false
                    }
                ],
                "volumes": 
                [
                    {
                        "name": "tmp",
                        "host":
                         {
                            "sourcePath": "/tmp"
                         }
                    }
                ]
            };
            var ecs = new AWS.ECS ();
            ecs.registerTaskDefinition (params, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else     init (null); // refresh
            });
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:task
         */
        function init (event)
        {
            if ((null == taskdefinitions) || (null == event))
            {
                var ecs = new AWS.ECS ();
                var params =
                {
                    "status": "ACTIVE"
                }
                ecs.listTaskDefinitions (params, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred
                    else     describe_tasks (data);     // successful response
                });
            }
        }

        function term (event)
        {
            this.taskdefinition = lookup_task ();
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "task",
                            title: "Task Definition",
                            template: "templates/task.mst",
                            hooks:
                            [
                                { id: "taskdefinition", event: "change", code: change_taskdefinition },
                                { id: "taskdefinition", event: "input", code: change_taskdefinition },
                                { id : "create_taskdefinition", event : "click", code : create_taskdefinition }
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