/**
 * @fileOverview Image selection step of the ECS provisioning wizard.
 * @name images
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Get an image for the master and slave instances.
     * @description Gets the ECS optimized images and allows the user to choose.
     * @name images
     * @exports images
     * @version 1.0
     */
    function ()
    {
        var ecs_images = null;

        function show_images (data)
        {
            var images = data.Images;
            function is_ecs_optimized (image)
            {
                return ((null != image.Name) && (-1 != image.Name.indexOf ("amazon-ecs-optimized")));
            }
            function descending_date (a, b)
            {
                return (b.CreationDate.localeCompare (a.CreationDate));
            }
            ecs_images = images.filter (is_ecs_optimized).sort (descending_date);
            function wrap (image)
            {
                return ("<option value=\"" + image.ImageId + "\">" + image.ImageId + " - " + image.Name + " (" + image.CreationDate + ") " + image.Description + "</option>")
            }
            var options = ecs_images.map (wrap).join ("\n");
            document.getElementById ("image").innerHTML = options;
        }

        function lookup_image ()
        {
            var name = document.getElementById ("image").value;
            var found = null;
            function find (image)
            {
                if (image.ImageId == name)
                    found = image;
            }
            if (null != ecs_images)
                ecs_images.forEach (find);
            return (found);
        }

        /**
         * Form initialization function.
         *
         * @param {object} event - the tab being shown event, <em>not used</em>
         * @function init
         * @memberOf module:images
         */
        function init (event)
        {
            if (null == ecs_images)
            {
                var ec2 = new AWS.EC2 ();
                // ECS optimized instances have a block device /dev/xvdcz
                // http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-ami-storage-config.html
                //  ...launch with an 8-GiB volume for the operating system that is
                // attached at /dev/xvda and mounted as the root of the file system.
                // There is an additional 22-GiB volume that is attached at /dev/xvdcz
                // that Docker uses for image and metadata storage...
                var params = {
                          Filters: [
                            {
                              Name: 'block-device-mapping.device-name',
                              Values: [
                                '/dev/xvdcz'
                              ]
                            }
                          ]
                        };
                ec2.describeImages (params, function (err, data) {
                  if (err) console.log (err, err.stack); // an error occurred
                  else     show_images (data);           // successful response
                });
            }
        }

        function term (event)
        {
            this.image = lookup_image ();
        }

        return (
            {
                getStep: function ()
                {
                    return (
                        {
                            id: "Image",
                            title: "Image",
                            template: "templates/image.mst",
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