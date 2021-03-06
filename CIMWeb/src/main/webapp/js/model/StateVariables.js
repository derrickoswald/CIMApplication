define
(
    ["model/base", "model/Wires"],
    /**
     * State variables for analysis solutions such as powerflow.
     *
     */
    function (base, Wires)
    {
        /**
         * An abstract class for state variables.
         *
         */
        class StateVariable extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.StateVariable;
                if (null == bucket)
                   cim_data.StateVariable = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.StateVariable[obj.id];
            }

            parse (context, sub)
            {
                let obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "StateVariable";
                let bucket = context.parsed.StateVariable;
                if (null == bucket)
                   context.parsed.StateVariable = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = [];

                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#StateVariable_collapse" aria-expanded="true" aria-controls="StateVariable_collapse" style="margin-left: 10px;">StateVariable</a></legend>
                    <div id="StateVariable_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + base.Element.prototype.template.call (this) +
                    `
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
            }

            uncondition (obj)
            {
                super.uncondition (obj);
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_StateVariable_collapse" aria-expanded="true" aria-controls="{{id}}_StateVariable_collapse" style="margin-left: 10px;">StateVariable</a></legend>
                    <div id="{{id}}_StateVariable_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + base.Element.prototype.edit_template.call (this) +
                    `
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                obj = obj || { id: id, cls: "StateVariable" };
                super.submit (id, obj);

                return (obj);
            }
        }

        /**
         * State variable for switch.
         *
         */
        class SvSwitch extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvSwitch;
                if (null == bucket)
                   cim_data.SvSwitch = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvSwitch[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvSwitch";
                base.parse_element (/<cim:SvSwitch.open>([\s\S]*?)<\/cim:SvSwitch.open>/g, obj, "open", base.to_boolean, sub, context);
                base.parse_attribute (/<cim:SvSwitch.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvSwitch.Switch\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "Switch", sub, context);
                let bucket = context.parsed.SvSwitch;
                if (null == bucket)
                   context.parsed.SvSwitch = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvSwitch", "open", "open",  base.from_boolean, fields);
                base.export_attribute (obj, "SvSwitch", "phase", "phase", fields);
                base.export_attribute (obj, "SvSwitch", "Switch", "Switch", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvSwitch_collapse" aria-expanded="true" aria-controls="SvSwitch_collapse" style="margin-left: 10px;">SvSwitch</a></legend>
                    <div id="SvSwitch_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#open}}<div><b>open</b>: {{open}}</div>{{/open}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#Switch}}<div><b>Switch</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{Switch}}");}); return false;'>{{Switch}}</a></div>{{/Switch}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvSwitch_collapse" aria-expanded="true" aria-controls="{{id}}_SvSwitch_collapse" style="margin-left: 10px;">SvSwitch</a></legend>
                    <div id="{{id}}_SvSwitch_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><div class='col-sm-4' for='{{id}}_open'>open: </div><div class='col-sm-8'><div class='form-check'><input id='{{id}}_open' class='form-check-input' type='checkbox'{{#open}} checked{{/open}}></div></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Switch'>Switch: </label><div class='col-sm-8'><input id='{{id}}_Switch' class='form-control' type='text'{{#Switch}} value='{{Switch}}'{{/Switch}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvSwitch" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_open").checked; if (temp) obj["open"] = true;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_Switch").value; if ("" !== temp) obj["Switch"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Switch", "1", "0..*", "Switch", "SvSwitch"]
                        ]
                    )
                );
            }
        }

        /**
         * The SvInjection reports the calculated bus injection minus the sum of the terminal flows.
         *
         * The terminal flow is positive out from the bus (load sign convention) and bus injection has positive flow into the bus. SvInjection may have the remainder after state estimation or slack after power flow calculation.
         *
         */
        class SvInjection extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvInjection;
                if (null == bucket)
                   cim_data.SvInjection = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvInjection[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvInjection";
                base.parse_element (/<cim:SvInjection.pInjection>([\s\S]*?)<\/cim:SvInjection.pInjection>/g, obj, "pInjection", base.to_string, sub, context);
                base.parse_element (/<cim:SvInjection.qInjection>([\s\S]*?)<\/cim:SvInjection.qInjection>/g, obj, "qInjection", base.to_string, sub, context);
                base.parse_attribute (/<cim:SvInjection.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvInjection.TopologicalNode\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "TopologicalNode", sub, context);
                let bucket = context.parsed.SvInjection;
                if (null == bucket)
                   context.parsed.SvInjection = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvInjection", "pInjection", "pInjection",  base.from_string, fields);
                base.export_element (obj, "SvInjection", "qInjection", "qInjection",  base.from_string, fields);
                base.export_attribute (obj, "SvInjection", "phase", "phase", fields);
                base.export_attribute (obj, "SvInjection", "TopologicalNode", "TopologicalNode", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvInjection_collapse" aria-expanded="true" aria-controls="SvInjection_collapse" style="margin-left: 10px;">SvInjection</a></legend>
                    <div id="SvInjection_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#pInjection}}<div><b>pInjection</b>: {{pInjection}}</div>{{/pInjection}}
                    {{#qInjection}}<div><b>qInjection</b>: {{qInjection}}</div>{{/qInjection}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#TopologicalNode}}<div><b>TopologicalNode</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{TopologicalNode}}");}); return false;'>{{TopologicalNode}}</a></div>{{/TopologicalNode}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvInjection_collapse" aria-expanded="true" aria-controls="{{id}}_SvInjection_collapse" style="margin-left: 10px;">SvInjection</a></legend>
                    <div id="{{id}}_SvInjection_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_pInjection'>pInjection: </label><div class='col-sm-8'><input id='{{id}}_pInjection' class='form-control' type='text'{{#pInjection}} value='{{pInjection}}'{{/pInjection}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_qInjection'>qInjection: </label><div class='col-sm-8'><input id='{{id}}_qInjection' class='form-control' type='text'{{#qInjection}} value='{{qInjection}}'{{/qInjection}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_TopologicalNode'>TopologicalNode: </label><div class='col-sm-8'><input id='{{id}}_TopologicalNode' class='form-control' type='text'{{#TopologicalNode}} value='{{TopologicalNode}}'{{/TopologicalNode}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvInjection" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_pInjection").value; if ("" !== temp) obj["pInjection"] = temp;
                temp = document.getElementById (id + "_qInjection").value; if ("" !== temp) obj["qInjection"] = temp;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_TopologicalNode").value; if ("" !== temp) obj["TopologicalNode"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["TopologicalNode", "1", "0..*", "TopologicalNode", "SvInjection"]
                        ]
                    )
                );
            }
        }

        /**
         * State variable for status.
         *
         */
        class SvStatus extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvStatus;
                if (null == bucket)
                   cim_data.SvStatus = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvStatus[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvStatus";
                base.parse_element (/<cim:SvStatus.inService>([\s\S]*?)<\/cim:SvStatus.inService>/g, obj, "inService", base.to_boolean, sub, context);
                base.parse_attribute (/<cim:SvStatus.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvStatus.ConductingEquipment\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "ConductingEquipment", sub, context);
                let bucket = context.parsed.SvStatus;
                if (null == bucket)
                   context.parsed.SvStatus = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvStatus", "inService", "inService",  base.from_boolean, fields);
                base.export_attribute (obj, "SvStatus", "phase", "phase", fields);
                base.export_attribute (obj, "SvStatus", "ConductingEquipment", "ConductingEquipment", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvStatus_collapse" aria-expanded="true" aria-controls="SvStatus_collapse" style="margin-left: 10px;">SvStatus</a></legend>
                    <div id="SvStatus_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#inService}}<div><b>inService</b>: {{inService}}</div>{{/inService}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#ConductingEquipment}}<div><b>ConductingEquipment</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{ConductingEquipment}}");}); return false;'>{{ConductingEquipment}}</a></div>{{/ConductingEquipment}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvStatus_collapse" aria-expanded="true" aria-controls="{{id}}_SvStatus_collapse" style="margin-left: 10px;">SvStatus</a></legend>
                    <div id="{{id}}_SvStatus_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><div class='col-sm-4' for='{{id}}_inService'>inService: </div><div class='col-sm-8'><div class='form-check'><input id='{{id}}_inService' class='form-check-input' type='checkbox'{{#inService}} checked{{/inService}}></div></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ConductingEquipment'>ConductingEquipment: </label><div class='col-sm-8'><input id='{{id}}_ConductingEquipment' class='form-control' type='text'{{#ConductingEquipment}} value='{{ConductingEquipment}}'{{/ConductingEquipment}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvStatus" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_inService").checked; if (temp) obj["inService"] = true;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_ConductingEquipment").value; if ("" !== temp) obj["ConductingEquipment"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["ConductingEquipment", "1", "0..*", "ConductingEquipment", "SvStatus"]
                        ]
                    )
                );
            }
        }

        /**
         * State variable for power flow.
         *
         * Load convention is used for flow direction. This means flow out from the TopologicalNode into the equipment is positive.
         *
         */
        class SvPowerFlow extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvPowerFlow;
                if (null == bucket)
                   cim_data.SvPowerFlow = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvPowerFlow[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvPowerFlow";
                base.parse_element (/<cim:SvPowerFlow.p>([\s\S]*?)<\/cim:SvPowerFlow.p>/g, obj, "p", base.to_string, sub, context);
                base.parse_element (/<cim:SvPowerFlow.q>([\s\S]*?)<\/cim:SvPowerFlow.q>/g, obj, "q", base.to_string, sub, context);
                base.parse_attribute (/<cim:SvPowerFlow.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvPowerFlow.Terminal\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "Terminal", sub, context);
                let bucket = context.parsed.SvPowerFlow;
                if (null == bucket)
                   context.parsed.SvPowerFlow = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvPowerFlow", "p", "p",  base.from_string, fields);
                base.export_element (obj, "SvPowerFlow", "q", "q",  base.from_string, fields);
                base.export_attribute (obj, "SvPowerFlow", "phase", "phase", fields);
                base.export_attribute (obj, "SvPowerFlow", "Terminal", "Terminal", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvPowerFlow_collapse" aria-expanded="true" aria-controls="SvPowerFlow_collapse" style="margin-left: 10px;">SvPowerFlow</a></legend>
                    <div id="SvPowerFlow_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#p}}<div><b>p</b>: {{p}}</div>{{/p}}
                    {{#q}}<div><b>q</b>: {{q}}</div>{{/q}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#Terminal}}<div><b>Terminal</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{Terminal}}");}); return false;'>{{Terminal}}</a></div>{{/Terminal}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvPowerFlow_collapse" aria-expanded="true" aria-controls="{{id}}_SvPowerFlow_collapse" style="margin-left: 10px;">SvPowerFlow</a></legend>
                    <div id="{{id}}_SvPowerFlow_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_p'>p: </label><div class='col-sm-8'><input id='{{id}}_p' class='form-control' type='text'{{#p}} value='{{p}}'{{/p}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_q'>q: </label><div class='col-sm-8'><input id='{{id}}_q' class='form-control' type='text'{{#q}} value='{{q}}'{{/q}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Terminal'>Terminal: </label><div class='col-sm-8'><input id='{{id}}_Terminal' class='form-control' type='text'{{#Terminal}} value='{{Terminal}}'{{/Terminal}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvPowerFlow" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_p").value; if ("" !== temp) obj["p"] = temp;
                temp = document.getElementById (id + "_q").value; if ("" !== temp) obj["q"] = temp;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_Terminal").value; if ("" !== temp) obj["Terminal"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Terminal", "1", "0..*", "Terminal", "SvPowerFlow"]
                        ]
                    )
                );
            }
        }

        /**
         * State variable for the number of sections in service for a shunt compensator.
         *
         */
        class SvShuntCompensatorSections extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvShuntCompensatorSections;
                if (null == bucket)
                   cim_data.SvShuntCompensatorSections = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvShuntCompensatorSections[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvShuntCompensatorSections";
                base.parse_element (/<cim:SvShuntCompensatorSections.sections>([\s\S]*?)<\/cim:SvShuntCompensatorSections.sections>/g, obj, "sections", base.to_float, sub, context);
                base.parse_attribute (/<cim:SvShuntCompensatorSections.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvShuntCompensatorSections.ShuntCompensator\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "ShuntCompensator", sub, context);
                let bucket = context.parsed.SvShuntCompensatorSections;
                if (null == bucket)
                   context.parsed.SvShuntCompensatorSections = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvShuntCompensatorSections", "sections", "sections",  base.from_float, fields);
                base.export_attribute (obj, "SvShuntCompensatorSections", "phase", "phase", fields);
                base.export_attribute (obj, "SvShuntCompensatorSections", "ShuntCompensator", "ShuntCompensator", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvShuntCompensatorSections_collapse" aria-expanded="true" aria-controls="SvShuntCompensatorSections_collapse" style="margin-left: 10px;">SvShuntCompensatorSections</a></legend>
                    <div id="SvShuntCompensatorSections_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#sections}}<div><b>sections</b>: {{sections}}</div>{{/sections}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#ShuntCompensator}}<div><b>ShuntCompensator</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{ShuntCompensator}}");}); return false;'>{{ShuntCompensator}}</a></div>{{/ShuntCompensator}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvShuntCompensatorSections_collapse" aria-expanded="true" aria-controls="{{id}}_SvShuntCompensatorSections_collapse" style="margin-left: 10px;">SvShuntCompensatorSections</a></legend>
                    <div id="{{id}}_SvShuntCompensatorSections_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_sections'>sections: </label><div class='col-sm-8'><input id='{{id}}_sections' class='form-control' type='text'{{#sections}} value='{{sections}}'{{/sections}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ShuntCompensator'>ShuntCompensator: </label><div class='col-sm-8'><input id='{{id}}_ShuntCompensator' class='form-control' type='text'{{#ShuntCompensator}} value='{{ShuntCompensator}}'{{/ShuntCompensator}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvShuntCompensatorSections" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_sections").value; if ("" !== temp) obj["sections"] = temp;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_ShuntCompensator").value; if ("" !== temp) obj["ShuntCompensator"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["ShuntCompensator", "1", "0..*", "ShuntCompensator", "SvShuntCompensatorSections"]
                        ]
                    )
                );
            }
        }

        /**
         * State variable for voltage.
         *
         */
        class SvVoltage extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvVoltage;
                if (null == bucket)
                   cim_data.SvVoltage = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvVoltage[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvVoltage";
                base.parse_element (/<cim:SvVoltage.angle>([\s\S]*?)<\/cim:SvVoltage.angle>/g, obj, "angle", base.to_string, sub, context);
                base.parse_element (/<cim:SvVoltage.v>([\s\S]*?)<\/cim:SvVoltage.v>/g, obj, "v", base.to_string, sub, context);
                base.parse_attribute (/<cim:SvVoltage.phase\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "phase", sub, context);
                base.parse_attribute (/<cim:SvVoltage.TopologicalNode\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "TopologicalNode", sub, context);
                let bucket = context.parsed.SvVoltage;
                if (null == bucket)
                   context.parsed.SvVoltage = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvVoltage", "angle", "angle",  base.from_string, fields);
                base.export_element (obj, "SvVoltage", "v", "v",  base.from_string, fields);
                base.export_attribute (obj, "SvVoltage", "phase", "phase", fields);
                base.export_attribute (obj, "SvVoltage", "TopologicalNode", "TopologicalNode", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvVoltage_collapse" aria-expanded="true" aria-controls="SvVoltage_collapse" style="margin-left: 10px;">SvVoltage</a></legend>
                    <div id="SvVoltage_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#angle}}<div><b>angle</b>: {{angle}}</div>{{/angle}}
                    {{#v}}<div><b>v</b>: {{v}}</div>{{/v}}
                    {{#phase}}<div><b>phase</b>: {{phase}}</div>{{/phase}}
                    {{#TopologicalNode}}<div><b>TopologicalNode</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{TopologicalNode}}");}); return false;'>{{TopologicalNode}}</a></div>{{/TopologicalNode}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj["phaseSinglePhaseKind"] = [{ id: '', selected: (!obj["phase"])}]; for (let property in Wires.SinglePhaseKind) obj["phaseSinglePhaseKind"].push ({ id: property, selected: obj["phase"] && obj["phase"].endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj["phaseSinglePhaseKind"];
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvVoltage_collapse" aria-expanded="true" aria-controls="{{id}}_SvVoltage_collapse" style="margin-left: 10px;">SvVoltage</a></legend>
                    <div id="{{id}}_SvVoltage_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_angle'>angle: </label><div class='col-sm-8'><input id='{{id}}_angle' class='form-control' type='text'{{#angle}} value='{{angle}}'{{/angle}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_v'>v: </label><div class='col-sm-8'><input id='{{id}}_v' class='form-control' type='text'{{#v}} value='{{v}}'{{/v}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phase'>phase: </label><div class='col-sm-8'><select id='{{id}}_phase' class='form-control custom-select'>{{#phaseSinglePhaseKind}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/phaseSinglePhaseKind}}</select></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_TopologicalNode'>TopologicalNode: </label><div class='col-sm-8'><input id='{{id}}_TopologicalNode' class='form-control' type='text'{{#TopologicalNode}} value='{{TopologicalNode}}'{{/TopologicalNode}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvVoltage" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_angle").value; if ("" !== temp) obj["angle"] = temp;
                temp = document.getElementById (id + "_v").value; if ("" !== temp) obj["v"] = temp;
                temp = Wires.SinglePhaseKind[document.getElementById (id + "_phase").value]; if (temp) obj["phase"] = "http://iec.ch/TC57/2016/CIM-schema-cim17#SinglePhaseKind." + temp; else delete obj["phase"];
                temp = document.getElementById (id + "_TopologicalNode").value; if ("" !== temp) obj["TopologicalNode"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["TopologicalNode", "1", "0..*", "TopologicalNode", "SvVoltage"]
                        ]
                    )
                );
            }
        }

        /**
         * State variable for transformer tap step.
         *
         */
        class SvTapStep extends StateVariable
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.SvTapStep;
                if (null == bucket)
                   cim_data.SvTapStep = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SvTapStep[obj.id];
            }

            parse (context, sub)
            {
                let obj = StateVariable.prototype.parse.call (this, context, sub);
                obj.cls = "SvTapStep";
                base.parse_element (/<cim:SvTapStep.position>([\s\S]*?)<\/cim:SvTapStep.position>/g, obj, "position", base.to_float, sub, context);
                base.parse_attribute (/<cim:SvTapStep.TapChanger\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "TapChanger", sub, context);
                let bucket = context.parsed.SvTapStep;
                if (null == bucket)
                   context.parsed.SvTapStep = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StateVariable.prototype.export.call (this, obj, false);

                base.export_element (obj, "SvTapStep", "position", "position",  base.from_float, fields);
                base.export_attribute (obj, "SvTapStep", "TapChanger", "TapChanger", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#SvTapStep_collapse" aria-expanded="true" aria-controls="SvTapStep_collapse" style="margin-left: 10px;">SvTapStep</a></legend>
                    <div id="SvTapStep_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.template.call (this) +
                    `
                    {{#position}}<div><b>position</b>: {{position}}</div>{{/position}}
                    {{#TapChanger}}<div><b>TapChanger</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{TapChanger}}");}); return false;'>{{TapChanger}}</a></div>{{/TapChanger}}
                    </div>
                    </fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
            }

            uncondition (obj)
            {
                super.uncondition (obj);
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_SvTapStep_collapse" aria-expanded="true" aria-controls="{{id}}_SvTapStep_collapse" style="margin-left: 10px;">SvTapStep</a></legend>
                    <div id="{{id}}_SvTapStep_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StateVariable.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_position'>position: </label><div class='col-sm-8'><input id='{{id}}_position' class='form-control' type='text'{{#position}} value='{{position}}'{{/position}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_TapChanger'>TapChanger: </label><div class='col-sm-8'><input id='{{id}}_TapChanger' class='form-control' type='text'{{#TapChanger}} value='{{TapChanger}}'{{/TapChanger}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "SvTapStep" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_position").value; if ("" !== temp) obj["position"] = temp;
                temp = document.getElementById (id + "_TapChanger").value; if ("" !== temp) obj["TapChanger"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["TapChanger", "1", "0..1", "TapChanger", "SvTapStep"]
                        ]
                    )
                );
            }
        }

        return (
            {
                StateVariable: StateVariable,
                SvInjection: SvInjection,
                SvTapStep: SvTapStep,
                SvShuntCompensatorSections: SvShuntCompensatorSections,
                SvPowerFlow: SvPowerFlow,
                SvStatus: SvStatus,
                SvVoltage: SvVoltage,
                SvSwitch: SvSwitch
            }
        );
    }
);