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
        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:overview
         */
        function init (event)
        {
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