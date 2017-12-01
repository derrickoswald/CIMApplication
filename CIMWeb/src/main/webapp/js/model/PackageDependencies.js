define
(
    ["model/base"],
    /**
     * This package shows all the root level subpackage dependencies of the combined CIM model.
     *
     */
    function (base)
    {

        /**
         * The version of dependencies description among top level subpackages of the combined CIM model.
         *
         * This is not the same as the combined packages version.
         *
         */
        class PackageDependenciesCIMVersion extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PackageDependenciesCIMVersion;
                if (null == bucket)
                   cim_data.PackageDependenciesCIMVersion = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PackageDependenciesCIMVersion[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PackageDependenciesCIMVersion";
                base.parse_element (/<cim:PackageDependenciesCIMVersion.date>([\s\S]*?)<\/cim:PackageDependenciesCIMVersion.date>/g, obj, "date", base.to_string, sub, context);
                base.parse_element (/<cim:PackageDependenciesCIMVersion.version>([\s\S]*?)<\/cim:PackageDependenciesCIMVersion.version>/g, obj, "version", base.to_string, sub, context);

                var bucket = context.parsed.PackageDependenciesCIMVersion;
                if (null == bucket)
                   context.parsed.PackageDependenciesCIMVersion = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PackageDependenciesCIMVersion", "date", base.from_string, fields);
                base.export_element (obj, "PackageDependenciesCIMVersion", "version", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PackageDependenciesCIMVersion_collapse" aria-expanded="true" aria-controls="PackageDependenciesCIMVersion_collapse">PackageDependenciesCIMVersion</a>
<div id="PackageDependenciesCIMVersion_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#date}}<div><b>date</b>: {{date}}</div>{{/date}}
{{#version}}<div><b>version</b>: {{version}}</div>{{/version}}
</div>
`
                );
           }        }

        return (
            {
                PackageDependenciesCIMVersion: PackageDependenciesCIMVersion
            }
        );
    }
);