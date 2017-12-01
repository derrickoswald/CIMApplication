define
(
    ["model/base", "model/Core"],
    /**
     * This section contains standard dynamic model specifications grouped into packages by standard function block (type of equipment being modelled).
     *
     * In the CIM, standard dynamic models are expressed by means of a class named with the standard model name and attributes reflecting each of the parameters necessary to describe the behaviour of an instance of the standard model.
     *
     */
    function (base, Core)
    {

        /**
         * Abstract parent class for all Dynamics function blocks.
         *
         */
        class DynamicsFunctionBlock extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.DynamicsFunctionBlock;
                if (null == bucket)
                   cim_data.DynamicsFunctionBlock = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.DynamicsFunctionBlock[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "DynamicsFunctionBlock";
                base.parse_element (/<cim:DynamicsFunctionBlock.enabled>([\s\S]*?)<\/cim:DynamicsFunctionBlock.enabled>/g, obj, "enabled", base.to_boolean, sub, context);

                var bucket = context.parsed.DynamicsFunctionBlock;
                if (null == bucket)
                   context.parsed.DynamicsFunctionBlock = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "DynamicsFunctionBlock", "enabled", base.from_boolean, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#DynamicsFunctionBlock_collapse" aria-expanded="true" aria-controls="DynamicsFunctionBlock_collapse">DynamicsFunctionBlock</a>
<div id="DynamicsFunctionBlock_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#enabled}}<div><b>enabled</b>: {{enabled}}</div>{{/enabled}}
</div>
`
                );
           }        }

        /**
         * Abstract parent class for all synchronous and asynchronous machine standard models.
         *
         */
        class RotatingMachineDynamics extends DynamicsFunctionBlock
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.RotatingMachineDynamics;
                if (null == bucket)
                   cim_data.RotatingMachineDynamics = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.RotatingMachineDynamics[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = DynamicsFunctionBlock.prototype.parse.call (this, context, sub);
                obj.cls = "RotatingMachineDynamics";
                base.parse_element (/<cim:RotatingMachineDynamics.damping>([\s\S]*?)<\/cim:RotatingMachineDynamics.damping>/g, obj, "damping", base.to_float, sub, context);
                base.parse_element (/<cim:RotatingMachineDynamics.inertia>([\s\S]*?)<\/cim:RotatingMachineDynamics.inertia>/g, obj, "inertia", base.to_string, sub, context);
                base.parse_element (/<cim:RotatingMachineDynamics.saturationFactor>([\s\S]*?)<\/cim:RotatingMachineDynamics.saturationFactor>/g, obj, "saturationFactor", base.to_float, sub, context);
                base.parse_element (/<cim:RotatingMachineDynamics.saturationFactor120>([\s\S]*?)<\/cim:RotatingMachineDynamics.saturationFactor120>/g, obj, "saturationFactor120", base.to_float, sub, context);
                base.parse_element (/<cim:RotatingMachineDynamics.statorLeakageReactance>([\s\S]*?)<\/cim:RotatingMachineDynamics.statorLeakageReactance>/g, obj, "statorLeakageReactance", base.to_string, sub, context);
                base.parse_element (/<cim:RotatingMachineDynamics.statorResistance>([\s\S]*?)<\/cim:RotatingMachineDynamics.statorResistance>/g, obj, "statorResistance", base.to_string, sub, context);

                var bucket = context.parsed.RotatingMachineDynamics;
                if (null == bucket)
                   context.parsed.RotatingMachineDynamics = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = DynamicsFunctionBlock.prototype.export.call (this, obj, false);

                base.export_element (obj, "RotatingMachineDynamics", "damping", base.from_float, fields);
                base.export_element (obj, "RotatingMachineDynamics", "inertia", base.from_string, fields);
                base.export_element (obj, "RotatingMachineDynamics", "saturationFactor", base.from_float, fields);
                base.export_element (obj, "RotatingMachineDynamics", "saturationFactor120", base.from_float, fields);
                base.export_element (obj, "RotatingMachineDynamics", "statorLeakageReactance", base.from_string, fields);
                base.export_element (obj, "RotatingMachineDynamics", "statorResistance", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#RotatingMachineDynamics_collapse" aria-expanded="true" aria-controls="RotatingMachineDynamics_collapse">RotatingMachineDynamics</a>
<div id="RotatingMachineDynamics_collapse" class="collapse in" style="margin-left: 10px;">
`
      + DynamicsFunctionBlock.prototype.template.call (this) +
`
{{#damping}}<div><b>damping</b>: {{damping}}</div>{{/damping}}
{{#inertia}}<div><b>inertia</b>: {{inertia}}</div>{{/inertia}}
{{#saturationFactor}}<div><b>saturationFactor</b>: {{saturationFactor}}</div>{{/saturationFactor}}
{{#saturationFactor120}}<div><b>saturationFactor120</b>: {{saturationFactor120}}</div>{{/saturationFactor120}}
{{#statorLeakageReactance}}<div><b>statorLeakageReactance</b>: {{statorLeakageReactance}}</div>{{/statorLeakageReactance}}
{{#statorResistance}}<div><b>statorResistance</b>: {{statorResistance}}</div>{{/statorResistance}}
</div>
`
                );
           }        }

        return (
            {
                DynamicsFunctionBlock: DynamicsFunctionBlock,
                RotatingMachineDynamics: RotatingMachineDynamics
            }
        );
    }
);