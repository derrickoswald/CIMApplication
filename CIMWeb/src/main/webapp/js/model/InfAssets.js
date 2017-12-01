define
(
    ["model/base", "model/Assets", "model/Common", "model/Core"],
    /**
     * The package is used to define asset-level models for objects.
     *
     * Assets may be comprised of other assets and may have relationships to other assets. Assets also have owners and values. Assets may also have a relationship to a PowerSystemResource in the Wires model.
     *
     */
    function (base, Assets, Common, Core)
    {

        /**
         * Kind of fill for Joint.
         *
         */
        class JointFillKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.JointFillKind;
                if (null == bucket)
                   cim_data.JointFillKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.JointFillKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "JointFillKind";
                base.parse_element (/<cim:JointFillKind.noFillPrefab>([\s\S]*?)<\/cim:JointFillKind.noFillPrefab>/g, obj, "noFillPrefab", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.airNoFilling>([\s\S]*?)<\/cim:JointFillKind.airNoFilling>/g, obj, "airNoFilling", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.petrolatum>([\s\S]*?)<\/cim:JointFillKind.petrolatum>/g, obj, "petrolatum", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.asphaltic>([\s\S]*?)<\/cim:JointFillKind.asphaltic>/g, obj, "asphaltic", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.oil>([\s\S]*?)<\/cim:JointFillKind.oil>/g, obj, "oil", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.bluefill254>([\s\S]*?)<\/cim:JointFillKind.bluefill254>/g, obj, "bluefill254", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.noVoid>([\s\S]*?)<\/cim:JointFillKind.noVoid>/g, obj, "noVoid", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.epoxy>([\s\S]*?)<\/cim:JointFillKind.epoxy>/g, obj, "epoxy", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.insoluseal>([\s\S]*?)<\/cim:JointFillKind.insoluseal>/g, obj, "insoluseal", base.to_string, sub, context);
                base.parse_element (/<cim:JointFillKind.other>([\s\S]*?)<\/cim:JointFillKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.JointFillKind;
                if (null == bucket)
                   context.parsed.JointFillKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "JointFillKind", "noFillPrefab", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "airNoFilling", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "petrolatum", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "asphaltic", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "oil", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "bluefill254", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "noVoid", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "epoxy", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "insoluseal", base.from_string, fields);
                base.export_element (obj, "JointFillKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#JointFillKind_collapse" aria-expanded="true" aria-controls="JointFillKind_collapse">JointFillKind</a>
<div id="JointFillKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#noFillPrefab}}<div><b>noFillPrefab</b>: {{noFillPrefab}}</div>{{/noFillPrefab}}
{{#airNoFilling}}<div><b>airNoFilling</b>: {{airNoFilling}}</div>{{/airNoFilling}}
{{#petrolatum}}<div><b>petrolatum</b>: {{petrolatum}}</div>{{/petrolatum}}
{{#asphaltic}}<div><b>asphaltic</b>: {{asphaltic}}</div>{{/asphaltic}}
{{#oil}}<div><b>oil</b>: {{oil}}</div>{{/oil}}
{{#bluefill254}}<div><b>bluefill254</b>: {{bluefill254}}</div>{{/bluefill254}}
{{#noVoid}}<div><b>noVoid</b>: {{noVoid}}</div>{{/noVoid}}
{{#epoxy}}<div><b>epoxy</b>: {{epoxy}}</div>{{/epoxy}}
{{#insoluseal}}<div><b>insoluseal</b>: {{insoluseal}}</div>{{/insoluseal}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * FACTS device asset.
         *
         */
        class FACTSDevice extends Assets.Asset
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FACTSDevice;
                if (null == bucket)
                   cim_data.FACTSDevice = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FACTSDevice[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.Asset.prototype.parse.call (this, context, sub);
                obj.cls = "FACTSDevice";
                base.parse_element (/<cim:FACTSDevice.kind>([\s\S]*?)<\/cim:FACTSDevice.kind>/g, obj, "kind", base.to_string, sub, context);

                var bucket = context.parsed.FACTSDevice;
                if (null == bucket)
                   context.parsed.FACTSDevice = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.Asset.prototype.export.call (this, obj, false);

                base.export_element (obj, "FACTSDevice", "kind", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FACTSDevice_collapse" aria-expanded="true" aria-controls="FACTSDevice_collapse">FACTSDevice</a>
<div id="FACTSDevice_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.Asset.prototype.template.call (this) +
`
{{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
</div>
`
                );
           }        }

        /**
         * Insulation kind for bushings.
         *
         */
        class BushingInsulationKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BushingInsulationKind;
                if (null == bucket)
                   cim_data.BushingInsulationKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BushingInsulationKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BushingInsulationKind";
                base.parse_element (/<cim:BushingInsulationKind.paperoil>([\s\S]*?)<\/cim:BushingInsulationKind.paperoil>/g, obj, "paperoil", base.to_string, sub, context);
                base.parse_element (/<cim:BushingInsulationKind.compound>([\s\S]*?)<\/cim:BushingInsulationKind.compound>/g, obj, "compound", base.to_string, sub, context);
                base.parse_element (/<cim:BushingInsulationKind.solidPorcelain>([\s\S]*?)<\/cim:BushingInsulationKind.solidPorcelain>/g, obj, "solidPorcelain", base.to_string, sub, context);
                base.parse_element (/<cim:BushingInsulationKind.other>([\s\S]*?)<\/cim:BushingInsulationKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.BushingInsulationKind;
                if (null == bucket)
                   context.parsed.BushingInsulationKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BushingInsulationKind", "paperoil", base.from_string, fields);
                base.export_element (obj, "BushingInsulationKind", "compound", base.from_string, fields);
                base.export_element (obj, "BushingInsulationKind", "solidPorcelain", base.from_string, fields);
                base.export_element (obj, "BushingInsulationKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BushingInsulationKind_collapse" aria-expanded="true" aria-controls="BushingInsulationKind_collapse">BushingInsulationKind</a>
<div id="BushingInsulationKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#paperoil}}<div><b>paperoil</b>: {{paperoil}}</div>{{/paperoil}}
{{#compound}}<div><b>compound</b>: {{compound}}</div>{{/compound}}
{{#solidPorcelain}}<div><b>solidPorcelain</b>: {{solidPorcelain}}</div>{{/solidPorcelain}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Specification can be used for various purposes relative to an asset, a logical device (PowerSystemResource), location, etc.
         *
         * Examples include documents supplied by manufacturers such as asset installation instructions, asset maintenance instructions, etc.
         *
         */
        class Specification extends Common.Document
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Specification;
                if (null == bucket)
                   cim_data.Specification = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Specification[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Common.Document.prototype.parse.call (this, context, sub);
                obj.cls = "Specification";

                var bucket = context.parsed.Specification;
                if (null == bucket)
                   context.parsed.Specification = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Common.Document.prototype.export.call (this, obj, false);

                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Specification_collapse" aria-expanded="true" aria-controls="Specification_collapse">Specification</a>
<div id="Specification_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Common.Document.prototype.template.call (this) +
`
</div>
`
                );
           }        }

        /**
         * An event where an asset has failed to perform its functions within specified parameters.
         *
         */
        class FailureEvent extends Common.ActivityRecord
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FailureEvent;
                if (null == bucket)
                   cim_data.FailureEvent = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FailureEvent[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Common.ActivityRecord.prototype.parse.call (this, context, sub);
                obj.cls = "FailureEvent";
                base.parse_element (/<cim:FailureEvent.corporateCode>([\s\S]*?)<\/cim:FailureEvent.corporateCode>/g, obj, "corporateCode", base.to_string, sub, context);
                base.parse_element (/<cim:FailureEvent.failureIsolationMethod>([\s\S]*?)<\/cim:FailureEvent.failureIsolationMethod>/g, obj, "failureIsolationMethod", base.to_string, sub, context);
                base.parse_element (/<cim:FailureEvent.faultLocatingMethod>([\s\S]*?)<\/cim:FailureEvent.faultLocatingMethod>/g, obj, "faultLocatingMethod", base.to_string, sub, context);
                base.parse_element (/<cim:FailureEvent.location>([\s\S]*?)<\/cim:FailureEvent.location>/g, obj, "location", base.to_string, sub, context);

                var bucket = context.parsed.FailureEvent;
                if (null == bucket)
                   context.parsed.FailureEvent = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Common.ActivityRecord.prototype.export.call (this, obj, false);

                base.export_element (obj, "FailureEvent", "corporateCode", base.from_string, fields);
                base.export_element (obj, "FailureEvent", "failureIsolationMethod", base.from_string, fields);
                base.export_element (obj, "FailureEvent", "faultLocatingMethod", base.from_string, fields);
                base.export_element (obj, "FailureEvent", "location", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FailureEvent_collapse" aria-expanded="true" aria-controls="FailureEvent_collapse">FailureEvent</a>
<div id="FailureEvent_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Common.ActivityRecord.prototype.template.call (this) +
`
{{#corporateCode}}<div><b>corporateCode</b>: {{corporateCode}}</div>{{/corporateCode}}
{{#failureIsolationMethod}}<div><b>failureIsolationMethod</b>: {{failureIsolationMethod}}</div>{{/failureIsolationMethod}}
{{#faultLocatingMethod}}<div><b>faultLocatingMethod</b>: {{faultLocatingMethod}}</div>{{/faultLocatingMethod}}
{{#location}}<div><b>location</b>: {{location}}</div>{{/location}}
</div>
`
                );
           }        }

        /**
         * Kind of anchor.
         *
         */
        class AnchorKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AnchorKind;
                if (null == bucket)
                   cim_data.AnchorKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AnchorKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AnchorKind";
                base.parse_element (/<cim:AnchorKind.concrete>([\s\S]*?)<\/cim:AnchorKind.concrete>/g, obj, "concrete", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.helix>([\s\S]*?)<\/cim:AnchorKind.helix>/g, obj, "helix", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.multiHelix>([\s\S]*?)<\/cim:AnchorKind.multiHelix>/g, obj, "multiHelix", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.rod>([\s\S]*?)<\/cim:AnchorKind.rod>/g, obj, "rod", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.screw>([\s\S]*?)<\/cim:AnchorKind.screw>/g, obj, "screw", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.unknown>([\s\S]*?)<\/cim:AnchorKind.unknown>/g, obj, "unknown", base.to_string, sub, context);
                base.parse_element (/<cim:AnchorKind.other>([\s\S]*?)<\/cim:AnchorKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.AnchorKind;
                if (null == bucket)
                   context.parsed.AnchorKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AnchorKind", "concrete", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "helix", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "multiHelix", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "rod", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "screw", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "unknown", base.from_string, fields);
                base.export_element (obj, "AnchorKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AnchorKind_collapse" aria-expanded="true" aria-controls="AnchorKind_collapse">AnchorKind</a>
<div id="AnchorKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#concrete}}<div><b>concrete</b>: {{concrete}}</div>{{/concrete}}
{{#helix}}<div><b>helix</b>: {{helix}}</div>{{/helix}}
{{#multiHelix}}<div><b>multiHelix</b>: {{multiHelix}}</div>{{/multiHelix}}
{{#rod}}<div><b>rod</b>: {{rod}}</div>{{/rod}}
{{#screw}}<div><b>screw</b>: {{screw}}</div>{{/screw}}
{{#unknown}}<div><b>unknown</b>: {{unknown}}</div>{{/unknown}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Construction holding assets such as conductors, transformers, switchgear, etc.
         *
         * Where applicable, number of conductors can be derived from the number of associated wire spacing instances.
         *
         */
        class Structure extends Assets.AssetContainer
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Structure;
                if (null == bucket)
                   cim_data.Structure = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Structure[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.AssetContainer.prototype.parse.call (this, context, sub);
                obj.cls = "Structure";
                base.parse_element (/<cim:Structure.fumigantAppliedDate>([\s\S]*?)<\/cim:Structure.fumigantAppliedDate>/g, obj, "fumigantAppliedDate", base.to_string, sub, context);
                base.parse_element (/<cim:Structure.fumigantName>([\s\S]*?)<\/cim:Structure.fumigantName>/g, obj, "fumigantName", base.to_string, sub, context);
                base.parse_element (/<cim:Structure.height>([\s\S]*?)<\/cim:Structure.height>/g, obj, "height", base.to_string, sub, context);
                base.parse_element (/<cim:Structure.materialKind>([\s\S]*?)<\/cim:Structure.materialKind>/g, obj, "materialKind", base.to_string, sub, context);
                base.parse_element (/<cim:Structure.ratedVoltage>([\s\S]*?)<\/cim:Structure.ratedVoltage>/g, obj, "ratedVoltage", base.to_string, sub, context);
                base.parse_element (/<cim:Structure.removeWeed>([\s\S]*?)<\/cim:Structure.removeWeed>/g, obj, "removeWeed", base.to_boolean, sub, context);
                base.parse_element (/<cim:Structure.weedRemovedDate>([\s\S]*?)<\/cim:Structure.weedRemovedDate>/g, obj, "weedRemovedDate", base.to_string, sub, context);

                var bucket = context.parsed.Structure;
                if (null == bucket)
                   context.parsed.Structure = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.AssetContainer.prototype.export.call (this, obj, false);

                base.export_element (obj, "Structure", "fumigantAppliedDate", base.from_string, fields);
                base.export_element (obj, "Structure", "fumigantName", base.from_string, fields);
                base.export_element (obj, "Structure", "height", base.from_string, fields);
                base.export_element (obj, "Structure", "materialKind", base.from_string, fields);
                base.export_element (obj, "Structure", "ratedVoltage", base.from_string, fields);
                base.export_element (obj, "Structure", "removeWeed", base.from_boolean, fields);
                base.export_element (obj, "Structure", "weedRemovedDate", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Structure_collapse" aria-expanded="true" aria-controls="Structure_collapse">Structure</a>
<div id="Structure_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.AssetContainer.prototype.template.call (this) +
`
{{#fumigantAppliedDate}}<div><b>fumigantAppliedDate</b>: {{fumigantAppliedDate}}</div>{{/fumigantAppliedDate}}
{{#fumigantName}}<div><b>fumigantName</b>: {{fumigantName}}</div>{{/fumigantName}}
{{#height}}<div><b>height</b>: {{height}}</div>{{/height}}
{{#materialKind}}<div><b>materialKind</b>: {{materialKind}}</div>{{/materialKind}}
{{#ratedVoltage}}<div><b>ratedVoltage</b>: {{ratedVoltage}}</div>{{/ratedVoltage}}
{{#removeWeed}}<div><b>removeWeed</b>: {{removeWeed}}</div>{{/removeWeed}}
{{#weedRemovedDate}}<div><b>weedRemovedDate</b>: {{weedRemovedDate}}</div>{{/weedRemovedDate}}
</div>
`
                );
           }        }

        /**
         * Kind of treatment for poles.
         *
         */
        class PoleTreatmentKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PoleTreatmentKind;
                if (null == bucket)
                   cim_data.PoleTreatmentKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PoleTreatmentKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PoleTreatmentKind";
                base.parse_element (/<cim:PoleTreatmentKind.full>([\s\S]*?)<\/cim:PoleTreatmentKind.full>/g, obj, "full", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.butt>([\s\S]*?)<\/cim:PoleTreatmentKind.butt>/g, obj, "butt", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.natural>([\s\S]*?)<\/cim:PoleTreatmentKind.natural>/g, obj, "natural", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.grayStain>([\s\S]*?)<\/cim:PoleTreatmentKind.grayStain>/g, obj, "grayStain", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.greenStain>([\s\S]*?)<\/cim:PoleTreatmentKind.greenStain>/g, obj, "greenStain", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.penta>([\s\S]*?)<\/cim:PoleTreatmentKind.penta>/g, obj, "penta", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.unknown>([\s\S]*?)<\/cim:PoleTreatmentKind.unknown>/g, obj, "unknown", base.to_string, sub, context);
                base.parse_element (/<cim:PoleTreatmentKind.other>([\s\S]*?)<\/cim:PoleTreatmentKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.PoleTreatmentKind;
                if (null == bucket)
                   context.parsed.PoleTreatmentKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PoleTreatmentKind", "full", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "butt", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "natural", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "grayStain", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "greenStain", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "penta", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "unknown", base.from_string, fields);
                base.export_element (obj, "PoleTreatmentKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PoleTreatmentKind_collapse" aria-expanded="true" aria-controls="PoleTreatmentKind_collapse">PoleTreatmentKind</a>
<div id="PoleTreatmentKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#full}}<div><b>full</b>: {{full}}</div>{{/full}}
{{#butt}}<div><b>butt</b>: {{butt}}</div>{{/butt}}
{{#natural}}<div><b>natural</b>: {{natural}}</div>{{/natural}}
{{#grayStain}}<div><b>grayStain</b>: {{grayStain}}</div>{{/grayStain}}
{{#greenStain}}<div><b>greenStain</b>: {{greenStain}}</div>{{/greenStain}}
{{#penta}}<div><b>penta</b>: {{penta}}</div>{{/penta}}
{{#unknown}}<div><b>unknown</b>: {{unknown}}</div>{{/unknown}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Generic asset or material item that may be used for planning, work or design purposes.
         *
         */
        class GenericAssetModelOrMaterial extends Assets.AssetModel
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.GenericAssetModelOrMaterial;
                if (null == bucket)
                   cim_data.GenericAssetModelOrMaterial = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.GenericAssetModelOrMaterial[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.AssetModel.prototype.parse.call (this, context, sub);
                obj.cls = "GenericAssetModelOrMaterial";
                base.parse_element (/<cim:GenericAssetModelOrMaterial.estimatedUnitCost>([\s\S]*?)<\/cim:GenericAssetModelOrMaterial.estimatedUnitCost>/g, obj, "estimatedUnitCost", base.to_string, sub, context);
                base.parse_element (/<cim:GenericAssetModelOrMaterial.quantity>([\s\S]*?)<\/cim:GenericAssetModelOrMaterial.quantity>/g, obj, "quantity", base.to_string, sub, context);
                base.parse_element (/<cim:GenericAssetModelOrMaterial.stockItem>([\s\S]*?)<\/cim:GenericAssetModelOrMaterial.stockItem>/g, obj, "stockItem", base.to_boolean, sub, context);
                base.parse_attribute (/<cim:GenericAssetModelOrMaterial.CUWorkEquipmentAsset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CUWorkEquipmentAsset", sub, context);
                base.parse_attribute (/<cim:GenericAssetModelOrMaterial.TypeAssetCatalogue\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "TypeAssetCatalogue", sub, context);
                base.parse_attribute (/<cim:GenericAssetModelOrMaterial.CUAsset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CUAsset", sub, context);

                var bucket = context.parsed.GenericAssetModelOrMaterial;
                if (null == bucket)
                   context.parsed.GenericAssetModelOrMaterial = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.AssetModel.prototype.export.call (this, obj, false);

                base.export_element (obj, "GenericAssetModelOrMaterial", "estimatedUnitCost", base.from_string, fields);
                base.export_element (obj, "GenericAssetModelOrMaterial", "quantity", base.from_string, fields);
                base.export_element (obj, "GenericAssetModelOrMaterial", "stockItem", base.from_boolean, fields);
                base.export_attribute (obj, "GenericAssetModelOrMaterial", "CUWorkEquipmentAsset", fields);
                base.export_attribute (obj, "GenericAssetModelOrMaterial", "TypeAssetCatalogue", fields);
                base.export_attribute (obj, "GenericAssetModelOrMaterial", "CUAsset", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#GenericAssetModelOrMaterial_collapse" aria-expanded="true" aria-controls="GenericAssetModelOrMaterial_collapse">GenericAssetModelOrMaterial</a>
<div id="GenericAssetModelOrMaterial_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.AssetModel.prototype.template.call (this) +
`
{{#estimatedUnitCost}}<div><b>estimatedUnitCost</b>: {{estimatedUnitCost}}</div>{{/estimatedUnitCost}}
{{#quantity}}<div><b>quantity</b>: {{quantity}}</div>{{/quantity}}
{{#stockItem}}<div><b>stockItem</b>: {{stockItem}}</div>{{/stockItem}}
{{#CUWorkEquipmentAsset}}<div><b>CUWorkEquipmentAsset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{CUWorkEquipmentAsset}}&quot;);})'>{{CUWorkEquipmentAsset}}</a></div>{{/CUWorkEquipmentAsset}}
{{#TypeAssetCatalogue}}<div><b>TypeAssetCatalogue</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{TypeAssetCatalogue}}&quot;);})'>{{TypeAssetCatalogue}}</a></div>{{/TypeAssetCatalogue}}
{{#CUAsset}}<div><b>CUAsset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{CUAsset}}&quot;);})'>{{CUAsset}}</a></div>{{/CUAsset}}
</div>
`
                );
           }        }

        /**
         * Kind of cooling.
         *
         */
        class CoolingKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CoolingKind;
                if (null == bucket)
                   cim_data.CoolingKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CoolingKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CoolingKind";
                base.parse_element (/<cim:CoolingKind.selfCooling>([\s\S]*?)<\/cim:CoolingKind.selfCooling>/g, obj, "selfCooling", base.to_string, sub, context);
                base.parse_element (/<cim:CoolingKind.forcedAir>([\s\S]*?)<\/cim:CoolingKind.forcedAir>/g, obj, "forcedAir", base.to_string, sub, context);
                base.parse_element (/<cim:CoolingKind.forcedOilAndAir>([\s\S]*?)<\/cim:CoolingKind.forcedOilAndAir>/g, obj, "forcedOilAndAir", base.to_string, sub, context);
                base.parse_element (/<cim:CoolingKind.other>([\s\S]*?)<\/cim:CoolingKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.CoolingKind;
                if (null == bucket)
                   context.parsed.CoolingKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CoolingKind", "selfCooling", base.from_string, fields);
                base.export_element (obj, "CoolingKind", "forcedAir", base.from_string, fields);
                base.export_element (obj, "CoolingKind", "forcedOilAndAir", base.from_string, fields);
                base.export_element (obj, "CoolingKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CoolingKind_collapse" aria-expanded="true" aria-controls="CoolingKind_collapse">CoolingKind</a>
<div id="CoolingKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#selfCooling}}<div><b>selfCooling</b>: {{selfCooling}}</div>{{/selfCooling}}
{{#forcedAir}}<div><b>forcedAir</b>: {{forcedAir}}</div>{{/forcedAir}}
{{#forcedOilAndAir}}<div><b>forcedOilAndAir</b>: {{forcedOilAndAir}}</div>{{/forcedOilAndAir}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * There are often stages of power which are associated with stages of cooling.
         *
         * For instance, a transformer may be rated 121kV on the primary, 15kV on the secondary and 4kV on the tertiary winding. These are voltage ratings and the power ratings are generally the same for all three windings and independent of the voltage ratings, there are instances where the tertiary may have a lower power rating.
         *
         */
        class CoolingPowerRating extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CoolingPowerRating;
                if (null == bucket)
                   cim_data.CoolingPowerRating = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CoolingPowerRating[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "CoolingPowerRating";
                base.parse_element (/<cim:CoolingPowerRating.coolingKind>([\s\S]*?)<\/cim:CoolingPowerRating.coolingKind>/g, obj, "coolingKind", base.to_string, sub, context);
                base.parse_element (/<cim:CoolingPowerRating.powerRating>([\s\S]*?)<\/cim:CoolingPowerRating.powerRating>/g, obj, "powerRating", base.to_string, sub, context);
                base.parse_element (/<cim:CoolingPowerRating.stage>([\s\S]*?)<\/cim:CoolingPowerRating.stage>/g, obj, "stage", base.to_string, sub, context);

                var bucket = context.parsed.CoolingPowerRating;
                if (null == bucket)
                   context.parsed.CoolingPowerRating = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "CoolingPowerRating", "coolingKind", base.from_string, fields);
                base.export_element (obj, "CoolingPowerRating", "powerRating", base.from_string, fields);
                base.export_element (obj, "CoolingPowerRating", "stage", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CoolingPowerRating_collapse" aria-expanded="true" aria-controls="CoolingPowerRating_collapse">CoolingPowerRating</a>
<div id="CoolingPowerRating_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#coolingKind}}<div><b>coolingKind</b>: {{coolingKind}}</div>{{/coolingKind}}
{{#powerRating}}<div><b>powerRating</b>: {{powerRating}}</div>{{/powerRating}}
{{#stage}}<div><b>stage</b>: {{stage}}</div>{{/stage}}
</div>
`
                );
           }        }

        /**
         * Kind of structure support.
         *
         */
        class StructureSupportKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.StructureSupportKind;
                if (null == bucket)
                   cim_data.StructureSupportKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.StructureSupportKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "StructureSupportKind";
                base.parse_element (/<cim:StructureSupportKind.anchor>([\s\S]*?)<\/cim:StructureSupportKind.anchor>/g, obj, "anchor", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupportKind.guy>([\s\S]*?)<\/cim:StructureSupportKind.guy>/g, obj, "guy", base.to_string, sub, context);

                var bucket = context.parsed.StructureSupportKind;
                if (null == bucket)
                   context.parsed.StructureSupportKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "StructureSupportKind", "anchor", base.from_string, fields);
                base.export_element (obj, "StructureSupportKind", "guy", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#StructureSupportKind_collapse" aria-expanded="true" aria-controls="StructureSupportKind_collapse">StructureSupportKind</a>
<div id="StructureSupportKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#anchor}}<div><b>anchor</b>: {{anchor}}</div>{{/anchor}}
{{#guy}}<div><b>guy</b>: {{guy}}</div>{{/guy}}
</div>
`
                );
           }        }

        /**
         * Various current financial properties associated with a particular asset.
         *
         * Historical properties may be determined by ActivityRecords associated with the asset.
         *
         */
        class FinancialInfo extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FinancialInfo;
                if (null == bucket)
                   cim_data.FinancialInfo = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FinancialInfo[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "FinancialInfo";
                base.parse_element (/<cim:FinancialInfo.account>([\s\S]*?)<\/cim:FinancialInfo.account>/g, obj, "account", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.actualPurchaseCost>([\s\S]*?)<\/cim:FinancialInfo.actualPurchaseCost>/g, obj, "actualPurchaseCost", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.costDescription>([\s\S]*?)<\/cim:FinancialInfo.costDescription>/g, obj, "costDescription", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.costType>([\s\S]*?)<\/cim:FinancialInfo.costType>/g, obj, "costType", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.financialValue>([\s\S]*?)<\/cim:FinancialInfo.financialValue>/g, obj, "financialValue", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.plantTransferDateTime>([\s\S]*?)<\/cim:FinancialInfo.plantTransferDateTime>/g, obj, "plantTransferDateTime", base.to_datetime, sub, context);
                base.parse_element (/<cim:FinancialInfo.purchaseDateTime>([\s\S]*?)<\/cim:FinancialInfo.purchaseDateTime>/g, obj, "purchaseDateTime", base.to_datetime, sub, context);
                base.parse_element (/<cim:FinancialInfo.purchaseOrderNumber>([\s\S]*?)<\/cim:FinancialInfo.purchaseOrderNumber>/g, obj, "purchaseOrderNumber", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.quantity>([\s\S]*?)<\/cim:FinancialInfo.quantity>/g, obj, "quantity", base.to_string, sub, context);
                base.parse_element (/<cim:FinancialInfo.valueDateTime>([\s\S]*?)<\/cim:FinancialInfo.valueDateTime>/g, obj, "valueDateTime", base.to_datetime, sub, context);
                base.parse_element (/<cim:FinancialInfo.warrantyEndDateTime>([\s\S]*?)<\/cim:FinancialInfo.warrantyEndDateTime>/g, obj, "warrantyEndDateTime", base.to_datetime, sub, context);
                base.parse_attribute (/<cim:FinancialInfo.Asset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Asset", sub, context);

                var bucket = context.parsed.FinancialInfo;
                if (null == bucket)
                   context.parsed.FinancialInfo = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "FinancialInfo", "account", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "actualPurchaseCost", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "costDescription", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "costType", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "financialValue", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "plantTransferDateTime", base.from_datetime, fields);
                base.export_element (obj, "FinancialInfo", "purchaseDateTime", base.from_datetime, fields);
                base.export_element (obj, "FinancialInfo", "purchaseOrderNumber", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "quantity", base.from_string, fields);
                base.export_element (obj, "FinancialInfo", "valueDateTime", base.from_datetime, fields);
                base.export_element (obj, "FinancialInfo", "warrantyEndDateTime", base.from_datetime, fields);
                base.export_attribute (obj, "FinancialInfo", "Asset", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FinancialInfo_collapse" aria-expanded="true" aria-controls="FinancialInfo_collapse">FinancialInfo</a>
<div id="FinancialInfo_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#account}}<div><b>account</b>: {{account}}</div>{{/account}}
{{#actualPurchaseCost}}<div><b>actualPurchaseCost</b>: {{actualPurchaseCost}}</div>{{/actualPurchaseCost}}
{{#costDescription}}<div><b>costDescription</b>: {{costDescription}}</div>{{/costDescription}}
{{#costType}}<div><b>costType</b>: {{costType}}</div>{{/costType}}
{{#financialValue}}<div><b>financialValue</b>: {{financialValue}}</div>{{/financialValue}}
{{#plantTransferDateTime}}<div><b>plantTransferDateTime</b>: {{plantTransferDateTime}}</div>{{/plantTransferDateTime}}
{{#purchaseDateTime}}<div><b>purchaseDateTime</b>: {{purchaseDateTime}}</div>{{/purchaseDateTime}}
{{#purchaseOrderNumber}}<div><b>purchaseOrderNumber</b>: {{purchaseOrderNumber}}</div>{{/purchaseOrderNumber}}
{{#quantity}}<div><b>quantity</b>: {{quantity}}</div>{{/quantity}}
{{#valueDateTime}}<div><b>valueDateTime</b>: {{valueDateTime}}</div>{{/valueDateTime}}
{{#warrantyEndDateTime}}<div><b>warrantyEndDateTime</b>: {{warrantyEndDateTime}}</div>{{/warrantyEndDateTime}}
{{#Asset}}<div><b>Asset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Asset}}&quot;);})'>{{Asset}}</a></div>{{/Asset}}
</div>
`
                );
           }        }

        /**
         * Preservative kind for poles.
         *
         */
        class PolePreservativeKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PolePreservativeKind;
                if (null == bucket)
                   cim_data.PolePreservativeKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PolePreservativeKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PolePreservativeKind";
                base.parse_element (/<cim:PolePreservativeKind.creosote>([\s\S]*?)<\/cim:PolePreservativeKind.creosote>/g, obj, "creosote", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.cellon>([\s\S]*?)<\/cim:PolePreservativeKind.cellon>/g, obj, "cellon", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.naphthena>([\s\S]*?)<\/cim:PolePreservativeKind.naphthena>/g, obj, "naphthena", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.penta>([\s\S]*?)<\/cim:PolePreservativeKind.penta>/g, obj, "penta", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.chemonite>([\s\S]*?)<\/cim:PolePreservativeKind.chemonite>/g, obj, "chemonite", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.unknown>([\s\S]*?)<\/cim:PolePreservativeKind.unknown>/g, obj, "unknown", base.to_string, sub, context);
                base.parse_element (/<cim:PolePreservativeKind.other>([\s\S]*?)<\/cim:PolePreservativeKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.PolePreservativeKind;
                if (null == bucket)
                   context.parsed.PolePreservativeKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PolePreservativeKind", "creosote", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "cellon", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "naphthena", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "penta", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "chemonite", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "unknown", base.from_string, fields);
                base.export_element (obj, "PolePreservativeKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PolePreservativeKind_collapse" aria-expanded="true" aria-controls="PolePreservativeKind_collapse">PolePreservativeKind</a>
<div id="PolePreservativeKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#creosote}}<div><b>creosote</b>: {{creosote}}</div>{{/creosote}}
{{#cellon}}<div><b>cellon</b>: {{cellon}}</div>{{/cellon}}
{{#naphthena}}<div><b>naphthena</b>: {{naphthena}}</div>{{/naphthena}}
{{#penta}}<div><b>penta</b>: {{penta}}</div>{{/penta}}
{{#chemonite}}<div><b>chemonite</b>: {{chemonite}}</div>{{/chemonite}}
{{#unknown}}<div><b>unknown</b>: {{unknown}}</div>{{/unknown}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Support for structure assets.
         *
         */
        class StructureSupport extends Assets.Asset
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.StructureSupport;
                if (null == bucket)
                   cim_data.StructureSupport = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.StructureSupport[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.Asset.prototype.parse.call (this, context, sub);
                obj.cls = "StructureSupport";
                base.parse_element (/<cim:StructureSupport.anchorKind>([\s\S]*?)<\/cim:StructureSupport.anchorKind>/g, obj, "anchorKind", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.anchorRodCount>([\s\S]*?)<\/cim:StructureSupport.anchorRodCount>/g, obj, "anchorRodCount", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.anchorRodLength>([\s\S]*?)<\/cim:StructureSupport.anchorRodLength>/g, obj, "anchorRodLength", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.direction>([\s\S]*?)<\/cim:StructureSupport.direction>/g, obj, "direction", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.kind>([\s\S]*?)<\/cim:StructureSupport.kind>/g, obj, "kind", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.length>([\s\S]*?)<\/cim:StructureSupport.length>/g, obj, "length", base.to_string, sub, context);
                base.parse_element (/<cim:StructureSupport.size>([\s\S]*?)<\/cim:StructureSupport.size>/g, obj, "size", base.to_string, sub, context);
                base.parse_attribute (/<cim:StructureSupport.SecuredStructure\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "SecuredStructure", sub, context);

                var bucket = context.parsed.StructureSupport;
                if (null == bucket)
                   context.parsed.StructureSupport = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.Asset.prototype.export.call (this, obj, false);

                base.export_element (obj, "StructureSupport", "anchorKind", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "anchorRodCount", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "anchorRodLength", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "direction", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "kind", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "length", base.from_string, fields);
                base.export_element (obj, "StructureSupport", "size", base.from_string, fields);
                base.export_attribute (obj, "StructureSupport", "SecuredStructure", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#StructureSupport_collapse" aria-expanded="true" aria-controls="StructureSupport_collapse">StructureSupport</a>
<div id="StructureSupport_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.Asset.prototype.template.call (this) +
`
{{#anchorKind}}<div><b>anchorKind</b>: {{anchorKind}}</div>{{/anchorKind}}
{{#anchorRodCount}}<div><b>anchorRodCount</b>: {{anchorRodCount}}</div>{{/anchorRodCount}}
{{#anchorRodLength}}<div><b>anchorRodLength</b>: {{anchorRodLength}}</div>{{/anchorRodLength}}
{{#direction}}<div><b>direction</b>: {{direction}}</div>{{/direction}}
{{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
{{#length}}<div><b>length</b>: {{length}}</div>{{/length}}
{{#size}}<div><b>size</b>: {{size}}</div>{{/size}}
{{#SecuredStructure}}<div><b>SecuredStructure</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{SecuredStructure}}&quot;);})'>{{SecuredStructure}}</a></div>{{/SecuredStructure}}
</div>
`
                );
           }        }

        /**
         * A duct contains individual wires in the layout as specified with associated wire spacing instances; number of them gives the number of conductors in this duct.
         *
         */
        class DuctBank extends Assets.AssetContainer
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.DuctBank;
                if (null == bucket)
                   cim_data.DuctBank = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.DuctBank[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.AssetContainer.prototype.parse.call (this, context, sub);
                obj.cls = "DuctBank";
                base.parse_element (/<cim:DuctBank.circuitCount>([\s\S]*?)<\/cim:DuctBank.circuitCount>/g, obj, "circuitCount", base.to_string, sub, context);

                var bucket = context.parsed.DuctBank;
                if (null == bucket)
                   context.parsed.DuctBank = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.AssetContainer.prototype.export.call (this, obj, false);

                base.export_element (obj, "DuctBank", "circuitCount", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#DuctBank_collapse" aria-expanded="true" aria-controls="DuctBank_collapse">DuctBank</a>
<div id="DuctBank_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.AssetContainer.prototype.template.call (this) +
`
{{#circuitCount}}<div><b>circuitCount</b>: {{circuitCount}}</div>{{/circuitCount}}
</div>
`
                );
           }        }

        /**
         * Kind of configuration for joints.
         *
         */
        class JointConfigurationKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.JointConfigurationKind;
                if (null == bucket)
                   cim_data.JointConfigurationKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.JointConfigurationKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "JointConfigurationKind";
                base.parse_element (/<cim:JointConfigurationKind.wires3to1>([\s\S]*?)<\/cim:JointConfigurationKind.wires3to1>/g, obj, "wires3to1", base.to_string, sub, context);
                base.parse_element (/<cim:JointConfigurationKind.wires2to1>([\s\S]*?)<\/cim:JointConfigurationKind.wires2to1>/g, obj, "wires2to1", base.to_string, sub, context);
                base.parse_element (/<cim:JointConfigurationKind.wires1to1>([\s\S]*?)<\/cim:JointConfigurationKind.wires1to1>/g, obj, "wires1to1", base.to_string, sub, context);
                base.parse_element (/<cim:JointConfigurationKind.other>([\s\S]*?)<\/cim:JointConfigurationKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.JointConfigurationKind;
                if (null == bucket)
                   context.parsed.JointConfigurationKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "JointConfigurationKind", "wires3to1", base.from_string, fields);
                base.export_element (obj, "JointConfigurationKind", "wires2to1", base.from_string, fields);
                base.export_element (obj, "JointConfigurationKind", "wires1to1", base.from_string, fields);
                base.export_element (obj, "JointConfigurationKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#JointConfigurationKind_collapse" aria-expanded="true" aria-controls="JointConfigurationKind_collapse">JointConfigurationKind</a>
<div id="JointConfigurationKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#wires3to1}}<div><b>wires3to1</b>: {{wires3to1}}</div>{{/wires3to1}}
{{#wires2to1}}<div><b>wires2to1</b>: {{wires2to1}}</div>{{/wires2to1}}
{{#wires1to1}}<div><b>wires1to1</b>: {{wires1to1}}</div>{{/wires1to1}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Bushing asset.
         *
         */
        class Bushing extends Assets.Asset
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Bushing;
                if (null == bucket)
                   cim_data.Bushing = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Bushing[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.Asset.prototype.parse.call (this, context, sub);
                obj.cls = "Bushing";
                base.parse_element (/<cim:Bushing.c1Capacitance>([\s\S]*?)<\/cim:Bushing.c1Capacitance>/g, obj, "c1Capacitance", base.to_string, sub, context);
                base.parse_element (/<cim:Bushing.c1PowerFactor>([\s\S]*?)<\/cim:Bushing.c1PowerFactor>/g, obj, "c1PowerFactor", base.to_float, sub, context);
                base.parse_element (/<cim:Bushing.c2Capacitance>([\s\S]*?)<\/cim:Bushing.c2Capacitance>/g, obj, "c2Capacitance", base.to_string, sub, context);
                base.parse_element (/<cim:Bushing.c2PowerFactor>([\s\S]*?)<\/cim:Bushing.c2PowerFactor>/g, obj, "c2PowerFactor", base.to_float, sub, context);
                base.parse_element (/<cim:Bushing.insulationKind>([\s\S]*?)<\/cim:Bushing.insulationKind>/g, obj, "insulationKind", base.to_string, sub, context);
                base.parse_attribute (/<cim:Bushing.Terminal\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Terminal", sub, context);

                var bucket = context.parsed.Bushing;
                if (null == bucket)
                   context.parsed.Bushing = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.Asset.prototype.export.call (this, obj, false);

                base.export_element (obj, "Bushing", "c1Capacitance", base.from_string, fields);
                base.export_element (obj, "Bushing", "c1PowerFactor", base.from_float, fields);
                base.export_element (obj, "Bushing", "c2Capacitance", base.from_string, fields);
                base.export_element (obj, "Bushing", "c2PowerFactor", base.from_float, fields);
                base.export_element (obj, "Bushing", "insulationKind", base.from_string, fields);
                base.export_attribute (obj, "Bushing", "Terminal", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Bushing_collapse" aria-expanded="true" aria-controls="Bushing_collapse">Bushing</a>
<div id="Bushing_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.Asset.prototype.template.call (this) +
`
{{#c1Capacitance}}<div><b>c1Capacitance</b>: {{c1Capacitance}}</div>{{/c1Capacitance}}
{{#c1PowerFactor}}<div><b>c1PowerFactor</b>: {{c1PowerFactor}}</div>{{/c1PowerFactor}}
{{#c2Capacitance}}<div><b>c2Capacitance</b>: {{c2Capacitance}}</div>{{/c2Capacitance}}
{{#c2PowerFactor}}<div><b>c2PowerFactor</b>: {{c2PowerFactor}}</div>{{/c2PowerFactor}}
{{#insulationKind}}<div><b>insulationKind</b>: {{insulationKind}}</div>{{/insulationKind}}
{{#Terminal}}<div><b>Terminal</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Terminal}}&quot;);})'>{{Terminal}}</a></div>{{/Terminal}}
</div>
`
                );
           }        }

        /**
         * Information regarding the experienced and expected reliability of a specific asset, type of asset, or asset model.
         *
         */
        class ReliabilityInfo extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ReliabilityInfo;
                if (null == bucket)
                   cim_data.ReliabilityInfo = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ReliabilityInfo[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "ReliabilityInfo";
                base.parse_element (/<cim:ReliabilityInfo.momFailureRate>([\s\S]*?)<\/cim:ReliabilityInfo.momFailureRate>/g, obj, "momFailureRate", base.to_string, sub, context);
                base.parse_element (/<cim:ReliabilityInfo.mTTR>([\s\S]*?)<\/cim:ReliabilityInfo.mTTR>/g, obj, "mTTR", base.to_string, sub, context);
                base.parse_attribute (/<cim:ReliabilityInfo.Specification\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Specification", sub, context);

                var bucket = context.parsed.ReliabilityInfo;
                if (null == bucket)
                   context.parsed.ReliabilityInfo = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "ReliabilityInfo", "momFailureRate", base.from_string, fields);
                base.export_element (obj, "ReliabilityInfo", "mTTR", base.from_string, fields);
                base.export_attribute (obj, "ReliabilityInfo", "Specification", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ReliabilityInfo_collapse" aria-expanded="true" aria-controls="ReliabilityInfo_collapse">ReliabilityInfo</a>
<div id="ReliabilityInfo_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#momFailureRate}}<div><b>momFailureRate</b>: {{momFailureRate}}</div>{{/momFailureRate}}
{{#mTTR}}<div><b>mTTR</b>: {{mTTR}}</div>{{/mTTR}}
{{#Specification}}<div><b>Specification</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Specification}}&quot;);})'>{{Specification}}</a></div>{{/Specification}}
</div>
`
                );
           }        }

        /**
         * Kind of tower construction.
         *
         */
        class TowerConstructionKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TowerConstructionKind;
                if (null == bucket)
                   cim_data.TowerConstructionKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TowerConstructionKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "TowerConstructionKind";
                base.parse_element (/<cim:TowerConstructionKind.suspension>([\s\S]*?)<\/cim:TowerConstructionKind.suspension>/g, obj, "suspension", base.to_string, sub, context);
                base.parse_element (/<cim:TowerConstructionKind.tension>([\s\S]*?)<\/cim:TowerConstructionKind.tension>/g, obj, "tension", base.to_string, sub, context);

                var bucket = context.parsed.TowerConstructionKind;
                if (null == bucket)
                   context.parsed.TowerConstructionKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "TowerConstructionKind", "suspension", base.from_string, fields);
                base.export_element (obj, "TowerConstructionKind", "tension", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TowerConstructionKind_collapse" aria-expanded="true" aria-controls="TowerConstructionKind_collapse">TowerConstructionKind</a>
<div id="TowerConstructionKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#suspension}}<div><b>suspension</b>: {{suspension}}</div>{{/suspension}}
{{#tension}}<div><b>tension</b>: {{tension}}</div>{{/tension}}
</div>
`
                );
           }        }

        /**
         * Kind of lamp for the streetlight.
         *
         */
        class StreetlightLampKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.StreetlightLampKind;
                if (null == bucket)
                   cim_data.StreetlightLampKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.StreetlightLampKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "StreetlightLampKind";
                base.parse_element (/<cim:StreetlightLampKind.highPressureSodium>([\s\S]*?)<\/cim:StreetlightLampKind.highPressureSodium>/g, obj, "highPressureSodium", base.to_string, sub, context);
                base.parse_element (/<cim:StreetlightLampKind.mercuryVapor>([\s\S]*?)<\/cim:StreetlightLampKind.mercuryVapor>/g, obj, "mercuryVapor", base.to_string, sub, context);
                base.parse_element (/<cim:StreetlightLampKind.metalHalide>([\s\S]*?)<\/cim:StreetlightLampKind.metalHalide>/g, obj, "metalHalide", base.to_string, sub, context);
                base.parse_element (/<cim:StreetlightLampKind.other>([\s\S]*?)<\/cim:StreetlightLampKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.StreetlightLampKind;
                if (null == bucket)
                   context.parsed.StreetlightLampKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "StreetlightLampKind", "highPressureSodium", base.from_string, fields);
                base.export_element (obj, "StreetlightLampKind", "mercuryVapor", base.from_string, fields);
                base.export_element (obj, "StreetlightLampKind", "metalHalide", base.from_string, fields);
                base.export_element (obj, "StreetlightLampKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#StreetlightLampKind_collapse" aria-expanded="true" aria-controls="StreetlightLampKind_collapse">StreetlightLampKind</a>
<div id="StreetlightLampKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#highPressureSodium}}<div><b>highPressureSodium</b>: {{highPressureSodium}}</div>{{/highPressureSodium}}
{{#mercuryVapor}}<div><b>mercuryVapor</b>: {{mercuryVapor}}</div>{{/mercuryVapor}}
{{#metalHalide}}<div><b>metalHalide</b>: {{metalHalide}}</div>{{/metalHalide}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Kind of PF test for bushing insulation.
         *
         */
        class BushingInsulationPfTestKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BushingInsulationPfTestKind;
                if (null == bucket)
                   cim_data.BushingInsulationPfTestKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BushingInsulationPfTestKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BushingInsulationPfTestKind";
                base.parse_element (/<cim:BushingInsulationPfTestKind.c1>([\s\S]*?)<\/cim:BushingInsulationPfTestKind.c1>/g, obj, "c1", base.to_string, sub, context);
                base.parse_element (/<cim:BushingInsulationPfTestKind.c2>([\s\S]*?)<\/cim:BushingInsulationPfTestKind.c2>/g, obj, "c2", base.to_string, sub, context);

                var bucket = context.parsed.BushingInsulationPfTestKind;
                if (null == bucket)
                   context.parsed.BushingInsulationPfTestKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BushingInsulationPfTestKind", "c1", base.from_string, fields);
                base.export_element (obj, "BushingInsulationPfTestKind", "c2", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BushingInsulationPfTestKind_collapse" aria-expanded="true" aria-controls="BushingInsulationPfTestKind_collapse">BushingInsulationPfTestKind</a>
<div id="BushingInsulationPfTestKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#c1}}<div><b>c1</b>: {{c1}}</div>{{/c1}}
{{#c2}}<div><b>c2</b>: {{c2}}</div>{{/c2}}
</div>
`
                );
           }        }

        /**
         * Kind of FACTS device.
         *
         */
        class FACTSDeviceKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FACTSDeviceKind;
                if (null == bucket)
                   cim_data.FACTSDeviceKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FACTSDeviceKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "FACTSDeviceKind";
                base.parse_element (/<cim:FACTSDeviceKind.svc>([\s\S]*?)<\/cim:FACTSDeviceKind.svc>/g, obj, "svc", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.statcom>([\s\S]*?)<\/cim:FACTSDeviceKind.statcom>/g, obj, "statcom", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.tcpar>([\s\S]*?)<\/cim:FACTSDeviceKind.tcpar>/g, obj, "tcpar", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.tcsc>([\s\S]*?)<\/cim:FACTSDeviceKind.tcsc>/g, obj, "tcsc", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.tcvl>([\s\S]*?)<\/cim:FACTSDeviceKind.tcvl>/g, obj, "tcvl", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.tsbr>([\s\S]*?)<\/cim:FACTSDeviceKind.tsbr>/g, obj, "tsbr", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.tssc>([\s\S]*?)<\/cim:FACTSDeviceKind.tssc>/g, obj, "tssc", base.to_string, sub, context);
                base.parse_element (/<cim:FACTSDeviceKind.upfc>([\s\S]*?)<\/cim:FACTSDeviceKind.upfc>/g, obj, "upfc", base.to_string, sub, context);

                var bucket = context.parsed.FACTSDeviceKind;
                if (null == bucket)
                   context.parsed.FACTSDeviceKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "FACTSDeviceKind", "svc", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "statcom", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "tcpar", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "tcsc", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "tcvl", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "tsbr", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "tssc", base.from_string, fields);
                base.export_element (obj, "FACTSDeviceKind", "upfc", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FACTSDeviceKind_collapse" aria-expanded="true" aria-controls="FACTSDeviceKind_collapse">FACTSDeviceKind</a>
<div id="FACTSDeviceKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#svc}}<div><b>svc</b>: {{svc}}</div>{{/svc}}
{{#statcom}}<div><b>statcom</b>: {{statcom}}</div>{{/statcom}}
{{#tcpar}}<div><b>tcpar</b>: {{tcpar}}</div>{{/tcpar}}
{{#tcsc}}<div><b>tcsc</b>: {{tcsc}}</div>{{/tcsc}}
{{#tcvl}}<div><b>tcvl</b>: {{tcvl}}</div>{{/tcvl}}
{{#tsbr}}<div><b>tsbr</b>: {{tsbr}}</div>{{/tsbr}}
{{#tssc}}<div><b>tssc</b>: {{tssc}}</div>{{/tssc}}
{{#upfc}}<div><b>upfc</b>: {{upfc}}</div>{{/upfc}}
</div>
`
                );
           }        }

        /**
         * Kind of medium.
         *
         */
        class MediumKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MediumKind;
                if (null == bucket)
                   cim_data.MediumKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MediumKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MediumKind";
                base.parse_element (/<cim:MediumKind.gas>([\s\S]*?)<\/cim:MediumKind.gas>/g, obj, "gas", base.to_string, sub, context);
                base.parse_element (/<cim:MediumKind.liquid>([\s\S]*?)<\/cim:MediumKind.liquid>/g, obj, "liquid", base.to_string, sub, context);
                base.parse_element (/<cim:MediumKind.solid>([\s\S]*?)<\/cim:MediumKind.solid>/g, obj, "solid", base.to_string, sub, context);

                var bucket = context.parsed.MediumKind;
                if (null == bucket)
                   context.parsed.MediumKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MediumKind", "gas", base.from_string, fields);
                base.export_element (obj, "MediumKind", "liquid", base.from_string, fields);
                base.export_element (obj, "MediumKind", "solid", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MediumKind_collapse" aria-expanded="true" aria-controls="MediumKind_collapse">MediumKind</a>
<div id="MediumKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#gas}}<div><b>gas</b>: {{gas}}</div>{{/gas}}
{{#liquid}}<div><b>liquid</b>: {{liquid}}</div>{{/liquid}}
{{#solid}}<div><b>solid</b>: {{solid}}</div>{{/solid}}
</div>
`
                );
           }        }

        /**
         * Winding insulation condition as a result of a test.
         *
         */
        class WindingInsulation extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.WindingInsulation;
                if (null == bucket)
                   cim_data.WindingInsulation = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.WindingInsulation[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "WindingInsulation";
                base.parse_element (/<cim:WindingInsulation.insulationPFStatus>([\s\S]*?)<\/cim:WindingInsulation.insulationPFStatus>/g, obj, "insulationPFStatus", base.to_string, sub, context);
                base.parse_element (/<cim:WindingInsulation.insulationResistance>([\s\S]*?)<\/cim:WindingInsulation.insulationResistance>/g, obj, "insulationResistance", base.to_string, sub, context);
                base.parse_element (/<cim:WindingInsulation.leakageReactance>([\s\S]*?)<\/cim:WindingInsulation.leakageReactance>/g, obj, "leakageReactance", base.to_string, sub, context);
                base.parse_element (/<cim:WindingInsulation.status>([\s\S]*?)<\/cim:WindingInsulation.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_attribute (/<cim:WindingInsulation.ToWinding\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ToWinding", sub, context);
                base.parse_attribute (/<cim:WindingInsulation.FromWinding\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "FromWinding", sub, context);
                base.parse_attribute (/<cim:WindingInsulation.TransformerObservation\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "TransformerObservation", sub, context);

                var bucket = context.parsed.WindingInsulation;
                if (null == bucket)
                   context.parsed.WindingInsulation = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "WindingInsulation", "insulationPFStatus", base.from_string, fields);
                base.export_element (obj, "WindingInsulation", "insulationResistance", base.from_string, fields);
                base.export_element (obj, "WindingInsulation", "leakageReactance", base.from_string, fields);
                base.export_element (obj, "WindingInsulation", "status", base.from_string, fields);
                base.export_attribute (obj, "WindingInsulation", "ToWinding", fields);
                base.export_attribute (obj, "WindingInsulation", "FromWinding", fields);
                base.export_attribute (obj, "WindingInsulation", "TransformerObservation", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#WindingInsulation_collapse" aria-expanded="true" aria-controls="WindingInsulation_collapse">WindingInsulation</a>
<div id="WindingInsulation_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#insulationPFStatus}}<div><b>insulationPFStatus</b>: {{insulationPFStatus}}</div>{{/insulationPFStatus}}
{{#insulationResistance}}<div><b>insulationResistance</b>: {{insulationResistance}}</div>{{/insulationResistance}}
{{#leakageReactance}}<div><b>leakageReactance</b>: {{leakageReactance}}</div>{{/leakageReactance}}
{{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
{{#ToWinding}}<div><b>ToWinding</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ToWinding}}&quot;);})'>{{ToWinding}}</a></div>{{/ToWinding}}
{{#FromWinding}}<div><b>FromWinding</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{FromWinding}}&quot;);})'>{{FromWinding}}</a></div>{{/FromWinding}}
{{#TransformerObservation}}<div><b>TransformerObservation</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{TransformerObservation}}&quot;);})'>{{TransformerObservation}}</a></div>{{/TransformerObservation}}
</div>
`
                );
           }        }

        /**
         * Enclosure that offers protection to the equipment it contains and/or safety to people/animals outside it.
         *
         */
        class Cabinet extends Assets.AssetContainer
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Cabinet;
                if (null == bucket)
                   cim_data.Cabinet = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Cabinet[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.AssetContainer.prototype.parse.call (this, context, sub);
                obj.cls = "Cabinet";

                var bucket = context.parsed.Cabinet;
                if (null == bucket)
                   context.parsed.Cabinet = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.AssetContainer.prototype.export.call (this, obj, false);

                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Cabinet_collapse" aria-expanded="true" aria-controls="Cabinet_collapse">Cabinet</a>
<div id="Cabinet_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.AssetContainer.prototype.template.call (this) +
`
</div>
`
                );
           }        }

        /**
         * Kind of underground structure.
         *
         */
        class UndergroundStructureKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.UndergroundStructureKind;
                if (null == bucket)
                   cim_data.UndergroundStructureKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.UndergroundStructureKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "UndergroundStructureKind";
                base.parse_element (/<cim:UndergroundStructureKind.burd>([\s\S]*?)<\/cim:UndergroundStructureKind.burd>/g, obj, "burd", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.enclosure>([\s\S]*?)<\/cim:UndergroundStructureKind.enclosure>/g, obj, "enclosure", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.handhole>([\s\S]*?)<\/cim:UndergroundStructureKind.handhole>/g, obj, "handhole", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.manhole>([\s\S]*?)<\/cim:UndergroundStructureKind.manhole>/g, obj, "manhole", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.pad>([\s\S]*?)<\/cim:UndergroundStructureKind.pad>/g, obj, "pad", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.subsurfaceEnclosure>([\s\S]*?)<\/cim:UndergroundStructureKind.subsurfaceEnclosure>/g, obj, "subsurfaceEnclosure", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.trench>([\s\S]*?)<\/cim:UndergroundStructureKind.trench>/g, obj, "trench", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.tunnel>([\s\S]*?)<\/cim:UndergroundStructureKind.tunnel>/g, obj, "tunnel", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.vault>([\s\S]*?)<\/cim:UndergroundStructureKind.vault>/g, obj, "vault", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructureKind.pullbox>([\s\S]*?)<\/cim:UndergroundStructureKind.pullbox>/g, obj, "pullbox", base.to_string, sub, context);

                var bucket = context.parsed.UndergroundStructureKind;
                if (null == bucket)
                   context.parsed.UndergroundStructureKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "UndergroundStructureKind", "burd", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "enclosure", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "handhole", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "manhole", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "pad", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "subsurfaceEnclosure", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "trench", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "tunnel", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "vault", base.from_string, fields);
                base.export_element (obj, "UndergroundStructureKind", "pullbox", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#UndergroundStructureKind_collapse" aria-expanded="true" aria-controls="UndergroundStructureKind_collapse">UndergroundStructureKind</a>
<div id="UndergroundStructureKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#burd}}<div><b>burd</b>: {{burd}}</div>{{/burd}}
{{#enclosure}}<div><b>enclosure</b>: {{enclosure}}</div>{{/enclosure}}
{{#handhole}}<div><b>handhole</b>: {{handhole}}</div>{{/handhole}}
{{#manhole}}<div><b>manhole</b>: {{manhole}}</div>{{/manhole}}
{{#pad}}<div><b>pad</b>: {{pad}}</div>{{/pad}}
{{#subsurfaceEnclosure}}<div><b>subsurfaceEnclosure</b>: {{subsurfaceEnclosure}}</div>{{/subsurfaceEnclosure}}
{{#trench}}<div><b>trench</b>: {{trench}}</div>{{/trench}}
{{#tunnel}}<div><b>tunnel</b>: {{tunnel}}</div>{{/tunnel}}
{{#vault}}<div><b>vault</b>: {{vault}}</div>{{/vault}}
{{#pullbox}}<div><b>pullbox</b>: {{pullbox}}</div>{{/pullbox}}
</div>
`
                );
           }        }

        /**
         * Kind of base for poles.
         *
         */
        class PoleBaseKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PoleBaseKind;
                if (null == bucket)
                   cim_data.PoleBaseKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PoleBaseKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PoleBaseKind";
                base.parse_element (/<cim:PoleBaseKind.asphalt>([\s\S]*?)<\/cim:PoleBaseKind.asphalt>/g, obj, "asphalt", base.to_string, sub, context);
                base.parse_element (/<cim:PoleBaseKind.cement>([\s\S]*?)<\/cim:PoleBaseKind.cement>/g, obj, "cement", base.to_string, sub, context);
                base.parse_element (/<cim:PoleBaseKind.dirt>([\s\S]*?)<\/cim:PoleBaseKind.dirt>/g, obj, "dirt", base.to_string, sub, context);
                base.parse_element (/<cim:PoleBaseKind.unknown>([\s\S]*?)<\/cim:PoleBaseKind.unknown>/g, obj, "unknown", base.to_string, sub, context);
                base.parse_element (/<cim:PoleBaseKind.other>([\s\S]*?)<\/cim:PoleBaseKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.PoleBaseKind;
                if (null == bucket)
                   context.parsed.PoleBaseKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PoleBaseKind", "asphalt", base.from_string, fields);
                base.export_element (obj, "PoleBaseKind", "cement", base.from_string, fields);
                base.export_element (obj, "PoleBaseKind", "dirt", base.from_string, fields);
                base.export_element (obj, "PoleBaseKind", "unknown", base.from_string, fields);
                base.export_element (obj, "PoleBaseKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PoleBaseKind_collapse" aria-expanded="true" aria-controls="PoleBaseKind_collapse">PoleBaseKind</a>
<div id="PoleBaseKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#asphalt}}<div><b>asphalt</b>: {{asphalt}}</div>{{/asphalt}}
{{#cement}}<div><b>cement</b>: {{cement}}</div>{{/cement}}
{{#dirt}}<div><b>dirt</b>: {{dirt}}</div>{{/dirt}}
{{#unknown}}<div><b>unknown</b>: {{unknown}}</div>{{/unknown}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * How the failure has been isolated.
         *
         */
        class FailureIsolationMethodKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FailureIsolationMethodKind;
                if (null == bucket)
                   cim_data.FailureIsolationMethodKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FailureIsolationMethodKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "FailureIsolationMethodKind";
                base.parse_element (/<cim:FailureIsolationMethodKind.breakerOperation>([\s\S]*?)<\/cim:FailureIsolationMethodKind.breakerOperation>/g, obj, "breakerOperation", base.to_string, sub, context);
                base.parse_element (/<cim:FailureIsolationMethodKind.fuse>([\s\S]*?)<\/cim:FailureIsolationMethodKind.fuse>/g, obj, "fuse", base.to_string, sub, context);
                base.parse_element (/<cim:FailureIsolationMethodKind.burnedInTheClear>([\s\S]*?)<\/cim:FailureIsolationMethodKind.burnedInTheClear>/g, obj, "burnedInTheClear", base.to_string, sub, context);
                base.parse_element (/<cim:FailureIsolationMethodKind.manuallyIsolated>([\s\S]*?)<\/cim:FailureIsolationMethodKind.manuallyIsolated>/g, obj, "manuallyIsolated", base.to_string, sub, context);
                base.parse_element (/<cim:FailureIsolationMethodKind.other>([\s\S]*?)<\/cim:FailureIsolationMethodKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.FailureIsolationMethodKind;
                if (null == bucket)
                   context.parsed.FailureIsolationMethodKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "FailureIsolationMethodKind", "breakerOperation", base.from_string, fields);
                base.export_element (obj, "FailureIsolationMethodKind", "fuse", base.from_string, fields);
                base.export_element (obj, "FailureIsolationMethodKind", "burnedInTheClear", base.from_string, fields);
                base.export_element (obj, "FailureIsolationMethodKind", "manuallyIsolated", base.from_string, fields);
                base.export_element (obj, "FailureIsolationMethodKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FailureIsolationMethodKind_collapse" aria-expanded="true" aria-controls="FailureIsolationMethodKind_collapse">FailureIsolationMethodKind</a>
<div id="FailureIsolationMethodKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#breakerOperation}}<div><b>breakerOperation</b>: {{breakerOperation}}</div>{{/breakerOperation}}
{{#fuse}}<div><b>fuse</b>: {{fuse}}</div>{{/fuse}}
{{#burnedInTheClear}}<div><b>burnedInTheClear</b>: {{burnedInTheClear}}</div>{{/burnedInTheClear}}
{{#manuallyIsolated}}<div><b>manuallyIsolated</b>: {{manuallyIsolated}}</div>{{/manuallyIsolated}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * An Asset Property that is described through curves rather than as a data point.
         *
         * The relationship is to be defined between an independent variable (X-axis) and one or two dependent variables (Y1-axis and Y2-axis).
         *
         */
        class AssetPropertyCurve extends Core.Curve
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AssetPropertyCurve;
                if (null == bucket)
                   cim_data.AssetPropertyCurve = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AssetPropertyCurve[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.Curve.prototype.parse.call (this, context, sub);
                obj.cls = "AssetPropertyCurve";
                base.parse_attribute (/<cim:AssetPropertyCurve.Specification\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Specification", sub, context);

                var bucket = context.parsed.AssetPropertyCurve;
                if (null == bucket)
                   context.parsed.AssetPropertyCurve = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.Curve.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "AssetPropertyCurve", "Specification", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AssetPropertyCurve_collapse" aria-expanded="true" aria-controls="AssetPropertyCurve_collapse">AssetPropertyCurve</a>
<div id="AssetPropertyCurve_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.Curve.prototype.template.call (this) +
`
{{#Specification}}<div><b>Specification</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Specification}}&quot;);})'>{{Specification}}</a></div>{{/Specification}}
</div>
`
                );
           }        }

        /**
         * Kind of material used for structures.
         *
         */
        class StructureMaterialKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.StructureMaterialKind;
                if (null == bucket)
                   cim_data.StructureMaterialKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.StructureMaterialKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "StructureMaterialKind";
                base.parse_element (/<cim:StructureMaterialKind.wood>([\s\S]*?)<\/cim:StructureMaterialKind.wood>/g, obj, "wood", base.to_string, sub, context);
                base.parse_element (/<cim:StructureMaterialKind.steel>([\s\S]*?)<\/cim:StructureMaterialKind.steel>/g, obj, "steel", base.to_string, sub, context);
                base.parse_element (/<cim:StructureMaterialKind.concrete>([\s\S]*?)<\/cim:StructureMaterialKind.concrete>/g, obj, "concrete", base.to_string, sub, context);
                base.parse_element (/<cim:StructureMaterialKind.other>([\s\S]*?)<\/cim:StructureMaterialKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.StructureMaterialKind;
                if (null == bucket)
                   context.parsed.StructureMaterialKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "StructureMaterialKind", "wood", base.from_string, fields);
                base.export_element (obj, "StructureMaterialKind", "steel", base.from_string, fields);
                base.export_element (obj, "StructureMaterialKind", "concrete", base.from_string, fields);
                base.export_element (obj, "StructureMaterialKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#StructureMaterialKind_collapse" aria-expanded="true" aria-controls="StructureMaterialKind_collapse">StructureMaterialKind</a>
<div id="StructureMaterialKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#wood}}<div><b>wood</b>: {{wood}}</div>{{/wood}}
{{#steel}}<div><b>steel</b>: {{steel}}</div>{{/steel}}
{{#concrete}}<div><b>concrete</b>: {{concrete}}</div>{{/concrete}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * As applicable, the basic linear, area, or volume dimensions of an asset, asset type (AssetModel) or other type of object (such as land area).
         *
         * Units and multipliers are specified per dimension.
         *
         */
        class DimensionsInfo extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.DimensionsInfo;
                if (null == bucket)
                   cim_data.DimensionsInfo = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.DimensionsInfo[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "DimensionsInfo";
                base.parse_element (/<cim:DimensionsInfo.orientation>([\s\S]*?)<\/cim:DimensionsInfo.orientation>/g, obj, "orientation", base.to_string, sub, context);
                base.parse_element (/<cim:DimensionsInfo.sizeDepth>([\s\S]*?)<\/cim:DimensionsInfo.sizeDepth>/g, obj, "sizeDepth", base.to_string, sub, context);
                base.parse_element (/<cim:DimensionsInfo.sizeDiameter>([\s\S]*?)<\/cim:DimensionsInfo.sizeDiameter>/g, obj, "sizeDiameter", base.to_string, sub, context);
                base.parse_element (/<cim:DimensionsInfo.sizeLength>([\s\S]*?)<\/cim:DimensionsInfo.sizeLength>/g, obj, "sizeLength", base.to_string, sub, context);
                base.parse_element (/<cim:DimensionsInfo.sizeWidth>([\s\S]*?)<\/cim:DimensionsInfo.sizeWidth>/g, obj, "sizeWidth", base.to_string, sub, context);

                var bucket = context.parsed.DimensionsInfo;
                if (null == bucket)
                   context.parsed.DimensionsInfo = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "DimensionsInfo", "orientation", base.from_string, fields);
                base.export_element (obj, "DimensionsInfo", "sizeDepth", base.from_string, fields);
                base.export_element (obj, "DimensionsInfo", "sizeDiameter", base.from_string, fields);
                base.export_element (obj, "DimensionsInfo", "sizeLength", base.from_string, fields);
                base.export_element (obj, "DimensionsInfo", "sizeWidth", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#DimensionsInfo_collapse" aria-expanded="true" aria-controls="DimensionsInfo_collapse">DimensionsInfo</a>
<div id="DimensionsInfo_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#orientation}}<div><b>orientation</b>: {{orientation}}</div>{{/orientation}}
{{#sizeDepth}}<div><b>sizeDepth</b>: {{sizeDepth}}</div>{{/sizeDepth}}
{{#sizeDiameter}}<div><b>sizeDiameter</b>: {{sizeDiameter}}</div>{{/sizeDiameter}}
{{#sizeLength}}<div><b>sizeLength</b>: {{sizeLength}}</div>{{/sizeLength}}
{{#sizeWidth}}<div><b>sizeWidth</b>: {{sizeWidth}}</div>{{/sizeWidth}}
</div>
`
                );
           }        }

        /**
         * Common information captured during transformer inspections and/or diagnostics.
         *
         * Note that some properties may be measured through other means and therefore have measurement values in addition to the observed values recorded here.
         *
         */
        class TransformerObservation extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TransformerObservation;
                if (null == bucket)
                   cim_data.TransformerObservation = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TransformerObservation[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "TransformerObservation";
                base.parse_element (/<cim:TransformerObservation.bushingTemp>([\s\S]*?)<\/cim:TransformerObservation.bushingTemp>/g, obj, "bushingTemp", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.dga>([\s\S]*?)<\/cim:TransformerObservation.dga>/g, obj, "dga", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.freqResp>([\s\S]*?)<\/cim:TransformerObservation.freqResp>/g, obj, "freqResp", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.furfuralDP>([\s\S]*?)<\/cim:TransformerObservation.furfuralDP>/g, obj, "furfuralDP", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.hotSpotTemp>([\s\S]*?)<\/cim:TransformerObservation.hotSpotTemp>/g, obj, "hotSpotTemp", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.oilColor>([\s\S]*?)<\/cim:TransformerObservation.oilColor>/g, obj, "oilColor", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.oilDielectricStrength>([\s\S]*?)<\/cim:TransformerObservation.oilDielectricStrength>/g, obj, "oilDielectricStrength", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.oilIFT>([\s\S]*?)<\/cim:TransformerObservation.oilIFT>/g, obj, "oilIFT", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.oilLevel>([\s\S]*?)<\/cim:TransformerObservation.oilLevel>/g, obj, "oilLevel", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.oilNeutralizationNumber>([\s\S]*?)<\/cim:TransformerObservation.oilNeutralizationNumber>/g, obj, "oilNeutralizationNumber", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.pumpVibration>([\s\S]*?)<\/cim:TransformerObservation.pumpVibration>/g, obj, "pumpVibration", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.status>([\s\S]*?)<\/cim:TransformerObservation.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.topOilTemp>([\s\S]*?)<\/cim:TransformerObservation.topOilTemp>/g, obj, "topOilTemp", base.to_string, sub, context);
                base.parse_element (/<cim:TransformerObservation.waterContent>([\s\S]*?)<\/cim:TransformerObservation.waterContent>/g, obj, "waterContent", base.to_string, sub, context);
                base.parse_attribute (/<cim:TransformerObservation.Reconditioning\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Reconditioning", sub, context);
                base.parse_attribute (/<cim:TransformerObservation.Transformer\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Transformer", sub, context);

                var bucket = context.parsed.TransformerObservation;
                if (null == bucket)
                   context.parsed.TransformerObservation = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "TransformerObservation", "bushingTemp", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "dga", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "freqResp", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "furfuralDP", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "hotSpotTemp", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "oilColor", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "oilDielectricStrength", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "oilIFT", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "oilLevel", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "oilNeutralizationNumber", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "pumpVibration", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "status", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "topOilTemp", base.from_string, fields);
                base.export_element (obj, "TransformerObservation", "waterContent", base.from_string, fields);
                base.export_attribute (obj, "TransformerObservation", "Reconditioning", fields);
                base.export_attribute (obj, "TransformerObservation", "Transformer", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TransformerObservation_collapse" aria-expanded="true" aria-controls="TransformerObservation_collapse">TransformerObservation</a>
<div id="TransformerObservation_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#bushingTemp}}<div><b>bushingTemp</b>: {{bushingTemp}}</div>{{/bushingTemp}}
{{#dga}}<div><b>dga</b>: {{dga}}</div>{{/dga}}
{{#freqResp}}<div><b>freqResp</b>: {{freqResp}}</div>{{/freqResp}}
{{#furfuralDP}}<div><b>furfuralDP</b>: {{furfuralDP}}</div>{{/furfuralDP}}
{{#hotSpotTemp}}<div><b>hotSpotTemp</b>: {{hotSpotTemp}}</div>{{/hotSpotTemp}}
{{#oilColor}}<div><b>oilColor</b>: {{oilColor}}</div>{{/oilColor}}
{{#oilDielectricStrength}}<div><b>oilDielectricStrength</b>: {{oilDielectricStrength}}</div>{{/oilDielectricStrength}}
{{#oilIFT}}<div><b>oilIFT</b>: {{oilIFT}}</div>{{/oilIFT}}
{{#oilLevel}}<div><b>oilLevel</b>: {{oilLevel}}</div>{{/oilLevel}}
{{#oilNeutralizationNumber}}<div><b>oilNeutralizationNumber</b>: {{oilNeutralizationNumber}}</div>{{/oilNeutralizationNumber}}
{{#pumpVibration}}<div><b>pumpVibration</b>: {{pumpVibration}}</div>{{/pumpVibration}}
{{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
{{#topOilTemp}}<div><b>topOilTemp</b>: {{topOilTemp}}</div>{{/topOilTemp}}
{{#waterContent}}<div><b>waterContent</b>: {{waterContent}}</div>{{/waterContent}}
{{#Reconditioning}}<div><b>Reconditioning</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Reconditioning}}&quot;);})'>{{Reconditioning}}</a></div>{{/Reconditioning}}
{{#Transformer}}<div><b>Transformer</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Transformer}}&quot;);})'>{{Transformer}}</a></div>{{/Transformer}}
</div>
`
                );
           }        }

        /**
         * Joint connects two or more cables.
         *
         * It includes the portion of cable under wipes, welds, or other seals.
         *
         */
        class Joint extends Assets.Asset
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Joint;
                if (null == bucket)
                   cim_data.Joint = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Joint[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.Asset.prototype.parse.call (this, context, sub);
                obj.cls = "Joint";
                base.parse_element (/<cim:Joint.configurationKind>([\s\S]*?)<\/cim:Joint.configurationKind>/g, obj, "configurationKind", base.to_string, sub, context);
                base.parse_element (/<cim:Joint.fillKind>([\s\S]*?)<\/cim:Joint.fillKind>/g, obj, "fillKind", base.to_string, sub, context);
                base.parse_element (/<cim:Joint.insulation>([\s\S]*?)<\/cim:Joint.insulation>/g, obj, "insulation", base.to_string, sub, context);

                var bucket = context.parsed.Joint;
                if (null == bucket)
                   context.parsed.Joint = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.Asset.prototype.export.call (this, obj, false);

                base.export_element (obj, "Joint", "configurationKind", base.from_string, fields);
                base.export_element (obj, "Joint", "fillKind", base.from_string, fields);
                base.export_element (obj, "Joint", "insulation", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Joint_collapse" aria-expanded="true" aria-controls="Joint_collapse">Joint</a>
<div id="Joint_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.Asset.prototype.template.call (this) +
`
{{#configurationKind}}<div><b>configurationKind</b>: {{configurationKind}}</div>{{/configurationKind}}
{{#fillKind}}<div><b>fillKind</b>: {{fillKind}}</div>{{/fillKind}}
{{#insulation}}<div><b>insulation</b>: {{insulation}}</div>{{/insulation}}
</div>
`
                );
           }        }

        /**
         * Reconditioning information for an asset.
         *
         */
        class Reconditioning extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Reconditioning;
                if (null == bucket)
                   cim_data.Reconditioning = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Reconditioning[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "Reconditioning";
                base.parse_element (/<cim:Reconditioning.dateTime>([\s\S]*?)<\/cim:Reconditioning.dateTime>/g, obj, "dateTime", base.to_datetime, sub, context);
                base.parse_attribute (/<cim:Reconditioning.Asset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Asset", sub, context);

                var bucket = context.parsed.Reconditioning;
                if (null == bucket)
                   context.parsed.Reconditioning = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "Reconditioning", "dateTime", base.from_datetime, fields);
                base.export_attribute (obj, "Reconditioning", "Asset", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Reconditioning_collapse" aria-expanded="true" aria-controls="Reconditioning_collapse">Reconditioning</a>
<div id="Reconditioning_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#dateTime}}<div><b>dateTime</b>: {{dateTime}}</div>{{/dateTime}}
{{#Asset}}<div><b>Asset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Asset}}&quot;);})'>{{Asset}}</a></div>{{/Asset}}
</div>
`
                );
           }        }

        /**
         * A substance that either (1) provides the means of transmission of a force or effect, such as hydraulic fluid, or (2) is used for a surrounding or enveloping substance, such as oil in a transformer or circuit breaker.
         *
         */
        class Medium extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Medium;
                if (null == bucket)
                   cim_data.Medium = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Medium[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "Medium";
                base.parse_element (/<cim:Medium.kind>([\s\S]*?)<\/cim:Medium.kind>/g, obj, "kind", base.to_string, sub, context);
                base.parse_element (/<cim:Medium.volumeSpec>([\s\S]*?)<\/cim:Medium.volumeSpec>/g, obj, "volumeSpec", base.to_string, sub, context);
                base.parse_attribute (/<cim:Medium.Specification\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Specification", sub, context);

                var bucket = context.parsed.Medium;
                if (null == bucket)
                   context.parsed.Medium = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "Medium", "kind", base.from_string, fields);
                base.export_element (obj, "Medium", "volumeSpec", base.from_string, fields);
                base.export_attribute (obj, "Medium", "Specification", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Medium_collapse" aria-expanded="true" aria-controls="Medium_collapse">Medium</a>
<div id="Medium_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
{{#volumeSpec}}<div><b>volumeSpec</b>: {{volumeSpec}}</div>{{/volumeSpec}}
{{#Specification}}<div><b>Specification</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Specification}}&quot;);})'>{{Specification}}</a></div>{{/Specification}}
</div>
`
                );
           }        }

        /**
         * A facility may contain buildings, storage facilities, switching facilities, power generation, manufacturing facilities, maintenance facilities, etc.
         *
         */
        class Facility extends Assets.AssetContainer
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Facility;
                if (null == bucket)
                   cim_data.Facility = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Facility[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.AssetContainer.prototype.parse.call (this, context, sub);
                obj.cls = "Facility";
                base.parse_element (/<cim:Facility.kind>([\s\S]*?)<\/cim:Facility.kind>/g, obj, "kind", base.to_string, sub, context);

                var bucket = context.parsed.Facility;
                if (null == bucket)
                   context.parsed.Facility = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.AssetContainer.prototype.export.call (this, obj, false);

                base.export_element (obj, "Facility", "kind", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Facility_collapse" aria-expanded="true" aria-controls="Facility_collapse">Facility</a>
<div id="Facility_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.AssetContainer.prototype.template.call (this) +
`
{{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
</div>
`
                );
           }        }

        /**
         * Bushing insulation power factor condition as a result of a test.
         *
         * Typical status values are: Acceptable, Minor Deterioration or Moisture Absorption, Major Deterioration or Moisture Absorption, Failed.
         *
         */
        class BushingInsulationPF extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BushingInsulationPF;
                if (null == bucket)
                   cim_data.BushingInsulationPF = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BushingInsulationPF[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "BushingInsulationPF";
                base.parse_element (/<cim:BushingInsulationPF.status>([\s\S]*?)<\/cim:BushingInsulationPF.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_element (/<cim:BushingInsulationPF.testKind>([\s\S]*?)<\/cim:BushingInsulationPF.testKind>/g, obj, "testKind", base.to_string, sub, context);
                base.parse_attribute (/<cim:BushingInsulationPF.Bushing\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Bushing", sub, context);
                base.parse_attribute (/<cim:BushingInsulationPF.TransformerObservation\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "TransformerObservation", sub, context);

                var bucket = context.parsed.BushingInsulationPF;
                if (null == bucket)
                   context.parsed.BushingInsulationPF = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "BushingInsulationPF", "status", base.from_string, fields);
                base.export_element (obj, "BushingInsulationPF", "testKind", base.from_string, fields);
                base.export_attribute (obj, "BushingInsulationPF", "Bushing", fields);
                base.export_attribute (obj, "BushingInsulationPF", "TransformerObservation", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BushingInsulationPF_collapse" aria-expanded="true" aria-controls="BushingInsulationPF_collapse">BushingInsulationPF</a>
<div id="BushingInsulationPF_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
{{#testKind}}<div><b>testKind</b>: {{testKind}}</div>{{/testKind}}
{{#Bushing}}<div><b>Bushing</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Bushing}}&quot;);})'>{{Bushing}}</a></div>{{/Bushing}}
{{#TransformerObservation}}<div><b>TransformerObservation</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{TransformerObservation}}&quot;);})'>{{TransformerObservation}}</a></div>{{/TransformerObservation}}
</div>
`
                );
           }        }

        /**
         * Streetlight asset.
         *
         */
        class Streetlight extends Assets.Asset
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Streetlight;
                if (null == bucket)
                   cim_data.Streetlight = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Streetlight[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Assets.Asset.prototype.parse.call (this, context, sub);
                obj.cls = "Streetlight";
                base.parse_element (/<cim:Streetlight.armLength>([\s\S]*?)<\/cim:Streetlight.armLength>/g, obj, "armLength", base.to_string, sub, context);
                base.parse_element (/<cim:Streetlight.lampKind>([\s\S]*?)<\/cim:Streetlight.lampKind>/g, obj, "lampKind", base.to_string, sub, context);
                base.parse_element (/<cim:Streetlight.lightRating>([\s\S]*?)<\/cim:Streetlight.lightRating>/g, obj, "lightRating", base.to_string, sub, context);
                base.parse_attribute (/<cim:Streetlight.Pole\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Pole", sub, context);

                var bucket = context.parsed.Streetlight;
                if (null == bucket)
                   context.parsed.Streetlight = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Assets.Asset.prototype.export.call (this, obj, false);

                base.export_element (obj, "Streetlight", "armLength", base.from_string, fields);
                base.export_element (obj, "Streetlight", "lampKind", base.from_string, fields);
                base.export_element (obj, "Streetlight", "lightRating", base.from_string, fields);
                base.export_attribute (obj, "Streetlight", "Pole", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Streetlight_collapse" aria-expanded="true" aria-controls="Streetlight_collapse">Streetlight</a>
<div id="Streetlight_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Assets.Asset.prototype.template.call (this) +
`
{{#armLength}}<div><b>armLength</b>: {{armLength}}</div>{{/armLength}}
{{#lampKind}}<div><b>lampKind</b>: {{lampKind}}</div>{{/lampKind}}
{{#lightRating}}<div><b>lightRating</b>: {{lightRating}}</div>{{/lightRating}}
{{#Pole}}<div><b>Pole</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Pole}}&quot;);})'>{{Pole}}</a></div>{{/Pole}}
</div>
`
                );
           }        }

        /**
         * Pole asset.
         *
         */
        class Pole extends Structure
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pole;
                if (null == bucket)
                   cim_data.Pole = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pole[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Structure.prototype.parse.call (this, context, sub);
                obj.cls = "Pole";
                base.parse_element (/<cim:Pole.baseKind>([\s\S]*?)<\/cim:Pole.baseKind>/g, obj, "baseKind", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.breastBlock>([\s\S]*?)<\/cim:Pole.breastBlock>/g, obj, "breastBlock", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pole.classification>([\s\S]*?)<\/cim:Pole.classification>/g, obj, "classification", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.construction>([\s\S]*?)<\/cim:Pole.construction>/g, obj, "construction", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.diameter>([\s\S]*?)<\/cim:Pole.diameter>/g, obj, "diameter", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.jpaReference>([\s\S]*?)<\/cim:Pole.jpaReference>/g, obj, "jpaReference", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.length>([\s\S]*?)<\/cim:Pole.length>/g, obj, "length", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.preservativeKind>([\s\S]*?)<\/cim:Pole.preservativeKind>/g, obj, "preservativeKind", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.speciesType>([\s\S]*?)<\/cim:Pole.speciesType>/g, obj, "speciesType", base.to_string, sub, context);
                base.parse_element (/<cim:Pole.treatedDateTime>([\s\S]*?)<\/cim:Pole.treatedDateTime>/g, obj, "treatedDateTime", base.to_datetime, sub, context);
                base.parse_element (/<cim:Pole.treatmentKind>([\s\S]*?)<\/cim:Pole.treatmentKind>/g, obj, "treatmentKind", base.to_string, sub, context);

                var bucket = context.parsed.Pole;
                if (null == bucket)
                   context.parsed.Pole = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Structure.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pole", "baseKind", base.from_string, fields);
                base.export_element (obj, "Pole", "breastBlock", base.from_boolean, fields);
                base.export_element (obj, "Pole", "classification", base.from_string, fields);
                base.export_element (obj, "Pole", "construction", base.from_string, fields);
                base.export_element (obj, "Pole", "diameter", base.from_string, fields);
                base.export_element (obj, "Pole", "jpaReference", base.from_string, fields);
                base.export_element (obj, "Pole", "length", base.from_string, fields);
                base.export_element (obj, "Pole", "preservativeKind", base.from_string, fields);
                base.export_element (obj, "Pole", "speciesType", base.from_string, fields);
                base.export_element (obj, "Pole", "treatedDateTime", base.from_datetime, fields);
                base.export_element (obj, "Pole", "treatmentKind", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pole_collapse" aria-expanded="true" aria-controls="Pole_collapse">Pole</a>
<div id="Pole_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Structure.prototype.template.call (this) +
`
{{#baseKind}}<div><b>baseKind</b>: {{baseKind}}</div>{{/baseKind}}
{{#breastBlock}}<div><b>breastBlock</b>: {{breastBlock}}</div>{{/breastBlock}}
{{#classification}}<div><b>classification</b>: {{classification}}</div>{{/classification}}
{{#construction}}<div><b>construction</b>: {{construction}}</div>{{/construction}}
{{#diameter}}<div><b>diameter</b>: {{diameter}}</div>{{/diameter}}
{{#jpaReference}}<div><b>jpaReference</b>: {{jpaReference}}</div>{{/jpaReference}}
{{#length}}<div><b>length</b>: {{length}}</div>{{/length}}
{{#preservativeKind}}<div><b>preservativeKind</b>: {{preservativeKind}}</div>{{/preservativeKind}}
{{#speciesType}}<div><b>speciesType</b>: {{speciesType}}</div>{{/speciesType}}
{{#treatedDateTime}}<div><b>treatedDateTime</b>: {{treatedDateTime}}</div>{{/treatedDateTime}}
{{#treatmentKind}}<div><b>treatmentKind</b>: {{treatmentKind}}</div>{{/treatmentKind}}
</div>
`
                );
           }        }

        /**
         * Tower asset.
         *
         * Dimensions of the Tower are specified in associated DimensionsInfo class.
         *
         */
        class Tower extends Structure
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Tower;
                if (null == bucket)
                   cim_data.Tower = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Tower[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Structure.prototype.parse.call (this, context, sub);
                obj.cls = "Tower";
                base.parse_element (/<cim:Tower.constructionKind>([\s\S]*?)<\/cim:Tower.constructionKind>/g, obj, "constructionKind", base.to_string, sub, context);

                var bucket = context.parsed.Tower;
                if (null == bucket)
                   context.parsed.Tower = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Structure.prototype.export.call (this, obj, false);

                base.export_element (obj, "Tower", "constructionKind", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Tower_collapse" aria-expanded="true" aria-controls="Tower_collapse">Tower</a>
<div id="Tower_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Structure.prototype.template.call (this) +
`
{{#constructionKind}}<div><b>constructionKind</b>: {{constructionKind}}</div>{{/constructionKind}}
</div>
`
                );
           }        }

        /**
         * Underground structure.
         *
         */
        class UndergroundStructure extends Structure
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.UndergroundStructure;
                if (null == bucket)
                   cim_data.UndergroundStructure = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.UndergroundStructure[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Structure.prototype.parse.call (this, context, sub);
                obj.cls = "UndergroundStructure";
                base.parse_element (/<cim:UndergroundStructure.hasVentilation>([\s\S]*?)<\/cim:UndergroundStructure.hasVentilation>/g, obj, "hasVentilation", base.to_boolean, sub, context);
                base.parse_element (/<cim:UndergroundStructure.kind>([\s\S]*?)<\/cim:UndergroundStructure.kind>/g, obj, "kind", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructure.material>([\s\S]*?)<\/cim:UndergroundStructure.material>/g, obj, "material", base.to_string, sub, context);
                base.parse_element (/<cim:UndergroundStructure.sealingWarrantyExpiresDate>([\s\S]*?)<\/cim:UndergroundStructure.sealingWarrantyExpiresDate>/g, obj, "sealingWarrantyExpiresDate", base.to_string, sub, context);

                var bucket = context.parsed.UndergroundStructure;
                if (null == bucket)
                   context.parsed.UndergroundStructure = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Structure.prototype.export.call (this, obj, false);

                base.export_element (obj, "UndergroundStructure", "hasVentilation", base.from_boolean, fields);
                base.export_element (obj, "UndergroundStructure", "kind", base.from_string, fields);
                base.export_element (obj, "UndergroundStructure", "material", base.from_string, fields);
                base.export_element (obj, "UndergroundStructure", "sealingWarrantyExpiresDate", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#UndergroundStructure_collapse" aria-expanded="true" aria-controls="UndergroundStructure_collapse">UndergroundStructure</a>
<div id="UndergroundStructure_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Structure.prototype.template.call (this) +
`
{{#hasVentilation}}<div><b>hasVentilation</b>: {{hasVentilation}}</div>{{/hasVentilation}}
{{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
{{#material}}<div><b>material</b>: {{material}}</div>{{/material}}
{{#sealingWarrantyExpiresDate}}<div><b>sealingWarrantyExpiresDate</b>: {{sealingWarrantyExpiresDate}}</div>{{/sealingWarrantyExpiresDate}}
</div>
`
                );
           }        }

        return (
            {
                TowerConstructionKind: TowerConstructionKind,
                FailureEvent: FailureEvent,
                BushingInsulationPfTestKind: BushingInsulationPfTestKind,
                FinancialInfo: FinancialInfo,
                BushingInsulationPF: BushingInsulationPF,
                StructureSupportKind: StructureSupportKind,
                GenericAssetModelOrMaterial: GenericAssetModelOrMaterial,
                TransformerObservation: TransformerObservation,
                CoolingPowerRating: CoolingPowerRating,
                Reconditioning: Reconditioning,
                Medium: Medium,
                Cabinet: Cabinet,
                StreetlightLampKind: StreetlightLampKind,
                PoleBaseKind: PoleBaseKind,
                Tower: Tower,
                DimensionsInfo: DimensionsInfo,
                JointConfigurationKind: JointConfigurationKind,
                JointFillKind: JointFillKind,
                AnchorKind: AnchorKind,
                Specification: Specification,
                Bushing: Bushing,
                UndergroundStructureKind: UndergroundStructureKind,
                StructureSupport: StructureSupport,
                Facility: Facility,
                DuctBank: DuctBank,
                FailureIsolationMethodKind: FailureIsolationMethodKind,
                CoolingKind: CoolingKind,
                MediumKind: MediumKind,
                Streetlight: Streetlight,
                UndergroundStructure: UndergroundStructure,
                Joint: Joint,
                WindingInsulation: WindingInsulation,
                FACTSDeviceKind: FACTSDeviceKind,
                ReliabilityInfo: ReliabilityInfo,
                BushingInsulationKind: BushingInsulationKind,
                Pole: Pole,
                FACTSDevice: FACTSDevice,
                AssetPropertyCurve: AssetPropertyCurve,
                Structure: Structure,
                PoleTreatmentKind: PoleTreatmentKind,
                PolePreservativeKind: PolePreservativeKind,
                StructureMaterialKind: StructureMaterialKind
            }
        );
    }
);