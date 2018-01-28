define
(
    ["model/base", "model/Common", "model/Core"],
    /**
     * Congestion rent is a major, highly volatile charge currently faced by many participants in the LMP-based electrical energy markets.
     *
     * For this reason, the ISOs offer congestion revenue rights (CRR), also known as financial transmission rights or transmission congestion contracts. These are financial instruments that allow market participants to hedge against congestion charges when they schedule their generation, load and bilateral energy transactions.
     *
     */
    function (base, Common, Core)
    {

        /**
         * CRRSegment represents a segment of a CRR in a particular time frame.
         *
         * The segment class contains CRR kind, type, quantity, hedger type, time of use flag, and segment period.
         *
         */
        class CRRSegment extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.CRRSegment;
                if (null == bucket)
                   cim_data.CRRSegment = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.CRRSegment[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "CRRSegment";
                base.parse_element (/<cim:CRRSegment.amount>([\s\S]*?)<\/cim:CRRSegment.amount>/g, obj, "amount", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegment.clearingPrice>([\s\S]*?)<\/cim:CRRSegment.clearingPrice>/g, obj, "clearingPrice", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegment.endDateTime>([\s\S]*?)<\/cim:CRRSegment.endDateTime>/g, obj, "endDateTime", base.to_datetime, sub, context);
                base.parse_element (/<cim:CRRSegment.quantity>([\s\S]*?)<\/cim:CRRSegment.quantity>/g, obj, "quantity", base.to_float, sub, context);
                base.parse_element (/<cim:CRRSegment.startDateTime>([\s\S]*?)<\/cim:CRRSegment.startDateTime>/g, obj, "startDateTime", base.to_datetime, sub, context);
                base.parse_attributes (/<cim:CRRSegment.Source\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Source", sub, context);
                base.parse_attributes (/<cim:CRRSegment.Sink\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Sink", sub, context);
                base.parse_attribute (/<cim:CRRSegment.CRR\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CRR", sub, context);
                var bucket = context.parsed.CRRSegment;
                if (null == bucket)
                   context.parsed.CRRSegment = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "CRRSegment", "amount", "amount",  base.from_string, fields);
                base.export_element (obj, "CRRSegment", "clearingPrice", "clearingPrice",  base.from_string, fields);
                base.export_element (obj, "CRRSegment", "endDateTime", "endDateTime",  base.from_datetime, fields);
                base.export_element (obj, "CRRSegment", "quantity", "quantity",  base.from_float, fields);
                base.export_element (obj, "CRRSegment", "startDateTime", "startDateTime",  base.from_datetime, fields);
                base.export_attributes (obj, "CRRSegment", "Source", "Source", fields);
                base.export_attributes (obj, "CRRSegment", "Sink", "Sink", fields);
                base.export_attribute (obj, "CRRSegment", "CRR", "CRR", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#CRRSegment_collapse" aria-expanded="true" aria-controls="CRRSegment_collapse" style="margin-left: 10px;">CRRSegment</a></legend>
                    <div id="CRRSegment_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.template.call (this) +
                    `
                    {{#amount}}<div><b>amount</b>: {{amount}}</div>{{/amount}}
                    {{#clearingPrice}}<div><b>clearingPrice</b>: {{clearingPrice}}</div>{{/clearingPrice}}
                    {{#endDateTime}}<div><b>endDateTime</b>: {{endDateTime}}</div>{{/endDateTime}}
                    {{#quantity}}<div><b>quantity</b>: {{quantity}}</div>{{/quantity}}
                    {{#startDateTime}}<div><b>startDateTime</b>: {{startDateTime}}</div>{{/startDateTime}}
                    {{#Source}}<div><b>Source</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Source}}
                    {{#Sink}}<div><b>Sink</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/Sink}}
                    {{#CRR}}<div><b>CRR</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{CRR}}&quot;);})'>{{CRR}}</a></div>{{/CRR}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.Source) obj.Source_string = obj.Source.join ();
                if (obj.Sink) obj.Sink_string = obj.Sink.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.Source_string;
                delete obj.Sink_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_CRRSegment_collapse" aria-expanded="true" aria-controls="{{id}}_CRRSegment_collapse" style="margin-left: 10px;">CRRSegment</a></legend>
                    <div id="{{id}}_CRRSegment_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Core.IdentifiedObject.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_amount'>amount: </label><div class='col-sm-8'><input id='{{id}}_amount' class='form-control' type='text'{{#amount}} value='{{amount}}'{{/amount}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_clearingPrice'>clearingPrice: </label><div class='col-sm-8'><input id='{{id}}_clearingPrice' class='form-control' type='text'{{#clearingPrice}} value='{{clearingPrice}}'{{/clearingPrice}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_endDateTime'>endDateTime: </label><div class='col-sm-8'><input id='{{id}}_endDateTime' class='form-control' type='text'{{#endDateTime}} value='{{endDateTime}}'{{/endDateTime}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_quantity'>quantity: </label><div class='col-sm-8'><input id='{{id}}_quantity' class='form-control' type='text'{{#quantity}} value='{{quantity}}'{{/quantity}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_startDateTime'>startDateTime: </label><div class='col-sm-8'><input id='{{id}}_startDateTime' class='form-control' type='text'{{#startDateTime}} value='{{startDateTime}}'{{/startDateTime}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Source'>Source: </label><div class='col-sm-8'><input id='{{id}}_Source' class='form-control' type='text'{{#Source}} value='{{Source}}_string'{{/Source}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Sink'>Sink: </label><div class='col-sm-8'><input id='{{id}}_Sink' class='form-control' type='text'{{#Sink}} value='{{Sink}}_string'{{/Sink}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_CRR'>CRR: </label><div class='col-sm-8'><input id='{{id}}_CRR' class='form-control' type='text'{{#CRR}} value='{{CRR}}'{{/CRR}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "CRRSegment" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_amount").value; if ("" != temp) obj.amount = temp;
                temp = document.getElementById (id + "_clearingPrice").value; if ("" != temp) obj.clearingPrice = temp;
                temp = document.getElementById (id + "_endDateTime").value; if ("" != temp) obj.endDateTime = temp;
                temp = document.getElementById (id + "_quantity").value; if ("" != temp) obj.quantity = temp;
                temp = document.getElementById (id + "_startDateTime").value; if ("" != temp) obj.startDateTime = temp;
                temp = document.getElementById (id + "_Source").value; if ("" != temp) obj.Source = temp.split (",");
                temp = document.getElementById (id + "_Sink").value; if ("" != temp) obj.Sink = temp.split (",");
                temp = document.getElementById (id + "_CRR").value; if ("" != temp) obj.CRR = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["Source", "0..*", "0..*", "Pnode", "SourceCRRSegment"],
                            ["Sink", "0..*", "0..*", "Pnode", "SinkCRRSegment"],
                            ["CRR", "1", "1..*", "CRR", "CRRSegment"]
                        ]
                    )
                );
            }
        }

        /**
         * Congestion Revenue Rights (CRR) class that is inherited from a Document class.
         *
         * A CRR is a financial concept that is used to hedge congestion charges.
         *
         */
        class CRR extends Common.Document
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.CRR;
                if (null == bucket)
                   cim_data.CRR = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.CRR[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Common.Document.prototype.parse.call (this, context, sub);
                obj.cls = "CRR";
                base.parse_element (/<cim:CRR.cRRcategory>([\s\S]*?)<\/cim:CRR.cRRcategory>/g, obj, "cRRcategory", base.to_string, sub, context);
                base.parse_element (/<cim:CRR.cRRtype>([\s\S]*?)<\/cim:CRR.cRRtype>/g, obj, "cRRtype", base.to_string, sub, context);
                base.parse_element (/<cim:CRR.hedgeType>([\s\S]*?)<\/cim:CRR.hedgeType>/g, obj, "hedgeType", base.to_string, sub, context);
                base.parse_element (/<cim:CRR.timeOfUse>([\s\S]*?)<\/cim:CRR.timeOfUse>/g, obj, "timeOfUse", base.to_string, sub, context);
                base.parse_element (/<cim:CRR.tradeSliceID>([\s\S]*?)<\/cim:CRR.tradeSliceID>/g, obj, "tradeSliceID", base.to_string, sub, context);
                base.parse_attributes (/<cim:CRR.CRROrgRole\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CRROrgRole", sub, context);
                base.parse_attribute (/<cim:CRR.CRRMarket\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CRRMarket", sub, context);
                base.parse_attribute (/<cim:CRR.Flowgate\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Flowgate", sub, context);
                base.parse_attributes (/<cim:CRR.CRRSegment\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CRRSegment", sub, context);
                var bucket = context.parsed.CRR;
                if (null == bucket)
                   context.parsed.CRR = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Common.Document.prototype.export.call (this, obj, false);

                base.export_element (obj, "CRR", "cRRcategory", "cRRcategory",  base.from_string, fields);
                base.export_element (obj, "CRR", "cRRtype", "cRRtype",  base.from_string, fields);
                base.export_element (obj, "CRR", "hedgeType", "hedgeType",  base.from_string, fields);
                base.export_element (obj, "CRR", "timeOfUse", "timeOfUse",  base.from_string, fields);
                base.export_element (obj, "CRR", "tradeSliceID", "tradeSliceID",  base.from_string, fields);
                base.export_attributes (obj, "CRR", "CRROrgRole", "CRROrgRole", fields);
                base.export_attribute (obj, "CRR", "CRRMarket", "CRRMarket", fields);
                base.export_attribute (obj, "CRR", "Flowgate", "Flowgate", fields);
                base.export_attributes (obj, "CRR", "CRRSegment", "CRRSegment", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#CRR_collapse" aria-expanded="true" aria-controls="CRR_collapse" style="margin-left: 10px;">CRR</a></legend>
                    <div id="CRR_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Common.Document.prototype.template.call (this) +
                    `
                    {{#cRRcategory}}<div><b>cRRcategory</b>: {{cRRcategory}}</div>{{/cRRcategory}}
                    {{#cRRtype}}<div><b>cRRtype</b>: {{cRRtype}}</div>{{/cRRtype}}
                    {{#hedgeType}}<div><b>hedgeType</b>: {{hedgeType}}</div>{{/hedgeType}}
                    {{#timeOfUse}}<div><b>timeOfUse</b>: {{timeOfUse}}</div>{{/timeOfUse}}
                    {{#tradeSliceID}}<div><b>tradeSliceID</b>: {{tradeSliceID}}</div>{{/tradeSliceID}}
                    {{#CRROrgRole}}<div><b>CRROrgRole</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/CRROrgRole}}
                    {{#CRRMarket}}<div><b>CRRMarket</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{CRRMarket}}&quot;);})'>{{CRRMarket}}</a></div>{{/CRRMarket}}
                    {{#Flowgate}}<div><b>Flowgate</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Flowgate}}&quot;);})'>{{Flowgate}}</a></div>{{/Flowgate}}
                    {{#CRRSegment}}<div><b>CRRSegment</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{.}}&quot;);})'>{{.}}</a></div>{{/CRRSegment}}
                    </div>
                    <fieldset>

                    `
                );
            }

            condition (obj)
            {
                super.condition (obj);
                if (obj.CRROrgRole) obj.CRROrgRole_string = obj.CRROrgRole.join ();
                if (obj.CRRSegment) obj.CRRSegment_string = obj.CRRSegment.join ();
            }

            uncondition (obj)
            {
                super.uncondition (obj);
                delete obj.CRROrgRole_string;
                delete obj.CRRSegment_string;
            }

            edit_template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_CRR_collapse" aria-expanded="true" aria-controls="{{id}}_CRR_collapse" style="margin-left: 10px;">CRR</a></legend>
                    <div id="{{id}}_CRR_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Common.Document.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_cRRcategory'>cRRcategory: </label><div class='col-sm-8'><input id='{{id}}_cRRcategory' class='form-control' type='text'{{#cRRcategory}} value='{{cRRcategory}}'{{/cRRcategory}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_cRRtype'>cRRtype: </label><div class='col-sm-8'><input id='{{id}}_cRRtype' class='form-control' type='text'{{#cRRtype}} value='{{cRRtype}}'{{/cRRtype}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_hedgeType'>hedgeType: </label><div class='col-sm-8'><input id='{{id}}_hedgeType' class='form-control' type='text'{{#hedgeType}} value='{{hedgeType}}'{{/hedgeType}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_timeOfUse'>timeOfUse: </label><div class='col-sm-8'><input id='{{id}}_timeOfUse' class='form-control' type='text'{{#timeOfUse}} value='{{timeOfUse}}'{{/timeOfUse}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_tradeSliceID'>tradeSliceID: </label><div class='col-sm-8'><input id='{{id}}_tradeSliceID' class='form-control' type='text'{{#tradeSliceID}} value='{{tradeSliceID}}'{{/tradeSliceID}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_CRRMarket'>CRRMarket: </label><div class='col-sm-8'><input id='{{id}}_CRRMarket' class='form-control' type='text'{{#CRRMarket}} value='{{CRRMarket}}'{{/CRRMarket}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_Flowgate'>Flowgate: </label><div class='col-sm-8'><input id='{{id}}_Flowgate' class='form-control' type='text'{{#Flowgate}} value='{{Flowgate}}'{{/Flowgate}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "CRR" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_cRRcategory").value; if ("" != temp) obj.cRRcategory = temp;
                temp = document.getElementById (id + "_cRRtype").value; if ("" != temp) obj.cRRtype = temp;
                temp = document.getElementById (id + "_hedgeType").value; if ("" != temp) obj.hedgeType = temp;
                temp = document.getElementById (id + "_timeOfUse").value; if ("" != temp) obj.timeOfUse = temp;
                temp = document.getElementById (id + "_tradeSliceID").value; if ("" != temp) obj.tradeSliceID = temp;
                temp = document.getElementById (id + "_CRRMarket").value; if ("" != temp) obj.CRRMarket = temp;
                temp = document.getElementById (id + "_Flowgate").value; if ("" != temp) obj.Flowgate = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["CRROrgRole", "1..*", "1", "CRROrgRole", "CRR"],
                            ["CRRMarket", "1", "1..*", "CRRMarket", "CRR"],
                            ["Flowgate", "0..1", "0..1", "Flowgate", "CRR"],
                            ["CRRSegment", "1..*", "1", "CRRSegment", "CRR"]
                        ]
                    )
                );
            }
        }

        /**
         * Identifies a way in which an organisation may participate with a defined Congestion Revenue Right (CRR).
         *
         */
        class CRROrgRole extends Common.OrganisationRole
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                var bucket = cim_data.CRROrgRole;
                if (null == bucket)
                   cim_data.CRROrgRole = bucket = {};
                bucket[template.id] = template;
            }

            remove (obj, cim_data)
            {
               super.remove (obj, cim_data);
               delete cim_data.CRROrgRole[obj.id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Common.OrganisationRole.prototype.parse.call (this, context, sub);
                obj.cls = "CRROrgRole";
                base.parse_element (/<cim:CRROrgRole.kind>([\s\S]*?)<\/cim:CRROrgRole.kind>/g, obj, "kind", base.to_string, sub, context);
                base.parse_element (/<cim:CRROrgRole.status>([\s\S]*?)<\/cim:CRROrgRole.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_attribute (/<cim:CRROrgRole.CRR\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "CRR", sub, context);
                base.parse_attribute (/<cim:CRROrgRole.MktOrganisation\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "MktOrganisation", sub, context);
                var bucket = context.parsed.CRROrgRole;
                if (null == bucket)
                   context.parsed.CRROrgRole = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Common.OrganisationRole.prototype.export.call (this, obj, false);

                base.export_element (obj, "CRROrgRole", "kind", "kind",  base.from_string, fields);
                base.export_element (obj, "CRROrgRole", "status", "status",  base.from_string, fields);
                base.export_attribute (obj, "CRROrgRole", "CRR", "CRR", fields);
                base.export_attribute (obj, "CRROrgRole", "MktOrganisation", "MktOrganisation", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }

            template ()
            {
                return (
                    `
                    <fieldset>
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#CRROrgRole_collapse" aria-expanded="true" aria-controls="CRROrgRole_collapse" style="margin-left: 10px;">CRROrgRole</a></legend>
                    <div id="CRROrgRole_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Common.OrganisationRole.prototype.template.call (this) +
                    `
                    {{#kind}}<div><b>kind</b>: {{kind}}</div>{{/kind}}
                    {{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
                    {{#CRR}}<div><b>CRR</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{CRR}}&quot;);})'>{{CRR}}</a></div>{{/CRR}}
                    {{#MktOrganisation}}<div><b>MktOrganisation</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{MktOrganisation}}&quot;);})'>{{MktOrganisation}}</a></div>{{/MktOrganisation}}
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
                    <legend class='col-form-legend'><a data-toggle="collapse" href="#{{id}}_CRROrgRole_collapse" aria-expanded="true" aria-controls="{{id}}_CRROrgRole_collapse" style="margin-left: 10px;">CRROrgRole</a></legend>
                    <div id="{{id}}_CRROrgRole_collapse" class="collapse in show" style="margin-left: 10px;">
                    `
                    + Common.OrganisationRole.prototype.edit_template.call (this) +
                    `
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_kind'>kind: </label><div class='col-sm-8'><input id='{{id}}_kind' class='form-control' type='text'{{#kind}} value='{{kind}}'{{/kind}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_status'>status: </label><div class='col-sm-8'><input id='{{id}}_status' class='form-control' type='text'{{#status}} value='{{status}}'{{/status}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_CRR'>CRR: </label><div class='col-sm-8'><input id='{{id}}_CRR' class='form-control' type='text'{{#CRR}} value='{{CRR}}'{{/CRR}}></div></div>
                    <div class='form-group row'><label class='col-sm-4 col-form-label' for='{{id}}_MktOrganisation'>MktOrganisation: </label><div class='col-sm-8'><input id='{{id}}_MktOrganisation' class='form-control' type='text'{{#MktOrganisation}} value='{{MktOrganisation}}'{{/MktOrganisation}}></div></div>
                    </div>
                    <fieldset>
                    `
                );
            }

            submit (id, obj)
            {
                var temp;

                var obj = obj || { id: id, cls: "CRROrgRole" };
                super.submit (id, obj);
                temp = document.getElementById (id + "_kind").value; if ("" != temp) obj.kind = temp;
                temp = document.getElementById (id + "_status").value; if ("" != temp) obj.status = temp;
                temp = document.getElementById (id + "_CRR").value; if ("" != temp) obj.CRR = temp;
                temp = document.getElementById (id + "_MktOrganisation").value; if ("" != temp) obj.MktOrganisation = temp;

                return (obj);
            }

            relations ()
            {
                return (
                    super.relations ().concat (
                        [
                            ["CRR", "1", "1..*", "CRR", "CRROrgRole"],
                            ["MktOrganisation", "1", "0..*", "MktOrganisation", "CRROrgRole"]
                        ]
                    )
                );
            }
        }

        return (
            {
                CRROrgRole: CRROrgRole,
                CRR: CRR,
                CRRSegment: CRRSegment
            }
        );
    }
);