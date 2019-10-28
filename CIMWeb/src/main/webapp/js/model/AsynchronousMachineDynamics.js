define
(
    ["model/base", "model/StandardModels"],
    /**
     * An asynchronous machine model represents a (induction) generator or motor with no external connection to the rotor windings, e.g. a squirrel-cage induction machine.
     *
     * The interconnection with the electrical network equations can differ among simulation tools.  The program only needs to know the terminal to which this asynchronous machine is connected in order to establish the correct interconnection.  The interconnection with the motor�s equipment could also differ due to input and output signals required by standard models.
     * The asynchronous machine model is used to model wind generators type 1 and type 2.  For these, normal practice is to include the rotor flux transients and neglect the stator flux transients.
     *
     */
    function (base, StandardModels)
    {

        /**
         * Asynchronous machine whose behaviour is described by reference to a standard model expressed in either time constant reactance form or equivalent circuit form <font color="#0f0f0f">or by definition of a user-defined model.</font>
         * Parameter details:
         * <ol>
         * <li>Asynchronous machine parameters such as <i>Xl, Xs,</i> etc. are actually used as inductances in the model, but are commonly referred to as reactances since, at nominal frequency, the PU values are the same.
         *
         * However, some references use the symbol <i>L</i> instead of <i>X</i>.</li>
         * </ol>
         *
         */
        class AsynchronousMachineDynamics extends StandardModels.RotatingMachineDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.AsynchronousMachineDynamics;
                if (null == bucket)
                   cim_data.AsynchronousMachineDynamics = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AsynchronousMachineDynamics[obj.id];
            }

            parse (context, sub)
            {
                let obj = StandardModels.RotatingMachineDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "AsynchronousMachineDynamics";
                base.parse_attribute (/<cim:AsynchronousMachineDynamics.AsynchronousMachine\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "AsynchronousMachine", sub, context);
                base.parse_attribute (/<cim:AsynchronousMachineDynamics.WindTurbineType1or2Dynamics\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "WindTurbineType1or2Dynamics", sub, context);
                base.parse_attribute (/<cim:AsynchronousMachineDynamics.TurbineGovernorDynamics\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "TurbineGovernorDynamics", sub, context);
                base.parse_attribute (/<cim:AsynchronousMachineDynamics.MechanicalLoadDynamics\s+rdf:resource\s*?=\s*?(["'])([\s\S]*?)\1\s*?\/>/g, obj, "MechanicalLoadDynamics", sub, context);
                let bucket = context.parsed.AsynchronousMachineDynamics;
                if (null == bucket)
                   context.parsed.AsynchronousMachineDynamics = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = StandardModels.RotatingMachineDynamics.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "AsynchronousMachineDynamics", "AsynchronousMachine", "AsynchronousMachine", fields);
                base.export_attribute (obj, "AsynchronousMachineDynamics", "WindTurbineType1or2Dynamics", "WindTurbineType1or2Dynamics", fields);
                base.export_attribute (obj, "AsynchronousMachineDynamics", "TurbineGovernorDynamics", "TurbineGovernorDynamics", fields);
                base.export_attribute (obj, "AsynchronousMachineDynamics", "MechanicalLoadDynamics", "MechanicalLoadDynamics", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#AsynchronousMachineDynamics_collapse" aria-expanded="true" aria-controls="AsynchronousMachineDynamics_collapse" style="margin-left: 10px;">AsynchronousMachineDynamics</a></legend>
                    <div id="AsynchronousMachineDynamics_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StandardModels.RotatingMachineDynamics.prototype.template.call (this) +
                    `
                    {{#AsynchronousMachine}}<div><b>AsynchronousMachine</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{AsynchronousMachine}}");}); return false;'>{{AsynchronousMachine}}</a></div>{{/AsynchronousMachine}}
                    {{#WindTurbineType1or2Dynamics}}<div><b>WindTurbineType1or2Dynamics</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{WindTurbineType1or2Dynamics}}");}); return false;'>{{WindTurbineType1or2Dynamics}}</a></div>{{/WindTurbineType1or2Dynamics}}
                    {{#TurbineGovernorDynamics}}<div><b>TurbineGovernorDynamics</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{TurbineGovernorDynamics}}");}); return false;'>{{TurbineGovernorDynamics}}</a></div>{{/TurbineGovernorDynamics}}
                    {{#MechanicalLoadDynamics}}<div><b>MechanicalLoadDynamics</b>: <a href='#' onclick='require(["cimmap"], function(cimmap) {cimmap.select ("{{MechanicalLoadDynamics}}");}); return false;'>{{MechanicalLoadDynamics}}</a></div>{{/MechanicalLoadDynamics}}
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
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_AsynchronousMachineDynamics_collapse" aria-expanded="true" aria-controls="{{id}}_AsynchronousMachineDynamics_collapse" style="margin-left: 10px;">AsynchronousMachineDynamics</a></legend>
                    <div id="{{id}}_AsynchronousMachineDynamics_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + StandardModels.RotatingMachineDynamics.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_AsynchronousMachine'>AsynchronousMachine: </label><div class='col-sm-8'><input id='{{id}}_AsynchronousMachine' class='form-control' type='text'{{#AsynchronousMachine}} value='{{AsynchronousMachine}}'{{/AsynchronousMachine}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_WindTurbineType1or2Dynamics'>WindTurbineType1or2Dynamics: </label><div class='col-sm-8'><input id='{{id}}_WindTurbineType1or2Dynamics' class='form-control' type='text'{{#WindTurbineType1or2Dynamics}} value='{{WindTurbineType1or2Dynamics}}'{{/WindTurbineType1or2Dynamics}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_TurbineGovernorDynamics'>TurbineGovernorDynamics: </label><div class='col-sm-8'><input id='{{id}}_TurbineGovernorDynamics' class='form-control' type='text'{{#TurbineGovernorDynamics}} value='{{TurbineGovernorDynamics}}'{{/TurbineGovernorDynamics}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_MechanicalLoadDynamics'>MechanicalLoadDynamics: </label><div class='col-sm-8'><input id='{{id}}_MechanicalLoadDynamics' class='form-control' type='text'{{#MechanicalLoadDynamics}} value='{{MechanicalLoadDynamics}}'{{/MechanicalLoadDynamics}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "AsynchronousMachineDynamics" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_AsynchronousMachine").value; if ("" !== temp) obj["AsynchronousMachine"] = temp;
                temp = document.getElementById (id + "_WindTurbineType1or2Dynamics").value; if ("" !== temp) obj["WindTurbineType1or2Dynamics"] = temp;
                temp = document.getElementById (id + "_TurbineGovernorDynamics").value; if ("" !== temp) obj["TurbineGovernorDynamics"] = temp;
                temp = document.getElementById (id + "_MechanicalLoadDynamics").value; if ("" !== temp) obj["MechanicalLoadDynamics"] = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["AsynchronousMachine", "1", "0..1", "AsynchronousMachine", "AsynchronousMachineDynamics"],
                            ["WindTurbineType1or2Dynamics", "0..1", "1", "WindTurbineType1or2Dynamics", "AsynchronousMachineDynamics"],
                            ["TurbineGovernorDynamics", "0..1", "0..1", "TurbineGovernorDynamics", "AsynchronousMachineDynamics"],
                            ["MechanicalLoadDynamics", "0..1", "0..1", "MechanicalLoadDynamics", "AsynchronousMachineDynamics"]
                        ]
                    )
                );
            }
        }

        /**
         * The electrical equations of all variations of the asynchronous model are based on the AsynchronousEquivalentCircuit diagram for the direct- and quadrature- axes, with two equivalent rotor windings in each axis.
         *
         * Equations for conversion between equivalent circuit and time constant reactance forms:
         * <i>Xs</i> = <i>Xm</i> + <i>Xl</i>
         * <i>X'</i> = <i>Xl</i> + <i>Xm</i> x <i>Xlr1 </i>/ (<i>Xm </i>+ <i>Xlr1</i>)
         * <i>X''</i> = <i>Xl</i> + <i>Xm</i> x <i>Xlr1</i> x <i>Xlr2</i> / (<i>Xm</i> x <i>Xlr1</i> + <i>Xm</i> x <i>Xlr2</i> + <i>Xlr1</i> x <i>Xlr2</i>)
         * <i>T'o</i> = (<i>Xm</i> + <i>Xlr1</i>) / (<i>omega</i><i><sub>0</sub></i> x <i>Rr1</i>)
         * <i>T''o</i> = (<i>Xm</i> x <i>Xlr1</i> + <i>Xm</i> x <i>Xlr2</i> + <i>Xlr1</i> x <i>Xlr2</i>) / (<i>omega</i><i><sub>0</sub></i> x <i>Rr2</i> x (<i>Xm</i> + <i>Xlr1</i>)
         * Same equations using CIM attributes from AsynchronousMachineTimeConstantReactance class on left of "=" and AsynchronousMachineEquivalentCircuit class on right (except as noted):
         * xs = xm + RotatingMachineDynamics.statorLeakageReactance
         * xp = RotatingMachineDynamics.statorLeakageReactance + xm x xlr1 / (xm + xlr1)
         * xpp = RotatingMachineDynamics.statorLeakageReactance + xm x xlr1 x xlr2 / (xm x xlr1 + xm x xlr2 + xlr1 x xlr2)
         * tpo = (xm + xlr1) / (2 x pi x nominal frequency x rr1)
         * tppo = (xm x xlr1 + xm x xlr2 + xlr1 x xlr2) / (2 x pi x nominal frequency x rr2 x (xm + xlr1).
         *
         */
        class AsynchronousMachineEquivalentCircuit extends AsynchronousMachineDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.AsynchronousMachineEquivalentCircuit;
                if (null == bucket)
                   cim_data.AsynchronousMachineEquivalentCircuit = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AsynchronousMachineEquivalentCircuit[obj.id];
            }

            parse (context, sub)
            {
                let obj = AsynchronousMachineDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "AsynchronousMachineEquivalentCircuit";
                base.parse_element (/<cim:AsynchronousMachineEquivalentCircuit.rr1>([\s\S]*?)<\/cim:AsynchronousMachineEquivalentCircuit.rr1>/g, obj, "rr1", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineEquivalentCircuit.rr2>([\s\S]*?)<\/cim:AsynchronousMachineEquivalentCircuit.rr2>/g, obj, "rr2", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineEquivalentCircuit.xlr1>([\s\S]*?)<\/cim:AsynchronousMachineEquivalentCircuit.xlr1>/g, obj, "xlr1", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineEquivalentCircuit.xlr2>([\s\S]*?)<\/cim:AsynchronousMachineEquivalentCircuit.xlr2>/g, obj, "xlr2", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineEquivalentCircuit.xm>([\s\S]*?)<\/cim:AsynchronousMachineEquivalentCircuit.xm>/g, obj, "xm", base.to_string, sub, context);
                let bucket = context.parsed.AsynchronousMachineEquivalentCircuit;
                if (null == bucket)
                   context.parsed.AsynchronousMachineEquivalentCircuit = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = AsynchronousMachineDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "AsynchronousMachineEquivalentCircuit", "rr1", "rr1",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineEquivalentCircuit", "rr2", "rr2",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineEquivalentCircuit", "xlr1", "xlr1",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineEquivalentCircuit", "xlr2", "xlr2",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineEquivalentCircuit", "xm", "xm",  base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#AsynchronousMachineEquivalentCircuit_collapse" aria-expanded="true" aria-controls="AsynchronousMachineEquivalentCircuit_collapse" style="margin-left: 10px;">AsynchronousMachineEquivalentCircuit</a></legend>
                    <div id="AsynchronousMachineEquivalentCircuit_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + AsynchronousMachineDynamics.prototype.template.call (this) +
                    `
                    {{#rr1}}<div><b>rr1</b>: {{rr1}}</div>{{/rr1}}
                    {{#rr2}}<div><b>rr2</b>: {{rr2}}</div>{{/rr2}}
                    {{#xlr1}}<div><b>xlr1</b>: {{xlr1}}</div>{{/xlr1}}
                    {{#xlr2}}<div><b>xlr2</b>: {{xlr2}}</div>{{/xlr2}}
                    {{#xm}}<div><b>xm</b>: {{xm}}</div>{{/xm}}
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
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_AsynchronousMachineEquivalentCircuit_collapse" aria-expanded="true" aria-controls="{{id}}_AsynchronousMachineEquivalentCircuit_collapse" style="margin-left: 10px;">AsynchronousMachineEquivalentCircuit</a></legend>
                    <div id="{{id}}_AsynchronousMachineEquivalentCircuit_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + AsynchronousMachineDynamics.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_rr1'>rr1: </label><div class='col-sm-8'><input id='{{id}}_rr1' class='form-control' type='text'{{#rr1}} value='{{rr1}}'{{/rr1}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_rr2'>rr2: </label><div class='col-sm-8'><input id='{{id}}_rr2' class='form-control' type='text'{{#rr2}} value='{{rr2}}'{{/rr2}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xlr1'>xlr1: </label><div class='col-sm-8'><input id='{{id}}_xlr1' class='form-control' type='text'{{#xlr1}} value='{{xlr1}}'{{/xlr1}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xlr2'>xlr2: </label><div class='col-sm-8'><input id='{{id}}_xlr2' class='form-control' type='text'{{#xlr2}} value='{{xlr2}}'{{/xlr2}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xm'>xm: </label><div class='col-sm-8'><input id='{{id}}_xm' class='form-control' type='text'{{#xm}} value='{{xm}}'{{/xm}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "AsynchronousMachineEquivalentCircuit" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_rr1").value; if ("" !== temp) obj["rr1"] = temp;
                temp = document.getElementById (id + "_rr2").value; if ("" !== temp) obj["rr2"] = temp;
                temp = document.getElementById (id + "_xlr1").value; if ("" !== temp) obj["xlr1"] = temp;
                temp = document.getElementById (id + "_xlr2").value; if ("" !== temp) obj["xlr2"] = temp;
                temp = document.getElementById (id + "_xm").value; if ("" !== temp) obj["xm"] = temp;

                return (obj);
            }
        }

        /**
         * Parameter details:
         * <ol>
         * <li>If <i>X'' </i>=<i> X'</i>, a single cage (one equivalent rotor winding per axis) is modelled.</li>
         * <li>The �<i>p</i>� in the attribute names is a substitution for a �prime� in the usual parameter notation, e.g. <i>tpo</i> refers to <i>T'o</i>.</li>
         * </ol>
         * The parameters used for models expressed in time constant reactance form include:
         * - RotatingMachine.ratedS (<i>MVAbase</i>);
         * - RotatingMachineDynamics.damping (<i>D</i>);
         * - RotatingMachineDynamics.inertia (<i>H</i>);
         * - RotatingMachineDynamics.saturationFactor (<i>S1</i>);
         * - RotatingMachineDynamics.saturationFactor120 (<i>S12</i>);
         * - RotatingMachineDynamics.statorLeakageReactance (<i>Xl</i>);
         * - RotatingMachineDynamics.statorResistance (<i>Rs</i>);
         * - .xs (<i>Xs</i>);
         * - .xp (<i>X'</i>);
         * - .xpp (<i>X''</i>);
         * - .tpo (<i>T'o</i>);
         *
         * - .tppo (<i>T''o</i>).
         *
         */
        class AsynchronousMachineTimeConstantReactance extends AsynchronousMachineDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                let bucket = cim_data.AsynchronousMachineTimeConstantReactance;
                if (null == bucket)
                   cim_data.AsynchronousMachineTimeConstantReactance = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AsynchronousMachineTimeConstantReactance[obj.id];
            }

            parse (context, sub)
            {
                let obj = AsynchronousMachineDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "AsynchronousMachineTimeConstantReactance";
                base.parse_element (/<cim:AsynchronousMachineTimeConstantReactance.tpo>([\s\S]*?)<\/cim:AsynchronousMachineTimeConstantReactance.tpo>/g, obj, "tpo", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineTimeConstantReactance.tppo>([\s\S]*?)<\/cim:AsynchronousMachineTimeConstantReactance.tppo>/g, obj, "tppo", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineTimeConstantReactance.xp>([\s\S]*?)<\/cim:AsynchronousMachineTimeConstantReactance.xp>/g, obj, "xp", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineTimeConstantReactance.xpp>([\s\S]*?)<\/cim:AsynchronousMachineTimeConstantReactance.xpp>/g, obj, "xpp", base.to_string, sub, context);
                base.parse_element (/<cim:AsynchronousMachineTimeConstantReactance.xs>([\s\S]*?)<\/cim:AsynchronousMachineTimeConstantReactance.xs>/g, obj, "xs", base.to_string, sub, context);
                let bucket = context.parsed.AsynchronousMachineTimeConstantReactance;
                if (null == bucket)
                   context.parsed.AsynchronousMachineTimeConstantReactance = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                let fields = AsynchronousMachineDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "AsynchronousMachineTimeConstantReactance", "tpo", "tpo",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineTimeConstantReactance", "tppo", "tppo",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineTimeConstantReactance", "xp", "xp",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineTimeConstantReactance", "xpp", "xpp",  base.from_string, fields);
                base.export_element (obj, "AsynchronousMachineTimeConstantReactance", "xs", "xs",  base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields);

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#AsynchronousMachineTimeConstantReactance_collapse" aria-expanded="true" aria-controls="AsynchronousMachineTimeConstantReactance_collapse" style="margin-left: 10px;">AsynchronousMachineTimeConstantReactance</a></legend>
                    <div id="AsynchronousMachineTimeConstantReactance_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + AsynchronousMachineDynamics.prototype.template.call (this) +
                    `
                    {{#tpo}}<div><b>tpo</b>: {{tpo}}</div>{{/tpo}}
                    {{#tppo}}<div><b>tppo</b>: {{tppo}}</div>{{/tppo}}
                    {{#xp}}<div><b>xp</b>: {{xp}}</div>{{/xp}}
                    {{#xpp}}<div><b>xpp</b>: {{xpp}}</div>{{/xpp}}
                    {{#xs}}<div><b>xs</b>: {{xs}}</div>{{/xs}}
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
                    <legend class='col-form-legend'><a class="collapse-link" data-toggle="collapse" href="#{{id}}_AsynchronousMachineTimeConstantReactance_collapse" aria-expanded="true" aria-controls="{{id}}_AsynchronousMachineTimeConstantReactance_collapse" style="margin-left: 10px;">AsynchronousMachineTimeConstantReactance</a></legend>
                    <div id="{{id}}_AsynchronousMachineTimeConstantReactance_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + AsynchronousMachineDynamics.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_tpo'>tpo: </label><div class='col-sm-8'><input id='{{id}}_tpo' class='form-control' type='text'{{#tpo}} value='{{tpo}}'{{/tpo}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_tppo'>tppo: </label><div class='col-sm-8'><input id='{{id}}_tppo' class='form-control' type='text'{{#tppo}} value='{{tppo}}'{{/tppo}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xp'>xp: </label><div class='col-sm-8'><input id='{{id}}_xp' class='form-control' type='text'{{#xp}} value='{{xp}}'{{/xp}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xpp'>xpp: </label><div class='col-sm-8'><input id='{{id}}_xpp' class='form-control' type='text'{{#xpp}} value='{{xpp}}'{{/xpp}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_xs'>xs: </label><div class='col-sm-8'><input id='{{id}}_xs' class='form-control' type='text'{{#xs}} value='{{xs}}'{{/xs}}></div></div>
                    </div>
                    </fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                let temp;

                obj = obj || { id: id, cls: "AsynchronousMachineTimeConstantReactance" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_tpo").value; if ("" !== temp) obj["tpo"] = temp;
                temp = document.getElementById (id + "_tppo").value; if ("" !== temp) obj["tppo"] = temp;
                temp = document.getElementById (id + "_xp").value; if ("" !== temp) obj["xp"] = temp;
                temp = document.getElementById (id + "_xpp").value; if ("" !== temp) obj["xpp"] = temp;
                temp = document.getElementById (id + "_xs").value; if ("" !== temp) obj["xs"] = temp;

                return (obj);
            }
        }

        return (
            {
                AsynchronousMachineDynamics: AsynchronousMachineDynamics,
                AsynchronousMachineEquivalentCircuit: AsynchronousMachineEquivalentCircuit,
                AsynchronousMachineTimeConstantReactance: AsynchronousMachineTimeConstantReactance
            }
        );
    }
);