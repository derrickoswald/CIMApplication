define
(
    ["model/base", "model/Core"],
    /**
     * Contains entities that describe dynamic measurement data exchanged between applications.
     *
     */
    function (base, Core)
    {

        /**
         * Validity for MeasurementValue.
         *
         */
        var Validity =
        {
            GOOD: "GOOD",
            QUESTIONABLE: "QUESTIONABLE",
            INVALID: "INVALID"
        };
        Object.freeze (Validity);

        /**
         * Describes the translation of a set of values into a name and is intendend to facilitate cusom translations.
         *
         * Each ValueAliasSet has a name, description etc. A specific Measurement may represent a discrete state like Open, Closed, Intermediate etc. This requires a translation from the MeasurementValue.value number to a string, e.g. 0-&gt;"Invalid", 1-&gt;"Open", 2-&gt;"Closed", 3-&gt;"Intermediate". Each ValueToAlias member in ValueAliasSet.Value describe a mapping for one particular value to a name.
         *
         */
        class ValueAliasSet extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.ValueAliasSet;
                if (null == bucket)
                   cim_data.ValueAliasSet = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.ValueAliasSet[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "ValueAliasSet";
                base.parse_attributes (/<cim:ValueAliasSet.Commands\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Commands", sub, context);
                base.parse_attributes (/<cim:ValueAliasSet.RaiseLowerCommands\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "RaiseLowerCommands", sub, context);
                base.parse_attributes (/<cim:ValueAliasSet.Discretes\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Discretes", sub, context);
                base.parse_attributes (/<cim:ValueAliasSet.Values\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Values", sub, context);
                var bucket = context.parsed.ValueAliasSet;
                if (null == bucket)
                   context.parsed.ValueAliasSet = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "ValueAliasSet", "Commands", "Commands", fields);
                base.export_attributes (obj, "ValueAliasSet", "RaiseLowerCommands", "RaiseLowerCommands", fields);
                base.export_attributes (obj, "ValueAliasSet", "Discretes", "Discretes", fields);
                base.export_attributes (obj, "ValueAliasSet", "Values", "Values", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#ValueAliasSet_collapse" aria-expanded="true" aria-controls="ValueAliasSet_collapse" style="margin-left: 10px;">ValueAliasSet</a></legend>
                    <div id="ValueAliasSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#Commands}}<div><b>Commands</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Commands}}
                    {{#RaiseLowerCommands}}<div><b>RaiseLowerCommands</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/RaiseLowerCommands}}
                    {{#Discretes}}<div><b>Discretes</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Discretes}}
                    {{#Values}}<div><b>Values</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Values}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Commands) obj.Commands_string = obj.Commands.join ();
                if (obj.RaiseLowerCommands) obj.RaiseLowerCommands_string = obj.RaiseLowerCommands.join ();
                if (obj.Discretes) obj.Discretes_string = obj.Discretes.join ();
                if (obj.Values) obj.Values_string = obj.Values.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Commands_string;
                delete obj.RaiseLowerCommands_string;
                delete obj.Discretes_string;
                delete obj.Values_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_ValueAliasSet_collapse" aria-expanded="true" aria-controls="{{id}}_ValueAliasSet_collapse" style="margin-left: 10px;">ValueAliasSet</a></legend>
                    <div id="{{id}}_ValueAliasSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var obj = obj || { id: id, cls: "ValueAliasSet" };
                super.submit (id, obj);

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Commands", "0..*", "0..1", "Command", "ValueAliasSet"],
                            ["RaiseLowerCommands", "0..*", "0..1", "RaiseLowerCommand", "ValueAliasSet"],
                            ["Discretes", "0..*", "0..1", "Discrete", "ValueAliasSet"],
                            ["Values", "1..*", "1", "ValueToAlias", "ValueAliasSet"]
                        ]
                    )
                );
            }
        }

        /**
         * The current state for a measurement.
         *
         * A state value is an instance of a measurement from a specific source. Measurements can be associated with many state values, each representing a different source for the measurement.
         *
         */
        class MeasurementValue extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.MeasurementValue;
                if (null == bucket)
                   cim_data.MeasurementValue = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.MeasurementValue[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "MeasurementValue";
                base.parse_element (/<cim:MeasurementValue.sensorAccuracy>([\s\S]*?)<\/cim:MeasurementValue.sensorAccuracy>/g, obj, "sensorAccuracy", base.to_string, sub, context);
                base.parse_element (/<cim:MeasurementValue.timeStamp>([\s\S]*?)<\/cim:MeasurementValue.timeStamp>/g, obj, "timeStamp", base.to_datetime, sub, context);
                base.parse_attribute (/<cim:MeasurementValue.MeasurementValueSource\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MeasurementValueSource", sub, context);
                base.parse_attributes (/<cim:MeasurementValue.ProcedureDataSets\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ProcedureDataSets", sub, context);
                base.parse_attribute (/<cim:MeasurementValue.ErpPerson\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ErpPerson", sub, context);
                base.parse_attribute (/<cim:MeasurementValue.MeasurementValueQuality\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MeasurementValueQuality", sub, context);
                base.parse_attribute (/<cim:MeasurementValue.RemoteSource\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "RemoteSource", sub, context);
                var bucket = context.parsed.MeasurementValue;
                if (null == bucket)
                   context.parsed.MeasurementValue = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "MeasurementValue", "sensorAccuracy", "sensorAccuracy",  base.from_string, fields);
                base.export_element (obj, "MeasurementValue", "timeStamp", "timeStamp",  base.from_datetime, fields);
                base.export_attribute (obj, "MeasurementValue", "MeasurementValueSource", "MeasurementValueSource", fields);
                base.export_attributes (obj, "MeasurementValue", "ProcedureDataSets", "ProcedureDataSets", fields);
                base.export_attribute (obj, "MeasurementValue", "ErpPerson", "ErpPerson", fields);
                base.export_attribute (obj, "MeasurementValue", "MeasurementValueQuality", "MeasurementValueQuality", fields);
                base.export_attribute (obj, "MeasurementValue", "RemoteSource", "RemoteSource", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#MeasurementValue_collapse" aria-expanded="true" aria-controls="MeasurementValue_collapse" style="margin-left: 10px;">MeasurementValue</a></legend>
                    <div id="MeasurementValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#sensorAccuracy}}<div><b>sensorAccuracy</b>: {{sensorAccuracy}}</div>{{/sensorAccuracy}}
                    {{#timeStamp}}<div><b>timeStamp</b>: {{timeStamp}}</div>{{/timeStamp}}
                    {{#MeasurementValueSource}}<div><b>MeasurementValueSource</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{MeasurementValueSource}}&quot;);})'>{{MeasurementValueSource}}</a></div>{{/MeasurementValueSource}}
                    {{#ProcedureDataSets}}<div><b>ProcedureDataSets</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/ProcedureDataSets}}
                    {{#ErpPerson}}<div><b>ErpPerson</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ErpPerson}}&quot;);})'>{{ErpPerson}}</a></div>{{/ErpPerson}}
                    {{#MeasurementValueQuality}}<div><b>MeasurementValueQuality</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{MeasurementValueQuality}}&quot;);})'>{{MeasurementValueQuality}}</a></div>{{/MeasurementValueQuality}}
                    {{#RemoteSource}}<div><b>RemoteSource</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{RemoteSource}}&quot;);})'>{{RemoteSource}}</a></div>{{/RemoteSource}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.ProcedureDataSets) obj.ProcedureDataSets_string = obj.ProcedureDataSets.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.ProcedureDataSets_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_MeasurementValue_collapse" aria-expanded="true" aria-controls="{{id}}_MeasurementValue_collapse" style="margin-left: 10px;">MeasurementValue</a></legend>
                    <div id="{{id}}_MeasurementValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_sensorAccuracy'>sensorAccuracy: </label><div class='col-sm-8'><input id='{{id}}_sensorAccuracy' class='form-control' type='text'{{#sensorAccuracy}} value='{{sensorAccuracy}}'{{/sensorAccuracy}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_timeStamp'>timeStamp: </label><div class='col-sm-8'><input id='{{id}}_timeStamp' class='form-control' type='text'{{#timeStamp}} value='{{timeStamp}}'{{/timeStamp}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_MeasurementValueSource'>MeasurementValueSource: </label><div class='col-sm-8'><input id='{{id}}_MeasurementValueSource' class='form-control' type='text'{{#MeasurementValueSource}} value='{{MeasurementValueSource}}'{{/MeasurementValueSource}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ProcedureDataSets'>ProcedureDataSets: </label><div class='col-sm-8'><input id='{{id}}_ProcedureDataSets' class='form-control' type='text'{{#ProcedureDataSets}} value='{{ProcedureDataSets}}_string'{{/ProcedureDataSets}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ErpPerson'>ErpPerson: </label><div class='col-sm-8'><input id='{{id}}_ErpPerson' class='form-control' type='text'{{#ErpPerson}} value='{{ErpPerson}}'{{/ErpPerson}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_MeasurementValueQuality'>MeasurementValueQuality: </label><div class='col-sm-8'><input id='{{id}}_MeasurementValueQuality' class='form-control' type='text'{{#MeasurementValueQuality}} value='{{MeasurementValueQuality}}'{{/MeasurementValueQuality}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_RemoteSource'>RemoteSource: </label><div class='col-sm-8'><input id='{{id}}_RemoteSource' class='form-control' type='text'{{#RemoteSource}} value='{{RemoteSource}}'{{/RemoteSource}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "MeasurementValue" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_sensorAccuracy").value; if ("" != temp) obj.sensorAccuracy = temp;
                temp = document.getElementById (id + "_timeStamp").value; if ("" != temp) obj.timeStamp = temp;
                temp = document.getElementById (id + "_MeasurementValueSource").value; if ("" != temp) obj.MeasurementValueSource = temp;
                temp = document.getElementById (id + "_ProcedureDataSets").value; if ("" != temp) obj.ProcedureDataSets = temp.split (",");
                temp = document.getElementById (id + "_ErpPerson").value; if ("" != temp) obj.ErpPerson = temp;
                temp = document.getElementById (id + "_MeasurementValueQuality").value; if ("" != temp) obj.MeasurementValueQuality = temp;
                temp = document.getElementById (id + "_RemoteSource").value; if ("" != temp) obj.RemoteSource = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["MeasurementValueSource", "1", "0..*", "MeasurementValueSource", "MeasurementValues"],
                            ["ProcedureDataSets", "0..*", "0..*", "ProcedureDataSet", "MeasurementValues"],
                            ["ErpPerson", "0..1", "0..*", "OldPerson", "MeasurementValues"],
                            ["MeasurementValueQuality", "0..1", "1", "MeasurementValueQuality", "MeasurementValue"],
                            ["RemoteSource", "0..1", "1", "RemoteSource", "MeasurementValue"]
                        ]
                    )
                );
            }
        }

        /**
         * Quality flags in this class are as defined in IEC 61850, except for estimatorReplaced, which has been included in this class for convenience.
         *
         */
        class Quality61850 extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Quality61850;
                if (null == bucket)
                   cim_data.Quality61850 = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Quality61850[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "Quality61850";
                base.parse_element (/<cim:Quality61850.badReference>([\s\S]*?)<\/cim:Quality61850.badReference>/g, obj, "badReference", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.estimatorReplaced>([\s\S]*?)<\/cim:Quality61850.estimatorReplaced>/g, obj, "estimatorReplaced", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.failure>([\s\S]*?)<\/cim:Quality61850.failure>/g, obj, "failure", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.oldData>([\s\S]*?)<\/cim:Quality61850.oldData>/g, obj, "oldData", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.operatorBlocked>([\s\S]*?)<\/cim:Quality61850.operatorBlocked>/g, obj, "operatorBlocked", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.oscillatory>([\s\S]*?)<\/cim:Quality61850.oscillatory>/g, obj, "oscillatory", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.outOfRange>([\s\S]*?)<\/cim:Quality61850.outOfRange>/g, obj, "outOfRange", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.overFlow>([\s\S]*?)<\/cim:Quality61850.overFlow>/g, obj, "overFlow", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.source>([\s\S]*?)<\/cim:Quality61850.source>/g, obj, "source", base.to_string, sub, context);
                base.parse_element (/<cim:Quality61850.suspect>([\s\S]*?)<\/cim:Quality61850.suspect>/g, obj, "suspect", base.to_boolean, sub, context);
                base.parse_element (/<cim:Quality61850.test>([\s\S]*?)<\/cim:Quality61850.test>/g, obj, "test", base.to_boolean, sub, context);
                base.parse_attribute (/<cim:Quality61850.validity\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "validity", sub, context);
                var bucket = context.parsed.Quality61850;
                if (null == bucket)
                   context.parsed.Quality61850 = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "Quality61850", "badReference", "badReference",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "estimatorReplaced", "estimatorReplaced",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "failure", "failure",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "oldData", "oldData",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "operatorBlocked", "operatorBlocked",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "oscillatory", "oscillatory",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "outOfRange", "outOfRange",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "overFlow", "overFlow",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "source", "source",  base.from_string, fields);
                base.export_element (obj, "Quality61850", "suspect", "suspect",  base.from_boolean, fields);
                base.export_element (obj, "Quality61850", "test", "test",  base.from_boolean, fields);
                base.export_attribute (obj, "Quality61850", "validity", "validity", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Quality61850_collapse" aria-expanded="true" aria-controls="Quality61850_collapse" style="margin-left: 10px;">Quality61850</a></legend>
                    <div id="Quality61850_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + base.Element.prototype.template.call (this) +
                    `
                    {{#badReference}}<div><b>badReference</b>: {{badReference}}</div>{{/badReference}}
                    {{#estimatorReplaced}}<div><b>estimatorReplaced</b>: {{estimatorReplaced}}</div>{{/estimatorReplaced}}
                    {{#failure}}<div><b>failure</b>: {{failure}}</div>{{/failure}}
                    {{#oldData}}<div><b>oldData</b>: {{oldData}}</div>{{/oldData}}
                    {{#operatorBlocked}}<div><b>operatorBlocked</b>: {{operatorBlocked}}</div>{{/operatorBlocked}}
                    {{#oscillatory}}<div><b>oscillatory</b>: {{oscillatory}}</div>{{/oscillatory}}
                    {{#outOfRange}}<div><b>outOfRange</b>: {{outOfRange}}</div>{{/outOfRange}}
                    {{#overFlow}}<div><b>overFlow</b>: {{overFlow}}</div>{{/overFlow}}
                    {{#source}}<div><b>source</b>: {{source}}</div>{{/source}}
                    {{#suspect}}<div><b>suspect</b>: {{suspect}}</div>{{/suspect}}
                    {{#test}}<div><b>test</b>: {{test}}</div>{{/test}}
                    {{#validity}}<div><b>validity</b>: {{validity}}</div>{{/validity}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                obj.Validity = []; if (!obj.validity) obj.Validity.push ({ id: '', selected: true}); for (var property in Validity) obj.Validity.push ({ id: property, selected: obj.validity && obj.validity.endsWith ('.' + property)});
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Validity;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Quality61850_collapse" aria-expanded="true" aria-controls="{{id}}_Quality61850_collapse" style="margin-left: 10px;">Quality61850</a></legend>
                    <div id="{{id}}_Quality61850_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + base.Element.prototype.edit_template.call (this) +
                    `
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_badReference'>badReference: </label><div class='col-sm-8'><input id='{{id}}_badReference' class='form-check-input' type='checkbox'{{#badReference}} checked{{/badReference}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_estimatorReplaced'>estimatorReplaced: </label><div class='col-sm-8'><input id='{{id}}_estimatorReplaced' class='form-check-input' type='checkbox'{{#estimatorReplaced}} checked{{/estimatorReplaced}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_failure'>failure: </label><div class='col-sm-8'><input id='{{id}}_failure' class='form-check-input' type='checkbox'{{#failure}} checked{{/failure}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_oldData'>oldData: </label><div class='col-sm-8'><input id='{{id}}_oldData' class='form-check-input' type='checkbox'{{#oldData}} checked{{/oldData}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_operatorBlocked'>operatorBlocked: </label><div class='col-sm-8'><input id='{{id}}_operatorBlocked' class='form-check-input' type='checkbox'{{#operatorBlocked}} checked{{/operatorBlocked}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_oscillatory'>oscillatory: </label><div class='col-sm-8'><input id='{{id}}_oscillatory' class='form-check-input' type='checkbox'{{#oscillatory}} checked{{/oscillatory}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_outOfRange'>outOfRange: </label><div class='col-sm-8'><input id='{{id}}_outOfRange' class='form-check-input' type='checkbox'{{#outOfRange}} checked{{/outOfRange}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_overFlow'>overFlow: </label><div class='col-sm-8'><input id='{{id}}_overFlow' class='form-check-input' type='checkbox'{{#overFlow}} checked{{/overFlow}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_source'>source: </label><div class='col-sm-8'><input id='{{id}}_source' class='form-control' type='text'{{#source}} value='{{source}}'{{/source}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_suspect'>suspect: </label><div class='col-sm-8'><input id='{{id}}_suspect' class='form-check-input' type='checkbox'{{#suspect}} checked{{/suspect}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_test'>test: </label><div class='col-sm-8'><input id='{{id}}_test' class='form-check-input' type='checkbox'{{#test}} checked{{/test}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_validity'>validity: </label><div class='col-sm-8'><select id='{{id}}_validity' class='form-control'>{{#Validity}}<option value='{{id}}'{{#selected}} selected{{/selected}}>{{id}}</option>{{/Validity}}</select></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Quality61850" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_badReference").checked; if (temp) obj.badReference = true;
                temp = document.getElementById (id + "_estimatorReplaced").checked; if (temp) obj.estimatorReplaced = true;
                temp = document.getElementById (id + "_failure").checked; if (temp) obj.failure = true;
                temp = document.getElementById (id + "_oldData").checked; if (temp) obj.oldData = true;
                temp = document.getElementById (id + "_operatorBlocked").checked; if (temp) obj.operatorBlocked = true;
                temp = document.getElementById (id + "_oscillatory").checked; if (temp) obj.oscillatory = true;
                temp = document.getElementById (id + "_outOfRange").checked; if (temp) obj.outOfRange = true;
                temp = document.getElementById (id + "_overFlow").checked; if (temp) obj.overFlow = true;
                temp = document.getElementById (id + "_source").value; if ("" != temp) obj.source = temp;
                temp = document.getElementById (id + "_suspect").checked; if (temp) obj.suspect = true;
                temp = document.getElementById (id + "_test").checked; if (temp) obj.test = true;
                temp = document.getElementById (id + "_validity").value; if ("" != temp) { temp = Validity[temp]; if ("undefined" != typeof (temp)) obj.validity = "http://iec.ch/TC57/2013/CIM-schema-cim16#Validity." + temp; }

                return (obj);
            }
        }

        /**
         * Specifies one limit value for a Measurement.
         *
         * A Measurement typically has several limits that are kept together by the LimitSet class. The actual meaning and use of a Limit instance (i.e., if it is an alarm or warning limit or if it is a high or low limit) is not captured in the Limit class. However the name of a Limit instance may indicate both meaning and use.
         *
         */
        class Limit extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Limit;
                if (null == bucket)
                   cim_data.Limit = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Limit[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "Limit";
                base.parse_attributes (/<cim:Limit.Procedures\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Procedures", sub, context);
                var bucket = context.parsed.Limit;
                if (null == bucket)
                   context.parsed.Limit = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "Limit", "Procedures", "Procedures", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Limit_collapse" aria-expanded="true" aria-controls="Limit_collapse" style="margin-left: 10px;">Limit</a></legend>
                    <div id="Limit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#Procedures}}<div><b>Procedures</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Procedures}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Procedures) obj.Procedures_string = obj.Procedures.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Procedures_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Limit_collapse" aria-expanded="true" aria-controls="{{id}}_Limit_collapse" style="margin-left: 10px;">Limit</a></legend>
                    <div id="{{id}}_Limit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Procedures'>Procedures: </label><div class='col-sm-8'><input id='{{id}}_Procedures' class='form-control' type='text'{{#Procedures}} value='{{Procedures}}_string'{{/Procedures}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Limit" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_Procedures").value; if ("" != temp) obj.Procedures = temp.split (",");

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Procedures", "0..*", "0..*", "Procedure", "Limits"]
                        ]
                    )
                );
            }
        }

        /**
         * Control is used for supervisory/device control.
         *
         * It represents control outputs that are used to change the state in a process, e.g. close or open breaker, a set point value or a raise lower command.
         *
         */
        class Control extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Control;
                if (null == bucket)
                   cim_data.Control = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Control[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "Control";
                base.parse_element (/<cim:Control.operationInProgress>([\s\S]*?)<\/cim:Control.operationInProgress>/g, obj, "operationInProgress", base.to_boolean, sub, context);
                base.parse_element (/<cim:Control.timeStamp>([\s\S]*?)<\/cim:Control.timeStamp>/g, obj, "timeStamp", base.to_datetime, sub, context);
                base.parse_element (/<cim:Control.unitMultiplier>([\s\S]*?)<\/cim:Control.unitMultiplier>/g, obj, "unitMultiplier", base.to_string, sub, context);
                base.parse_element (/<cim:Control.unitSymbol>([\s\S]*?)<\/cim:Control.unitSymbol>/g, obj, "unitSymbol", base.to_string, sub, context);
                base.parse_element (/<cim:Control.controlType>([\s\S]*?)<\/cim:Control.controlType>/g, obj, "controlType", base.to_string, sub, context);
                base.parse_attribute (/<cim:Control.PowerSystemResource\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "PowerSystemResource", sub, context);
                base.parse_attribute (/<cim:Control.RemoteControl\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "RemoteControl", sub, context);
                var bucket = context.parsed.Control;
                if (null == bucket)
                   context.parsed.Control = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "Control", "operationInProgress", "operationInProgress",  base.from_boolean, fields);
                base.export_element (obj, "Control", "timeStamp", "timeStamp",  base.from_datetime, fields);
                base.export_element (obj, "Control", "unitMultiplier", "unitMultiplier",  base.from_string, fields);
                base.export_element (obj, "Control", "unitSymbol", "unitSymbol",  base.from_string, fields);
                base.export_element (obj, "Control", "controlType", "controlType",  base.from_string, fields);
                base.export_attribute (obj, "Control", "PowerSystemResource", "PowerSystemResource", fields);
                base.export_attribute (obj, "Control", "RemoteControl", "RemoteControl", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Control_collapse" aria-expanded="true" aria-controls="Control_collapse" style="margin-left: 10px;">Control</a></legend>
                    <div id="Control_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#operationInProgress}}<div><b>operationInProgress</b>: {{operationInProgress}}</div>{{/operationInProgress}}
                    {{#timeStamp}}<div><b>timeStamp</b>: {{timeStamp}}</div>{{/timeStamp}}
                    {{#unitMultiplier}}<div><b>unitMultiplier</b>: {{unitMultiplier}}</div>{{/unitMultiplier}}
                    {{#unitSymbol}}<div><b>unitSymbol</b>: {{unitSymbol}}</div>{{/unitSymbol}}
                    {{#controlType}}<div><b>controlType</b>: {{controlType}}</div>{{/controlType}}
                    {{#PowerSystemResource}}<div><b>PowerSystemResource</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{PowerSystemResource}}&quot;);})'>{{PowerSystemResource}}</a></div>{{/PowerSystemResource}}
                    {{#RemoteControl}}<div><b>RemoteControl</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{RemoteControl}}&quot;);})'>{{RemoteControl}}</a></div>{{/RemoteControl}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Control_collapse" aria-expanded="true" aria-controls="{{id}}_Control_collapse" style="margin-left: 10px;">Control</a></legend>
                    <div id="{{id}}_Control_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_operationInProgress'>operationInProgress: </label><div class='col-sm-8'><input id='{{id}}_operationInProgress' class='form-check-input' type='checkbox'{{#operationInProgress}} checked{{/operationInProgress}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_timeStamp'>timeStamp: </label><div class='col-sm-8'><input id='{{id}}_timeStamp' class='form-control' type='text'{{#timeStamp}} value='{{timeStamp}}'{{/timeStamp}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_unitMultiplier'>unitMultiplier: </label><div class='col-sm-8'><input id='{{id}}_unitMultiplier' class='form-control' type='text'{{#unitMultiplier}} value='{{unitMultiplier}}'{{/unitMultiplier}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_unitSymbol'>unitSymbol: </label><div class='col-sm-8'><input id='{{id}}_unitSymbol' class='form-control' type='text'{{#unitSymbol}} value='{{unitSymbol}}'{{/unitSymbol}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_controlType'>controlType: </label><div class='col-sm-8'><input id='{{id}}_controlType' class='form-control' type='text'{{#controlType}} value='{{controlType}}'{{/controlType}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_PowerSystemResource'>PowerSystemResource: </label><div class='col-sm-8'><input id='{{id}}_PowerSystemResource' class='form-control' type='text'{{#PowerSystemResource}} value='{{PowerSystemResource}}'{{/PowerSystemResource}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_RemoteControl'>RemoteControl: </label><div class='col-sm-8'><input id='{{id}}_RemoteControl' class='form-control' type='text'{{#RemoteControl}} value='{{RemoteControl}}'{{/RemoteControl}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Control" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_operationInProgress").checked; if (temp) obj.operationInProgress = true;
                temp = document.getElementById (id + "_timeStamp").value; if ("" != temp) obj.timeStamp = temp;
                temp = document.getElementById (id + "_unitMultiplier").value; if ("" != temp) obj.unitMultiplier = temp;
                temp = document.getElementById (id + "_unitSymbol").value; if ("" != temp) obj.unitSymbol = temp;
                temp = document.getElementById (id + "_controlType").value; if ("" != temp) obj.controlType = temp;
                temp = document.getElementById (id + "_PowerSystemResource").value; if ("" != temp) obj.PowerSystemResource = temp;
                temp = document.getElementById (id + "_RemoteControl").value; if ("" != temp) obj.RemoteControl = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["PowerSystemResource", "0..1", "0..*", "PowerSystemResource", "Controls"],
                            ["RemoteControl", "0..1", "1", "RemoteControl", "Control"]
                        ]
                    )
                );
            }
        }

        /**
         * MeasurementValueSource describes the alternative sources updating a MeasurementValue.
         *
         * User conventions for how to use the MeasurementValueSource attributes are described in the introduction to IEC 61970-301.
         *
         */
        class MeasurementValueSource extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.MeasurementValueSource;
                if (null == bucket)
                   cim_data.MeasurementValueSource = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.MeasurementValueSource[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "MeasurementValueSource";
                base.parse_attributes (/<cim:MeasurementValueSource.MeasurementValues\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MeasurementValues", sub, context);
                var bucket = context.parsed.MeasurementValueSource;
                if (null == bucket)
                   context.parsed.MeasurementValueSource = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "MeasurementValueSource", "MeasurementValues", "MeasurementValues", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#MeasurementValueSource_collapse" aria-expanded="true" aria-controls="MeasurementValueSource_collapse" style="margin-left: 10px;">MeasurementValueSource</a></legend>
                    <div id="MeasurementValueSource_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#MeasurementValues}}<div><b>MeasurementValues</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/MeasurementValues}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.MeasurementValues) obj.MeasurementValues_string = obj.MeasurementValues.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.MeasurementValues_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_MeasurementValueSource_collapse" aria-expanded="true" aria-controls="{{id}}_MeasurementValueSource_collapse" style="margin-left: 10px;">MeasurementValueSource</a></legend>
                    <div id="{{id}}_MeasurementValueSource_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var obj = obj || { id: id, cls: "MeasurementValueSource" };
                super.submit (id, obj);

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["MeasurementValues", "0..*", "1", "MeasurementValue", "MeasurementValueSource"]
                        ]
                    )
                );
            }
        }

        /**
         * A Measurement represents any measured, calculated or non-measured non-calculated quantity.
         *
         * Any piece of equipment may contain Measurements, e.g. a substation may have temperature measurements and door open indications, a transformer may have oil temperature and tank pressure measurements, a bay may contain a number of power flow measurements and a Breaker may contain a switch status measurement.
         *
         */
        class Measurement extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Measurement;
                if (null == bucket)
                   cim_data.Measurement = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Measurement[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "Measurement";
                base.parse_element (/<cim:Measurement.measurementType>([\s\S]*?)<\/cim:Measurement.measurementType>/g, obj, "measurementType", base.to_string, sub, context);
                base.parse_element (/<cim:Measurement.phases>([\s\S]*?)<\/cim:Measurement.phases>/g, obj, "phases", base.to_string, sub, context);
                base.parse_element (/<cim:Measurement.unitMultiplier>([\s\S]*?)<\/cim:Measurement.unitMultiplier>/g, obj, "unitMultiplier", base.to_string, sub, context);
                base.parse_element (/<cim:Measurement.unitSymbol>([\s\S]*?)<\/cim:Measurement.unitSymbol>/g, obj, "unitSymbol", base.to_string, sub, context);
                base.parse_attribute (/<cim:Measurement.Terminal\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Terminal", sub, context);
                base.parse_attributes (/<cim:Measurement.Procedures\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Procedures", sub, context);
                base.parse_attributes (/<cim:Measurement.Locations\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Locations", sub, context);
                base.parse_attributes (/<cim:Measurement.ProtectiveActionAdjustment\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ProtectiveActionAdjustment", sub, context);
                base.parse_attributes (/<cim:Measurement.MeasurementCalculatorInput\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MeasurementCalculatorInput", sub, context);
                base.parse_attribute (/<cim:Measurement.Asset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Asset", sub, context);
                base.parse_attributes (/<cim:Measurement.PinMeasurement\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "PinMeasurement", sub, context);
                base.parse_attribute (/<cim:Measurement.PowerSystemResource\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "PowerSystemResource", sub, context);
                var bucket = context.parsed.Measurement;
                if (null == bucket)
                   context.parsed.Measurement = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "Measurement", "measurementType", "measurementType",  base.from_string, fields);
                base.export_element (obj, "Measurement", "phases", "phases",  base.from_string, fields);
                base.export_element (obj, "Measurement", "unitMultiplier", "unitMultiplier",  base.from_string, fields);
                base.export_element (obj, "Measurement", "unitSymbol", "unitSymbol",  base.from_string, fields);
                base.export_attribute (obj, "Measurement", "Terminal", "Terminal", fields);
                base.export_attributes (obj, "Measurement", "Procedures", "Procedures", fields);
                base.export_attributes (obj, "Measurement", "Locations", "Locations", fields);
                base.export_attributes (obj, "Measurement", "ProtectiveActionAdjustment", "ProtectiveActionAdjustment", fields);
                base.export_attributes (obj, "Measurement", "MeasurementCalculatorInput", "MeasurementCalculatorInput", fields);
                base.export_attribute (obj, "Measurement", "Asset", "Asset", fields);
                base.export_attributes (obj, "Measurement", "PinMeasurement", "PinMeasurement", fields);
                base.export_attribute (obj, "Measurement", "PowerSystemResource", "PowerSystemResource", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Measurement_collapse" aria-expanded="true" aria-controls="Measurement_collapse" style="margin-left: 10px;">Measurement</a></legend>
                    <div id="Measurement_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#measurementType}}<div><b>measurementType</b>: {{measurementType}}</div>{{/measurementType}}
                    {{#phases}}<div><b>phases</b>: {{phases}}</div>{{/phases}}
                    {{#unitMultiplier}}<div><b>unitMultiplier</b>: {{unitMultiplier}}</div>{{/unitMultiplier}}
                    {{#unitSymbol}}<div><b>unitSymbol</b>: {{unitSymbol}}</div>{{/unitSymbol}}
                    {{#Terminal}}<div><b>Terminal</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Terminal}}&quot;);})'>{{Terminal}}</a></div>{{/Terminal}}
                    {{#Procedures}}<div><b>Procedures</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Procedures}}
                    {{#Locations}}<div><b>Locations</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Locations}}
                    {{#ProtectiveActionAdjustment}}<div><b>ProtectiveActionAdjustment</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/ProtectiveActionAdjustment}}
                    {{#MeasurementCalculatorInput}}<div><b>MeasurementCalculatorInput</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/MeasurementCalculatorInput}}
                    {{#Asset}}<div><b>Asset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Asset}}&quot;);})'>{{Asset}}</a></div>{{/Asset}}
                    {{#PinMeasurement}}<div><b>PinMeasurement</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/PinMeasurement}}
                    {{#PowerSystemResource}}<div><b>PowerSystemResource</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{PowerSystemResource}}&quot;);})'>{{PowerSystemResource}}</a></div>{{/PowerSystemResource}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Procedures) obj.Procedures_string = obj.Procedures.join ();
                if (obj.Locations) obj.Locations_string = obj.Locations.join ();
                if (obj.ProtectiveActionAdjustment) obj.ProtectiveActionAdjustment_string = obj.ProtectiveActionAdjustment.join ();
                if (obj.MeasurementCalculatorInput) obj.MeasurementCalculatorInput_string = obj.MeasurementCalculatorInput.join ();
                if (obj.PinMeasurement) obj.PinMeasurement_string = obj.PinMeasurement.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Procedures_string;
                delete obj.Locations_string;
                delete obj.ProtectiveActionAdjustment_string;
                delete obj.MeasurementCalculatorInput_string;
                delete obj.PinMeasurement_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Measurement_collapse" aria-expanded="true" aria-controls="{{id}}_Measurement_collapse" style="margin-left: 10px;">Measurement</a></legend>
                    <div id="{{id}}_Measurement_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_measurementType'>measurementType: </label><div class='col-sm-8'><input id='{{id}}_measurementType' class='form-control' type='text'{{#measurementType}} value='{{measurementType}}'{{/measurementType}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_phases'>phases: </label><div class='col-sm-8'><input id='{{id}}_phases' class='form-control' type='text'{{#phases}} value='{{phases}}'{{/phases}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_unitMultiplier'>unitMultiplier: </label><div class='col-sm-8'><input id='{{id}}_unitMultiplier' class='form-control' type='text'{{#unitMultiplier}} value='{{unitMultiplier}}'{{/unitMultiplier}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_unitSymbol'>unitSymbol: </label><div class='col-sm-8'><input id='{{id}}_unitSymbol' class='form-control' type='text'{{#unitSymbol}} value='{{unitSymbol}}'{{/unitSymbol}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Terminal'>Terminal: </label><div class='col-sm-8'><input id='{{id}}_Terminal' class='form-control' type='text'{{#Terminal}} value='{{Terminal}}'{{/Terminal}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Procedures'>Procedures: </label><div class='col-sm-8'><input id='{{id}}_Procedures' class='form-control' type='text'{{#Procedures}} value='{{Procedures}}_string'{{/Procedures}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Locations'>Locations: </label><div class='col-sm-8'><input id='{{id}}_Locations' class='form-control' type='text'{{#Locations}} value='{{Locations}}_string'{{/Locations}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Asset'>Asset: </label><div class='col-sm-8'><input id='{{id}}_Asset' class='form-control' type='text'{{#Asset}} value='{{Asset}}'{{/Asset}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_PowerSystemResource'>PowerSystemResource: </label><div class='col-sm-8'><input id='{{id}}_PowerSystemResource' class='form-control' type='text'{{#PowerSystemResource}} value='{{PowerSystemResource}}'{{/PowerSystemResource}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Measurement" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_measurementType").value; if ("" != temp) obj.measurementType = temp;
                temp = document.getElementById (id + "_phases").value; if ("" != temp) obj.phases = temp;
                temp = document.getElementById (id + "_unitMultiplier").value; if ("" != temp) obj.unitMultiplier = temp;
                temp = document.getElementById (id + "_unitSymbol").value; if ("" != temp) obj.unitSymbol = temp;
                temp = document.getElementById (id + "_Terminal").value; if ("" != temp) obj.Terminal = temp;
                temp = document.getElementById (id + "_Procedures").value; if ("" != temp) obj.Procedures = temp.split (",");
                temp = document.getElementById (id + "_Locations").value; if ("" != temp) obj.Locations = temp.split (",");
                temp = document.getElementById (id + "_Asset").value; if ("" != temp) obj.Asset = temp;
                temp = document.getElementById (id + "_PowerSystemResource").value; if ("" != temp) obj.PowerSystemResource = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Terminal", "0..1", "0..*", "ACDCTerminal", "Measurements"],
                            ["Procedures", "0..*", "0..*", "Procedure", "Measurements"],
                            ["Locations", "0..*", "0..*", "Location", "Measurements"],
                            ["ProtectiveActionAdjustment", "0..*", "0..1", "ProtectiveActionAdjustment", "Measurement"],
                            ["MeasurementCalculatorInput", "0..*", "1", "MeasurementCalculatorInput", "Measurement"],
                            ["Asset", "0..1", "0..*", "Asset", "Measurements"],
                            ["PinMeasurement", "0..*", "0..1", "PinMeasurement", "Measurement"],
                            ["PowerSystemResource", "0..1", "0..*", "PowerSystemResource", "Measurements"]
                        ]
                    )
                );
            }
        }

        /**
         * Specifies a set of Limits that are associated with a Measurement.
         *
         * A Measurement may have several LimitSets corresponding to seasonal or other changing conditions. The condition is captured in the name and description attributes. The same LimitSet may be used for several Measurements. In particular percentage limits are used this way.
         *
         */
        class LimitSet extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.LimitSet;
                if (null == bucket)
                   cim_data.LimitSet = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.LimitSet[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "LimitSet";
                base.parse_element (/<cim:LimitSet.isPercentageLimits>([\s\S]*?)<\/cim:LimitSet.isPercentageLimits>/g, obj, "isPercentageLimits", base.to_boolean, sub, context);
                var bucket = context.parsed.LimitSet;
                if (null == bucket)
                   context.parsed.LimitSet = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "LimitSet", "isPercentageLimits", "isPercentageLimits",  base.from_boolean, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#LimitSet_collapse" aria-expanded="true" aria-controls="LimitSet_collapse" style="margin-left: 10px;">LimitSet</a></legend>
                    <div id="LimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#isPercentageLimits}}<div><b>isPercentageLimits</b>: {{isPercentageLimits}}</div>{{/isPercentageLimits}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_LimitSet_collapse" aria-expanded="true" aria-controls="{{id}}_LimitSet_collapse" style="margin-left: 10px;">LimitSet</a></legend>
                    <div id="{{id}}_LimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_isPercentageLimits'>isPercentageLimits: </label><div class='col-sm-8'><input id='{{id}}_isPercentageLimits' class='form-check-input' type='checkbox'{{#isPercentageLimits}} checked{{/isPercentageLimits}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "LimitSet" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_isPercentageLimits").checked; if (temp) obj.isPercentageLimits = true;

                return (obj);
            }
        }

        /**
         * Describes the translation of one particular value into a name, e.g. 1 as "Open".
         *
         */
        class ValueToAlias extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.ValueToAlias;
                if (null == bucket)
                   cim_data.ValueToAlias = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.ValueToAlias[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "ValueToAlias";
                base.parse_element (/<cim:ValueToAlias.value>([\s\S]*?)<\/cim:ValueToAlias.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:ValueToAlias.ValueAliasSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ValueAliasSet", sub, context);
                var bucket = context.parsed.ValueToAlias;
                if (null == bucket)
                   context.parsed.ValueToAlias = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "ValueToAlias", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "ValueToAlias", "ValueAliasSet", "ValueAliasSet", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#ValueToAlias_collapse" aria-expanded="true" aria-controls="ValueToAlias_collapse" style="margin-left: 10px;">ValueToAlias</a></legend>
                    <div id="ValueToAlias_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#ValueAliasSet}}<div><b>ValueAliasSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ValueAliasSet}}&quot;);})'>{{ValueAliasSet}}</a></div>{{/ValueAliasSet}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_ValueToAlias_collapse" aria-expanded="true" aria-controls="{{id}}_ValueToAlias_collapse" style="margin-left: 10px;">ValueToAlias</a></legend>
                    <div id="{{id}}_ValueToAlias_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ValueAliasSet'>ValueAliasSet: </label><div class='col-sm-8'><input id='{{id}}_ValueAliasSet' class='form-control' type='text'{{#ValueAliasSet}} value='{{ValueAliasSet}}'{{/ValueAliasSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "ValueToAlias" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_ValueAliasSet").value; if ("" != temp) obj.ValueAliasSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["ValueAliasSet", "1", "1..*", "ValueAliasSet", "Values"]
                        ]
                    )
                );
            }
        }

        /**
         * DiscreteValue represents a discrete MeasurementValue.
         *
         */
        class DiscreteValue extends MeasurementValue
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.DiscreteValue;
                if (null == bucket)
                   cim_data.DiscreteValue = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.DiscreteValue[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = MeasurementValue.prototype.parse.call (this, context, sub);
                obj.cls = "DiscreteValue";
                base.parse_element (/<cim:DiscreteValue.value>([\s\S]*?)<\/cim:DiscreteValue.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:DiscreteValue.Command\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Command", sub, context);
                base.parse_attribute (/<cim:DiscreteValue.Discrete\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Discrete", sub, context);
                var bucket = context.parsed.DiscreteValue;
                if (null == bucket)
                   context.parsed.DiscreteValue = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = MeasurementValue.prototype.export.call (this, obj, false);

                base.export_element (obj, "DiscreteValue", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "DiscreteValue", "Command", "Command", fields);
                base.export_attribute (obj, "DiscreteValue", "Discrete", "Discrete", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#DiscreteValue_collapse" aria-expanded="true" aria-controls="DiscreteValue_collapse" style="margin-left: 10px;">DiscreteValue</a></legend>
                    <div id="DiscreteValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#Command}}<div><b>Command</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Command}}&quot;);})'>{{Command}}</a></div>{{/Command}}
                    {{#Discrete}}<div><b>Discrete</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Discrete}}&quot;);})'>{{Discrete}}</a></div>{{/Discrete}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_DiscreteValue_collapse" aria-expanded="true" aria-controls="{{id}}_DiscreteValue_collapse" style="margin-left: 10px;">DiscreteValue</a></legend>
                    <div id="{{id}}_DiscreteValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Command'>Command: </label><div class='col-sm-8'><input id='{{id}}_Command' class='form-control' type='text'{{#Command}} value='{{Command}}'{{/Command}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Discrete'>Discrete: </label><div class='col-sm-8'><input id='{{id}}_Discrete' class='form-control' type='text'{{#Discrete}} value='{{Discrete}}'{{/Discrete}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "DiscreteValue" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_Command").value; if ("" != temp) obj.Command = temp;
                temp = document.getElementById (id + "_Discrete").value; if ("" != temp) obj.Discrete = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Command", "0..1", "1", "Command", "DiscreteValue"],
                            ["Discrete", "1", "0..*", "Discrete", "DiscreteValues"]
                        ]
                    )
                );
            }
        }

        /**
         * StringMeasurementValue represents a measurement value of type string.
         *
         */
        class StringMeasurementValue extends MeasurementValue
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.StringMeasurementValue;
                if (null == bucket)
                   cim_data.StringMeasurementValue = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.StringMeasurementValue[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = MeasurementValue.prototype.parse.call (this, context, sub);
                obj.cls = "StringMeasurementValue";
                base.parse_element (/<cim:StringMeasurementValue.value>([\s\S]*?)<\/cim:StringMeasurementValue.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:StringMeasurementValue.StringMeasurement\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "StringMeasurement", sub, context);
                var bucket = context.parsed.StringMeasurementValue;
                if (null == bucket)
                   context.parsed.StringMeasurementValue = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = MeasurementValue.prototype.export.call (this, obj, false);

                base.export_element (obj, "StringMeasurementValue", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "StringMeasurementValue", "StringMeasurement", "StringMeasurement", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#StringMeasurementValue_collapse" aria-expanded="true" aria-controls="StringMeasurementValue_collapse" style="margin-left: 10px;">StringMeasurementValue</a></legend>
                    <div id="StringMeasurementValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#StringMeasurement}}<div><b>StringMeasurement</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{StringMeasurement}}&quot;);})'>{{StringMeasurement}}</a></div>{{/StringMeasurement}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_StringMeasurementValue_collapse" aria-expanded="true" aria-controls="{{id}}_StringMeasurementValue_collapse" style="margin-left: 10px;">StringMeasurementValue</a></legend>
                    <div id="{{id}}_StringMeasurementValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_StringMeasurement'>StringMeasurement: </label><div class='col-sm-8'><input id='{{id}}_StringMeasurement' class='form-control' type='text'{{#StringMeasurement}} value='{{StringMeasurement}}'{{/StringMeasurement}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "StringMeasurementValue" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_StringMeasurement").value; if ("" != temp) obj.StringMeasurement = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["StringMeasurement", "1", "0..*", "StringMeasurement", "StringMeasurementValues"]
                        ]
                    )
                );
            }
        }

        /**
         * AccumulatorValue represents an accumulated (counted) MeasurementValue.
         *
         */
        class AccumulatorValue extends MeasurementValue
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AccumulatorValue;
                if (null == bucket)
                   cim_data.AccumulatorValue = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AccumulatorValue[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = MeasurementValue.prototype.parse.call (this, context, sub);
                obj.cls = "AccumulatorValue";
                base.parse_element (/<cim:AccumulatorValue.value>([\s\S]*?)<\/cim:AccumulatorValue.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:AccumulatorValue.Accumulator\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Accumulator", sub, context);
                base.parse_attribute (/<cim:AccumulatorValue.AccumulatorReset\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AccumulatorReset", sub, context);
                var bucket = context.parsed.AccumulatorValue;
                if (null == bucket)
                   context.parsed.AccumulatorValue = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = MeasurementValue.prototype.export.call (this, obj, false);

                base.export_element (obj, "AccumulatorValue", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "AccumulatorValue", "Accumulator", "Accumulator", fields);
                base.export_attribute (obj, "AccumulatorValue", "AccumulatorReset", "AccumulatorReset", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AccumulatorValue_collapse" aria-expanded="true" aria-controls="AccumulatorValue_collapse" style="margin-left: 10px;">AccumulatorValue</a></legend>
                    <div id="AccumulatorValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#Accumulator}}<div><b>Accumulator</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Accumulator}}&quot;);})'>{{Accumulator}}</a></div>{{/Accumulator}}
                    {{#AccumulatorReset}}<div><b>AccumulatorReset</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AccumulatorReset}}&quot;);})'>{{AccumulatorReset}}</a></div>{{/AccumulatorReset}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AccumulatorValue_collapse" aria-expanded="true" aria-controls="{{id}}_AccumulatorValue_collapse" style="margin-left: 10px;">AccumulatorValue</a></legend>
                    <div id="{{id}}_AccumulatorValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Accumulator'>Accumulator: </label><div class='col-sm-8'><input id='{{id}}_Accumulator' class='form-control' type='text'{{#Accumulator}} value='{{Accumulator}}'{{/Accumulator}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_AccumulatorReset'>AccumulatorReset: </label><div class='col-sm-8'><input id='{{id}}_AccumulatorReset' class='form-control' type='text'{{#AccumulatorReset}} value='{{AccumulatorReset}}'{{/AccumulatorReset}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AccumulatorValue" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_Accumulator").value; if ("" != temp) obj.Accumulator = temp;
                temp = document.getElementById (id + "_AccumulatorReset").value; if ("" != temp) obj.AccumulatorReset = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Accumulator", "1", "0..*", "Accumulator", "AccumulatorValues"],
                            ["AccumulatorReset", "0..1", "1", "AccumulatorReset", "AccumulatorValue"]
                        ]
                    )
                );
            }
        }

        /**
         * AnalogValue represents an analog MeasurementValue.
         *
         */
        class AnalogValue extends MeasurementValue
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AnalogValue;
                if (null == bucket)
                   cim_data.AnalogValue = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AnalogValue[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = MeasurementValue.prototype.parse.call (this, context, sub);
                obj.cls = "AnalogValue";
                base.parse_element (/<cim:AnalogValue.value>([\s\S]*?)<\/cim:AnalogValue.value>/g, obj, "value", base.to_float, sub, context);
                base.parse_attribute (/<cim:AnalogValue.Analog\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Analog", sub, context);
                base.parse_attributes (/<cim:AnalogValue.AltTieMeas\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AltTieMeas", sub, context);
                base.parse_attribute (/<cim:AnalogValue.AnalogControl\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AnalogControl", sub, context);
                base.parse_attributes (/<cim:AnalogValue.AltGeneratingUnit\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AltGeneratingUnit", sub, context);
                var bucket = context.parsed.AnalogValue;
                if (null == bucket)
                   context.parsed.AnalogValue = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = MeasurementValue.prototype.export.call (this, obj, false);

                base.export_element (obj, "AnalogValue", "value", "value",  base.from_float, fields);
                base.export_attribute (obj, "AnalogValue", "Analog", "Analog", fields);
                base.export_attributes (obj, "AnalogValue", "AltTieMeas", "AltTieMeas", fields);
                base.export_attribute (obj, "AnalogValue", "AnalogControl", "AnalogControl", fields);
                base.export_attributes (obj, "AnalogValue", "AltGeneratingUnit", "AltGeneratingUnit", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AnalogValue_collapse" aria-expanded="true" aria-controls="AnalogValue_collapse" style="margin-left: 10px;">AnalogValue</a></legend>
                    <div id="AnalogValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#Analog}}<div><b>Analog</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Analog}}&quot;);})'>{{Analog}}</a></div>{{/Analog}}
                    {{#AltTieMeas}}<div><b>AltTieMeas</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/AltTieMeas}}
                    {{#AnalogControl}}<div><b>AnalogControl</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AnalogControl}}&quot;);})'>{{AnalogControl}}</a></div>{{/AnalogControl}}
                    {{#AltGeneratingUnit}}<div><b>AltGeneratingUnit</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/AltGeneratingUnit}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.AltTieMeas) obj.AltTieMeas_string = obj.AltTieMeas.join ();
                if (obj.AltGeneratingUnit) obj.AltGeneratingUnit_string = obj.AltGeneratingUnit.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.AltTieMeas_string;
                delete obj.AltGeneratingUnit_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AnalogValue_collapse" aria-expanded="true" aria-controls="{{id}}_AnalogValue_collapse" style="margin-left: 10px;">AnalogValue</a></legend>
                    <div id="{{id}}_AnalogValue_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + MeasurementValue.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Analog'>Analog: </label><div class='col-sm-8'><input id='{{id}}_Analog' class='form-control' type='text'{{#Analog}} value='{{Analog}}'{{/Analog}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_AnalogControl'>AnalogControl: </label><div class='col-sm-8'><input id='{{id}}_AnalogControl' class='form-control' type='text'{{#AnalogControl}} value='{{AnalogControl}}'{{/AnalogControl}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AnalogValue" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_Analog").value; if ("" != temp) obj.Analog = temp;
                temp = document.getElementById (id + "_AnalogControl").value; if ("" != temp) obj.AnalogControl = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Analog", "1", "0..*", "Analog", "AnalogValues"],
                            ["AltTieMeas", "0..*", "1", "AltTieMeas", "AnalogValue"],
                            ["AnalogControl", "0..1", "1", "AnalogControl", "AnalogValue"],
                            ["AltGeneratingUnit", "0..*", "1", "AltGeneratingUnitMeas", "AnalogValue"]
                        ]
                    )
                );
            }
        }

        /**
         * Measurement quality flags.
         *
         * Bits 0-10 are defined for substation automation in draft IEC 61850 part 7-3. Bits 11-15 are reserved for future expansion by that document. Bits 16-31 are reserved for EMS applications.
         *
         */
        class MeasurementValueQuality extends Quality61850
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.MeasurementValueQuality;
                if (null == bucket)
                   cim_data.MeasurementValueQuality = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.MeasurementValueQuality[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Quality61850.prototype.parse.call (this, context, sub);
                obj.cls = "MeasurementValueQuality";
                base.parse_attribute (/<cim:MeasurementValueQuality.MeasurementValue\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MeasurementValue", sub, context);
                var bucket = context.parsed.MeasurementValueQuality;
                if (null == bucket)
                   context.parsed.MeasurementValueQuality = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Quality61850.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "MeasurementValueQuality", "MeasurementValue", "MeasurementValue", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#MeasurementValueQuality_collapse" aria-expanded="true" aria-controls="MeasurementValueQuality_collapse" style="margin-left: 10px;">MeasurementValueQuality</a></legend>
                    <div id="MeasurementValueQuality_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Quality61850.prototype.template.call (this) +
                    `
                    {{#MeasurementValue}}<div><b>MeasurementValue</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{MeasurementValue}}&quot;);})'>{{MeasurementValue}}</a></div>{{/MeasurementValue}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_MeasurementValueQuality_collapse" aria-expanded="true" aria-controls="{{id}}_MeasurementValueQuality_collapse" style="margin-left: 10px;">MeasurementValueQuality</a></legend>
                    <div id="{{id}}_MeasurementValueQuality_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Quality61850.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_MeasurementValue'>MeasurementValue: </label><div class='col-sm-8'><input id='{{id}}_MeasurementValue' class='form-control' type='text'{{#MeasurementValue}} value='{{MeasurementValue}}'{{/MeasurementValue}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "MeasurementValueQuality" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_MeasurementValue").value; if ("" != temp) obj.MeasurementValue = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["MeasurementValue", "1", "0..1", "MeasurementValue", "MeasurementValueQuality"]
                        ]
                    )
                );
            }
        }

        /**
         * Limit values for Accumulator measurements.
         *
         */
        class AccumulatorLimit extends Limit
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AccumulatorLimit;
                if (null == bucket)
                   cim_data.AccumulatorLimit = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AccumulatorLimit[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Limit.prototype.parse.call (this, context, sub);
                obj.cls = "AccumulatorLimit";
                base.parse_element (/<cim:AccumulatorLimit.value>([\s\S]*?)<\/cim:AccumulatorLimit.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:AccumulatorLimit.LimitSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "LimitSet", sub, context);
                var bucket = context.parsed.AccumulatorLimit;
                if (null == bucket)
                   context.parsed.AccumulatorLimit = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Limit.prototype.export.call (this, obj, false);

                base.export_element (obj, "AccumulatorLimit", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "AccumulatorLimit", "LimitSet", "LimitSet", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AccumulatorLimit_collapse" aria-expanded="true" aria-controls="AccumulatorLimit_collapse" style="margin-left: 10px;">AccumulatorLimit</a></legend>
                    <div id="AccumulatorLimit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Limit.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#LimitSet}}<div><b>LimitSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{LimitSet}}&quot;);})'>{{LimitSet}}</a></div>{{/LimitSet}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AccumulatorLimit_collapse" aria-expanded="true" aria-controls="{{id}}_AccumulatorLimit_collapse" style="margin-left: 10px;">AccumulatorLimit</a></legend>
                    <div id="{{id}}_AccumulatorLimit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Limit.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_LimitSet'>LimitSet: </label><div class='col-sm-8'><input id='{{id}}_LimitSet' class='form-control' type='text'{{#LimitSet}} value='{{LimitSet}}'{{/LimitSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AccumulatorLimit" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_LimitSet").value; if ("" != temp) obj.LimitSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["LimitSet", "1", "1..*", "AccumulatorLimitSet", "Limits"]
                        ]
                    )
                );
            }
        }

        /**
         * Limit values for Analog measurements.
         *
         */
        class AnalogLimit extends Limit
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AnalogLimit;
                if (null == bucket)
                   cim_data.AnalogLimit = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AnalogLimit[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Limit.prototype.parse.call (this, context, sub);
                obj.cls = "AnalogLimit";
                base.parse_element (/<cim:AnalogLimit.value>([\s\S]*?)<\/cim:AnalogLimit.value>/g, obj, "value", base.to_float, sub, context);
                base.parse_attribute (/<cim:AnalogLimit.LimitSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "LimitSet", sub, context);
                var bucket = context.parsed.AnalogLimit;
                if (null == bucket)
                   context.parsed.AnalogLimit = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Limit.prototype.export.call (this, obj, false);

                base.export_element (obj, "AnalogLimit", "value", "value",  base.from_float, fields);
                base.export_attribute (obj, "AnalogLimit", "LimitSet", "LimitSet", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AnalogLimit_collapse" aria-expanded="true" aria-controls="AnalogLimit_collapse" style="margin-left: 10px;">AnalogLimit</a></legend>
                    <div id="AnalogLimit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Limit.prototype.template.call (this) +
                    `
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#LimitSet}}<div><b>LimitSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{LimitSet}}&quot;);})'>{{LimitSet}}</a></div>{{/LimitSet}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AnalogLimit_collapse" aria-expanded="true" aria-controls="{{id}}_AnalogLimit_collapse" style="margin-left: 10px;">AnalogLimit</a></legend>
                    <div id="{{id}}_AnalogLimit_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Limit.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_LimitSet'>LimitSet: </label><div class='col-sm-8'><input id='{{id}}_LimitSet' class='form-control' type='text'{{#LimitSet}} value='{{LimitSet}}'{{/LimitSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AnalogLimit" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_LimitSet").value; if ("" != temp) obj.LimitSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["LimitSet", "1", "0..*", "AnalogLimitSet", "Limits"]
                        ]
                    )
                );
            }
        }

        /**
         * This command reset the counter value to zero.
         *
         */
        class AccumulatorReset extends Control
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AccumulatorReset;
                if (null == bucket)
                   cim_data.AccumulatorReset = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AccumulatorReset[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Control.prototype.parse.call (this, context, sub);
                obj.cls = "AccumulatorReset";
                base.parse_attribute (/<cim:AccumulatorReset.AccumulatorValue\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AccumulatorValue", sub, context);
                var bucket = context.parsed.AccumulatorReset;
                if (null == bucket)
                   context.parsed.AccumulatorReset = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Control.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "AccumulatorReset", "AccumulatorValue", "AccumulatorValue", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AccumulatorReset_collapse" aria-expanded="true" aria-controls="AccumulatorReset_collapse" style="margin-left: 10px;">AccumulatorReset</a></legend>
                    <div id="AccumulatorReset_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.template.call (this) +
                    `
                    {{#AccumulatorValue}}<div><b>AccumulatorValue</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AccumulatorValue}}&quot;);})'>{{AccumulatorValue}}</a></div>{{/AccumulatorValue}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AccumulatorReset_collapse" aria-expanded="true" aria-controls="{{id}}_AccumulatorReset_collapse" style="margin-left: 10px;">AccumulatorReset</a></legend>
                    <div id="{{id}}_AccumulatorReset_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_AccumulatorValue'>AccumulatorValue: </label><div class='col-sm-8'><input id='{{id}}_AccumulatorValue' class='form-control' type='text'{{#AccumulatorValue}} value='{{AccumulatorValue}}'{{/AccumulatorValue}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AccumulatorReset" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_AccumulatorValue").value; if ("" != temp) obj.AccumulatorValue = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["AccumulatorValue", "1", "0..1", "AccumulatorValue", "AccumulatorReset"]
                        ]
                    )
                );
            }
        }

        /**
         * An analog control used for supervisory control.
         *
         */
        class AnalogControl extends Control
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AnalogControl;
                if (null == bucket)
                   cim_data.AnalogControl = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AnalogControl[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Control.prototype.parse.call (this, context, sub);
                obj.cls = "AnalogControl";
                base.parse_element (/<cim:AnalogControl.maxValue>([\s\S]*?)<\/cim:AnalogControl.maxValue>/g, obj, "maxValue", base.to_float, sub, context);
                base.parse_element (/<cim:AnalogControl.minValue>([\s\S]*?)<\/cim:AnalogControl.minValue>/g, obj, "minValue", base.to_float, sub, context);
                base.parse_attribute (/<cim:AnalogControl.AnalogValue\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AnalogValue", sub, context);
                var bucket = context.parsed.AnalogControl;
                if (null == bucket)
                   context.parsed.AnalogControl = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Control.prototype.export.call (this, obj, false);

                base.export_element (obj, "AnalogControl", "maxValue", "maxValue",  base.from_float, fields);
                base.export_element (obj, "AnalogControl", "minValue", "minValue",  base.from_float, fields);
                base.export_attribute (obj, "AnalogControl", "AnalogValue", "AnalogValue", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AnalogControl_collapse" aria-expanded="true" aria-controls="AnalogControl_collapse" style="margin-left: 10px;">AnalogControl</a></legend>
                    <div id="AnalogControl_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.template.call (this) +
                    `
                    {{#maxValue}}<div><b>maxValue</b>: {{maxValue}}</div>{{/maxValue}}
                    {{#minValue}}<div><b>minValue</b>: {{minValue}}</div>{{/minValue}}
                    {{#AnalogValue}}<div><b>AnalogValue</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AnalogValue}}&quot;);})'>{{AnalogValue}}</a></div>{{/AnalogValue}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AnalogControl_collapse" aria-expanded="true" aria-controls="{{id}}_AnalogControl_collapse" style="margin-left: 10px;">AnalogControl</a></legend>
                    <div id="{{id}}_AnalogControl_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_maxValue'>maxValue: </label><div class='col-sm-8'><input id='{{id}}_maxValue' class='form-control' type='text'{{#maxValue}} value='{{maxValue}}'{{/maxValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_minValue'>minValue: </label><div class='col-sm-8'><input id='{{id}}_minValue' class='form-control' type='text'{{#minValue}} value='{{minValue}}'{{/minValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_AnalogValue'>AnalogValue: </label><div class='col-sm-8'><input id='{{id}}_AnalogValue' class='form-control' type='text'{{#AnalogValue}} value='{{AnalogValue}}'{{/AnalogValue}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AnalogControl" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_maxValue").value; if ("" != temp) obj.maxValue = temp;
                temp = document.getElementById (id + "_minValue").value; if ("" != temp) obj.minValue = temp;
                temp = document.getElementById (id + "_AnalogValue").value; if ("" != temp) obj.AnalogValue = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["AnalogValue", "1", "0..1", "AnalogValue", "AnalogControl"]
                        ]
                    )
                );
            }
        }

        /**
         * A Command is a discrete control used for supervisory control.
         *
         */
        class Command extends Control
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Command;
                if (null == bucket)
                   cim_data.Command = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Command[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Control.prototype.parse.call (this, context, sub);
                obj.cls = "Command";
                base.parse_element (/<cim:Command.normalValue>([\s\S]*?)<\/cim:Command.normalValue>/g, obj, "normalValue", base.to_string, sub, context);
                base.parse_element (/<cim:Command.value>([\s\S]*?)<\/cim:Command.value>/g, obj, "value", base.to_string, sub, context);
                base.parse_attribute (/<cim:Command.DiscreteValue\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "DiscreteValue", sub, context);
                base.parse_attribute (/<cim:Command.ValueAliasSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ValueAliasSet", sub, context);
                var bucket = context.parsed.Command;
                if (null == bucket)
                   context.parsed.Command = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Control.prototype.export.call (this, obj, false);

                base.export_element (obj, "Command", "normalValue", "normalValue",  base.from_string, fields);
                base.export_element (obj, "Command", "value", "value",  base.from_string, fields);
                base.export_attribute (obj, "Command", "DiscreteValue", "DiscreteValue", fields);
                base.export_attribute (obj, "Command", "ValueAliasSet", "ValueAliasSet", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Command_collapse" aria-expanded="true" aria-controls="Command_collapse" style="margin-left: 10px;">Command</a></legend>
                    <div id="Command_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.template.call (this) +
                    `
                    {{#normalValue}}<div><b>normalValue</b>: {{normalValue}}</div>{{/normalValue}}
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    {{#DiscreteValue}}<div><b>DiscreteValue</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{DiscreteValue}}&quot;);})'>{{DiscreteValue}}</a></div>{{/DiscreteValue}}
                    {{#ValueAliasSet}}<div><b>ValueAliasSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ValueAliasSet}}&quot;);})'>{{ValueAliasSet}}</a></div>{{/ValueAliasSet}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Command_collapse" aria-expanded="true" aria-controls="{{id}}_Command_collapse" style="margin-left: 10px;">Command</a></legend>
                    <div id="{{id}}_Command_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Control.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_normalValue'>normalValue: </label><div class='col-sm-8'><input id='{{id}}_normalValue' class='form-control' type='text'{{#normalValue}} value='{{normalValue}}'{{/normalValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_DiscreteValue'>DiscreteValue: </label><div class='col-sm-8'><input id='{{id}}_DiscreteValue' class='form-control' type='text'{{#DiscreteValue}} value='{{DiscreteValue}}'{{/DiscreteValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ValueAliasSet'>ValueAliasSet: </label><div class='col-sm-8'><input id='{{id}}_ValueAliasSet' class='form-control' type='text'{{#ValueAliasSet}} value='{{ValueAliasSet}}'{{/ValueAliasSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Command" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_normalValue").value; if ("" != temp) obj.normalValue = temp;
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;
                temp = document.getElementById (id + "_DiscreteValue").value; if ("" != temp) obj.DiscreteValue = temp;
                temp = document.getElementById (id + "_ValueAliasSet").value; if ("" != temp) obj.ValueAliasSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["DiscreteValue", "1", "0..1", "DiscreteValue", "Command"],
                            ["ValueAliasSet", "0..1", "0..*", "ValueAliasSet", "Commands"]
                        ]
                    )
                );
            }
        }

        class DiscreteCommand extends Command
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.DiscreteCommand;
                if (null == bucket)
                   cim_data.DiscreteCommand = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.DiscreteCommand[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Command.prototype.parse.call (this, context, sub);
                obj.cls = "DiscreteCommand";
                var bucket = context.parsed.DiscreteCommand;
                if (null == bucket)
                   context.parsed.DiscreteCommand = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Command.prototype.export.call (this, obj, false);

                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#DiscreteCommand_collapse" aria-expanded="true" aria-controls="DiscreteCommand_collapse" style="margin-left: 10px;">DiscreteCommand</a></legend>
                    <div id="DiscreteCommand_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Command.prototype.template.call (this) +
                    `
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_DiscreteCommand_collapse" aria-expanded="true" aria-controls="{{id}}_DiscreteCommand_collapse" style="margin-left: 10px;">DiscreteCommand</a></legend>
                    <div id="{{id}}_DiscreteCommand_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Command.prototype.edit_template.call (this) +
                    `
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var obj = obj || { id: id, cls: "DiscreteCommand" };
                super.submit (id, obj);

                return (obj);
            }
        }

        /**
         * An analog control that increase or decrease a set point value with pulses.
         *
         */
        class RaiseLowerCommand extends AnalogControl
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.RaiseLowerCommand;
                if (null == bucket)
                   cim_data.RaiseLowerCommand = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.RaiseLowerCommand[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = AnalogControl.prototype.parse.call (this, context, sub);
                obj.cls = "RaiseLowerCommand";
                base.parse_attribute (/<cim:RaiseLowerCommand.ValueAliasSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ValueAliasSet", sub, context);
                var bucket = context.parsed.RaiseLowerCommand;
                if (null == bucket)
                   context.parsed.RaiseLowerCommand = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = AnalogControl.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "RaiseLowerCommand", "ValueAliasSet", "ValueAliasSet", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#RaiseLowerCommand_collapse" aria-expanded="true" aria-controls="RaiseLowerCommand_collapse" style="margin-left: 10px;">RaiseLowerCommand</a></legend>
                    <div id="RaiseLowerCommand_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + AnalogControl.prototype.template.call (this) +
                    `
                    {{#ValueAliasSet}}<div><b>ValueAliasSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ValueAliasSet}}&quot;);})'>{{ValueAliasSet}}</a></div>{{/ValueAliasSet}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_RaiseLowerCommand_collapse" aria-expanded="true" aria-controls="{{id}}_RaiseLowerCommand_collapse" style="margin-left: 10px;">RaiseLowerCommand</a></legend>
                    <div id="{{id}}_RaiseLowerCommand_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + AnalogControl.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ValueAliasSet'>ValueAliasSet: </label><div class='col-sm-8'><input id='{{id}}_ValueAliasSet' class='form-control' type='text'{{#ValueAliasSet}} value='{{ValueAliasSet}}'{{/ValueAliasSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "RaiseLowerCommand" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_ValueAliasSet").value; if ("" != temp) obj.ValueAliasSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["ValueAliasSet", "0..1", "0..*", "ValueAliasSet", "RaiseLowerCommands"]
                        ]
                    )
                );
            }
        }

        /**
         * An analog control that issue a set point value.
         *
         */
        class SetPoint extends AnalogControl
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.SetPoint;
                if (null == bucket)
                   cim_data.SetPoint = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.SetPoint[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = AnalogControl.prototype.parse.call (this, context, sub);
                obj.cls = "SetPoint";
                base.parse_element (/<cim:SetPoint.normalValue>([\s\S]*?)<\/cim:SetPoint.normalValue>/g, obj, "normalValue", base.to_float, sub, context);
                base.parse_element (/<cim:SetPoint.value>([\s\S]*?)<\/cim:SetPoint.value>/g, obj, "value", base.to_float, sub, context);
                var bucket = context.parsed.SetPoint;
                if (null == bucket)
                   context.parsed.SetPoint = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = AnalogControl.prototype.export.call (this, obj, false);

                base.export_element (obj, "SetPoint", "normalValue", "normalValue",  base.from_float, fields);
                base.export_element (obj, "SetPoint", "value", "value",  base.from_float, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#SetPoint_collapse" aria-expanded="true" aria-controls="SetPoint_collapse" style="margin-left: 10px;">SetPoint</a></legend>
                    <div id="SetPoint_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + AnalogControl.prototype.template.call (this) +
                    `
                    {{#normalValue}}<div><b>normalValue</b>: {{normalValue}}</div>{{/normalValue}}
                    {{#value}}<div><b>value</b>: {{value}}</div>{{/value}}
                    </div>
                    <fieldset>

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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_SetPoint_collapse" aria-expanded="true" aria-controls="{{id}}_SetPoint_collapse" style="margin-left: 10px;">SetPoint</a></legend>
                    <div id="{{id}}_SetPoint_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + AnalogControl.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_normalValue'>normalValue: </label><div class='col-sm-8'><input id='{{id}}_normalValue' class='form-control' type='text'{{#normalValue}} value='{{normalValue}}'{{/normalValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_value'>value: </label><div class='col-sm-8'><input id='{{id}}_value' class='form-control' type='text'{{#value}} value='{{value}}'{{/value}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "SetPoint" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_normalValue").value; if ("" != temp) obj.normalValue = temp;
                temp = document.getElementById (id + "_value").value; if ("" != temp) obj.value = temp;

                return (obj);
            }
        }

        /**
         * Discrete represents a discrete Measurement, i.e. a Measurement representing discrete values, e.g. a Breaker position.
         *
         */
        class Discrete extends Measurement
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Discrete;
                if (null == bucket)
                   cim_data.Discrete = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Discrete[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Measurement.prototype.parse.call (this, context, sub);
                obj.cls = "Discrete";
                base.parse_element (/<cim:Discrete.maxValue>([\s\S]*?)<\/cim:Discrete.maxValue>/g, obj, "maxValue", base.to_string, sub, context);
                base.parse_element (/<cim:Discrete.minValue>([\s\S]*?)<\/cim:Discrete.minValue>/g, obj, "minValue", base.to_string, sub, context);
                base.parse_element (/<cim:Discrete.normalValue>([\s\S]*?)<\/cim:Discrete.normalValue>/g, obj, "normalValue", base.to_string, sub, context);
                base.parse_attribute (/<cim:Discrete.ValueAliasSet\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ValueAliasSet", sub, context);
                base.parse_attributes (/<cim:Discrete.DiscreteValues\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "DiscreteValues", sub, context);
                var bucket = context.parsed.Discrete;
                if (null == bucket)
                   context.parsed.Discrete = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Measurement.prototype.export.call (this, obj, false);

                base.export_element (obj, "Discrete", "maxValue", "maxValue",  base.from_string, fields);
                base.export_element (obj, "Discrete", "minValue", "minValue",  base.from_string, fields);
                base.export_element (obj, "Discrete", "normalValue", "normalValue",  base.from_string, fields);
                base.export_attribute (obj, "Discrete", "ValueAliasSet", "ValueAliasSet", fields);
                base.export_attributes (obj, "Discrete", "DiscreteValues", "DiscreteValues", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Discrete_collapse" aria-expanded="true" aria-controls="Discrete_collapse" style="margin-left: 10px;">Discrete</a></legend>
                    <div id="Discrete_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.template.call (this) +
                    `
                    {{#maxValue}}<div><b>maxValue</b>: {{maxValue}}</div>{{/maxValue}}
                    {{#minValue}}<div><b>minValue</b>: {{minValue}}</div>{{/minValue}}
                    {{#normalValue}}<div><b>normalValue</b>: {{normalValue}}</div>{{/normalValue}}
                    {{#ValueAliasSet}}<div><b>ValueAliasSet</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ValueAliasSet}}&quot;);})'>{{ValueAliasSet}}</a></div>{{/ValueAliasSet}}
                    {{#DiscreteValues}}<div><b>DiscreteValues</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/DiscreteValues}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.DiscreteValues) obj.DiscreteValues_string = obj.DiscreteValues.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.DiscreteValues_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Discrete_collapse" aria-expanded="true" aria-controls="{{id}}_Discrete_collapse" style="margin-left: 10px;">Discrete</a></legend>
                    <div id="{{id}}_Discrete_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_maxValue'>maxValue: </label><div class='col-sm-8'><input id='{{id}}_maxValue' class='form-control' type='text'{{#maxValue}} value='{{maxValue}}'{{/maxValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_minValue'>minValue: </label><div class='col-sm-8'><input id='{{id}}_minValue' class='form-control' type='text'{{#minValue}} value='{{minValue}}'{{/minValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_normalValue'>normalValue: </label><div class='col-sm-8'><input id='{{id}}_normalValue' class='form-control' type='text'{{#normalValue}} value='{{normalValue}}'{{/normalValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_ValueAliasSet'>ValueAliasSet: </label><div class='col-sm-8'><input id='{{id}}_ValueAliasSet' class='form-control' type='text'{{#ValueAliasSet}} value='{{ValueAliasSet}}'{{/ValueAliasSet}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Discrete" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_maxValue").value; if ("" != temp) obj.maxValue = temp;
                temp = document.getElementById (id + "_minValue").value; if ("" != temp) obj.minValue = temp;
                temp = document.getElementById (id + "_normalValue").value; if ("" != temp) obj.normalValue = temp;
                temp = document.getElementById (id + "_ValueAliasSet").value; if ("" != temp) obj.ValueAliasSet = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["ValueAliasSet", "0..1", "0..*", "ValueAliasSet", "Discretes"],
                            ["DiscreteValues", "0..*", "1", "DiscreteValue", "Discrete"]
                        ]
                    )
                );
            }
        }

        /**
         * Analog represents an analog Measurement.
         *
         */
        class Analog extends Measurement
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Analog;
                if (null == bucket)
                   cim_data.Analog = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Analog[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Measurement.prototype.parse.call (this, context, sub);
                obj.cls = "Analog";
                base.parse_element (/<cim:Analog.maxValue>([\s\S]*?)<\/cim:Analog.maxValue>/g, obj, "maxValue", base.to_float, sub, context);
                base.parse_element (/<cim:Analog.minValue>([\s\S]*?)<\/cim:Analog.minValue>/g, obj, "minValue", base.to_float, sub, context);
                base.parse_element (/<cim:Analog.normalValue>([\s\S]*?)<\/cim:Analog.normalValue>/g, obj, "normalValue", base.to_float, sub, context);
                base.parse_element (/<cim:Analog.positiveFlowIn>([\s\S]*?)<\/cim:Analog.positiveFlowIn>/g, obj, "positiveFlowIn", base.to_boolean, sub, context);
                base.parse_attributes (/<cim:Analog.AnalogValues\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AnalogValues", sub, context);
                base.parse_attributes (/<cim:Analog.LimitSets\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "LimitSets", sub, context);
                var bucket = context.parsed.Analog;
                if (null == bucket)
                   context.parsed.Analog = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Measurement.prototype.export.call (this, obj, false);

                base.export_element (obj, "Analog", "maxValue", "maxValue",  base.from_float, fields);
                base.export_element (obj, "Analog", "minValue", "minValue",  base.from_float, fields);
                base.export_element (obj, "Analog", "normalValue", "normalValue",  base.from_float, fields);
                base.export_element (obj, "Analog", "positiveFlowIn", "positiveFlowIn",  base.from_boolean, fields);
                base.export_attributes (obj, "Analog", "AnalogValues", "AnalogValues", fields);
                base.export_attributes (obj, "Analog", "LimitSets", "LimitSets", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Analog_collapse" aria-expanded="true" aria-controls="Analog_collapse" style="margin-left: 10px;">Analog</a></legend>
                    <div id="Analog_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.template.call (this) +
                    `
                    {{#maxValue}}<div><b>maxValue</b>: {{maxValue}}</div>{{/maxValue}}
                    {{#minValue}}<div><b>minValue</b>: {{minValue}}</div>{{/minValue}}
                    {{#normalValue}}<div><b>normalValue</b>: {{normalValue}}</div>{{/normalValue}}
                    {{#positiveFlowIn}}<div><b>positiveFlowIn</b>: {{positiveFlowIn}}</div>{{/positiveFlowIn}}
                    {{#AnalogValues}}<div><b>AnalogValues</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/AnalogValues}}
                    {{#LimitSets}}<div><b>LimitSets</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/LimitSets}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.AnalogValues) obj.AnalogValues_string = obj.AnalogValues.join ();
                if (obj.LimitSets) obj.LimitSets_string = obj.LimitSets.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.AnalogValues_string;
                delete obj.LimitSets_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Analog_collapse" aria-expanded="true" aria-controls="{{id}}_Analog_collapse" style="margin-left: 10px;">Analog</a></legend>
                    <div id="{{id}}_Analog_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_maxValue'>maxValue: </label><div class='col-sm-8'><input id='{{id}}_maxValue' class='form-control' type='text'{{#maxValue}} value='{{maxValue}}'{{/maxValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_minValue'>minValue: </label><div class='col-sm-8'><input id='{{id}}_minValue' class='form-control' type='text'{{#minValue}} value='{{minValue}}'{{/minValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_normalValue'>normalValue: </label><div class='col-sm-8'><input id='{{id}}_normalValue' class='form-control' type='text'{{#normalValue}} value='{{normalValue}}'{{/normalValue}}></div></div>
                    <div class='form-check row'><label class='form-check-label col-sm-4 col-form-label' for='{{id}}_positiveFlowIn'>positiveFlowIn: </label><div class='col-sm-8'><input id='{{id}}_positiveFlowIn' class='form-check-input' type='checkbox'{{#positiveFlowIn}} checked{{/positiveFlowIn}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_LimitSets'>LimitSets: </label><div class='col-sm-8'><input id='{{id}}_LimitSets' class='form-control' type='text'{{#LimitSets}} value='{{LimitSets}}_string'{{/LimitSets}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Analog" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_maxValue").value; if ("" != temp) obj.maxValue = temp;
                temp = document.getElementById (id + "_minValue").value; if ("" != temp) obj.minValue = temp;
                temp = document.getElementById (id + "_normalValue").value; if ("" != temp) obj.normalValue = temp;
                temp = document.getElementById (id + "_positiveFlowIn").checked; if (temp) obj.positiveFlowIn = true;
                temp = document.getElementById (id + "_LimitSets").value; if ("" != temp) obj.LimitSets = temp.split (",");

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["AnalogValues", "0..*", "1", "AnalogValue", "Analog"],
                            ["LimitSets", "0..*", "0..*", "AnalogLimitSet", "Measurements"]
                        ]
                    )
                );
            }
        }

        /**
         * Accumulator represents an accumulated (counted) Measurement, e.g. an energy value.
         *
         */
        class Accumulator extends Measurement
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.Accumulator;
                if (null == bucket)
                   cim_data.Accumulator = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.Accumulator[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Measurement.prototype.parse.call (this, context, sub);
                obj.cls = "Accumulator";
                base.parse_element (/<cim:Accumulator.maxValue>([\s\S]*?)<\/cim:Accumulator.maxValue>/g, obj, "maxValue", base.to_string, sub, context);
                base.parse_attributes (/<cim:Accumulator.AccumulatorValues\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AccumulatorValues", sub, context);
                base.parse_attributes (/<cim:Accumulator.LimitSets\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "LimitSets", sub, context);
                var bucket = context.parsed.Accumulator;
                if (null == bucket)
                   context.parsed.Accumulator = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Measurement.prototype.export.call (this, obj, false);

                base.export_element (obj, "Accumulator", "maxValue", "maxValue",  base.from_string, fields);
                base.export_attributes (obj, "Accumulator", "AccumulatorValues", "AccumulatorValues", fields);
                base.export_attributes (obj, "Accumulator", "LimitSets", "LimitSets", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#Accumulator_collapse" aria-expanded="true" aria-controls="Accumulator_collapse" style="margin-left: 10px;">Accumulator</a></legend>
                    <div id="Accumulator_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.template.call (this) +
                    `
                    {{#maxValue}}<div><b>maxValue</b>: {{maxValue}}</div>{{/maxValue}}
                    {{#AccumulatorValues}}<div><b>AccumulatorValues</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/AccumulatorValues}}
                    {{#LimitSets}}<div><b>LimitSets</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/LimitSets}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.AccumulatorValues) obj.AccumulatorValues_string = obj.AccumulatorValues.join ();
                if (obj.LimitSets) obj.LimitSets_string = obj.LimitSets.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.AccumulatorValues_string;
                delete obj.LimitSets_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_Accumulator_collapse" aria-expanded="true" aria-controls="{{id}}_Accumulator_collapse" style="margin-left: 10px;">Accumulator</a></legend>
                    <div id="{{id}}_Accumulator_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_maxValue'>maxValue: </label><div class='col-sm-8'><input id='{{id}}_maxValue' class='form-control' type='text'{{#maxValue}} value='{{maxValue}}'{{/maxValue}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_LimitSets'>LimitSets: </label><div class='col-sm-8'><input id='{{id}}_LimitSets' class='form-control' type='text'{{#LimitSets}} value='{{LimitSets}}_string'{{/LimitSets}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "Accumulator" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_maxValue").value; if ("" != temp) obj.maxValue = temp;
                temp = document.getElementById (id + "_LimitSets").value; if ("" != temp) obj.LimitSets = temp.split (",");

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["AccumulatorValues", "0..*", "1", "AccumulatorValue", "Accumulator"],
                            ["LimitSets", "0..*", "0..*", "AccumulatorLimitSet", "Measurements"]
                        ]
                    )
                );
            }
        }

        /**
         * StringMeasurement represents a measurement with values of type string.
         *
         */
        class StringMeasurement extends Measurement
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.StringMeasurement;
                if (null == bucket)
                   cim_data.StringMeasurement = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.StringMeasurement[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Measurement.prototype.parse.call (this, context, sub);
                obj.cls = "StringMeasurement";
                base.parse_attributes (/<cim:StringMeasurement.StringMeasurementValues\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "StringMeasurementValues", sub, context);
                var bucket = context.parsed.StringMeasurement;
                if (null == bucket)
                   context.parsed.StringMeasurement = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Measurement.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "StringMeasurement", "StringMeasurementValues", "StringMeasurementValues", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#StringMeasurement_collapse" aria-expanded="true" aria-controls="StringMeasurement_collapse" style="margin-left: 10px;">StringMeasurement</a></legend>
                    <div id="StringMeasurement_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.template.call (this) +
                    `
                    {{#StringMeasurementValues}}<div><b>StringMeasurementValues</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/StringMeasurementValues}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.StringMeasurementValues) obj.StringMeasurementValues_string = obj.StringMeasurementValues.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.StringMeasurementValues_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_StringMeasurement_collapse" aria-expanded="true" aria-controls="{{id}}_StringMeasurement_collapse" style="margin-left: 10px;">StringMeasurement</a></legend>
                    <div id="{{id}}_StringMeasurement_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + Measurement.prototype.edit_template.call (this) +
                    `
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var obj = obj || { id: id, cls: "StringMeasurement" };
                super.submit (id, obj);

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["StringMeasurementValues", "0..*", "1", "StringMeasurementValue", "StringMeasurement"]
                        ]
                    )
                );
            }
        }

        /**
         * An AccumulatorLimitSet specifies a set of Limits that are associated with an Accumulator measurement.
         *
         */
        class AccumulatorLimitSet extends LimitSet
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AccumulatorLimitSet;
                if (null == bucket)
                   cim_data.AccumulatorLimitSet = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AccumulatorLimitSet[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = LimitSet.prototype.parse.call (this, context, sub);
                obj.cls = "AccumulatorLimitSet";
                base.parse_attributes (/<cim:AccumulatorLimitSet.Limits\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Limits", sub, context);
                base.parse_attributes (/<cim:AccumulatorLimitSet.Measurements\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Measurements", sub, context);
                var bucket = context.parsed.AccumulatorLimitSet;
                if (null == bucket)
                   context.parsed.AccumulatorLimitSet = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = LimitSet.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "AccumulatorLimitSet", "Limits", "Limits", fields);
                base.export_attributes (obj, "AccumulatorLimitSet", "Measurements", "Measurements", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AccumulatorLimitSet_collapse" aria-expanded="true" aria-controls="AccumulatorLimitSet_collapse" style="margin-left: 10px;">AccumulatorLimitSet</a></legend>
                    <div id="AccumulatorLimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + LimitSet.prototype.template.call (this) +
                    `
                    {{#Limits}}<div><b>Limits</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Limits}}
                    {{#Measurements}}<div><b>Measurements</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Measurements}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Limits) obj.Limits_string = obj.Limits.join ();
                if (obj.Measurements) obj.Measurements_string = obj.Measurements.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Limits_string;
                delete obj.Measurements_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AccumulatorLimitSet_collapse" aria-expanded="true" aria-controls="{{id}}_AccumulatorLimitSet_collapse" style="margin-left: 10px;">AccumulatorLimitSet</a></legend>
                    <div id="{{id}}_AccumulatorLimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + LimitSet.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Measurements'>Measurements: </label><div class='col-sm-8'><input id='{{id}}_Measurements' class='form-control' type='text'{{#Measurements}} value='{{Measurements}}_string'{{/Measurements}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AccumulatorLimitSet" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_Measurements").value; if ("" != temp) obj.Measurements = temp.split (",");

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Limits", "1..*", "1", "AccumulatorLimit", "LimitSet"],
                            ["Measurements", "0..*", "0..*", "Accumulator", "LimitSets"]
                        ]
                    )
                );
            }
        }

        /**
         * An AnalogLimitSet specifies a set of Limits that are associated with an Analog measurement.
         *
         */
        class AnalogLimitSet extends LimitSet
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.AnalogLimitSet;
                if (null == bucket)
                   cim_data.AnalogLimitSet = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.AnalogLimitSet[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = LimitSet.prototype.parse.call (this, context, sub);
                obj.cls = "AnalogLimitSet";
                base.parse_attributes (/<cim:AnalogLimitSet.Measurements\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Measurements", sub, context);
                base.parse_attributes (/<cim:AnalogLimitSet.Limits\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Limits", sub, context);
                var bucket = context.parsed.AnalogLimitSet;
                if (null == bucket)
                   context.parsed.AnalogLimitSet = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = LimitSet.prototype.export.call (this, obj, false);

                base.export_attributes (obj, "AnalogLimitSet", "Measurements", "Measurements", fields);
                base.export_attributes (obj, "AnalogLimitSet", "Limits", "Limits", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#AnalogLimitSet_collapse" aria-expanded="true" aria-controls="AnalogLimitSet_collapse" style="margin-left: 10px;">AnalogLimitSet</a></legend>
                    <div id="AnalogLimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + LimitSet.prototype.template.call (this) +
                    `
                    {{#Measurements}}<div><b>Measurements</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Measurements}}
                    {{#Limits}}<div><b>Limits</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Limits}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Measurements) obj.Measurements_string = obj.Measurements.join ();
                if (obj.Limits) obj.Limits_string = obj.Limits.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Measurements_string;
                delete obj.Limits_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_AnalogLimitSet_collapse" aria-expanded="true" aria-controls="{{id}}_AnalogLimitSet_collapse" style="margin-left: 10px;">AnalogLimitSet</a></legend>
                    <div id="{{id}}_AnalogLimitSet_collapse" class="collapse in" style="margin-left: 10px;">
                    `
                    + LimitSet.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Measurements'>Measurements: </label><div class='col-sm-8'><input id='{{id}}_Measurements' class='form-control' type='text'{{#Measurements}} value='{{Measurements}}_string'{{/Measurements}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "AnalogLimitSet" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_Measurements").value; if ("" != temp) obj.Measurements = temp.split (",");

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Measurements", "0..*", "0..*", "Analog", "LimitSets"],
                            ["Limits", "0..*", "1", "AnalogLimit", "LimitSet"]
                        ]
                    )
                );
            }
        }

        return (
            {
                RaiseLowerCommand: RaiseLowerCommand,
                Command: Command,
                ValueToAlias: ValueToAlias,
                ValueAliasSet: ValueAliasSet,
                Analog: Analog,
                LimitSet: LimitSet,
                Measurement: Measurement,
                MeasurementValue: MeasurementValue,
                AccumulatorLimit: AccumulatorLimit,
                AnalogControl: AnalogControl,
                StringMeasurementValue: StringMeasurementValue,
                MeasurementValueQuality: MeasurementValueQuality,
                AnalogLimitSet: AnalogLimitSet,
                Discrete: Discrete,
                AccumulatorLimitSet: AccumulatorLimitSet,
                AnalogValue: AnalogValue,
                AnalogLimit: AnalogLimit,
                SetPoint: SetPoint,
                AccumulatorReset: AccumulatorReset,
                DiscreteValue: DiscreteValue,
                AccumulatorValue: AccumulatorValue,
                StringMeasurement: StringMeasurement,
                MeasurementValueSource: MeasurementValueSource,
                Control: Control,
                DiscreteCommand: DiscreteCommand,
                Limit: Limit,
                Accumulator: Accumulator,
                Quality61850: Quality61850
            }
        );
    }
);