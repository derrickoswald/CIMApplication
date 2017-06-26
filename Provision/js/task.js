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
     * @description Gets the task that the user chooses or create a new task for both master and worker.
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
            document.getElementById ("master_taskdefinition_list").innerHTML = options;
            document.getElementById ("worker_taskdefinition_list").innerHTML = options;
            // if there are exactly one task definition named master and the other named worker, then select them
            var masters = taskdefinitions.filter (function (taskdef) { return ("master" == taskdef.containerDefinitions[0].name); });
            var workers = taskdefinitions.filter (function (taskdef) { return ("worker" == taskdef.containerDefinitions[0].name); });
            if ((1 == masters.length) && (1 == workers.length))
            {
                document.getElementById ("master_taskdefinition").value = masters[0].family;
                document.getElementById ("worker_taskdefinition").value = workers[0].family;
                change_taskdefinition (null);
                this.master_taskdefinition = masters[0];
                this.worker_taskdefinition = workers[0];
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

        function lookup_task (name)
        {
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

        function lookup_tasks ()
        {
            return (
                {
                    master_taskdefinition: lookup_task (document.getElementById ("master_taskdefinition").value),
                    worker_taskdefinition: lookup_task (document.getElementById ("worker_taskdefinition").value)
                }
            );
        }

        function change_taskdefinition (event)
        {
            var tasks = lookup_tasks ();
            var image = document.getElementById ("dockerimage").value;
            var master_creatable = ((null != tasks.master_taskdefinition) && (image != ""))
            document.getElementById ("create_master_taskdefinition").disabled = master_creatable;
            var worker_creatable = ((null != tasks.worker_taskdefinition) && (image != ""))
            document.getElementById ("create_worker_taskdefinition").disabled = worker_creatable;
            if (null != tasks.master_taskdefinition)
                document.getElementById ("dockerimage").value = tasks.master_taskdefinition.containerDefinitions[0].image
            else if (null != tasks.worker_taskdefinition)
                    document.getElementById ("dockerimage").value = tasks.worker_taskdefinition.containerDefinitions[0].image
        }

        function create_taskdefinition (event)
        {
            var target = event.currentTarget.id;
            var master = ("create_master_taskdefinition" == target);
            var name = document.getElementById (master ? "master_taskdefinition" : "worker_taskdefinition").value;
            var image = document.getElementById ("dockerimage").value;
            var params =
            {
                "family": name, 
                "networkMode": "host", 
                "containerDefinitions":
                [
                    {
                        "name": (master ? "master" : "worker"),
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
                        "hostname": (master ? "master" : "worker"),
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
            var tasks = lookup_tasks ();
            this.master_taskdefinition = tasks.master_taskdefinition;
            this.worker_taskdefinition = tasks.worker_taskdefinition;
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
                                { id: "master_taskdefinition", event: "change", code: change_taskdefinition },
                                { id: "master_taskdefinition", event: "input", code: change_taskdefinition },
                                { id: "create_master_taskdefinition", event : "click", code : create_taskdefinition },
                                { id: "worker_taskdefinition", event: "change", code: change_taskdefinition },
                                { id: "worker_taskdefinition", event: "input", code: change_taskdefinition },
                                { id: "create_worker_taskdefinition", event : "click", code : create_taskdefinition }
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