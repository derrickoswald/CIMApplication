define
(
    ["model/base", "model/StandardModels"],
    /**
     * The power system stabilizer (PSS) model provides an input (Vs) to the excitation system model to improve damping of system oscillations.
     *
     * A variety of input signals may be used depending on the particular design.
     *
     */
    function (base, StandardModels)
    {

        /**
         * Power system stabilizer function block whose behaviour is described by reference to a standard model <font color="#0f0f0f">or by definition of a user-defined model.</font>
         *
         */
        class PowerSystemStabilizerDynamics extends StandardModels.DynamicsFunctionBlock
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PowerSystemStabilizerDynamics;
                if (null == bucket)
                   cim_data.PowerSystemStabilizerDynamics = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PowerSystemStabilizerDynamics[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = StandardModels.DynamicsFunctionBlock.prototype.parse.call (this, context, sub);
                obj.cls = "PowerSystemStabilizerDynamics";
                base.parse_attribute (/<cim:PowerSystemStabilizerDynamics.ExcitationSystemDynamics\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ExcitationSystemDynamics", sub, context);

                var bucket = context.parsed.PowerSystemStabilizerDynamics;
                if (null == bucket)
                   context.parsed.PowerSystemStabilizerDynamics = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = StandardModels.DynamicsFunctionBlock.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "PowerSystemStabilizerDynamics", "ExcitationSystemDynamics", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PowerSystemStabilizerDynamics_collapse" aria-expanded="true" aria-controls="PowerSystemStabilizerDynamics_collapse">PowerSystemStabilizerDynamics</a>
<div id="PowerSystemStabilizerDynamics_collapse" class="collapse in" style="margin-left: 10px;">
`
      + StandardModels.DynamicsFunctionBlock.prototype.template.call (this) +
`
{{#ExcitationSystemDynamics}}<div><b>ExcitationSystemDynamics</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ExcitationSystemDynamics}}&quot;);})'>{{ExcitationSystemDynamics}}</a></div>{{/ExcitationSystemDynamics}}
</div>
`
                );
           }        }

        /**
         * Input signal type.
         *
         * In Dynamics modelling, commonly represented by j parameter.
         *
         */
        class InputSignalKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.InputSignalKind;
                if (null == bucket)
                   cim_data.InputSignalKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.InputSignalKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "InputSignalKind";
                base.parse_element (/<cim:InputSignalKind.rotorSpeed>([\s\S]*?)<\/cim:InputSignalKind.rotorSpeed>/g, obj, "rotorSpeed", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.rotorAngularFrequencyDeviation>([\s\S]*?)<\/cim:InputSignalKind.rotorAngularFrequencyDeviation>/g, obj, "rotorAngularFrequencyDeviation", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.busFrequency>([\s\S]*?)<\/cim:InputSignalKind.busFrequency>/g, obj, "busFrequency", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.busFrequencyDeviation>([\s\S]*?)<\/cim:InputSignalKind.busFrequencyDeviation>/g, obj, "busFrequencyDeviation", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.generatorElectricalPower>([\s\S]*?)<\/cim:InputSignalKind.generatorElectricalPower>/g, obj, "generatorElectricalPower", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.generatorAcceleratingPower>([\s\S]*?)<\/cim:InputSignalKind.generatorAcceleratingPower>/g, obj, "generatorAcceleratingPower", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.busVoltage>([\s\S]*?)<\/cim:InputSignalKind.busVoltage>/g, obj, "busVoltage", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.busVoltageDerivative>([\s\S]*?)<\/cim:InputSignalKind.busVoltageDerivative>/g, obj, "busVoltageDerivative", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.branchCurrent>([\s\S]*?)<\/cim:InputSignalKind.branchCurrent>/g, obj, "branchCurrent", base.to_string, sub, context);
                base.parse_element (/<cim:InputSignalKind.fieldCurrent>([\s\S]*?)<\/cim:InputSignalKind.fieldCurrent>/g, obj, "fieldCurrent", base.to_string, sub, context);

                var bucket = context.parsed.InputSignalKind;
                if (null == bucket)
                   context.parsed.InputSignalKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "InputSignalKind", "rotorSpeed", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "rotorAngularFrequencyDeviation", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "busFrequency", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "busFrequencyDeviation", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "generatorElectricalPower", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "generatorAcceleratingPower", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "busVoltage", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "busVoltageDerivative", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "branchCurrent", base.from_string, fields);
                base.export_element (obj, "InputSignalKind", "fieldCurrent", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#InputSignalKind_collapse" aria-expanded="true" aria-controls="InputSignalKind_collapse">InputSignalKind</a>
<div id="InputSignalKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#rotorSpeed}}<div><b>rotorSpeed</b>: {{rotorSpeed}}</div>{{/rotorSpeed}}
{{#rotorAngularFrequencyDeviation}}<div><b>rotorAngularFrequencyDeviation</b>: {{rotorAngularFrequencyDeviation}}</div>{{/rotorAngularFrequencyDeviation}}
{{#busFrequency}}<div><b>busFrequency</b>: {{busFrequency}}</div>{{/busFrequency}}
{{#busFrequencyDeviation}}<div><b>busFrequencyDeviation</b>: {{busFrequencyDeviation}}</div>{{/busFrequencyDeviation}}
{{#generatorElectricalPower}}<div><b>generatorElectricalPower</b>: {{generatorElectricalPower}}</div>{{/generatorElectricalPower}}
{{#generatorAcceleratingPower}}<div><b>generatorAcceleratingPower</b>: {{generatorAcceleratingPower}}</div>{{/generatorAcceleratingPower}}
{{#busVoltage}}<div><b>busVoltage</b>: {{busVoltage}}</div>{{/busVoltage}}
{{#busVoltageDerivative}}<div><b>busVoltageDerivative</b>: {{busVoltageDerivative}}</div>{{/busVoltageDerivative}}
{{#branchCurrent}}<div><b>branchCurrent</b>: {{branchCurrent}}</div>{{/branchCurrent}}
{{#fieldCurrent}}<div><b>fieldCurrent</b>: {{fieldCurrent}}</div>{{/fieldCurrent}}
</div>
`
                );
           }        }

        /**
         * PTI Microprocessor-Based Stabilizer type 1.
         *
         */
        class PssPTIST1 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssPTIST1;
                if (null == bucket)
                   cim_data.PssPTIST1 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssPTIST1[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssPTIST1";
                base.parse_element (/<cim:PssPTIST1.dtc>([\s\S]*?)<\/cim:PssPTIST1.dtc>/g, obj, "dtc", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.dtf>([\s\S]*?)<\/cim:PssPTIST1.dtf>/g, obj, "dtf", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.dtp>([\s\S]*?)<\/cim:PssPTIST1.dtp>/g, obj, "dtp", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.k>([\s\S]*?)<\/cim:PssPTIST1.k>/g, obj, "k", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.m>([\s\S]*?)<\/cim:PssPTIST1.m>/g, obj, "m", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.t1>([\s\S]*?)<\/cim:PssPTIST1.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.t2>([\s\S]*?)<\/cim:PssPTIST1.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.t3>([\s\S]*?)<\/cim:PssPTIST1.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.t4>([\s\S]*?)<\/cim:PssPTIST1.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.tf>([\s\S]*?)<\/cim:PssPTIST1.tf>/g, obj, "tf", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST1.tp>([\s\S]*?)<\/cim:PssPTIST1.tp>/g, obj, "tp", base.to_string, sub, context);

                var bucket = context.parsed.PssPTIST1;
                if (null == bucket)
                   context.parsed.PssPTIST1 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssPTIST1", "dtc", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "dtf", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "dtp", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "k", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "m", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "t1", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "t2", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "t3", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "t4", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "tf", base.from_string, fields);
                base.export_element (obj, "PssPTIST1", "tp", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssPTIST1_collapse" aria-expanded="true" aria-controls="PssPTIST1_collapse">PssPTIST1</a>
<div id="PssPTIST1_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#dtc}}<div><b>dtc</b>: {{dtc}}</div>{{/dtc}}
{{#dtf}}<div><b>dtf</b>: {{dtf}}</div>{{/dtf}}
{{#dtp}}<div><b>dtp</b>: {{dtp}}</div>{{/dtp}}
{{#k}}<div><b>k</b>: {{k}}</div>{{/k}}
{{#m}}<div><b>m</b>: {{m}}</div>{{/m}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#tf}}<div><b>tf</b>: {{tf}}</div>{{/tf}}
{{#tp}}<div><b>tp</b>: {{tp}}</div>{{/tp}}
</div>
`
                );
           }        }

        /**
         * The class represents IEEE Std 421.5-2005 type PSS2B power system stabilizer model.
         *
         * This stabilizer model is designed to represent a variety of dual-input stabilizers, which normally use combinations of power and speed or frequency to derive the stabilizing signal.
         *
         */
        class PssIEEE2B extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssIEEE2B;
                if (null == bucket)
                   cim_data.PssIEEE2B = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssIEEE2B[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssIEEE2B";
                base.parse_element (/<cim:PssIEEE2B.inputSignal1Type>([\s\S]*?)<\/cim:PssIEEE2B.inputSignal1Type>/g, obj, "inputSignal1Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.inputSignal2Type>([\s\S]*?)<\/cim:PssIEEE2B.inputSignal2Type>/g, obj, "inputSignal2Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.ks1>([\s\S]*?)<\/cim:PssIEEE2B.ks1>/g, obj, "ks1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.ks2>([\s\S]*?)<\/cim:PssIEEE2B.ks2>/g, obj, "ks2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.ks3>([\s\S]*?)<\/cim:PssIEEE2B.ks3>/g, obj, "ks3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.m>([\s\S]*?)<\/cim:PssIEEE2B.m>/g, obj, "m", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.n>([\s\S]*?)<\/cim:PssIEEE2B.n>/g, obj, "n", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t1>([\s\S]*?)<\/cim:PssIEEE2B.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t10>([\s\S]*?)<\/cim:PssIEEE2B.t10>/g, obj, "t10", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t11>([\s\S]*?)<\/cim:PssIEEE2B.t11>/g, obj, "t11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t2>([\s\S]*?)<\/cim:PssIEEE2B.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t3>([\s\S]*?)<\/cim:PssIEEE2B.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t4>([\s\S]*?)<\/cim:PssIEEE2B.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t6>([\s\S]*?)<\/cim:PssIEEE2B.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t7>([\s\S]*?)<\/cim:PssIEEE2B.t7>/g, obj, "t7", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t8>([\s\S]*?)<\/cim:PssIEEE2B.t8>/g, obj, "t8", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.t9>([\s\S]*?)<\/cim:PssIEEE2B.t9>/g, obj, "t9", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.tw1>([\s\S]*?)<\/cim:PssIEEE2B.tw1>/g, obj, "tw1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.tw2>([\s\S]*?)<\/cim:PssIEEE2B.tw2>/g, obj, "tw2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.tw3>([\s\S]*?)<\/cim:PssIEEE2B.tw3>/g, obj, "tw3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.tw4>([\s\S]*?)<\/cim:PssIEEE2B.tw4>/g, obj, "tw4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vsi1max>([\s\S]*?)<\/cim:PssIEEE2B.vsi1max>/g, obj, "vsi1max", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vsi1min>([\s\S]*?)<\/cim:PssIEEE2B.vsi1min>/g, obj, "vsi1min", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vsi2max>([\s\S]*?)<\/cim:PssIEEE2B.vsi2max>/g, obj, "vsi2max", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vsi2min>([\s\S]*?)<\/cim:PssIEEE2B.vsi2min>/g, obj, "vsi2min", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vstmax>([\s\S]*?)<\/cim:PssIEEE2B.vstmax>/g, obj, "vstmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE2B.vstmin>([\s\S]*?)<\/cim:PssIEEE2B.vstmin>/g, obj, "vstmin", base.to_string, sub, context);

                var bucket = context.parsed.PssIEEE2B;
                if (null == bucket)
                   context.parsed.PssIEEE2B = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssIEEE2B", "inputSignal1Type", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "inputSignal2Type", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "ks1", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "ks2", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "ks3", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "m", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "n", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t1", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t10", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t11", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t2", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t3", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t4", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t6", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t7", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t8", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "t9", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "tw1", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "tw2", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "tw3", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "tw4", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vsi1max", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vsi1min", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vsi2max", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vsi2min", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vstmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE2B", "vstmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssIEEE2B_collapse" aria-expanded="true" aria-controls="PssIEEE2B_collapse">PssIEEE2B</a>
<div id="PssIEEE2B_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#inputSignal1Type}}<div><b>inputSignal1Type</b>: {{inputSignal1Type}}</div>{{/inputSignal1Type}}
{{#inputSignal2Type}}<div><b>inputSignal2Type</b>: {{inputSignal2Type}}</div>{{/inputSignal2Type}}
{{#ks1}}<div><b>ks1</b>: {{ks1}}</div>{{/ks1}}
{{#ks2}}<div><b>ks2</b>: {{ks2}}</div>{{/ks2}}
{{#ks3}}<div><b>ks3</b>: {{ks3}}</div>{{/ks3}}
{{#m}}<div><b>m</b>: {{m}}</div>{{/m}}
{{#n}}<div><b>n</b>: {{n}}</div>{{/n}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t10}}<div><b>t10</b>: {{t10}}</div>{{/t10}}
{{#t11}}<div><b>t11</b>: {{t11}}</div>{{/t11}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#t7}}<div><b>t7</b>: {{t7}}</div>{{/t7}}
{{#t8}}<div><b>t8</b>: {{t8}}</div>{{/t8}}
{{#t9}}<div><b>t9</b>: {{t9}}</div>{{/t9}}
{{#tw1}}<div><b>tw1</b>: {{tw1}}</div>{{/tw1}}
{{#tw2}}<div><b>tw2</b>: {{tw2}}</div>{{/tw2}}
{{#tw3}}<div><b>tw3</b>: {{tw3}}</div>{{/tw3}}
{{#tw4}}<div><b>tw4</b>: {{tw4}}</div>{{/tw4}}
{{#vsi1max}}<div><b>vsi1max</b>: {{vsi1max}}</div>{{/vsi1max}}
{{#vsi1min}}<div><b>vsi1min</b>: {{vsi1min}}</div>{{/vsi1min}}
{{#vsi2max}}<div><b>vsi2max</b>: {{vsi2max}}</div>{{/vsi2max}}
{{#vsi2min}}<div><b>vsi2min</b>: {{vsi2min}}</div>{{/vsi2min}}
{{#vstmax}}<div><b>vstmax</b>: {{vstmax}}</div>{{/vstmax}}
{{#vstmin}}<div><b>vstmin</b>: {{vstmin}}</div>{{/vstmin}}
</div>
`
                );
           }        }

        /**
         * PTI Microprocessor-Based Stabilizer type 1.
         *
         */
        class Pss2ST extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pss2ST;
                if (null == bucket)
                   cim_data.Pss2ST = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pss2ST[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "Pss2ST";
                base.parse_element (/<cim:Pss2ST.inputSignal1Type>([\s\S]*?)<\/cim:Pss2ST.inputSignal1Type>/g, obj, "inputSignal1Type", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.inputSignal2Type>([\s\S]*?)<\/cim:Pss2ST.inputSignal2Type>/g, obj, "inputSignal2Type", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.k1>([\s\S]*?)<\/cim:Pss2ST.k1>/g, obj, "k1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.k2>([\s\S]*?)<\/cim:Pss2ST.k2>/g, obj, "k2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.lsmax>([\s\S]*?)<\/cim:Pss2ST.lsmax>/g, obj, "lsmax", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.lsmin>([\s\S]*?)<\/cim:Pss2ST.lsmin>/g, obj, "lsmin", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t1>([\s\S]*?)<\/cim:Pss2ST.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t10>([\s\S]*?)<\/cim:Pss2ST.t10>/g, obj, "t10", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t2>([\s\S]*?)<\/cim:Pss2ST.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t3>([\s\S]*?)<\/cim:Pss2ST.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t4>([\s\S]*?)<\/cim:Pss2ST.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t5>([\s\S]*?)<\/cim:Pss2ST.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t6>([\s\S]*?)<\/cim:Pss2ST.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t7>([\s\S]*?)<\/cim:Pss2ST.t7>/g, obj, "t7", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t8>([\s\S]*?)<\/cim:Pss2ST.t8>/g, obj, "t8", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.t9>([\s\S]*?)<\/cim:Pss2ST.t9>/g, obj, "t9", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.vcl>([\s\S]*?)<\/cim:Pss2ST.vcl>/g, obj, "vcl", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2ST.vcu>([\s\S]*?)<\/cim:Pss2ST.vcu>/g, obj, "vcu", base.to_string, sub, context);

                var bucket = context.parsed.Pss2ST;
                if (null == bucket)
                   context.parsed.Pss2ST = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pss2ST", "inputSignal1Type", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "inputSignal2Type", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "k1", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "k2", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "lsmax", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "lsmin", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t1", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t10", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t2", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t3", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t4", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t5", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t6", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t7", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t8", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "t9", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "vcl", base.from_string, fields);
                base.export_element (obj, "Pss2ST", "vcu", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pss2ST_collapse" aria-expanded="true" aria-controls="Pss2ST_collapse">Pss2ST</a>
<div id="Pss2ST_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#inputSignal1Type}}<div><b>inputSignal1Type</b>: {{inputSignal1Type}}</div>{{/inputSignal1Type}}
{{#inputSignal2Type}}<div><b>inputSignal2Type</b>: {{inputSignal2Type}}</div>{{/inputSignal2Type}}
{{#k1}}<div><b>k1</b>: {{k1}}</div>{{/k1}}
{{#k2}}<div><b>k2</b>: {{k2}}</div>{{/k2}}
{{#lsmax}}<div><b>lsmax</b>: {{lsmax}}</div>{{/lsmax}}
{{#lsmin}}<div><b>lsmin</b>: {{lsmin}}</div>{{/lsmin}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t10}}<div><b>t10</b>: {{t10}}</div>{{/t10}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#t7}}<div><b>t7</b>: {{t7}}</div>{{/t7}}
{{#t8}}<div><b>t8</b>: {{t8}}</div>{{/t8}}
{{#t9}}<div><b>t9</b>: {{t9}}</div>{{/t9}}
{{#vcl}}<div><b>vcl</b>: {{vcl}}</div>{{/vcl}}
{{#vcu}}<div><b>vcu</b>: {{vcu}}</div>{{/vcu}}
</div>
`
                );
           }        }

        /**
         * Italian PSS - three input PSS (speed, frequency, power).
         *
         */
        class Pss1 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pss1;
                if (null == bucket)
                   cim_data.Pss1 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pss1[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "Pss1";
                base.parse_element (/<cim:Pss1.kf>([\s\S]*?)<\/cim:Pss1.kf>/g, obj, "kf", base.to_float, sub, context);
                base.parse_element (/<cim:Pss1.kpe>([\s\S]*?)<\/cim:Pss1.kpe>/g, obj, "kpe", base.to_float, sub, context);
                base.parse_element (/<cim:Pss1.ks>([\s\S]*?)<\/cim:Pss1.ks>/g, obj, "ks", base.to_float, sub, context);
                base.parse_element (/<cim:Pss1.kw>([\s\S]*?)<\/cim:Pss1.kw>/g, obj, "kw", base.to_float, sub, context);
                base.parse_element (/<cim:Pss1.pmin>([\s\S]*?)<\/cim:Pss1.pmin>/g, obj, "pmin", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t10>([\s\S]*?)<\/cim:Pss1.t10>/g, obj, "t10", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t5>([\s\S]*?)<\/cim:Pss1.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t6>([\s\S]*?)<\/cim:Pss1.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t7>([\s\S]*?)<\/cim:Pss1.t7>/g, obj, "t7", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t8>([\s\S]*?)<\/cim:Pss1.t8>/g, obj, "t8", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.t9>([\s\S]*?)<\/cim:Pss1.t9>/g, obj, "t9", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.tpe>([\s\S]*?)<\/cim:Pss1.tpe>/g, obj, "tpe", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.vadat>([\s\S]*?)<\/cim:Pss1.vadat>/g, obj, "vadat", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pss1.vsmn>([\s\S]*?)<\/cim:Pss1.vsmn>/g, obj, "vsmn", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1.vsmx>([\s\S]*?)<\/cim:Pss1.vsmx>/g, obj, "vsmx", base.to_string, sub, context);

                var bucket = context.parsed.Pss1;
                if (null == bucket)
                   context.parsed.Pss1 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pss1", "kf", base.from_float, fields);
                base.export_element (obj, "Pss1", "kpe", base.from_float, fields);
                base.export_element (obj, "Pss1", "ks", base.from_float, fields);
                base.export_element (obj, "Pss1", "kw", base.from_float, fields);
                base.export_element (obj, "Pss1", "pmin", base.from_string, fields);
                base.export_element (obj, "Pss1", "t10", base.from_string, fields);
                base.export_element (obj, "Pss1", "t5", base.from_string, fields);
                base.export_element (obj, "Pss1", "t6", base.from_string, fields);
                base.export_element (obj, "Pss1", "t7", base.from_string, fields);
                base.export_element (obj, "Pss1", "t8", base.from_string, fields);
                base.export_element (obj, "Pss1", "t9", base.from_string, fields);
                base.export_element (obj, "Pss1", "tpe", base.from_string, fields);
                base.export_element (obj, "Pss1", "vadat", base.from_boolean, fields);
                base.export_element (obj, "Pss1", "vsmn", base.from_string, fields);
                base.export_element (obj, "Pss1", "vsmx", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pss1_collapse" aria-expanded="true" aria-controls="Pss1_collapse">Pss1</a>
<div id="Pss1_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#kf}}<div><b>kf</b>: {{kf}}</div>{{/kf}}
{{#kpe}}<div><b>kpe</b>: {{kpe}}</div>{{/kpe}}
{{#ks}}<div><b>ks</b>: {{ks}}</div>{{/ks}}
{{#kw}}<div><b>kw</b>: {{kw}}</div>{{/kw}}
{{#pmin}}<div><b>pmin</b>: {{pmin}}</div>{{/pmin}}
{{#t10}}<div><b>t10</b>: {{t10}}</div>{{/t10}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#t7}}<div><b>t7</b>: {{t7}}</div>{{/t7}}
{{#t8}}<div><b>t8</b>: {{t8}}</div>{{/t8}}
{{#t9}}<div><b>t9</b>: {{t9}}</div>{{/t9}}
{{#tpe}}<div><b>tpe</b>: {{tpe}}</div>{{/tpe}}
{{#vadat}}<div><b>vadat</b>: {{vadat}}</div>{{/vadat}}
{{#vsmn}}<div><b>vsmn</b>: {{vsmn}}</div>{{/vsmn}}
{{#vsmx}}<div><b>vsmx</b>: {{vsmx}}</div>{{/vsmx}}
</div>
`
                );
           }        }

        /**
         * Model for Siemens �H infinity� power system stabilizer with generator electrical power input.
         *
         */
        class PssSH extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssSH;
                if (null == bucket)
                   cim_data.PssSH = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssSH[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssSH";
                base.parse_element (/<cim:PssSH.k>([\s\S]*?)<\/cim:PssSH.k>/g, obj, "k", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.k0>([\s\S]*?)<\/cim:PssSH.k0>/g, obj, "k0", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.k1>([\s\S]*?)<\/cim:PssSH.k1>/g, obj, "k1", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.k2>([\s\S]*?)<\/cim:PssSH.k2>/g, obj, "k2", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.k3>([\s\S]*?)<\/cim:PssSH.k3>/g, obj, "k3", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.k4>([\s\S]*?)<\/cim:PssSH.k4>/g, obj, "k4", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.t1>([\s\S]*?)<\/cim:PssSH.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.t2>([\s\S]*?)<\/cim:PssSH.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.t3>([\s\S]*?)<\/cim:PssSH.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.t4>([\s\S]*?)<\/cim:PssSH.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.td>([\s\S]*?)<\/cim:PssSH.td>/g, obj, "td", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.vsmax>([\s\S]*?)<\/cim:PssSH.vsmax>/g, obj, "vsmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssSH.vsmin>([\s\S]*?)<\/cim:PssSH.vsmin>/g, obj, "vsmin", base.to_string, sub, context);

                var bucket = context.parsed.PssSH;
                if (null == bucket)
                   context.parsed.PssSH = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssSH", "k", base.from_string, fields);
                base.export_element (obj, "PssSH", "k0", base.from_string, fields);
                base.export_element (obj, "PssSH", "k1", base.from_string, fields);
                base.export_element (obj, "PssSH", "k2", base.from_string, fields);
                base.export_element (obj, "PssSH", "k3", base.from_string, fields);
                base.export_element (obj, "PssSH", "k4", base.from_string, fields);
                base.export_element (obj, "PssSH", "t1", base.from_string, fields);
                base.export_element (obj, "PssSH", "t2", base.from_string, fields);
                base.export_element (obj, "PssSH", "t3", base.from_string, fields);
                base.export_element (obj, "PssSH", "t4", base.from_string, fields);
                base.export_element (obj, "PssSH", "td", base.from_string, fields);
                base.export_element (obj, "PssSH", "vsmax", base.from_string, fields);
                base.export_element (obj, "PssSH", "vsmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssSH_collapse" aria-expanded="true" aria-controls="PssSH_collapse">PssSH</a>
<div id="PssSH_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#k}}<div><b>k</b>: {{k}}</div>{{/k}}
{{#k0}}<div><b>k0</b>: {{k0}}</div>{{/k0}}
{{#k1}}<div><b>k1</b>: {{k1}}</div>{{/k1}}
{{#k2}}<div><b>k2</b>: {{k2}}</div>{{/k2}}
{{#k3}}<div><b>k3</b>: {{k3}}</div>{{/k3}}
{{#k4}}<div><b>k4</b>: {{k4}}</div>{{/k4}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#td}}<div><b>td</b>: {{td}}</div>{{/td}}
{{#vsmax}}<div><b>vsmax</b>: {{vsmax}}</div>{{/vsmax}}
{{#vsmin}}<div><b>vsmin</b>: {{vsmin}}</div>{{/vsmin}}
</div>
`
                );
           }        }

        /**
         * Single input power system stabilizer.
         *
         * It is a modified version in order to allow representation of various vendors' implementations on PSS type 1A.
         *
         */
        class Pss1A extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pss1A;
                if (null == bucket)
                   cim_data.Pss1A = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pss1A[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "Pss1A";
                base.parse_element (/<cim:Pss1A.a1>([\s\S]*?)<\/cim:Pss1A.a1>/g, obj, "a1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a2>([\s\S]*?)<\/cim:Pss1A.a2>/g, obj, "a2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a3>([\s\S]*?)<\/cim:Pss1A.a3>/g, obj, "a3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a4>([\s\S]*?)<\/cim:Pss1A.a4>/g, obj, "a4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a5>([\s\S]*?)<\/cim:Pss1A.a5>/g, obj, "a5", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a6>([\s\S]*?)<\/cim:Pss1A.a6>/g, obj, "a6", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a7>([\s\S]*?)<\/cim:Pss1A.a7>/g, obj, "a7", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.a8>([\s\S]*?)<\/cim:Pss1A.a8>/g, obj, "a8", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.inputSignalType>([\s\S]*?)<\/cim:Pss1A.inputSignalType>/g, obj, "inputSignalType", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.kd>([\s\S]*?)<\/cim:Pss1A.kd>/g, obj, "kd", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pss1A.ks>([\s\S]*?)<\/cim:Pss1A.ks>/g, obj, "ks", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t1>([\s\S]*?)<\/cim:Pss1A.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t2>([\s\S]*?)<\/cim:Pss1A.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t3>([\s\S]*?)<\/cim:Pss1A.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t4>([\s\S]*?)<\/cim:Pss1A.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t5>([\s\S]*?)<\/cim:Pss1A.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.t6>([\s\S]*?)<\/cim:Pss1A.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.tdelay>([\s\S]*?)<\/cim:Pss1A.tdelay>/g, obj, "tdelay", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.vcl>([\s\S]*?)<\/cim:Pss1A.vcl>/g, obj, "vcl", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.vcu>([\s\S]*?)<\/cim:Pss1A.vcu>/g, obj, "vcu", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.vrmax>([\s\S]*?)<\/cim:Pss1A.vrmax>/g, obj, "vrmax", base.to_string, sub, context);
                base.parse_element (/<cim:Pss1A.vrmin>([\s\S]*?)<\/cim:Pss1A.vrmin>/g, obj, "vrmin", base.to_string, sub, context);

                var bucket = context.parsed.Pss1A;
                if (null == bucket)
                   context.parsed.Pss1A = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pss1A", "a1", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a2", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a3", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a4", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a5", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a6", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a7", base.from_string, fields);
                base.export_element (obj, "Pss1A", "a8", base.from_string, fields);
                base.export_element (obj, "Pss1A", "inputSignalType", base.from_string, fields);
                base.export_element (obj, "Pss1A", "kd", base.from_boolean, fields);
                base.export_element (obj, "Pss1A", "ks", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t1", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t2", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t3", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t4", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t5", base.from_string, fields);
                base.export_element (obj, "Pss1A", "t6", base.from_string, fields);
                base.export_element (obj, "Pss1A", "tdelay", base.from_string, fields);
                base.export_element (obj, "Pss1A", "vcl", base.from_string, fields);
                base.export_element (obj, "Pss1A", "vcu", base.from_string, fields);
                base.export_element (obj, "Pss1A", "vrmax", base.from_string, fields);
                base.export_element (obj, "Pss1A", "vrmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pss1A_collapse" aria-expanded="true" aria-controls="Pss1A_collapse">Pss1A</a>
<div id="Pss1A_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#a1}}<div><b>a1</b>: {{a1}}</div>{{/a1}}
{{#a2}}<div><b>a2</b>: {{a2}}</div>{{/a2}}
{{#a3}}<div><b>a3</b>: {{a3}}</div>{{/a3}}
{{#a4}}<div><b>a4</b>: {{a4}}</div>{{/a4}}
{{#a5}}<div><b>a5</b>: {{a5}}</div>{{/a5}}
{{#a6}}<div><b>a6</b>: {{a6}}</div>{{/a6}}
{{#a7}}<div><b>a7</b>: {{a7}}</div>{{/a7}}
{{#a8}}<div><b>a8</b>: {{a8}}</div>{{/a8}}
{{#inputSignalType}}<div><b>inputSignalType</b>: {{inputSignalType}}</div>{{/inputSignalType}}
{{#kd}}<div><b>kd</b>: {{kd}}</div>{{/kd}}
{{#ks}}<div><b>ks</b>: {{ks}}</div>{{/ks}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#tdelay}}<div><b>tdelay</b>: {{tdelay}}</div>{{/tdelay}}
{{#vcl}}<div><b>vcl</b>: {{vcl}}</div>{{/vcl}}
{{#vcu}}<div><b>vcu</b>: {{vcu}}</div>{{/vcu}}
{{#vrmax}}<div><b>vrmax</b>: {{vrmax}}</div>{{/vrmax}}
{{#vrmin}}<div><b>vrmin</b>: {{vrmin}}</div>{{/vrmin}}
</div>
`
                );
           }        }

        /**
         * PSS Slovakian type � three inputs.
         *
         */
        class PssSK extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssSK;
                if (null == bucket)
                   cim_data.PssSK = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssSK[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssSK";
                base.parse_element (/<cim:PssSK.k1>([\s\S]*?)<\/cim:PssSK.k1>/g, obj, "k1", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.k2>([\s\S]*?)<\/cim:PssSK.k2>/g, obj, "k2", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.k3>([\s\S]*?)<\/cim:PssSK.k3>/g, obj, "k3", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t1>([\s\S]*?)<\/cim:PssSK.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t2>([\s\S]*?)<\/cim:PssSK.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t3>([\s\S]*?)<\/cim:PssSK.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t4>([\s\S]*?)<\/cim:PssSK.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t5>([\s\S]*?)<\/cim:PssSK.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.t6>([\s\S]*?)<\/cim:PssSK.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.vsmax>([\s\S]*?)<\/cim:PssSK.vsmax>/g, obj, "vsmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssSK.vsmin>([\s\S]*?)<\/cim:PssSK.vsmin>/g, obj, "vsmin", base.to_string, sub, context);

                var bucket = context.parsed.PssSK;
                if (null == bucket)
                   context.parsed.PssSK = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssSK", "k1", base.from_string, fields);
                base.export_element (obj, "PssSK", "k2", base.from_string, fields);
                base.export_element (obj, "PssSK", "k3", base.from_string, fields);
                base.export_element (obj, "PssSK", "t1", base.from_string, fields);
                base.export_element (obj, "PssSK", "t2", base.from_string, fields);
                base.export_element (obj, "PssSK", "t3", base.from_string, fields);
                base.export_element (obj, "PssSK", "t4", base.from_string, fields);
                base.export_element (obj, "PssSK", "t5", base.from_string, fields);
                base.export_element (obj, "PssSK", "t6", base.from_string, fields);
                base.export_element (obj, "PssSK", "vsmax", base.from_string, fields);
                base.export_element (obj, "PssSK", "vsmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssSK_collapse" aria-expanded="true" aria-controls="PssSK_collapse">PssSK</a>
<div id="PssSK_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#k1}}<div><b>k1</b>: {{k1}}</div>{{/k1}}
{{#k2}}<div><b>k2</b>: {{k2}}</div>{{/k2}}
{{#k3}}<div><b>k3</b>: {{k3}}</div>{{/k3}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#vsmax}}<div><b>vsmax</b>: {{vsmax}}</div>{{/vsmax}}
{{#vsmin}}<div><b>vsmin</b>: {{vsmin}}</div>{{/vsmin}}
</div>
`
                );
           }        }

        /**
         * The class represents IEEE Std 421.5-2005 type PSS1A power system stabilizer model.
         *
         * PSS1A is the generalized form of a PSS with a single input. Some common stabilizer input signals are speed, frequency, and power.
         *
         */
        class PssIEEE1A extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssIEEE1A;
                if (null == bucket)
                   cim_data.PssIEEE1A = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssIEEE1A[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssIEEE1A";
                base.parse_element (/<cim:PssIEEE1A.a1>([\s\S]*?)<\/cim:PssIEEE1A.a1>/g, obj, "a1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.a2>([\s\S]*?)<\/cim:PssIEEE1A.a2>/g, obj, "a2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.inputSignalType>([\s\S]*?)<\/cim:PssIEEE1A.inputSignalType>/g, obj, "inputSignalType", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.ks>([\s\S]*?)<\/cim:PssIEEE1A.ks>/g, obj, "ks", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t1>([\s\S]*?)<\/cim:PssIEEE1A.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t2>([\s\S]*?)<\/cim:PssIEEE1A.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t3>([\s\S]*?)<\/cim:PssIEEE1A.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t4>([\s\S]*?)<\/cim:PssIEEE1A.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t5>([\s\S]*?)<\/cim:PssIEEE1A.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.t6>([\s\S]*?)<\/cim:PssIEEE1A.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.vrmax>([\s\S]*?)<\/cim:PssIEEE1A.vrmax>/g, obj, "vrmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE1A.vrmin>([\s\S]*?)<\/cim:PssIEEE1A.vrmin>/g, obj, "vrmin", base.to_string, sub, context);

                var bucket = context.parsed.PssIEEE1A;
                if (null == bucket)
                   context.parsed.PssIEEE1A = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssIEEE1A", "a1", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "a2", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "inputSignalType", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "ks", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t1", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t2", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t3", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t4", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t5", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "t6", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "vrmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE1A", "vrmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssIEEE1A_collapse" aria-expanded="true" aria-controls="PssIEEE1A_collapse">PssIEEE1A</a>
<div id="PssIEEE1A_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#a1}}<div><b>a1</b>: {{a1}}</div>{{/a1}}
{{#a2}}<div><b>a2</b>: {{a2}}</div>{{/a2}}
{{#inputSignalType}}<div><b>inputSignalType</b>: {{inputSignalType}}</div>{{/inputSignalType}}
{{#ks}}<div><b>ks</b>: {{ks}}</div>{{/ks}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#vrmax}}<div><b>vrmax</b>: {{vrmax}}</div>{{/vrmax}}
{{#vrmin}}<div><b>vrmin</b>: {{vrmin}}</div>{{/vrmin}}
</div>
`
                );
           }        }

        /**
         * PTI Microprocessor-Based Stabilizer type 3.
         *
         */
        class PssPTIST3 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssPTIST3;
                if (null == bucket)
                   cim_data.PssPTIST3 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssPTIST3[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssPTIST3";
                base.parse_element (/<cim:PssPTIST3.a0>([\s\S]*?)<\/cim:PssPTIST3.a0>/g, obj, "a0", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.a1>([\s\S]*?)<\/cim:PssPTIST3.a1>/g, obj, "a1", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.a2>([\s\S]*?)<\/cim:PssPTIST3.a2>/g, obj, "a2", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.a3>([\s\S]*?)<\/cim:PssPTIST3.a3>/g, obj, "a3", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.a4>([\s\S]*?)<\/cim:PssPTIST3.a4>/g, obj, "a4", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.a5>([\s\S]*?)<\/cim:PssPTIST3.a5>/g, obj, "a5", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.al>([\s\S]*?)<\/cim:PssPTIST3.al>/g, obj, "al", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.athres>([\s\S]*?)<\/cim:PssPTIST3.athres>/g, obj, "athres", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b0>([\s\S]*?)<\/cim:PssPTIST3.b0>/g, obj, "b0", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b1>([\s\S]*?)<\/cim:PssPTIST3.b1>/g, obj, "b1", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b2>([\s\S]*?)<\/cim:PssPTIST3.b2>/g, obj, "b2", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b3>([\s\S]*?)<\/cim:PssPTIST3.b3>/g, obj, "b3", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b4>([\s\S]*?)<\/cim:PssPTIST3.b4>/g, obj, "b4", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.b5>([\s\S]*?)<\/cim:PssPTIST3.b5>/g, obj, "b5", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.dl>([\s\S]*?)<\/cim:PssPTIST3.dl>/g, obj, "dl", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.dtc>([\s\S]*?)<\/cim:PssPTIST3.dtc>/g, obj, "dtc", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.dtf>([\s\S]*?)<\/cim:PssPTIST3.dtf>/g, obj, "dtf", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.dtp>([\s\S]*?)<\/cim:PssPTIST3.dtp>/g, obj, "dtp", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.isw>([\s\S]*?)<\/cim:PssPTIST3.isw>/g, obj, "isw", base.to_boolean, sub, context);
                base.parse_element (/<cim:PssPTIST3.k>([\s\S]*?)<\/cim:PssPTIST3.k>/g, obj, "k", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.lthres>([\s\S]*?)<\/cim:PssPTIST3.lthres>/g, obj, "lthres", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.m>([\s\S]*?)<\/cim:PssPTIST3.m>/g, obj, "m", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.nav>([\s\S]*?)<\/cim:PssPTIST3.nav>/g, obj, "nav", base.to_float, sub, context);
                base.parse_element (/<cim:PssPTIST3.ncl>([\s\S]*?)<\/cim:PssPTIST3.ncl>/g, obj, "ncl", base.to_float, sub, context);
                base.parse_element (/<cim:PssPTIST3.ncr>([\s\S]*?)<\/cim:PssPTIST3.ncr>/g, obj, "ncr", base.to_float, sub, context);
                base.parse_element (/<cim:PssPTIST3.pmin>([\s\S]*?)<\/cim:PssPTIST3.pmin>/g, obj, "pmin", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t1>([\s\S]*?)<\/cim:PssPTIST3.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t2>([\s\S]*?)<\/cim:PssPTIST3.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t3>([\s\S]*?)<\/cim:PssPTIST3.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t4>([\s\S]*?)<\/cim:PssPTIST3.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t5>([\s\S]*?)<\/cim:PssPTIST3.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.t6>([\s\S]*?)<\/cim:PssPTIST3.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.tf>([\s\S]*?)<\/cim:PssPTIST3.tf>/g, obj, "tf", base.to_string, sub, context);
                base.parse_element (/<cim:PssPTIST3.tp>([\s\S]*?)<\/cim:PssPTIST3.tp>/g, obj, "tp", base.to_string, sub, context);

                var bucket = context.parsed.PssPTIST3;
                if (null == bucket)
                   context.parsed.PssPTIST3 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssPTIST3", "a0", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "a1", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "a2", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "a3", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "a4", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "a5", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "al", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "athres", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b0", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b1", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b2", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b3", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b4", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "b5", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "dl", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "dtc", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "dtf", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "dtp", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "isw", base.from_boolean, fields);
                base.export_element (obj, "PssPTIST3", "k", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "lthres", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "m", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "nav", base.from_float, fields);
                base.export_element (obj, "PssPTIST3", "ncl", base.from_float, fields);
                base.export_element (obj, "PssPTIST3", "ncr", base.from_float, fields);
                base.export_element (obj, "PssPTIST3", "pmin", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t1", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t2", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t3", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t4", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t5", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "t6", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "tf", base.from_string, fields);
                base.export_element (obj, "PssPTIST3", "tp", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssPTIST3_collapse" aria-expanded="true" aria-controls="PssPTIST3_collapse">PssPTIST3</a>
<div id="PssPTIST3_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#a0}}<div><b>a0</b>: {{a0}}</div>{{/a0}}
{{#a1}}<div><b>a1</b>: {{a1}}</div>{{/a1}}
{{#a2}}<div><b>a2</b>: {{a2}}</div>{{/a2}}
{{#a3}}<div><b>a3</b>: {{a3}}</div>{{/a3}}
{{#a4}}<div><b>a4</b>: {{a4}}</div>{{/a4}}
{{#a5}}<div><b>a5</b>: {{a5}}</div>{{/a5}}
{{#al}}<div><b>al</b>: {{al}}</div>{{/al}}
{{#athres}}<div><b>athres</b>: {{athres}}</div>{{/athres}}
{{#b0}}<div><b>b0</b>: {{b0}}</div>{{/b0}}
{{#b1}}<div><b>b1</b>: {{b1}}</div>{{/b1}}
{{#b2}}<div><b>b2</b>: {{b2}}</div>{{/b2}}
{{#b3}}<div><b>b3</b>: {{b3}}</div>{{/b3}}
{{#b4}}<div><b>b4</b>: {{b4}}</div>{{/b4}}
{{#b5}}<div><b>b5</b>: {{b5}}</div>{{/b5}}
{{#dl}}<div><b>dl</b>: {{dl}}</div>{{/dl}}
{{#dtc}}<div><b>dtc</b>: {{dtc}}</div>{{/dtc}}
{{#dtf}}<div><b>dtf</b>: {{dtf}}</div>{{/dtf}}
{{#dtp}}<div><b>dtp</b>: {{dtp}}</div>{{/dtp}}
{{#isw}}<div><b>isw</b>: {{isw}}</div>{{/isw}}
{{#k}}<div><b>k</b>: {{k}}</div>{{/k}}
{{#lthres}}<div><b>lthres</b>: {{lthres}}</div>{{/lthres}}
{{#m}}<div><b>m</b>: {{m}}</div>{{/m}}
{{#nav}}<div><b>nav</b>: {{nav}}</div>{{/nav}}
{{#ncl}}<div><b>ncl</b>: {{ncl}}</div>{{/ncl}}
{{#ncr}}<div><b>ncr</b>: {{ncr}}</div>{{/ncr}}
{{#pmin}}<div><b>pmin</b>: {{pmin}}</div>{{/pmin}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#tf}}<div><b>tf</b>: {{tf}}</div>{{/tf}}
{{#tp}}<div><b>tp</b>: {{tp}}</div>{{/tp}}
</div>
`
                );
           }        }

        /**
         * The class represents IEEE Std 421.5-2005 type PSS2B power system stabilizer model.
         *
         * The PSS4B model represents a structure based on multiple working frequency bands. Three separate bands, respectively dedicated to the low-, intermediate- and high-frequency modes of oscillations, are used in this delta-omega (speed input) PSS.
         *
         */
        class PssIEEE4B extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssIEEE4B;
                if (null == bucket)
                   cim_data.PssIEEE4B = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssIEEE4B[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssIEEE4B";
                base.parse_element (/<cim:PssIEEE4B.bwh1>([\s\S]*?)<\/cim:PssIEEE4B.bwh1>/g, obj, "bwh1", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.bwh2>([\s\S]*?)<\/cim:PssIEEE4B.bwh2>/g, obj, "bwh2", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.bwl1>([\s\S]*?)<\/cim:PssIEEE4B.bwl1>/g, obj, "bwl1", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.bwl2>([\s\S]*?)<\/cim:PssIEEE4B.bwl2>/g, obj, "bwl2", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kh>([\s\S]*?)<\/cim:PssIEEE4B.kh>/g, obj, "kh", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kh1>([\s\S]*?)<\/cim:PssIEEE4B.kh1>/g, obj, "kh1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kh11>([\s\S]*?)<\/cim:PssIEEE4B.kh11>/g, obj, "kh11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kh17>([\s\S]*?)<\/cim:PssIEEE4B.kh17>/g, obj, "kh17", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kh2>([\s\S]*?)<\/cim:PssIEEE4B.kh2>/g, obj, "kh2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ki>([\s\S]*?)<\/cim:PssIEEE4B.ki>/g, obj, "ki", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ki1>([\s\S]*?)<\/cim:PssIEEE4B.ki1>/g, obj, "ki1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ki11>([\s\S]*?)<\/cim:PssIEEE4B.ki11>/g, obj, "ki11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ki17>([\s\S]*?)<\/cim:PssIEEE4B.ki17>/g, obj, "ki17", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ki2>([\s\S]*?)<\/cim:PssIEEE4B.ki2>/g, obj, "ki2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kl>([\s\S]*?)<\/cim:PssIEEE4B.kl>/g, obj, "kl", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kl1>([\s\S]*?)<\/cim:PssIEEE4B.kl1>/g, obj, "kl1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kl11>([\s\S]*?)<\/cim:PssIEEE4B.kl11>/g, obj, "kl11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kl17>([\s\S]*?)<\/cim:PssIEEE4B.kl17>/g, obj, "kl17", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.kl2>([\s\S]*?)<\/cim:PssIEEE4B.kl2>/g, obj, "kl2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.omeganh1>([\s\S]*?)<\/cim:PssIEEE4B.omeganh1>/g, obj, "omeganh1", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.omeganh2>([\s\S]*?)<\/cim:PssIEEE4B.omeganh2>/g, obj, "omeganh2", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.omeganl1>([\s\S]*?)<\/cim:PssIEEE4B.omeganl1>/g, obj, "omeganl1", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.omeganl2>([\s\S]*?)<\/cim:PssIEEE4B.omeganl2>/g, obj, "omeganl2", base.to_float, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th1>([\s\S]*?)<\/cim:PssIEEE4B.th1>/g, obj, "th1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th10>([\s\S]*?)<\/cim:PssIEEE4B.th10>/g, obj, "th10", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th11>([\s\S]*?)<\/cim:PssIEEE4B.th11>/g, obj, "th11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th12>([\s\S]*?)<\/cim:PssIEEE4B.th12>/g, obj, "th12", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th2>([\s\S]*?)<\/cim:PssIEEE4B.th2>/g, obj, "th2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th3>([\s\S]*?)<\/cim:PssIEEE4B.th3>/g, obj, "th3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th4>([\s\S]*?)<\/cim:PssIEEE4B.th4>/g, obj, "th4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th5>([\s\S]*?)<\/cim:PssIEEE4B.th5>/g, obj, "th5", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th6>([\s\S]*?)<\/cim:PssIEEE4B.th6>/g, obj, "th6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th7>([\s\S]*?)<\/cim:PssIEEE4B.th7>/g, obj, "th7", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th8>([\s\S]*?)<\/cim:PssIEEE4B.th8>/g, obj, "th8", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.th9>([\s\S]*?)<\/cim:PssIEEE4B.th9>/g, obj, "th9", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti1>([\s\S]*?)<\/cim:PssIEEE4B.ti1>/g, obj, "ti1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti10>([\s\S]*?)<\/cim:PssIEEE4B.ti10>/g, obj, "ti10", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti11>([\s\S]*?)<\/cim:PssIEEE4B.ti11>/g, obj, "ti11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti12>([\s\S]*?)<\/cim:PssIEEE4B.ti12>/g, obj, "ti12", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti2>([\s\S]*?)<\/cim:PssIEEE4B.ti2>/g, obj, "ti2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti3>([\s\S]*?)<\/cim:PssIEEE4B.ti3>/g, obj, "ti3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti4>([\s\S]*?)<\/cim:PssIEEE4B.ti4>/g, obj, "ti4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti5>([\s\S]*?)<\/cim:PssIEEE4B.ti5>/g, obj, "ti5", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti6>([\s\S]*?)<\/cim:PssIEEE4B.ti6>/g, obj, "ti6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti7>([\s\S]*?)<\/cim:PssIEEE4B.ti7>/g, obj, "ti7", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti8>([\s\S]*?)<\/cim:PssIEEE4B.ti8>/g, obj, "ti8", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.ti9>([\s\S]*?)<\/cim:PssIEEE4B.ti9>/g, obj, "ti9", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl1>([\s\S]*?)<\/cim:PssIEEE4B.tl1>/g, obj, "tl1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl10>([\s\S]*?)<\/cim:PssIEEE4B.tl10>/g, obj, "tl10", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl11>([\s\S]*?)<\/cim:PssIEEE4B.tl11>/g, obj, "tl11", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl12>([\s\S]*?)<\/cim:PssIEEE4B.tl12>/g, obj, "tl12", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl2>([\s\S]*?)<\/cim:PssIEEE4B.tl2>/g, obj, "tl2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl3>([\s\S]*?)<\/cim:PssIEEE4B.tl3>/g, obj, "tl3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl4>([\s\S]*?)<\/cim:PssIEEE4B.tl4>/g, obj, "tl4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl5>([\s\S]*?)<\/cim:PssIEEE4B.tl5>/g, obj, "tl5", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl6>([\s\S]*?)<\/cim:PssIEEE4B.tl6>/g, obj, "tl6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl7>([\s\S]*?)<\/cim:PssIEEE4B.tl7>/g, obj, "tl7", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl8>([\s\S]*?)<\/cim:PssIEEE4B.tl8>/g, obj, "tl8", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.tl9>([\s\S]*?)<\/cim:PssIEEE4B.tl9>/g, obj, "tl9", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vhmax>([\s\S]*?)<\/cim:PssIEEE4B.vhmax>/g, obj, "vhmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vhmin>([\s\S]*?)<\/cim:PssIEEE4B.vhmin>/g, obj, "vhmin", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vimax>([\s\S]*?)<\/cim:PssIEEE4B.vimax>/g, obj, "vimax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vimin>([\s\S]*?)<\/cim:PssIEEE4B.vimin>/g, obj, "vimin", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vlmax>([\s\S]*?)<\/cim:PssIEEE4B.vlmax>/g, obj, "vlmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vlmin>([\s\S]*?)<\/cim:PssIEEE4B.vlmin>/g, obj, "vlmin", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vstmax>([\s\S]*?)<\/cim:PssIEEE4B.vstmax>/g, obj, "vstmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE4B.vstmin>([\s\S]*?)<\/cim:PssIEEE4B.vstmin>/g, obj, "vstmin", base.to_string, sub, context);

                var bucket = context.parsed.PssIEEE4B;
                if (null == bucket)
                   context.parsed.PssIEEE4B = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssIEEE4B", "bwh1", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "bwh2", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "bwl1", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "bwl2", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "kh", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kh1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kh11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kh17", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kh2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ki", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ki1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ki11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ki17", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ki2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kl", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kl1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kl11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kl17", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "kl2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "omeganh1", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "omeganh2", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "omeganl1", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "omeganl2", base.from_float, fields);
                base.export_element (obj, "PssIEEE4B", "th1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th10", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th12", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th3", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th4", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th5", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th6", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th7", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th8", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "th9", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti10", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti12", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti3", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti4", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti5", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti6", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti7", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti8", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "ti9", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl1", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl10", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl11", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl12", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl2", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl3", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl4", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl5", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl6", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl7", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl8", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "tl9", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vhmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vhmin", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vimax", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vimin", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vlmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vlmin", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vstmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE4B", "vstmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssIEEE4B_collapse" aria-expanded="true" aria-controls="PssIEEE4B_collapse">PssIEEE4B</a>
<div id="PssIEEE4B_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#bwh1}}<div><b>bwh1</b>: {{bwh1}}</div>{{/bwh1}}
{{#bwh2}}<div><b>bwh2</b>: {{bwh2}}</div>{{/bwh2}}
{{#bwl1}}<div><b>bwl1</b>: {{bwl1}}</div>{{/bwl1}}
{{#bwl2}}<div><b>bwl2</b>: {{bwl2}}</div>{{/bwl2}}
{{#kh}}<div><b>kh</b>: {{kh}}</div>{{/kh}}
{{#kh1}}<div><b>kh1</b>: {{kh1}}</div>{{/kh1}}
{{#kh11}}<div><b>kh11</b>: {{kh11}}</div>{{/kh11}}
{{#kh17}}<div><b>kh17</b>: {{kh17}}</div>{{/kh17}}
{{#kh2}}<div><b>kh2</b>: {{kh2}}</div>{{/kh2}}
{{#ki}}<div><b>ki</b>: {{ki}}</div>{{/ki}}
{{#ki1}}<div><b>ki1</b>: {{ki1}}</div>{{/ki1}}
{{#ki11}}<div><b>ki11</b>: {{ki11}}</div>{{/ki11}}
{{#ki17}}<div><b>ki17</b>: {{ki17}}</div>{{/ki17}}
{{#ki2}}<div><b>ki2</b>: {{ki2}}</div>{{/ki2}}
{{#kl}}<div><b>kl</b>: {{kl}}</div>{{/kl}}
{{#kl1}}<div><b>kl1</b>: {{kl1}}</div>{{/kl1}}
{{#kl11}}<div><b>kl11</b>: {{kl11}}</div>{{/kl11}}
{{#kl17}}<div><b>kl17</b>: {{kl17}}</div>{{/kl17}}
{{#kl2}}<div><b>kl2</b>: {{kl2}}</div>{{/kl2}}
{{#omeganh1}}<div><b>omeganh1</b>: {{omeganh1}}</div>{{/omeganh1}}
{{#omeganh2}}<div><b>omeganh2</b>: {{omeganh2}}</div>{{/omeganh2}}
{{#omeganl1}}<div><b>omeganl1</b>: {{omeganl1}}</div>{{/omeganl1}}
{{#omeganl2}}<div><b>omeganl2</b>: {{omeganl2}}</div>{{/omeganl2}}
{{#th1}}<div><b>th1</b>: {{th1}}</div>{{/th1}}
{{#th10}}<div><b>th10</b>: {{th10}}</div>{{/th10}}
{{#th11}}<div><b>th11</b>: {{th11}}</div>{{/th11}}
{{#th12}}<div><b>th12</b>: {{th12}}</div>{{/th12}}
{{#th2}}<div><b>th2</b>: {{th2}}</div>{{/th2}}
{{#th3}}<div><b>th3</b>: {{th3}}</div>{{/th3}}
{{#th4}}<div><b>th4</b>: {{th4}}</div>{{/th4}}
{{#th5}}<div><b>th5</b>: {{th5}}</div>{{/th5}}
{{#th6}}<div><b>th6</b>: {{th6}}</div>{{/th6}}
{{#th7}}<div><b>th7</b>: {{th7}}</div>{{/th7}}
{{#th8}}<div><b>th8</b>: {{th8}}</div>{{/th8}}
{{#th9}}<div><b>th9</b>: {{th9}}</div>{{/th9}}
{{#ti1}}<div><b>ti1</b>: {{ti1}}</div>{{/ti1}}
{{#ti10}}<div><b>ti10</b>: {{ti10}}</div>{{/ti10}}
{{#ti11}}<div><b>ti11</b>: {{ti11}}</div>{{/ti11}}
{{#ti12}}<div><b>ti12</b>: {{ti12}}</div>{{/ti12}}
{{#ti2}}<div><b>ti2</b>: {{ti2}}</div>{{/ti2}}
{{#ti3}}<div><b>ti3</b>: {{ti3}}</div>{{/ti3}}
{{#ti4}}<div><b>ti4</b>: {{ti4}}</div>{{/ti4}}
{{#ti5}}<div><b>ti5</b>: {{ti5}}</div>{{/ti5}}
{{#ti6}}<div><b>ti6</b>: {{ti6}}</div>{{/ti6}}
{{#ti7}}<div><b>ti7</b>: {{ti7}}</div>{{/ti7}}
{{#ti8}}<div><b>ti8</b>: {{ti8}}</div>{{/ti8}}
{{#ti9}}<div><b>ti9</b>: {{ti9}}</div>{{/ti9}}
{{#tl1}}<div><b>tl1</b>: {{tl1}}</div>{{/tl1}}
{{#tl10}}<div><b>tl10</b>: {{tl10}}</div>{{/tl10}}
{{#tl11}}<div><b>tl11</b>: {{tl11}}</div>{{/tl11}}
{{#tl12}}<div><b>tl12</b>: {{tl12}}</div>{{/tl12}}
{{#tl2}}<div><b>tl2</b>: {{tl2}}</div>{{/tl2}}
{{#tl3}}<div><b>tl3</b>: {{tl3}}</div>{{/tl3}}
{{#tl4}}<div><b>tl4</b>: {{tl4}}</div>{{/tl4}}
{{#tl5}}<div><b>tl5</b>: {{tl5}}</div>{{/tl5}}
{{#tl6}}<div><b>tl6</b>: {{tl6}}</div>{{/tl6}}
{{#tl7}}<div><b>tl7</b>: {{tl7}}</div>{{/tl7}}
{{#tl8}}<div><b>tl8</b>: {{tl8}}</div>{{/tl8}}
{{#tl9}}<div><b>tl9</b>: {{tl9}}</div>{{/tl9}}
{{#vhmax}}<div><b>vhmax</b>: {{vhmax}}</div>{{/vhmax}}
{{#vhmin}}<div><b>vhmin</b>: {{vhmin}}</div>{{/vhmin}}
{{#vimax}}<div><b>vimax</b>: {{vimax}}</div>{{/vimax}}
{{#vimin}}<div><b>vimin</b>: {{vimin}}</div>{{/vimin}}
{{#vlmax}}<div><b>vlmax</b>: {{vlmax}}</div>{{/vlmax}}
{{#vlmin}}<div><b>vlmin</b>: {{vlmin}}</div>{{/vlmin}}
{{#vstmax}}<div><b>vstmax</b>: {{vstmax}}</div>{{/vstmax}}
{{#vstmin}}<div><b>vstmin</b>: {{vstmin}}</div>{{/vstmin}}
</div>
`
                );
           }        }

        /**
         * Italian PSS - Detailed PSS.
         *
         */
        class Pss5 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pss5;
                if (null == bucket)
                   cim_data.Pss5 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pss5[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "Pss5";
                base.parse_element (/<cim:Pss5.ctw2>([\s\S]*?)<\/cim:Pss5.ctw2>/g, obj, "ctw2", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pss5.deadband>([\s\S]*?)<\/cim:Pss5.deadband>/g, obj, "deadband", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.isfreq>([\s\S]*?)<\/cim:Pss5.isfreq>/g, obj, "isfreq", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pss5.kf>([\s\S]*?)<\/cim:Pss5.kf>/g, obj, "kf", base.to_float, sub, context);
                base.parse_element (/<cim:Pss5.kpe>([\s\S]*?)<\/cim:Pss5.kpe>/g, obj, "kpe", base.to_float, sub, context);
                base.parse_element (/<cim:Pss5.kpss>([\s\S]*?)<\/cim:Pss5.kpss>/g, obj, "kpss", base.to_float, sub, context);
                base.parse_element (/<cim:Pss5.pmm>([\s\S]*?)<\/cim:Pss5.pmm>/g, obj, "pmm", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tl1>([\s\S]*?)<\/cim:Pss5.tl1>/g, obj, "tl1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tl2>([\s\S]*?)<\/cim:Pss5.tl2>/g, obj, "tl2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tl3>([\s\S]*?)<\/cim:Pss5.tl3>/g, obj, "tl3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tl4>([\s\S]*?)<\/cim:Pss5.tl4>/g, obj, "tl4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tpe>([\s\S]*?)<\/cim:Pss5.tpe>/g, obj, "tpe", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tw1>([\s\S]*?)<\/cim:Pss5.tw1>/g, obj, "tw1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.tw2>([\s\S]*?)<\/cim:Pss5.tw2>/g, obj, "tw2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.vadat>([\s\S]*?)<\/cim:Pss5.vadat>/g, obj, "vadat", base.to_boolean, sub, context);
                base.parse_element (/<cim:Pss5.vsmn>([\s\S]*?)<\/cim:Pss5.vsmn>/g, obj, "vsmn", base.to_string, sub, context);
                base.parse_element (/<cim:Pss5.vsmx>([\s\S]*?)<\/cim:Pss5.vsmx>/g, obj, "vsmx", base.to_string, sub, context);

                var bucket = context.parsed.Pss5;
                if (null == bucket)
                   context.parsed.Pss5 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pss5", "ctw2", base.from_boolean, fields);
                base.export_element (obj, "Pss5", "deadband", base.from_string, fields);
                base.export_element (obj, "Pss5", "isfreq", base.from_boolean, fields);
                base.export_element (obj, "Pss5", "kf", base.from_float, fields);
                base.export_element (obj, "Pss5", "kpe", base.from_float, fields);
                base.export_element (obj, "Pss5", "kpss", base.from_float, fields);
                base.export_element (obj, "Pss5", "pmm", base.from_string, fields);
                base.export_element (obj, "Pss5", "tl1", base.from_string, fields);
                base.export_element (obj, "Pss5", "tl2", base.from_string, fields);
                base.export_element (obj, "Pss5", "tl3", base.from_string, fields);
                base.export_element (obj, "Pss5", "tl4", base.from_string, fields);
                base.export_element (obj, "Pss5", "tpe", base.from_string, fields);
                base.export_element (obj, "Pss5", "tw1", base.from_string, fields);
                base.export_element (obj, "Pss5", "tw2", base.from_string, fields);
                base.export_element (obj, "Pss5", "vadat", base.from_boolean, fields);
                base.export_element (obj, "Pss5", "vsmn", base.from_string, fields);
                base.export_element (obj, "Pss5", "vsmx", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pss5_collapse" aria-expanded="true" aria-controls="Pss5_collapse">Pss5</a>
<div id="Pss5_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#ctw2}}<div><b>ctw2</b>: {{ctw2}}</div>{{/ctw2}}
{{#deadband}}<div><b>deadband</b>: {{deadband}}</div>{{/deadband}}
{{#isfreq}}<div><b>isfreq</b>: {{isfreq}}</div>{{/isfreq}}
{{#kf}}<div><b>kf</b>: {{kf}}</div>{{/kf}}
{{#kpe}}<div><b>kpe</b>: {{kpe}}</div>{{/kpe}}
{{#kpss}}<div><b>kpss</b>: {{kpss}}</div>{{/kpss}}
{{#pmm}}<div><b>pmm</b>: {{pmm}}</div>{{/pmm}}
{{#tl1}}<div><b>tl1</b>: {{tl1}}</div>{{/tl1}}
{{#tl2}}<div><b>tl2</b>: {{tl2}}</div>{{/tl2}}
{{#tl3}}<div><b>tl3</b>: {{tl3}}</div>{{/tl3}}
{{#tl4}}<div><b>tl4</b>: {{tl4}}</div>{{/tl4}}
{{#tpe}}<div><b>tpe</b>: {{tpe}}</div>{{/tpe}}
{{#tw1}}<div><b>tw1</b>: {{tw1}}</div>{{/tw1}}
{{#tw2}}<div><b>tw2</b>: {{tw2}}</div>{{/tw2}}
{{#vadat}}<div><b>vadat</b>: {{vadat}}</div>{{/vadat}}
{{#vsmn}}<div><b>vsmn</b>: {{vsmn}}</div>{{/vsmn}}
{{#vsmx}}<div><b>vsmx</b>: {{vsmx}}</div>{{/vsmx}}
</div>
`
                );
           }        }

        /**
         * Power sensitive stabilizer model.
         *
         */
        class PssSB4 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssSB4;
                if (null == bucket)
                   cim_data.PssSB4 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssSB4[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssSB4";
                base.parse_element (/<cim:PssSB4.kx>([\s\S]*?)<\/cim:PssSB4.kx>/g, obj, "kx", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.ta>([\s\S]*?)<\/cim:PssSB4.ta>/g, obj, "ta", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.tb>([\s\S]*?)<\/cim:PssSB4.tb>/g, obj, "tb", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.tc>([\s\S]*?)<\/cim:PssSB4.tc>/g, obj, "tc", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.td>([\s\S]*?)<\/cim:PssSB4.td>/g, obj, "td", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.te>([\s\S]*?)<\/cim:PssSB4.te>/g, obj, "te", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.tt>([\s\S]*?)<\/cim:PssSB4.tt>/g, obj, "tt", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.tx1>([\s\S]*?)<\/cim:PssSB4.tx1>/g, obj, "tx1", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.tx2>([\s\S]*?)<\/cim:PssSB4.tx2>/g, obj, "tx2", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.vsmax>([\s\S]*?)<\/cim:PssSB4.vsmax>/g, obj, "vsmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssSB4.vsmin>([\s\S]*?)<\/cim:PssSB4.vsmin>/g, obj, "vsmin", base.to_string, sub, context);

                var bucket = context.parsed.PssSB4;
                if (null == bucket)
                   context.parsed.PssSB4 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssSB4", "kx", base.from_string, fields);
                base.export_element (obj, "PssSB4", "ta", base.from_string, fields);
                base.export_element (obj, "PssSB4", "tb", base.from_string, fields);
                base.export_element (obj, "PssSB4", "tc", base.from_string, fields);
                base.export_element (obj, "PssSB4", "td", base.from_string, fields);
                base.export_element (obj, "PssSB4", "te", base.from_string, fields);
                base.export_element (obj, "PssSB4", "tt", base.from_string, fields);
                base.export_element (obj, "PssSB4", "tx1", base.from_string, fields);
                base.export_element (obj, "PssSB4", "tx2", base.from_string, fields);
                base.export_element (obj, "PssSB4", "vsmax", base.from_string, fields);
                base.export_element (obj, "PssSB4", "vsmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssSB4_collapse" aria-expanded="true" aria-controls="PssSB4_collapse">PssSB4</a>
<div id="PssSB4_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#kx}}<div><b>kx</b>: {{kx}}</div>{{/kx}}
{{#ta}}<div><b>ta</b>: {{ta}}</div>{{/ta}}
{{#tb}}<div><b>tb</b>: {{tb}}</div>{{/tb}}
{{#tc}}<div><b>tc</b>: {{tc}}</div>{{/tc}}
{{#td}}<div><b>td</b>: {{td}}</div>{{/td}}
{{#te}}<div><b>te</b>: {{te}}</div>{{/te}}
{{#tt}}<div><b>tt</b>: {{tt}}</div>{{/tt}}
{{#tx1}}<div><b>tx1</b>: {{tx1}}</div>{{/tx1}}
{{#tx2}}<div><b>tx2</b>: {{tx2}}</div>{{/tx2}}
{{#vsmax}}<div><b>vsmax</b>: {{vsmax}}</div>{{/vsmax}}
{{#vsmin}}<div><b>vsmin</b>: {{vsmin}}</div>{{/vsmin}}
</div>
`
                );
           }        }

        /**
         * The class represents IEEE Std 421.5-2005 type PSS3B power system stabilizer model.
         *
         * The PSS model PSS3B has dual inputs of electrical power and rotor angular frequency deviation. The signals are used to derive an equivalent mechanical power signal.
         *
         */
        class PssIEEE3B extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssIEEE3B;
                if (null == bucket)
                   cim_data.PssIEEE3B = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssIEEE3B[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssIEEE3B";
                base.parse_element (/<cim:PssIEEE3B.a1>([\s\S]*?)<\/cim:PssIEEE3B.a1>/g, obj, "a1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a2>([\s\S]*?)<\/cim:PssIEEE3B.a2>/g, obj, "a2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a3>([\s\S]*?)<\/cim:PssIEEE3B.a3>/g, obj, "a3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a4>([\s\S]*?)<\/cim:PssIEEE3B.a4>/g, obj, "a4", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a5>([\s\S]*?)<\/cim:PssIEEE3B.a5>/g, obj, "a5", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a6>([\s\S]*?)<\/cim:PssIEEE3B.a6>/g, obj, "a6", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a7>([\s\S]*?)<\/cim:PssIEEE3B.a7>/g, obj, "a7", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.a8>([\s\S]*?)<\/cim:PssIEEE3B.a8>/g, obj, "a8", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.inputSignal1Type>([\s\S]*?)<\/cim:PssIEEE3B.inputSignal1Type>/g, obj, "inputSignal1Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.inputSignal2Type>([\s\S]*?)<\/cim:PssIEEE3B.inputSignal2Type>/g, obj, "inputSignal2Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.ks1>([\s\S]*?)<\/cim:PssIEEE3B.ks1>/g, obj, "ks1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.ks2>([\s\S]*?)<\/cim:PssIEEE3B.ks2>/g, obj, "ks2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.t1>([\s\S]*?)<\/cim:PssIEEE3B.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.t2>([\s\S]*?)<\/cim:PssIEEE3B.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.tw1>([\s\S]*?)<\/cim:PssIEEE3B.tw1>/g, obj, "tw1", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.tw2>([\s\S]*?)<\/cim:PssIEEE3B.tw2>/g, obj, "tw2", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.tw3>([\s\S]*?)<\/cim:PssIEEE3B.tw3>/g, obj, "tw3", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.vstmax>([\s\S]*?)<\/cim:PssIEEE3B.vstmax>/g, obj, "vstmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssIEEE3B.vstmin>([\s\S]*?)<\/cim:PssIEEE3B.vstmin>/g, obj, "vstmin", base.to_string, sub, context);

                var bucket = context.parsed.PssIEEE3B;
                if (null == bucket)
                   context.parsed.PssIEEE3B = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssIEEE3B", "a1", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a2", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a3", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a4", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a5", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a6", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a7", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "a8", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "inputSignal1Type", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "inputSignal2Type", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "ks1", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "ks2", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "t1", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "t2", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "tw1", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "tw2", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "tw3", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "vstmax", base.from_string, fields);
                base.export_element (obj, "PssIEEE3B", "vstmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssIEEE3B_collapse" aria-expanded="true" aria-controls="PssIEEE3B_collapse">PssIEEE3B</a>
<div id="PssIEEE3B_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#a1}}<div><b>a1</b>: {{a1}}</div>{{/a1}}
{{#a2}}<div><b>a2</b>: {{a2}}</div>{{/a2}}
{{#a3}}<div><b>a3</b>: {{a3}}</div>{{/a3}}
{{#a4}}<div><b>a4</b>: {{a4}}</div>{{/a4}}
{{#a5}}<div><b>a5</b>: {{a5}}</div>{{/a5}}
{{#a6}}<div><b>a6</b>: {{a6}}</div>{{/a6}}
{{#a7}}<div><b>a7</b>: {{a7}}</div>{{/a7}}
{{#a8}}<div><b>a8</b>: {{a8}}</div>{{/a8}}
{{#inputSignal1Type}}<div><b>inputSignal1Type</b>: {{inputSignal1Type}}</div>{{/inputSignal1Type}}
{{#inputSignal2Type}}<div><b>inputSignal2Type</b>: {{inputSignal2Type}}</div>{{/inputSignal2Type}}
{{#ks1}}<div><b>ks1</b>: {{ks1}}</div>{{/ks1}}
{{#ks2}}<div><b>ks2</b>: {{ks2}}</div>{{/ks2}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#tw1}}<div><b>tw1</b>: {{tw1}}</div>{{/tw1}}
{{#tw2}}<div><b>tw2</b>: {{tw2}}</div>{{/tw2}}
{{#tw3}}<div><b>tw3</b>: {{tw3}}</div>{{/tw3}}
{{#vstmax}}<div><b>vstmax</b>: {{vstmax}}</div>{{/vstmax}}
{{#vstmin}}<div><b>vstmin</b>: {{vstmin}}</div>{{/vstmin}}
</div>
`
                );
           }        }

        /**
         * Power system stabilizer typically associated with ExcELIN2 (though PssIEEE2B or Pss2B can also be used).
         *
         */
        class PssELIN2 extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssELIN2;
                if (null == bucket)
                   cim_data.PssELIN2 = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssELIN2[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssELIN2";
                base.parse_element (/<cim:PssELIN2.apss>([\s\S]*?)<\/cim:PssELIN2.apss>/g, obj, "apss", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ks1>([\s\S]*?)<\/cim:PssELIN2.ks1>/g, obj, "ks1", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ks2>([\s\S]*?)<\/cim:PssELIN2.ks2>/g, obj, "ks2", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ppss>([\s\S]*?)<\/cim:PssELIN2.ppss>/g, obj, "ppss", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.psslim>([\s\S]*?)<\/cim:PssELIN2.psslim>/g, obj, "psslim", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts1>([\s\S]*?)<\/cim:PssELIN2.ts1>/g, obj, "ts1", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts2>([\s\S]*?)<\/cim:PssELIN2.ts2>/g, obj, "ts2", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts3>([\s\S]*?)<\/cim:PssELIN2.ts3>/g, obj, "ts3", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts4>([\s\S]*?)<\/cim:PssELIN2.ts4>/g, obj, "ts4", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts5>([\s\S]*?)<\/cim:PssELIN2.ts5>/g, obj, "ts5", base.to_string, sub, context);
                base.parse_element (/<cim:PssELIN2.ts6>([\s\S]*?)<\/cim:PssELIN2.ts6>/g, obj, "ts6", base.to_string, sub, context);

                var bucket = context.parsed.PssELIN2;
                if (null == bucket)
                   context.parsed.PssELIN2 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssELIN2", "apss", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ks1", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ks2", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ppss", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "psslim", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts1", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts2", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts3", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts4", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts5", base.from_string, fields);
                base.export_element (obj, "PssELIN2", "ts6", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssELIN2_collapse" aria-expanded="true" aria-controls="PssELIN2_collapse">PssELIN2</a>
<div id="PssELIN2_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#apss}}<div><b>apss</b>: {{apss}}</div>{{/apss}}
{{#ks1}}<div><b>ks1</b>: {{ks1}}</div>{{/ks1}}
{{#ks2}}<div><b>ks2</b>: {{ks2}}</div>{{/ks2}}
{{#ppss}}<div><b>ppss</b>: {{ppss}}</div>{{/ppss}}
{{#psslim}}<div><b>psslim</b>: {{psslim}}</div>{{/psslim}}
{{#ts1}}<div><b>ts1</b>: {{ts1}}</div>{{/ts1}}
{{#ts2}}<div><b>ts2</b>: {{ts2}}</div>{{/ts2}}
{{#ts3}}<div><b>ts3</b>: {{ts3}}</div>{{/ts3}}
{{#ts4}}<div><b>ts4</b>: {{ts4}}</div>{{/ts4}}
{{#ts5}}<div><b>ts5</b>: {{ts5}}</div>{{/ts5}}
{{#ts6}}<div><b>ts6</b>: {{ts6}}</div>{{/ts6}}
</div>
`
                );
           }        }

        /**
         * Modified IEEE PSS2B Model.
         *
         * Extra lead/lag (or rate) block added at end (up to 4 lead/lags total).
         *
         */
        class Pss2B extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.Pss2B;
                if (null == bucket)
                   cim_data.Pss2B = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.Pss2B[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "Pss2B";
                base.parse_element (/<cim:Pss2B.a>([\s\S]*?)<\/cim:Pss2B.a>/g, obj, "a", base.to_float, sub, context);
                base.parse_element (/<cim:Pss2B.inputSignal1Type>([\s\S]*?)<\/cim:Pss2B.inputSignal1Type>/g, obj, "inputSignal1Type", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.inputSignal2Type>([\s\S]*?)<\/cim:Pss2B.inputSignal2Type>/g, obj, "inputSignal2Type", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.ks1>([\s\S]*?)<\/cim:Pss2B.ks1>/g, obj, "ks1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.ks2>([\s\S]*?)<\/cim:Pss2B.ks2>/g, obj, "ks2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.ks3>([\s\S]*?)<\/cim:Pss2B.ks3>/g, obj, "ks3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.ks4>([\s\S]*?)<\/cim:Pss2B.ks4>/g, obj, "ks4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.m>([\s\S]*?)<\/cim:Pss2B.m>/g, obj, "m", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.n>([\s\S]*?)<\/cim:Pss2B.n>/g, obj, "n", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t1>([\s\S]*?)<\/cim:Pss2B.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t10>([\s\S]*?)<\/cim:Pss2B.t10>/g, obj, "t10", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t11>([\s\S]*?)<\/cim:Pss2B.t11>/g, obj, "t11", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t2>([\s\S]*?)<\/cim:Pss2B.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t3>([\s\S]*?)<\/cim:Pss2B.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t4>([\s\S]*?)<\/cim:Pss2B.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t6>([\s\S]*?)<\/cim:Pss2B.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t7>([\s\S]*?)<\/cim:Pss2B.t7>/g, obj, "t7", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t8>([\s\S]*?)<\/cim:Pss2B.t8>/g, obj, "t8", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.t9>([\s\S]*?)<\/cim:Pss2B.t9>/g, obj, "t9", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.ta>([\s\S]*?)<\/cim:Pss2B.ta>/g, obj, "ta", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.tb>([\s\S]*?)<\/cim:Pss2B.tb>/g, obj, "tb", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.tw1>([\s\S]*?)<\/cim:Pss2B.tw1>/g, obj, "tw1", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.tw2>([\s\S]*?)<\/cim:Pss2B.tw2>/g, obj, "tw2", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.tw3>([\s\S]*?)<\/cim:Pss2B.tw3>/g, obj, "tw3", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.tw4>([\s\S]*?)<\/cim:Pss2B.tw4>/g, obj, "tw4", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vsi1max>([\s\S]*?)<\/cim:Pss2B.vsi1max>/g, obj, "vsi1max", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vsi1min>([\s\S]*?)<\/cim:Pss2B.vsi1min>/g, obj, "vsi1min", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vsi2max>([\s\S]*?)<\/cim:Pss2B.vsi2max>/g, obj, "vsi2max", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vsi2min>([\s\S]*?)<\/cim:Pss2B.vsi2min>/g, obj, "vsi2min", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vstmax>([\s\S]*?)<\/cim:Pss2B.vstmax>/g, obj, "vstmax", base.to_string, sub, context);
                base.parse_element (/<cim:Pss2B.vstmin>([\s\S]*?)<\/cim:Pss2B.vstmin>/g, obj, "vstmin", base.to_string, sub, context);

                var bucket = context.parsed.Pss2B;
                if (null == bucket)
                   context.parsed.Pss2B = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "Pss2B", "a", base.from_float, fields);
                base.export_element (obj, "Pss2B", "inputSignal1Type", base.from_string, fields);
                base.export_element (obj, "Pss2B", "inputSignal2Type", base.from_string, fields);
                base.export_element (obj, "Pss2B", "ks1", base.from_string, fields);
                base.export_element (obj, "Pss2B", "ks2", base.from_string, fields);
                base.export_element (obj, "Pss2B", "ks3", base.from_string, fields);
                base.export_element (obj, "Pss2B", "ks4", base.from_string, fields);
                base.export_element (obj, "Pss2B", "m", base.from_string, fields);
                base.export_element (obj, "Pss2B", "n", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t1", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t10", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t11", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t2", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t3", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t4", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t6", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t7", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t8", base.from_string, fields);
                base.export_element (obj, "Pss2B", "t9", base.from_string, fields);
                base.export_element (obj, "Pss2B", "ta", base.from_string, fields);
                base.export_element (obj, "Pss2B", "tb", base.from_string, fields);
                base.export_element (obj, "Pss2B", "tw1", base.from_string, fields);
                base.export_element (obj, "Pss2B", "tw2", base.from_string, fields);
                base.export_element (obj, "Pss2B", "tw3", base.from_string, fields);
                base.export_element (obj, "Pss2B", "tw4", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vsi1max", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vsi1min", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vsi2max", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vsi2min", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vstmax", base.from_string, fields);
                base.export_element (obj, "Pss2B", "vstmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#Pss2B_collapse" aria-expanded="true" aria-controls="Pss2B_collapse">Pss2B</a>
<div id="Pss2B_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#a}}<div><b>a</b>: {{a}}</div>{{/a}}
{{#inputSignal1Type}}<div><b>inputSignal1Type</b>: {{inputSignal1Type}}</div>{{/inputSignal1Type}}
{{#inputSignal2Type}}<div><b>inputSignal2Type</b>: {{inputSignal2Type}}</div>{{/inputSignal2Type}}
{{#ks1}}<div><b>ks1</b>: {{ks1}}</div>{{/ks1}}
{{#ks2}}<div><b>ks2</b>: {{ks2}}</div>{{/ks2}}
{{#ks3}}<div><b>ks3</b>: {{ks3}}</div>{{/ks3}}
{{#ks4}}<div><b>ks4</b>: {{ks4}}</div>{{/ks4}}
{{#m}}<div><b>m</b>: {{m}}</div>{{/m}}
{{#n}}<div><b>n</b>: {{n}}</div>{{/n}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t10}}<div><b>t10</b>: {{t10}}</div>{{/t10}}
{{#t11}}<div><b>t11</b>: {{t11}}</div>{{/t11}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#t7}}<div><b>t7</b>: {{t7}}</div>{{/t7}}
{{#t8}}<div><b>t8</b>: {{t8}}</div>{{/t8}}
{{#t9}}<div><b>t9</b>: {{t9}}</div>{{/t9}}
{{#ta}}<div><b>ta</b>: {{ta}}</div>{{/ta}}
{{#tb}}<div><b>tb</b>: {{tb}}</div>{{/tb}}
{{#tw1}}<div><b>tw1</b>: {{tw1}}</div>{{/tw1}}
{{#tw2}}<div><b>tw2</b>: {{tw2}}</div>{{/tw2}}
{{#tw3}}<div><b>tw3</b>: {{tw3}}</div>{{/tw3}}
{{#tw4}}<div><b>tw4</b>: {{tw4}}</div>{{/tw4}}
{{#vsi1max}}<div><b>vsi1max</b>: {{vsi1max}}</div>{{/vsi1max}}
{{#vsi1min}}<div><b>vsi1min</b>: {{vsi1min}}</div>{{/vsi1min}}
{{#vsi2max}}<div><b>vsi2max</b>: {{vsi2max}}</div>{{/vsi2max}}
{{#vsi2min}}<div><b>vsi2min</b>: {{vsi2min}}</div>{{/vsi2min}}
{{#vstmax}}<div><b>vstmax</b>: {{vstmax}}</div>{{/vstmax}}
{{#vstmin}}<div><b>vstmin</b>: {{vstmin}}</div>{{/vstmin}}
</div>
`
                );
           }        }

        /**
         * Dual input Power System Stabilizer, based on IEEE type 2, with modified output limiter defined by WECC (Western Electricity Coordinating Council, USA).
         *
         */
        class PssWECC extends PowerSystemStabilizerDynamics
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PssWECC;
                if (null == bucket)
                   cim_data.PssWECC = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PssWECC[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemStabilizerDynamics.prototype.parse.call (this, context, sub);
                obj.cls = "PssWECC";
                base.parse_element (/<cim:PssWECC.inputSignal1Type>([\s\S]*?)<\/cim:PssWECC.inputSignal1Type>/g, obj, "inputSignal1Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.inputSignal2Type>([\s\S]*?)<\/cim:PssWECC.inputSignal2Type>/g, obj, "inputSignal2Type", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.k1>([\s\S]*?)<\/cim:PssWECC.k1>/g, obj, "k1", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.k2>([\s\S]*?)<\/cim:PssWECC.k2>/g, obj, "k2", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t1>([\s\S]*?)<\/cim:PssWECC.t1>/g, obj, "t1", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t10>([\s\S]*?)<\/cim:PssWECC.t10>/g, obj, "t10", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t2>([\s\S]*?)<\/cim:PssWECC.t2>/g, obj, "t2", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t3>([\s\S]*?)<\/cim:PssWECC.t3>/g, obj, "t3", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t4>([\s\S]*?)<\/cim:PssWECC.t4>/g, obj, "t4", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t5>([\s\S]*?)<\/cim:PssWECC.t5>/g, obj, "t5", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t6>([\s\S]*?)<\/cim:PssWECC.t6>/g, obj, "t6", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t7>([\s\S]*?)<\/cim:PssWECC.t7>/g, obj, "t7", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t8>([\s\S]*?)<\/cim:PssWECC.t8>/g, obj, "t8", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.t9>([\s\S]*?)<\/cim:PssWECC.t9>/g, obj, "t9", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.vcl>([\s\S]*?)<\/cim:PssWECC.vcl>/g, obj, "vcl", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.vcu>([\s\S]*?)<\/cim:PssWECC.vcu>/g, obj, "vcu", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.vsmax>([\s\S]*?)<\/cim:PssWECC.vsmax>/g, obj, "vsmax", base.to_string, sub, context);
                base.parse_element (/<cim:PssWECC.vsmin>([\s\S]*?)<\/cim:PssWECC.vsmin>/g, obj, "vsmin", base.to_string, sub, context);

                var bucket = context.parsed.PssWECC;
                if (null == bucket)
                   context.parsed.PssWECC = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemStabilizerDynamics.prototype.export.call (this, obj, false);

                base.export_element (obj, "PssWECC", "inputSignal1Type", base.from_string, fields);
                base.export_element (obj, "PssWECC", "inputSignal2Type", base.from_string, fields);
                base.export_element (obj, "PssWECC", "k1", base.from_string, fields);
                base.export_element (obj, "PssWECC", "k2", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t1", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t10", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t2", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t3", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t4", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t5", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t6", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t7", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t8", base.from_string, fields);
                base.export_element (obj, "PssWECC", "t9", base.from_string, fields);
                base.export_element (obj, "PssWECC", "vcl", base.from_string, fields);
                base.export_element (obj, "PssWECC", "vcu", base.from_string, fields);
                base.export_element (obj, "PssWECC", "vsmax", base.from_string, fields);
                base.export_element (obj, "PssWECC", "vsmin", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PssWECC_collapse" aria-expanded="true" aria-controls="PssWECC_collapse">PssWECC</a>
<div id="PssWECC_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemStabilizerDynamics.prototype.template.call (this) +
`
{{#inputSignal1Type}}<div><b>inputSignal1Type</b>: {{inputSignal1Type}}</div>{{/inputSignal1Type}}
{{#inputSignal2Type}}<div><b>inputSignal2Type</b>: {{inputSignal2Type}}</div>{{/inputSignal2Type}}
{{#k1}}<div><b>k1</b>: {{k1}}</div>{{/k1}}
{{#k2}}<div><b>k2</b>: {{k2}}</div>{{/k2}}
{{#t1}}<div><b>t1</b>: {{t1}}</div>{{/t1}}
{{#t10}}<div><b>t10</b>: {{t10}}</div>{{/t10}}
{{#t2}}<div><b>t2</b>: {{t2}}</div>{{/t2}}
{{#t3}}<div><b>t3</b>: {{t3}}</div>{{/t3}}
{{#t4}}<div><b>t4</b>: {{t4}}</div>{{/t4}}
{{#t5}}<div><b>t5</b>: {{t5}}</div>{{/t5}}
{{#t6}}<div><b>t6</b>: {{t6}}</div>{{/t6}}
{{#t7}}<div><b>t7</b>: {{t7}}</div>{{/t7}}
{{#t8}}<div><b>t8</b>: {{t8}}</div>{{/t8}}
{{#t9}}<div><b>t9</b>: {{t9}}</div>{{/t9}}
{{#vcl}}<div><b>vcl</b>: {{vcl}}</div>{{/vcl}}
{{#vcu}}<div><b>vcu</b>: {{vcu}}</div>{{/vcu}}
{{#vsmax}}<div><b>vsmax</b>: {{vsmax}}</div>{{/vsmax}}
{{#vsmin}}<div><b>vsmin</b>: {{vsmin}}</div>{{/vsmin}}
</div>
`
                );
           }        }

        return (
            {
                PssPTIST3: PssPTIST3,
                PssELIN2: PssELIN2,
                PowerSystemStabilizerDynamics: PowerSystemStabilizerDynamics,
                PssSK: PssSK,
                PssSB4: PssSB4,
                PssSH: PssSH,
                PssIEEE1A: PssIEEE1A,
                PssIEEE4B: PssIEEE4B,
                Pss1A: Pss1A,
                InputSignalKind: InputSignalKind,
                PssIEEE3B: PssIEEE3B,
                PssPTIST1: PssPTIST1,
                Pss1: Pss1,
                PssWECC: PssWECC,
                Pss2B: Pss2B,
                Pss5: Pss5,
                Pss2ST: Pss2ST,
                PssIEEE2B: PssIEEE2B
            }
        );
    }
);