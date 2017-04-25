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