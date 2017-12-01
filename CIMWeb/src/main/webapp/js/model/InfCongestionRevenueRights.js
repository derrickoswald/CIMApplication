define
(
    ["model/base", "model/Common", "model/Meas"],
    function (base, Common, Meas)
    {

        /**
         * A type of limit that indicates if it is enforced and, through association, the organisation responsible for setting the limit.
         *
         */
        class ViolationLimit extends Meas.Limit
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ViolationLimit;
                if (null == bucket)
                   cim_data.ViolationLimit = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ViolationLimit[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Meas.Limit.prototype.parse.call (this, context, sub);
                obj.cls = "ViolationLimit";
                base.parse_element (/<cim:ViolationLimit.enforced>([\s\S]*?)<\/cim:ViolationLimit.enforced>/g, obj, "enforced", base.to_boolean, sub, context);
                base.parse_attribute (/<cim:ViolationLimit.MktMeasurement\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MktMeasurement", sub, context);
                base.parse_attribute (/<cim:ViolationLimit.Flowgate\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Flowgate", sub, context);

                var bucket = context.parsed.ViolationLimit;
                if (null == bucket)
                   context.parsed.ViolationLimit = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Meas.Limit.prototype.export.call (this, obj, false);

                base.export_element (obj, "ViolationLimit", "enforced", base.from_boolean, fields);
                base.export_attribute (obj, "ViolationLimit", "MktMeasurement", fields);
                base.export_attribute (obj, "ViolationLimit", "Flowgate", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ViolationLimit_collapse" aria-expanded="true" aria-controls="ViolationLimit_collapse">ViolationLimit</a>
<div id="ViolationLimit_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Meas.Limit.prototype.template.call (this) +
`
{{#enforced}}<div><b>enforced</b>: {{enforced}}</div>{{/enforced}}
{{#MktMeasurement}}<div><b>MktMeasurement</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{MktMeasurement}}&quot;);})'>{{MktMeasurement}}</a></div>{{/MktMeasurement}}
{{#Flowgate}}<div><b>Flowgate</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Flowgate}}&quot;);})'>{{Flowgate}}</a></div>{{/Flowgate}}
</div>
`
                );
           }        }

        /**
         * Financial Transmission Rights (FTR) regarding transmission capacity at a flowgate.
         *
         */
        class FTR extends Common.Agreement
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FTR;
                if (null == bucket)
                   cim_data.FTR = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FTR[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Common.Agreement.prototype.parse.call (this, context, sub);
                obj.cls = "FTR";
                base.parse_element (/<cim:FTR.optimized>([\s\S]*?)<\/cim:FTR.optimized>/g, obj, "optimized", base.to_string, sub, context);
                base.parse_element (/<cim:FTR.action>([\s\S]*?)<\/cim:FTR.action>/g, obj, "action", base.to_string, sub, context);
                base.parse_element (/<cim:FTR.baseEnergy>([\s\S]*?)<\/cim:FTR.baseEnergy>/g, obj, "baseEnergy", base.to_string, sub, context);
                base.parse_element (/<cim:FTR.ftrType>([\s\S]*?)<\/cim:FTR.ftrType>/g, obj, "ftrType", base.to_string, sub, context);
                base.parse_element (/<cim:FTR.class>([\s\S]*?)<\/cim:FTR.class>/g, obj, "class", base.to_string, sub, context);
                base.parse_attribute (/<cim:FTR.EnergyPriceCurve\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "EnergyPriceCurve", sub, context);
                base.parse_attribute (/<cim:FTR.Flowgate\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Flowgate", sub, context);

                var bucket = context.parsed.FTR;
                if (null == bucket)
                   context.parsed.FTR = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Common.Agreement.prototype.export.call (this, obj, false);

                base.export_element (obj, "FTR", "optimized", base.from_string, fields);
                base.export_element (obj, "FTR", "action", base.from_string, fields);
                base.export_element (obj, "FTR", "baseEnergy", base.from_string, fields);
                base.export_element (obj, "FTR", "ftrType", base.from_string, fields);
                base.export_element (obj, "FTR", "class", base.from_string, fields);
                base.export_attribute (obj, "FTR", "EnergyPriceCurve", fields);
                base.export_attribute (obj, "FTR", "Flowgate", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FTR_collapse" aria-expanded="true" aria-controls="FTR_collapse">FTR</a>
<div id="FTR_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Common.Agreement.prototype.template.call (this) +
`
{{#optimized}}<div><b>optimized</b>: {{optimized}}</div>{{/optimized}}
{{#action}}<div><b>action</b>: {{action}}</div>{{/action}}
{{#baseEnergy}}<div><b>baseEnergy</b>: {{baseEnergy}}</div>{{/baseEnergy}}
{{#ftrType}}<div><b>ftrType</b>: {{ftrType}}</div>{{/ftrType}}
{{#class}}<div><b>class</b>: {{class}}</div>{{/class}}
{{#EnergyPriceCurve}}<div><b>EnergyPriceCurve</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{EnergyPriceCurve}}&quot;);})'>{{EnergyPriceCurve}}</a></div>{{/EnergyPriceCurve}}
{{#Flowgate}}<div><b>Flowgate</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Flowgate}}&quot;);})'>{{Flowgate}}</a></div>{{/Flowgate}}
</div>
`
                );
           }        }

        return (
            {
                FTR: FTR,
                ViolationLimit: ViolationLimit
            }
        );
    }
);