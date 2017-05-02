/**
 * @fileOverview Initial overview step of the ECS provisioning wizard.
 * @name overview
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["localstorage"],
    /**
     * @summary Introduce users to the ECS provisioning wizard and get valid credentials.
     * @description Provides a high level overview of the process for creating a cluster.
     * @name overview
     * @exports overview
     * @version 1.0
     */
    function (localstorage)
    {

        var regions =
        [
            {
                code: "us-east-1",
                name: "US East (N. Virginia)"
            },
            {
                code: "us-east-2",
                name: "US East (Ohio)"
            },
            {
                code: "us-west-1",
                name: "US West (N. California)"
            },
            {
                code: "us-west-2",
                name: "US West (Oregon)"
            },
            {
                code: "ca-central-1",
                name: "Canada (Central)"
            },
            {
                code: "eu-west-1",
                name: "EU (Ireland)"
            },
            {
                code: "eu-central-1",
                name: "EU (Frankfurt)"
            },
            {
                code: "eu-west-2",
                name: "EU (London)"
            },
            {
                code: "ap-northeast-1",
                name: "Asia Pacific (Tokyo)"
            },
            {
                code: "ap-northeast-2",
                name: "Asia Pacific (Seoul)"
            },
            {
                code: "ap-southeast-1",
                name: "Asia Pacific (Singapore)"
            },
            {
                code: "ap-southeast-2",
                name: "Asia Pacific (Sydney)"
            },
            {
                code: "ap-south-1",
                name: "Asia Pacific (Mumbai)"
            },
            {
                code: "sa-east-1",
                name: "South America (São Paulo)"
            }
        ];

        function wrap (region)
        {
            return ("<option value=\"" + region.code + "\">" + region.name + " - " + region.code + "</option>")
        }

        /**
         * @summary Read the configuration files..
         * @description 
         * @param {FileList} files - the files dropped or selected by the user
         * @param {object} data - the context object for the wizard
         * @function add_files
         * @memberOf module:thingmaker/files
         */
        function read_configuration (files, data)
        {
            function get (array, key)
            {
                var value = null;
                function check (str)
                {
                    var ret = false;
                    if (str.startsWith (key))
                    {
                        var index = str.indexOf ("=");
                        if (-1 != index)
                        {
                            value = str.substring (index + 1).trim ();
                            ret = true;
                        }
                    }
                    return (ret);
                }
                array.map (check);
                return (value);
            };

            function onLoadEnd (event)
            {
                if (event.target.readyState == FileReader.DONE)
                {
                    var text = event.target.result.split ("\n");
                    var aws_access_key_id = get (text, "aws_access_key_id");
                    if (null != aws_access_key_id)
                        document.getElementById ("accessKeyId").value = aws_access_key_id
                    var aws_secret_access_key = get (text, "aws_secret_access_key");
                    if (null != aws_secret_access_key)
                        document.getElementById ("secretAccessKey").value = aws_secret_access_key
                    var region = get (text, "region");
                    if (null != region)
                        document.getElementById ("region").value = region
                }
            };

            for (var i = 0; i < files.length; i++)
            {
                console.log (files.item (i));
                var reader = new FileReader ();
                reader.onloadend = onLoadEnd;
                reader.readAsText (files[i]);
            }
        }

        /**
         * @summary Handler for file change events.
         * @description Read the configuration file and update the display.
         * @param {object} event - the file change event
         * @function file_change
         * @memberOf module:overview
         */
        function file_change (event)
        {
            read_configuration (event.target.files, this);
        }

        /**
         * @summary Event handler for dropped files.
         * @description Attached to the drop target, this handler responds to dropped files,
         * adding them to the list of files.
         * @see {module:thingmaker/files.add_files}
         * @param {object} event - the drop event
         * @memberOf module:overview
         */
        function file_drop (event)
        {
            event.stopPropagation ();
            event.preventDefault ();
            read_configuration (event.dataTransfer.files, this);
        }

        /**
         * @summary Event handler for dragging files.
         * @description Attached to the drop target, this handler simply modifies the effect to copy,
         * (which produces the typical hand cursor).
         * @param {object} event - the dragover event
         * @memberOf module:overview
         */
        function file_drag (event)
        {
            event.stopPropagation ();
            event.preventDefault ();
            event.dataTransfer.dropEffect = 'copy';
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:overview
         */
        function init (event)
        {
            document.getElementById ("region").innerHTML = regions.map (wrap).join ("\n");

            var value;
            var found = false;
            if (null != (value = localstorage.loadProperty ("accessKeyId")))
            {
                document.getElementById ("accessKeyId").value = value;
                found = true;
            }
            if (null != (value = localstorage.loadProperty ("secretAccessKey")))
            {
                document.getElementById ("secretAccessKey").value = value;
                found = true;
            }
            if (null != (value = localstorage.loadProperty ("region")))
            {
                document.getElementById ("region").value = value;
                found = true;
            }
            document.getElementById ("remember").checked = found;
        }

        function term (event)
        {
            AWS.config = new AWS.Config (
                    {
                        accessKeyId: document.getElementById ("accessKeyId").value,
                        secretAccessKey: document.getElementById ("secretAccessKey").value,
                        region: document.getElementById ("region").value
                    }
                );

            if (document.getElementById ("remember").checked)
            {
                localstorage.storeProperty ("accessKeyId", document.getElementById ("accessKeyId").value);
                localstorage.storeProperty ("secretAccessKey", document.getElementById ("secretAccessKey").value);
                localstorage.storeProperty ("region", document.getElementById ("region").value);
            }
            else
            {
                localstorage.clearProperty ("accessKeyId");
                localstorage.clearProperty ("secretAccessKey");
                localstorage.clearProperty ("region");
            }
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "overview",
                            title: "Overview",
                            template: "templates/overview.mst",
                            hooks:
                            [
                                { id: "config_file", event: "change", code: file_change },
                                { id: "files_drop_zone", event: "dragover", code: file_drag },
                                { id: "files_drop_zone", event: "drop", code: file_drop }
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