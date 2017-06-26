/**
 * @fileOverview Key pair creation step of the ECS provisioning wizard.
 * @name keys
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get/create a key pair.
     * @description Gets the key pairs the user has and choose or create a key pair.
     * @name keys
     * @exports keys
     * @version 1.0
     */
    function ()
    {
        var keys = null;

        function show_keys (data)
        {
            keys = data.KeyPairs
            function wrap (keypair)
            {
                return ("<option value=\"" + keypair.KeyName + "\">" + keypair.KeyName + " (" + keypair.KeyFingerprint + ")" + "</option>")
            }
            var options = keys.map (wrap).join ("\n");
            document.getElementById ("keypair_list").innerHTML = options;
            // if there is only one key pair, select it
            if (1 == keys.length)
            {
                document.getElementById ("keypair").value = keys[0].KeyName;
                change_keypair (null);
                this.keypair = keys[0];
            }
        }

        function lookup_keypair ()
        {
            var name = document.getElementById ("keypair").value;
            var found = null;
            function find (keypair)
            {
                if (keypair.KeyName == name)
                    found = keypair;
            }
            if (null != keys)
                keys.forEach (find);
            return (found);
        }

        function change_keypair (event)
        {
            var keypair = lookup_keypair ();
            var name = document.getElementById ("keypair").value;
            var creatable = ((null == keypair) && ("" != name))
            document.getElementById ("create_keypair").disabled = !creatable;
        }

        function make_link (name, data)
        {
            var key = data.KeyMaterial;
            a = document.createElement ("a");
            a.setAttribute ("href", "data:application/octet-stream;base64," + btoa (key));
            a.setAttribute ("download", name + ".pem");
            a.appendChild (document.createTextNode (name + ".pem"));
            document.getElementById ("generated_key_pair").appendChild (a);
            // init (null); // refresh
            keys.push (data);
        }

        function create_keypair (event)
        {
            var name = document.getElementById ("keypair").value;
            var ec2 = new AWS.EC2 ();
            ec2.createKeyPair ({ KeyName: name }, function (err, data) {
                if (err) console.log (err, err.stack); // an error occurred
                else make_link (name, data);
            });
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:keys
         */
        function init (event)
        {
            if ((null == keys) || (null == event))
            {
                var ec2 = new AWS.EC2 ();
                ec2.describeKeyPairs ({}, function (err, data) {
                    if (err) console.log (err, err.stack); // an error occurred, e.g. 403 Forbidden
                    else     show_keys (data);     //  successful response
                });
            }
        }

        function term (event)
        {
            this.keypair = lookup_keypair ();
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "keys",
                            title: "Key Pair",
                            template: "templates/keys.mst",
                            hooks:
                            [
                                { id: "keypair", event: "change", code: change_keypair },
                                { id: "keypair", event: "input", code: change_keypair },
                                { id : "create_keypair", event : "click", code : create_keypair }
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