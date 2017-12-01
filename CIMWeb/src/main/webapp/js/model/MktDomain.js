define
(
    ["model/base"],
    /**
     * The MktDomain package is a data dictionary of quantities and units that define datatypes for attributes (properties) that may be used by any class in any other package within MarketOperations.
     *
     */
    function (base)
    {

        /**
         * Energy product type
         *
         */
        class EnergyProductType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.EnergyProductType;
                if (null == bucket)
                   cim_data.EnergyProductType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.EnergyProductType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "EnergyProductType";
                base.parse_element (/<cim:EnergyProductType.FIRM>([\s\S]*?)<\/cim:EnergyProductType.FIRM>/g, obj, "FIRM", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyProductType.NFRM>([\s\S]*?)<\/cim:EnergyProductType.NFRM>/g, obj, "NFRM", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyProductType.DYN>([\s\S]*?)<\/cim:EnergyProductType.DYN>/g, obj, "DYN", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyProductType.WHL>([\s\S]*?)<\/cim:EnergyProductType.WHL>/g, obj, "WHL", base.to_string, sub, context);

                var bucket = context.parsed.EnergyProductType;
                if (null == bucket)
                   context.parsed.EnergyProductType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "EnergyProductType", "FIRM", base.from_string, fields);
                base.export_element (obj, "EnergyProductType", "NFRM", base.from_string, fields);
                base.export_element (obj, "EnergyProductType", "DYN", base.from_string, fields);
                base.export_element (obj, "EnergyProductType", "WHL", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#EnergyProductType_collapse" aria-expanded="true" aria-controls="EnergyProductType_collapse">EnergyProductType</a>
<div id="EnergyProductType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#FIRM}}<div><b>FIRM</b>: {{FIRM}}</div>{{/FIRM}}
{{#NFRM}}<div><b>NFRM</b>: {{NFRM}}</div>{{/NFRM}}
{{#DYN}}<div><b>DYN</b>: {{DYN}}</div>{{/DYN}}
{{#WHL}}<div><b>WHL</b>: {{WHL}}</div>{{/WHL}}
</div>
`
                );
           }        }

        /**
         * Indicates whether the unit is RMR and it's condition type, for example:
         * N' - not an RMR unit
         * '1' - RMR Condition 1 unit
         *
         * '2' - RMR Condition 2 unit
         *
         */
        class FlagTypeRMR extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FlagTypeRMR;
                if (null == bucket)
                   cim_data.FlagTypeRMR = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FlagTypeRMR[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "FlagTypeRMR";
                base.parse_element (/<cim:FlagTypeRMR.N>([\s\S]*?)<\/cim:FlagTypeRMR.N>/g, obj, "N", base.to_string, sub, context);
                base.parse_element (/<cim:FlagTypeRMR.1>([\s\S]*?)<\/cim:FlagTypeRMR.1>/g, obj, "1", base.to_string, sub, context);
                base.parse_element (/<cim:FlagTypeRMR.2>([\s\S]*?)<\/cim:FlagTypeRMR.2>/g, obj, "2", base.to_string, sub, context);

                var bucket = context.parsed.FlagTypeRMR;
                if (null == bucket)
                   context.parsed.FlagTypeRMR = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "FlagTypeRMR", "N", base.from_string, fields);
                base.export_element (obj, "FlagTypeRMR", "1", base.from_string, fields);
                base.export_element (obj, "FlagTypeRMR", "2", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FlagTypeRMR_collapse" aria-expanded="true" aria-controls="FlagTypeRMR_collapse">FlagTypeRMR</a>
<div id="FlagTypeRMR_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#N}}<div><b>N</b>: {{N}}</div>{{/N}}
{{#1}}<div><b>1</b>: {{1}}</div>{{/1}}
{{#2}}<div><b>2</b>: {{2}}</div>{{/2}}
</div>
`
                );
           }        }

        /**
         * Maket type.
         *
         */
        class MarketType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MarketType;
                if (null == bucket)
                   cim_data.MarketType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MarketType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MarketType";
                base.parse_element (/<cim:MarketType.DAM>([\s\S]*?)<\/cim:MarketType.DAM>/g, obj, "DAM", base.to_string, sub, context);
                base.parse_element (/<cim:MarketType.RTM>([\s\S]*?)<\/cim:MarketType.RTM>/g, obj, "RTM", base.to_string, sub, context);
                base.parse_element (/<cim:MarketType.HAM>([\s\S]*?)<\/cim:MarketType.HAM>/g, obj, "HAM", base.to_string, sub, context);
                base.parse_element (/<cim:MarketType.RUC>([\s\S]*?)<\/cim:MarketType.RUC>/g, obj, "RUC", base.to_string, sub, context);

                var bucket = context.parsed.MarketType;
                if (null == bucket)
                   context.parsed.MarketType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MarketType", "DAM", base.from_string, fields);
                base.export_element (obj, "MarketType", "RTM", base.from_string, fields);
                base.export_element (obj, "MarketType", "HAM", base.from_string, fields);
                base.export_element (obj, "MarketType", "RUC", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MarketType_collapse" aria-expanded="true" aria-controls="MarketType_collapse">MarketType</a>
<div id="MarketType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#DAM}}<div><b>DAM</b>: {{DAM}}</div>{{/DAM}}
{{#RTM}}<div><b>RTM</b>: {{RTM}}</div>{{/RTM}}
{{#HAM}}<div><b>HAM</b>: {{HAM}}</div>{{/HAM}}
{{#RUC}}<div><b>RUC</b>: {{RUC}}</div>{{/RUC}}
</div>
`
                );
           }        }

        /**
         * Ramp rate condition
         *
         */
        class RampRateCondition extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.RampRateCondition;
                if (null == bucket)
                   cim_data.RampRateCondition = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.RampRateCondition[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "RampRateCondition";
                base.parse_element (/<cim:RampRateCondition.WORST>([\s\S]*?)<\/cim:RampRateCondition.WORST>/g, obj, "WORST", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateCondition.BEST>([\s\S]*?)<\/cim:RampRateCondition.BEST>/g, obj, "BEST", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateCondition.NORMAL>([\s\S]*?)<\/cim:RampRateCondition.NORMAL>/g, obj, "NORMAL", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateCondition.NA>([\s\S]*?)<\/cim:RampRateCondition.NA>/g, obj, "NA", base.to_string, sub, context);

                var bucket = context.parsed.RampRateCondition;
                if (null == bucket)
                   context.parsed.RampRateCondition = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "RampRateCondition", "WORST", base.from_string, fields);
                base.export_element (obj, "RampRateCondition", "BEST", base.from_string, fields);
                base.export_element (obj, "RampRateCondition", "NORMAL", base.from_string, fields);
                base.export_element (obj, "RampRateCondition", "NA", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#RampRateCondition_collapse" aria-expanded="true" aria-controls="RampRateCondition_collapse">RampRateCondition</a>
<div id="RampRateCondition_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#WORST}}<div><b>WORST</b>: {{WORST}}</div>{{/WORST}}
{{#BEST}}<div><b>BEST</b>: {{BEST}}</div>{{/BEST}}
{{#NORMAL}}<div><b>NORMAL</b>: {{NORMAL}}</div>{{/NORMAL}}
{{#NA}}<div><b>NA</b>: {{NA}}</div>{{/NA}}
</div>
`
                );
           }        }

        /**
         * The basis used to calculate the bid price curve for an energy default bid.
         *
         */
        class BidCalculationBasis extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BidCalculationBasis;
                if (null == bucket)
                   cim_data.BidCalculationBasis = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BidCalculationBasis[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BidCalculationBasis";
                base.parse_element (/<cim:BidCalculationBasis.LMP_BASED>([\s\S]*?)<\/cim:BidCalculationBasis.LMP_BASED>/g, obj, "LMP_BASED", base.to_string, sub, context);
                base.parse_element (/<cim:BidCalculationBasis.COST_BASED>([\s\S]*?)<\/cim:BidCalculationBasis.COST_BASED>/g, obj, "COST_BASED", base.to_string, sub, context);
                base.parse_element (/<cim:BidCalculationBasis.NEGOTIATED>([\s\S]*?)<\/cim:BidCalculationBasis.NEGOTIATED>/g, obj, "NEGOTIATED", base.to_string, sub, context);

                var bucket = context.parsed.BidCalculationBasis;
                if (null == bucket)
                   context.parsed.BidCalculationBasis = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BidCalculationBasis", "LMP_BASED", base.from_string, fields);
                base.export_element (obj, "BidCalculationBasis", "COST_BASED", base.from_string, fields);
                base.export_element (obj, "BidCalculationBasis", "NEGOTIATED", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BidCalculationBasis_collapse" aria-expanded="true" aria-controls="BidCalculationBasis_collapse">BidCalculationBasis</a>
<div id="BidCalculationBasis_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#LMP_BASED}}<div><b>LMP_BASED</b>: {{LMP_BASED}}</div>{{/LMP_BASED}}
{{#COST_BASED}}<div><b>COST_BASED</b>: {{COST_BASED}}</div>{{/COST_BASED}}
{{#NEGOTIATED}}<div><b>NEGOTIATED</b>: {{NEGOTIATED}}</div>{{/NEGOTIATED}}
</div>
`
                );
           }        }

        /**
         * Defines the state of a transaction.
         *
         */
        class EnergyTransactionType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.EnergyTransactionType;
                if (null == bucket)
                   cim_data.EnergyTransactionType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.EnergyTransactionType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "EnergyTransactionType";
                base.parse_element (/<cim:EnergyTransactionType.approve>([\s\S]*?)<\/cim:EnergyTransactionType.approve>/g, obj, "approve", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyTransactionType.deny>([\s\S]*?)<\/cim:EnergyTransactionType.deny>/g, obj, "deny", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyTransactionType.study>([\s\S]*?)<\/cim:EnergyTransactionType.study>/g, obj, "study", base.to_string, sub, context);

                var bucket = context.parsed.EnergyTransactionType;
                if (null == bucket)
                   context.parsed.EnergyTransactionType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "EnergyTransactionType", "approve", base.from_string, fields);
                base.export_element (obj, "EnergyTransactionType", "deny", base.from_string, fields);
                base.export_element (obj, "EnergyTransactionType", "study", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#EnergyTransactionType_collapse" aria-expanded="true" aria-controls="EnergyTransactionType_collapse">EnergyTransactionType</a>
<div id="EnergyTransactionType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#approve}}<div><b>approve</b>: {{approve}}</div>{{/approve}}
{{#deny}}<div><b>deny</b>: {{deny}}</div>{{/deny}}
{{#study}}<div><b>study</b>: {{study}}</div>{{/study}}
</div>
`
                );
           }        }

        /**
         * Transmission Contract Type, For example:
         * O - Other
         * TE - Transmission Export
         * TI - Transmission Import
         * ETC - Existing Transmission Contract
         * RMT - RMT Contract
         * TOR - Transmission Ownership Right
         * RMR - Reliability Must Run Contract
         *
         * CVR - Converted contract.
         *
         */
        class ContractType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ContractType;
                if (null == bucket)
                   cim_data.ContractType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ContractType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ContractType";
                base.parse_element (/<cim:ContractType.ETC>([\s\S]*?)<\/cim:ContractType.ETC>/g, obj, "ETC", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.TOR>([\s\S]*?)<\/cim:ContractType.TOR>/g, obj, "TOR", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.RMR>([\s\S]*?)<\/cim:ContractType.RMR>/g, obj, "RMR", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.RMT>([\s\S]*?)<\/cim:ContractType.RMT>/g, obj, "RMT", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.O>([\s\S]*?)<\/cim:ContractType.O>/g, obj, "O", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.TE>([\s\S]*?)<\/cim:ContractType.TE>/g, obj, "TE", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.TI>([\s\S]*?)<\/cim:ContractType.TI>/g, obj, "TI", base.to_string, sub, context);
                base.parse_element (/<cim:ContractType.CVR>([\s\S]*?)<\/cim:ContractType.CVR>/g, obj, "CVR", base.to_string, sub, context);

                var bucket = context.parsed.ContractType;
                if (null == bucket)
                   context.parsed.ContractType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ContractType", "ETC", base.from_string, fields);
                base.export_element (obj, "ContractType", "TOR", base.from_string, fields);
                base.export_element (obj, "ContractType", "RMR", base.from_string, fields);
                base.export_element (obj, "ContractType", "RMT", base.from_string, fields);
                base.export_element (obj, "ContractType", "O", base.from_string, fields);
                base.export_element (obj, "ContractType", "TE", base.from_string, fields);
                base.export_element (obj, "ContractType", "TI", base.from_string, fields);
                base.export_element (obj, "ContractType", "CVR", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ContractType_collapse" aria-expanded="true" aria-controls="ContractType_collapse">ContractType</a>
<div id="ContractType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ETC}}<div><b>ETC</b>: {{ETC}}</div>{{/ETC}}
{{#TOR}}<div><b>TOR</b>: {{TOR}}</div>{{/TOR}}
{{#RMR}}<div><b>RMR</b>: {{RMR}}</div>{{/RMR}}
{{#RMT}}<div><b>RMT</b>: {{RMT}}</div>{{/RMT}}
{{#O}}<div><b>O</b>: {{O}}</div>{{/O}}
{{#TE}}<div><b>TE</b>: {{TE}}</div>{{/TE}}
{{#TI}}<div><b>TI</b>: {{TI}}</div>{{/TI}}
{{#CVR}}<div><b>CVR</b>: {{CVR}}</div>{{/CVR}}
</div>
`
                );
           }        }

        /**
         * For example:
         * DEFAULT_ENERGY_BID
         * DEFAULT_STARTUP_BID
         *
         * DEFAULT_MINIMUM_LOAD_BID
         *
         */
        class BidType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BidType;
                if (null == bucket)
                   cim_data.BidType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BidType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BidType";
                base.parse_element (/<cim:BidType.DEFAULT_ENERGY_BID>([\s\S]*?)<\/cim:BidType.DEFAULT_ENERGY_BID>/g, obj, "DEFAULT_ENERGY_BID", base.to_string, sub, context);
                base.parse_element (/<cim:BidType.DEFAULT_STARTUP_BID>([\s\S]*?)<\/cim:BidType.DEFAULT_STARTUP_BID>/g, obj, "DEFAULT_STARTUP_BID", base.to_string, sub, context);
                base.parse_element (/<cim:BidType.DEFAULT_MINIMUM_LOAD_BID>([\s\S]*?)<\/cim:BidType.DEFAULT_MINIMUM_LOAD_BID>/g, obj, "DEFAULT_MINIMUM_LOAD_BID", base.to_string, sub, context);

                var bucket = context.parsed.BidType;
                if (null == bucket)
                   context.parsed.BidType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BidType", "DEFAULT_ENERGY_BID", base.from_string, fields);
                base.export_element (obj, "BidType", "DEFAULT_STARTUP_BID", base.from_string, fields);
                base.export_element (obj, "BidType", "DEFAULT_MINIMUM_LOAD_BID", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BidType_collapse" aria-expanded="true" aria-controls="BidType_collapse">BidType</a>
<div id="BidType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#DEFAULT_ENERGY_BID}}<div><b>DEFAULT_ENERGY_BID</b>: {{DEFAULT_ENERGY_BID}}</div>{{/DEFAULT_ENERGY_BID}}
{{#DEFAULT_STARTUP_BID}}<div><b>DEFAULT_STARTUP_BID</b>: {{DEFAULT_STARTUP_BID}}</div>{{/DEFAULT_STARTUP_BID}}
{{#DEFAULT_MINIMUM_LOAD_BID}}<div><b>DEFAULT_MINIMUM_LOAD_BID</b>: {{DEFAULT_MINIMUM_LOAD_BID}}</div>{{/DEFAULT_MINIMUM_LOAD_BID}}
</div>
`
                );
           }        }

        /**
         * Status of equipment
         *
         */
        class EquipmentStatusType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.EquipmentStatusType;
                if (null == bucket)
                   cim_data.EquipmentStatusType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.EquipmentStatusType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "EquipmentStatusType";
                base.parse_element (/<cim:EquipmentStatusType.In>([\s\S]*?)<\/cim:EquipmentStatusType.In>/g, obj, "In", base.to_string, sub, context);
                base.parse_element (/<cim:EquipmentStatusType.Out>([\s\S]*?)<\/cim:EquipmentStatusType.Out>/g, obj, "Out", base.to_string, sub, context);

                var bucket = context.parsed.EquipmentStatusType;
                if (null == bucket)
                   context.parsed.EquipmentStatusType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "EquipmentStatusType", "In", base.from_string, fields);
                base.export_element (obj, "EquipmentStatusType", "Out", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#EquipmentStatusType_collapse" aria-expanded="true" aria-controls="EquipmentStatusType_collapse">EquipmentStatusType</a>
<div id="EquipmentStatusType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#In}}<div><b>In</b>: {{In}}</div>{{/In}}
{{#Out}}<div><b>Out</b>: {{Out}}</div>{{/Out}}
</div>
`
                );
           }        }

        /**
         * Market product self schedule bid types.
         *
         */
        class MarketProductSelfSchedType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MarketProductSelfSchedType;
                if (null == bucket)
                   cim_data.MarketProductSelfSchedType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MarketProductSelfSchedType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MarketProductSelfSchedType";
                base.parse_element (/<cim:MarketProductSelfSchedType.ETC>([\s\S]*?)<\/cim:MarketProductSelfSchedType.ETC>/g, obj, "ETC", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.TOR>([\s\S]*?)<\/cim:MarketProductSelfSchedType.TOR>/g, obj, "TOR", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.RMR>([\s\S]*?)<\/cim:MarketProductSelfSchedType.RMR>/g, obj, "RMR", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.RGMR>([\s\S]*?)<\/cim:MarketProductSelfSchedType.RGMR>/g, obj, "RGMR", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.RMT>([\s\S]*?)<\/cim:MarketProductSelfSchedType.RMT>/g, obj, "RMT", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.PT>([\s\S]*?)<\/cim:MarketProductSelfSchedType.PT>/g, obj, "PT", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.LPT>([\s\S]*?)<\/cim:MarketProductSelfSchedType.LPT>/g, obj, "LPT", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.SP>([\s\S]*?)<\/cim:MarketProductSelfSchedType.SP>/g, obj, "SP", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductSelfSchedType.RA>([\s\S]*?)<\/cim:MarketProductSelfSchedType.RA>/g, obj, "RA", base.to_string, sub, context);

                var bucket = context.parsed.MarketProductSelfSchedType;
                if (null == bucket)
                   context.parsed.MarketProductSelfSchedType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MarketProductSelfSchedType", "ETC", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "TOR", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "RMR", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "RGMR", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "RMT", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "PT", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "LPT", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "SP", base.from_string, fields);
                base.export_element (obj, "MarketProductSelfSchedType", "RA", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MarketProductSelfSchedType_collapse" aria-expanded="true" aria-controls="MarketProductSelfSchedType_collapse">MarketProductSelfSchedType</a>
<div id="MarketProductSelfSchedType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ETC}}<div><b>ETC</b>: {{ETC}}</div>{{/ETC}}
{{#TOR}}<div><b>TOR</b>: {{TOR}}</div>{{/TOR}}
{{#RMR}}<div><b>RMR</b>: {{RMR}}</div>{{/RMR}}
{{#RGMR}}<div><b>RGMR</b>: {{RGMR}}</div>{{/RGMR}}
{{#RMT}}<div><b>RMT</b>: {{RMT}}</div>{{/RMT}}
{{#PT}}<div><b>PT</b>: {{PT}}</div>{{/PT}}
{{#LPT}}<div><b>LPT</b>: {{LPT}}</div>{{/LPT}}
{{#SP}}<div><b>SP</b>: {{SP}}</div>{{/SP}}
{{#RA}}<div><b>RA</b>: {{RA}}</div>{{/RA}}
</div>
`
                );
           }        }

        /**
         * For example:
         * ADD - add
         *
         * CHG - change
         *
         */
        class MQSCHGType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MQSCHGType;
                if (null == bucket)
                   cim_data.MQSCHGType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MQSCHGType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MQSCHGType";
                base.parse_element (/<cim:MQSCHGType.ADD>([\s\S]*?)<\/cim:MQSCHGType.ADD>/g, obj, "ADD", base.to_string, sub, context);
                base.parse_element (/<cim:MQSCHGType.CHG>([\s\S]*?)<\/cim:MQSCHGType.CHG>/g, obj, "CHG", base.to_string, sub, context);

                var bucket = context.parsed.MQSCHGType;
                if (null == bucket)
                   context.parsed.MQSCHGType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MQSCHGType", "ADD", base.from_string, fields);
                base.export_element (obj, "MQSCHGType", "CHG", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MQSCHGType_collapse" aria-expanded="true" aria-controls="MQSCHGType_collapse">MQSCHGType</a>
<div id="MQSCHGType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ADD}}<div><b>ADD</b>: {{ADD}}</div>{{/ADD}}
{{#CHG}}<div><b>CHG</b>: {{CHG}}</div>{{/CHG}}
</div>
`
                );
           }        }

        /**
         * Direction of an intertie.
         *
         */
        class InterTieDirection extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.InterTieDirection;
                if (null == bucket)
                   cim_data.InterTieDirection = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.InterTieDirection[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "InterTieDirection";
                base.parse_element (/<cim:InterTieDirection.E>([\s\S]*?)<\/cim:InterTieDirection.E>/g, obj, "E", base.to_string, sub, context);
                base.parse_element (/<cim:InterTieDirection.I>([\s\S]*?)<\/cim:InterTieDirection.I>/g, obj, "I", base.to_string, sub, context);

                var bucket = context.parsed.InterTieDirection;
                if (null == bucket)
                   context.parsed.InterTieDirection = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "InterTieDirection", "E", base.from_string, fields);
                base.export_element (obj, "InterTieDirection", "I", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#InterTieDirection_collapse" aria-expanded="true" aria-controls="InterTieDirection_collapse">InterTieDirection</a>
<div id="InterTieDirection_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#E}}<div><b>E</b>: {{E}}</div>{{/E}}
{{#I}}<div><b>I</b>: {{I}}</div>{{/I}}
</div>
`
                );
           }        }

        /**
         * Automatic Dispatch mode
         *
         */
        class AutomaticDispatchMode extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AutomaticDispatchMode;
                if (null == bucket)
                   cim_data.AutomaticDispatchMode = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AutomaticDispatchMode[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AutomaticDispatchMode";
                base.parse_element (/<cim:AutomaticDispatchMode.INTERVAL>([\s\S]*?)<\/cim:AutomaticDispatchMode.INTERVAL>/g, obj, "INTERVAL", base.to_string, sub, context);
                base.parse_element (/<cim:AutomaticDispatchMode.CONTINGENCY>([\s\S]*?)<\/cim:AutomaticDispatchMode.CONTINGENCY>/g, obj, "CONTINGENCY", base.to_string, sub, context);
                base.parse_element (/<cim:AutomaticDispatchMode.MANUAL>([\s\S]*?)<\/cim:AutomaticDispatchMode.MANUAL>/g, obj, "MANUAL", base.to_string, sub, context);

                var bucket = context.parsed.AutomaticDispatchMode;
                if (null == bucket)
                   context.parsed.AutomaticDispatchMode = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AutomaticDispatchMode", "INTERVAL", base.from_string, fields);
                base.export_element (obj, "AutomaticDispatchMode", "CONTINGENCY", base.from_string, fields);
                base.export_element (obj, "AutomaticDispatchMode", "MANUAL", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AutomaticDispatchMode_collapse" aria-expanded="true" aria-controls="AutomaticDispatchMode_collapse">AutomaticDispatchMode</a>
<div id="AutomaticDispatchMode_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#INTERVAL}}<div><b>INTERVAL</b>: {{INTERVAL}}</div>{{/INTERVAL}}
{{#CONTINGENCY}}<div><b>CONTINGENCY</b>: {{CONTINGENCY}}</div>{{/CONTINGENCY}}
{{#MANUAL}}<div><b>MANUAL</b>: {{MANUAL}}</div>{{/MANUAL}}
</div>
`
                );
           }        }

        /**
         * For example:
         * SELF - Self commitment
         * ISO - New commitment for this market period
         *
         * UC - Existing commitment that was a hold over from a previous market.
         *
         */
        class CommitmentType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CommitmentType;
                if (null == bucket)
                   cim_data.CommitmentType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CommitmentType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CommitmentType";
                base.parse_element (/<cim:CommitmentType.SELF>([\s\S]*?)<\/cim:CommitmentType.SELF>/g, obj, "SELF", base.to_string, sub, context);
                base.parse_element (/<cim:CommitmentType.ISO>([\s\S]*?)<\/cim:CommitmentType.ISO>/g, obj, "ISO", base.to_string, sub, context);
                base.parse_element (/<cim:CommitmentType.UC>([\s\S]*?)<\/cim:CommitmentType.UC>/g, obj, "UC", base.to_string, sub, context);

                var bucket = context.parsed.CommitmentType;
                if (null == bucket)
                   context.parsed.CommitmentType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CommitmentType", "SELF", base.from_string, fields);
                base.export_element (obj, "CommitmentType", "ISO", base.from_string, fields);
                base.export_element (obj, "CommitmentType", "UC", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CommitmentType_collapse" aria-expanded="true" aria-controls="CommitmentType_collapse">CommitmentType</a>
<div id="CommitmentType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#SELF}}<div><b>SELF</b>: {{SELF}}</div>{{/SELF}}
{{#ISO}}<div><b>ISO</b>: {{ISO}}</div>{{/ISO}}
{{#UC}}<div><b>UC</b>: {{UC}}</div>{{/UC}}
</div>
`
                );
           }        }

        /**
         * Used as a flag set to Yes or No.
         *
         */
        class YesNo extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.YesNo;
                if (null == bucket)
                   cim_data.YesNo = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.YesNo[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "YesNo";
                base.parse_element (/<cim:YesNo.YES>([\s\S]*?)<\/cim:YesNo.YES>/g, obj, "YES", base.to_string, sub, context);
                base.parse_element (/<cim:YesNo.NO>([\s\S]*?)<\/cim:YesNo.NO>/g, obj, "NO", base.to_string, sub, context);

                var bucket = context.parsed.YesNo;
                if (null == bucket)
                   context.parsed.YesNo = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "YesNo", "YES", base.from_string, fields);
                base.export_element (obj, "YesNo", "NO", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#YesNo_collapse" aria-expanded="true" aria-controls="YesNo_collapse">YesNo</a>
<div id="YesNo_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#YES}}<div><b>YES</b>: {{YES}}</div>{{/YES}}
{{#NO}}<div><b>NO</b>: {{NO}}</div>{{/NO}}
</div>
`
                );
           }        }

        /**
         * For example:
         * Asset Owner Sink designator for use by CRR
         * Asset Owner Source designator for use by CRR
         * Reliability Must Run
         * Scheduling Coordinator
         *
         * Load Serving Entity
         *
         */
        class ResourceAssnType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResourceAssnType;
                if (null == bucket)
                   cim_data.ResourceAssnType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResourceAssnType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResourceAssnType";
                base.parse_element (/<cim:ResourceAssnType.CSNK>([\s\S]*?)<\/cim:ResourceAssnType.CSNK>/g, obj, "CSNK", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAssnType.CSRC>([\s\S]*?)<\/cim:ResourceAssnType.CSRC>/g, obj, "CSRC", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAssnType.RMR>([\s\S]*?)<\/cim:ResourceAssnType.RMR>/g, obj, "RMR", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAssnType.SC>([\s\S]*?)<\/cim:ResourceAssnType.SC>/g, obj, "SC", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAssnType.LSE>([\s\S]*?)<\/cim:ResourceAssnType.LSE>/g, obj, "LSE", base.to_string, sub, context);

                var bucket = context.parsed.ResourceAssnType;
                if (null == bucket)
                   context.parsed.ResourceAssnType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResourceAssnType", "CSNK", base.from_string, fields);
                base.export_element (obj, "ResourceAssnType", "CSRC", base.from_string, fields);
                base.export_element (obj, "ResourceAssnType", "RMR", base.from_string, fields);
                base.export_element (obj, "ResourceAssnType", "SC", base.from_string, fields);
                base.export_element (obj, "ResourceAssnType", "LSE", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResourceAssnType_collapse" aria-expanded="true" aria-controls="ResourceAssnType_collapse">ResourceAssnType</a>
<div id="ResourceAssnType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#CSNK}}<div><b>CSNK</b>: {{CSNK}}</div>{{/CSNK}}
{{#CSRC}}<div><b>CSRC</b>: {{CSRC}}</div>{{/CSRC}}
{{#RMR}}<div><b>RMR</b>: {{RMR}}</div>{{/RMR}}
{{#SC}}<div><b>SC</b>: {{SC}}</div>{{/SC}}
{{#LSE}}<div><b>LSE</b>: {{LSE}}</div>{{/LSE}}
</div>
`
                );
           }        }

        /**
         * Limit type specified for AnalogLimits.
         *
         */
        class AnalogLimitType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AnalogLimitType;
                if (null == bucket)
                   cim_data.AnalogLimitType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AnalogLimitType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AnalogLimitType";
                base.parse_element (/<cim:AnalogLimitType.BranchMediumTerm>([\s\S]*?)<\/cim:AnalogLimitType.BranchMediumTerm>/g, obj, "BranchMediumTerm", base.to_string, sub, context);
                base.parse_element (/<cim:AnalogLimitType.BranchLongTerm>([\s\S]*?)<\/cim:AnalogLimitType.BranchLongTerm>/g, obj, "BranchLongTerm", base.to_string, sub, context);
                base.parse_element (/<cim:AnalogLimitType.BranchShortTerm>([\s\S]*?)<\/cim:AnalogLimitType.BranchShortTerm>/g, obj, "BranchShortTerm", base.to_string, sub, context);
                base.parse_element (/<cim:AnalogLimitType.VoltageHigh>([\s\S]*?)<\/cim:AnalogLimitType.VoltageHigh>/g, obj, "VoltageHigh", base.to_string, sub, context);
                base.parse_element (/<cim:AnalogLimitType.VoltageLow>([\s\S]*?)<\/cim:AnalogLimitType.VoltageLow>/g, obj, "VoltageLow", base.to_string, sub, context);

                var bucket = context.parsed.AnalogLimitType;
                if (null == bucket)
                   context.parsed.AnalogLimitType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AnalogLimitType", "BranchMediumTerm", base.from_string, fields);
                base.export_element (obj, "AnalogLimitType", "BranchLongTerm", base.from_string, fields);
                base.export_element (obj, "AnalogLimitType", "BranchShortTerm", base.from_string, fields);
                base.export_element (obj, "AnalogLimitType", "VoltageHigh", base.from_string, fields);
                base.export_element (obj, "AnalogLimitType", "VoltageLow", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AnalogLimitType_collapse" aria-expanded="true" aria-controls="AnalogLimitType_collapse">AnalogLimitType</a>
<div id="AnalogLimitType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#BranchMediumTerm}}<div><b>BranchMediumTerm</b>: {{BranchMediumTerm}}</div>{{/BranchMediumTerm}}
{{#BranchLongTerm}}<div><b>BranchLongTerm</b>: {{BranchLongTerm}}</div>{{/BranchLongTerm}}
{{#BranchShortTerm}}<div><b>BranchShortTerm</b>: {{BranchShortTerm}}</div>{{/BranchShortTerm}}
{{#VoltageHigh}}<div><b>VoltageHigh</b>: {{VoltageHigh}}</div>{{/VoltageHigh}}
{{#VoltageLow}}<div><b>VoltageLow</b>: {{VoltageLow}}</div>{{/VoltageLow}}
</div>
`
                );
           }        }

        /**
         * Market power mitigation test method type.
         *
         * Tests with the normal (default) thresholds or tests with the alternate thresholds.
         *
         */
        class MPMTestMethodType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MPMTestMethodType;
                if (null == bucket)
                   cim_data.MPMTestMethodType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MPMTestMethodType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MPMTestMethodType";
                base.parse_element (/<cim:MPMTestMethodType.NORMAL>([\s\S]*?)<\/cim:MPMTestMethodType.NORMAL>/g, obj, "NORMAL", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestMethodType.ALTERNATE>([\s\S]*?)<\/cim:MPMTestMethodType.ALTERNATE>/g, obj, "ALTERNATE", base.to_string, sub, context);

                var bucket = context.parsed.MPMTestMethodType;
                if (null == bucket)
                   context.parsed.MPMTestMethodType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MPMTestMethodType", "NORMAL", base.from_string, fields);
                base.export_element (obj, "MPMTestMethodType", "ALTERNATE", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MPMTestMethodType_collapse" aria-expanded="true" aria-controls="MPMTestMethodType_collapse">MPMTestMethodType</a>
<div id="MPMTestMethodType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#NORMAL}}<div><b>NORMAL</b>: {{NORMAL}}</div>{{/NORMAL}}
{{#ALTERNATE}}<div><b>ALTERNATE</b>: {{ALTERNATE}}</div>{{/ALTERNATE}}
</div>
`
                );
           }        }

        /**
         * Commitment instruction types.
         *
         */
        class AutomaticDispInstTypeCommitment extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AutomaticDispInstTypeCommitment;
                if (null == bucket)
                   cim_data.AutomaticDispInstTypeCommitment = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AutomaticDispInstTypeCommitment[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AutomaticDispInstTypeCommitment";
                base.parse_element (/<cim:AutomaticDispInstTypeCommitment.START_UP>([\s\S]*?)<\/cim:AutomaticDispInstTypeCommitment.START_UP>/g, obj, "START_UP", base.to_string, sub, context);
                base.parse_element (/<cim:AutomaticDispInstTypeCommitment.SHUT_DOWN>([\s\S]*?)<\/cim:AutomaticDispInstTypeCommitment.SHUT_DOWN>/g, obj, "SHUT_DOWN", base.to_string, sub, context);

                var bucket = context.parsed.AutomaticDispInstTypeCommitment;
                if (null == bucket)
                   context.parsed.AutomaticDispInstTypeCommitment = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AutomaticDispInstTypeCommitment", "START_UP", base.from_string, fields);
                base.export_element (obj, "AutomaticDispInstTypeCommitment", "SHUT_DOWN", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AutomaticDispInstTypeCommitment_collapse" aria-expanded="true" aria-controls="AutomaticDispInstTypeCommitment_collapse">AutomaticDispInstTypeCommitment</a>
<div id="AutomaticDispInstTypeCommitment_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#START_UP}}<div><b>START_UP</b>: {{START_UP}}</div>{{/START_UP}}
{{#SHUT_DOWN}}<div><b>SHUT_DOWN</b>: {{SHUT_DOWN}}</div>{{/SHUT_DOWN}}
</div>
`
                );
           }        }

        /**
         * Defines the individual passes that produce results per execution type/market type
         *
         */
        class PassIndicatorType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PassIndicatorType;
                if (null == bucket)
                   cim_data.PassIndicatorType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PassIndicatorType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PassIndicatorType";
                base.parse_element (/<cim:PassIndicatorType.MPM-1>([\s\S]*?)<\/cim:PassIndicatorType.MPM-1>/g, obj, "MPM-1", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.MPM-2>([\s\S]*?)<\/cim:PassIndicatorType.MPM-2>/g, obj, "MPM-2", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.MPM-3>([\s\S]*?)<\/cim:PassIndicatorType.MPM-3>/g, obj, "MPM-3", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.MPM-4>([\s\S]*?)<\/cim:PassIndicatorType.MPM-4>/g, obj, "MPM-4", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.RUC>([\s\S]*?)<\/cim:PassIndicatorType.RUC>/g, obj, "RUC", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.RTPD>([\s\S]*?)<\/cim:PassIndicatorType.RTPD>/g, obj, "RTPD", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.RTED>([\s\S]*?)<\/cim:PassIndicatorType.RTED>/g, obj, "RTED", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.HA-SCUC>([\s\S]*?)<\/cim:PassIndicatorType.HA-SCUC>/g, obj, "HA-SCUC", base.to_string, sub, context);
                base.parse_element (/<cim:PassIndicatorType.DA>([\s\S]*?)<\/cim:PassIndicatorType.DA>/g, obj, "DA", base.to_string, sub, context);

                var bucket = context.parsed.PassIndicatorType;
                if (null == bucket)
                   context.parsed.PassIndicatorType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PassIndicatorType", "MPM-1", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "MPM-2", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "MPM-3", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "MPM-4", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "RUC", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "RTPD", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "RTED", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "HA-SCUC", base.from_string, fields);
                base.export_element (obj, "PassIndicatorType", "DA", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PassIndicatorType_collapse" aria-expanded="true" aria-controls="PassIndicatorType_collapse">PassIndicatorType</a>
<div id="PassIndicatorType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#MPM-1}}<div><b>MPM-1</b>: {{MPM-1}}</div>{{/MPM-1}}
{{#MPM-2}}<div><b>MPM-2</b>: {{MPM-2}}</div>{{/MPM-2}}
{{#MPM-3}}<div><b>MPM-3</b>: {{MPM-3}}</div>{{/MPM-3}}
{{#MPM-4}}<div><b>MPM-4</b>: {{MPM-4}}</div>{{/MPM-4}}
{{#RUC}}<div><b>RUC</b>: {{RUC}}</div>{{/RUC}}
{{#RTPD}}<div><b>RTPD</b>: {{RTPD}}</div>{{/RTPD}}
{{#RTED}}<div><b>RTED</b>: {{RTED}}</div>{{/RTED}}
{{#HA-SCUC}}<div><b>HA-SCUC</b>: {{HA-SCUC}}</div>{{/HA-SCUC}}
{{#DA}}<div><b>DA</b>: {{DA}}</div>{{/DA}}
</div>
`
                );
           }        }

        /**
         * Action type associated with an ActionRequest against a ParticipantInterfaces::Trade.
         *
         */
        class ActionType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ActionType;
                if (null == bucket)
                   cim_data.ActionType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ActionType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ActionType";
                base.parse_element (/<cim:ActionType.CANCEL>([\s\S]*?)<\/cim:ActionType.CANCEL>/g, obj, "CANCEL", base.to_string, sub, context);

                var bucket = context.parsed.ActionType;
                if (null == bucket)
                   context.parsed.ActionType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ActionType", "CANCEL", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ActionType_collapse" aria-expanded="true" aria-controls="ActionType_collapse">ActionType</a>
<div id="ActionType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#CANCEL}}<div><b>CANCEL</b>: {{CANCEL}}</div>{{/CANCEL}}
</div>
`
                );
           }        }

        /**
         * Market results binding constraint types.
         *
         */
        class ResultsConstraintType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResultsConstraintType;
                if (null == bucket)
                   cim_data.ResultsConstraintType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResultsConstraintType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResultsConstraintType";
                base.parse_element (/<cim:ResultsConstraintType.FG_act>([\s\S]*?)<\/cim:ResultsConstraintType.FG_act>/g, obj, "FG_act", base.to_string, sub, context);
                base.parse_element (/<cim:ResultsConstraintType.Contingency>([\s\S]*?)<\/cim:ResultsConstraintType.Contingency>/g, obj, "Contingency", base.to_string, sub, context);
                base.parse_element (/<cim:ResultsConstraintType.Interface>([\s\S]*?)<\/cim:ResultsConstraintType.Interface>/g, obj, "Interface", base.to_string, sub, context);
                base.parse_element (/<cim:ResultsConstraintType.Actual>([\s\S]*?)<\/cim:ResultsConstraintType.Actual>/g, obj, "Actual", base.to_string, sub, context);

                var bucket = context.parsed.ResultsConstraintType;
                if (null == bucket)
                   context.parsed.ResultsConstraintType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResultsConstraintType", "FG_act", base.from_string, fields);
                base.export_element (obj, "ResultsConstraintType", "Contingency", base.from_string, fields);
                base.export_element (obj, "ResultsConstraintType", "Interface", base.from_string, fields);
                base.export_element (obj, "ResultsConstraintType", "Actual", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResultsConstraintType_collapse" aria-expanded="true" aria-controls="ResultsConstraintType_collapse">ResultsConstraintType</a>
<div id="ResultsConstraintType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#FG_act}}<div><b>FG_act</b>: {{FG_act}}</div>{{/FG_act}}
{{#Contingency}}<div><b>Contingency</b>: {{Contingency}}</div>{{/Contingency}}
{{#Interface}}<div><b>Interface</b>: {{Interface}}</div>{{/Interface}}
{{#Actual}}<div><b>Actual</b>: {{Actual}}</div>{{/Actual}}
</div>
`
                );
           }        }

        /**
         * Specifies the direction of energy flow in the flowgate.
         *
         */
        class FlowDirectionType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FlowDirectionType;
                if (null == bucket)
                   cim_data.FlowDirectionType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FlowDirectionType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "FlowDirectionType";
                base.parse_element (/<cim:FlowDirectionType.Forward>([\s\S]*?)<\/cim:FlowDirectionType.Forward>/g, obj, "Forward", base.to_string, sub, context);
                base.parse_element (/<cim:FlowDirectionType.Reverse>([\s\S]*?)<\/cim:FlowDirectionType.Reverse>/g, obj, "Reverse", base.to_string, sub, context);

                var bucket = context.parsed.FlowDirectionType;
                if (null == bucket)
                   context.parsed.FlowDirectionType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "FlowDirectionType", "Forward", base.from_string, fields);
                base.export_element (obj, "FlowDirectionType", "Reverse", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FlowDirectionType_collapse" aria-expanded="true" aria-controls="FlowDirectionType_collapse">FlowDirectionType</a>
<div id="FlowDirectionType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#Forward}}<div><b>Forward</b>: {{Forward}}</div>{{/Forward}}
{{#Reverse}}<div><b>Reverse</b>: {{Reverse}}</div>{{/Reverse}}
</div>
`
                );
           }        }

        /**
         * For example:
         * Bio Gas (Landfill, Sewage, Digester, etc.)
         * Biomass
         * Coal
         * DIST
         * Natural Gas
         * Geothermal
         * HRCV
         * None
         * Nuclear
         * Oil
         * Other
         * Solar
         * Waste to Energy
         * Water
         *
         * Wind
         *
         */
        class FuelSource extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.FuelSource;
                if (null == bucket)
                   cim_data.FuelSource = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.FuelSource[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "FuelSource";
                base.parse_element (/<cim:FuelSource.NG>([\s\S]*?)<\/cim:FuelSource.NG>/g, obj, "NG", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.NNG>([\s\S]*?)<\/cim:FuelSource.NNG>/g, obj, "NNG", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.BGAS>([\s\S]*?)<\/cim:FuelSource.BGAS>/g, obj, "BGAS", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.BIOM>([\s\S]*?)<\/cim:FuelSource.BIOM>/g, obj, "BIOM", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.COAL>([\s\S]*?)<\/cim:FuelSource.COAL>/g, obj, "COAL", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.DIST>([\s\S]*?)<\/cim:FuelSource.DIST>/g, obj, "DIST", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.GAS>([\s\S]*?)<\/cim:FuelSource.GAS>/g, obj, "GAS", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.GEOT>([\s\S]*?)<\/cim:FuelSource.GEOT>/g, obj, "GEOT", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.HRCV>([\s\S]*?)<\/cim:FuelSource.HRCV>/g, obj, "HRCV", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.NONE>([\s\S]*?)<\/cim:FuelSource.NONE>/g, obj, "NONE", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.NUCL>([\s\S]*?)<\/cim:FuelSource.NUCL>/g, obj, "NUCL", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.OIL>([\s\S]*?)<\/cim:FuelSource.OIL>/g, obj, "OIL", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.OTHR>([\s\S]*?)<\/cim:FuelSource.OTHR>/g, obj, "OTHR", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.SOLR>([\s\S]*?)<\/cim:FuelSource.SOLR>/g, obj, "SOLR", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.WAST>([\s\S]*?)<\/cim:FuelSource.WAST>/g, obj, "WAST", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.WATR>([\s\S]*?)<\/cim:FuelSource.WATR>/g, obj, "WATR", base.to_string, sub, context);
                base.parse_element (/<cim:FuelSource.WIND>([\s\S]*?)<\/cim:FuelSource.WIND>/g, obj, "WIND", base.to_string, sub, context);

                var bucket = context.parsed.FuelSource;
                if (null == bucket)
                   context.parsed.FuelSource = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "FuelSource", "NG", base.from_string, fields);
                base.export_element (obj, "FuelSource", "NNG", base.from_string, fields);
                base.export_element (obj, "FuelSource", "BGAS", base.from_string, fields);
                base.export_element (obj, "FuelSource", "BIOM", base.from_string, fields);
                base.export_element (obj, "FuelSource", "COAL", base.from_string, fields);
                base.export_element (obj, "FuelSource", "DIST", base.from_string, fields);
                base.export_element (obj, "FuelSource", "GAS", base.from_string, fields);
                base.export_element (obj, "FuelSource", "GEOT", base.from_string, fields);
                base.export_element (obj, "FuelSource", "HRCV", base.from_string, fields);
                base.export_element (obj, "FuelSource", "NONE", base.from_string, fields);
                base.export_element (obj, "FuelSource", "NUCL", base.from_string, fields);
                base.export_element (obj, "FuelSource", "OIL", base.from_string, fields);
                base.export_element (obj, "FuelSource", "OTHR", base.from_string, fields);
                base.export_element (obj, "FuelSource", "SOLR", base.from_string, fields);
                base.export_element (obj, "FuelSource", "WAST", base.from_string, fields);
                base.export_element (obj, "FuelSource", "WATR", base.from_string, fields);
                base.export_element (obj, "FuelSource", "WIND", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#FuelSource_collapse" aria-expanded="true" aria-controls="FuelSource_collapse">FuelSource</a>
<div id="FuelSource_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#NG}}<div><b>NG</b>: {{NG}}</div>{{/NG}}
{{#NNG}}<div><b>NNG</b>: {{NNG}}</div>{{/NNG}}
{{#BGAS}}<div><b>BGAS</b>: {{BGAS}}</div>{{/BGAS}}
{{#BIOM}}<div><b>BIOM</b>: {{BIOM}}</div>{{/BIOM}}
{{#COAL}}<div><b>COAL</b>: {{COAL}}</div>{{/COAL}}
{{#DIST}}<div><b>DIST</b>: {{DIST}}</div>{{/DIST}}
{{#GAS}}<div><b>GAS</b>: {{GAS}}</div>{{/GAS}}
{{#GEOT}}<div><b>GEOT</b>: {{GEOT}}</div>{{/GEOT}}
{{#HRCV}}<div><b>HRCV</b>: {{HRCV}}</div>{{/HRCV}}
{{#NONE}}<div><b>NONE</b>: {{NONE}}</div>{{/NONE}}
{{#NUCL}}<div><b>NUCL</b>: {{NUCL}}</div>{{/NUCL}}
{{#OIL}}<div><b>OIL</b>: {{OIL}}</div>{{/OIL}}
{{#OTHR}}<div><b>OTHR</b>: {{OTHR}}</div>{{/OTHR}}
{{#SOLR}}<div><b>SOLR</b>: {{SOLR}}</div>{{/SOLR}}
{{#WAST}}<div><b>WAST</b>: {{WAST}}</div>{{/WAST}}
{{#WATR}}<div><b>WATR</b>: {{WATR}}</div>{{/WATR}}
{{#WIND}}<div><b>WIND</b>: {{WIND}}</div>{{/WIND}}
</div>
`
                );
           }        }

        /**
         * Time of Use used by a CRR definition for specifying the time the CRR spans.
         *
         * ON - CRR spans the on peak hours of the day, OFF - CRR spans the off peak hours of the day, 24HR - CRR spans the entire day.
         *
         */
        class TimeOfUse extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TimeOfUse;
                if (null == bucket)
                   cim_data.TimeOfUse = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TimeOfUse[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "TimeOfUse";
                base.parse_element (/<cim:TimeOfUse.ON>([\s\S]*?)<\/cim:TimeOfUse.ON>/g, obj, "ON", base.to_string, sub, context);
                base.parse_element (/<cim:TimeOfUse.OFF>([\s\S]*?)<\/cim:TimeOfUse.OFF>/g, obj, "OFF", base.to_string, sub, context);
                base.parse_element (/<cim:TimeOfUse.24HR>([\s\S]*?)<\/cim:TimeOfUse.24HR>/g, obj, "24HR", base.to_string, sub, context);

                var bucket = context.parsed.TimeOfUse;
                if (null == bucket)
                   context.parsed.TimeOfUse = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "TimeOfUse", "ON", base.from_string, fields);
                base.export_element (obj, "TimeOfUse", "OFF", base.from_string, fields);
                base.export_element (obj, "TimeOfUse", "24HR", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TimeOfUse_collapse" aria-expanded="true" aria-controls="TimeOfUse_collapse">TimeOfUse</a>
<div id="TimeOfUse_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ON}}<div><b>ON</b>: {{ON}}</div>{{/ON}}
{{#OFF}}<div><b>OFF</b>: {{OFF}}</div>{{/OFF}}
{{#24HR}}<div><b>24HR</b>: {{24HR}}</div>{{/24HR}}
</div>
`
                );
           }        }

        /**
         * For example:
         * Passed
         * Failed
         * Disabled
         *
         * Skipped
         *
         */
        class MPMTestOutcome extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MPMTestOutcome;
                if (null == bucket)
                   cim_data.MPMTestOutcome = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MPMTestOutcome[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MPMTestOutcome";
                base.parse_element (/<cim:MPMTestOutcome.P>([\s\S]*?)<\/cim:MPMTestOutcome.P>/g, obj, "P", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestOutcome.F>([\s\S]*?)<\/cim:MPMTestOutcome.F>/g, obj, "F", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestOutcome.D>([\s\S]*?)<\/cim:MPMTestOutcome.D>/g, obj, "D", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestOutcome.S>([\s\S]*?)<\/cim:MPMTestOutcome.S>/g, obj, "S", base.to_string, sub, context);

                var bucket = context.parsed.MPMTestOutcome;
                if (null == bucket)
                   context.parsed.MPMTestOutcome = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MPMTestOutcome", "P", base.from_string, fields);
                base.export_element (obj, "MPMTestOutcome", "F", base.from_string, fields);
                base.export_element (obj, "MPMTestOutcome", "D", base.from_string, fields);
                base.export_element (obj, "MPMTestOutcome", "S", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MPMTestOutcome_collapse" aria-expanded="true" aria-controls="MPMTestOutcome_collapse">MPMTestOutcome</a>
<div id="MPMTestOutcome_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#P}}<div><b>P</b>: {{P}}</div>{{/P}}
{{#F}}<div><b>F</b>: {{F}}</div>{{/F}}
{{#D}}<div><b>D</b>: {{D}}</div>{{/D}}
{{#S}}<div><b>S</b>: {{S}}</div>{{/S}}
</div>
`
                );
           }        }

        /**
         * For example:
         * 0: ignore ramping limits,
         * 1: 20-minute ramping rule,
         *
         * 2: 60-minute ramping rule
         *
         */
        class RampModeType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.RampModeType;
                if (null == bucket)
                   cim_data.RampModeType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.RampModeType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "RampModeType";
                base.parse_element (/<cim:RampModeType.0>([\s\S]*?)<\/cim:RampModeType.0>/g, obj, "0", base.to_string, sub, context);
                base.parse_element (/<cim:RampModeType.1>([\s\S]*?)<\/cim:RampModeType.1>/g, obj, "1", base.to_string, sub, context);
                base.parse_element (/<cim:RampModeType.2>([\s\S]*?)<\/cim:RampModeType.2>/g, obj, "2", base.to_string, sub, context);

                var bucket = context.parsed.RampModeType;
                if (null == bucket)
                   context.parsed.RampModeType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "RampModeType", "0", base.from_string, fields);
                base.export_element (obj, "RampModeType", "1", base.from_string, fields);
                base.export_element (obj, "RampModeType", "2", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#RampModeType_collapse" aria-expanded="true" aria-controls="RampModeType_collapse">RampModeType</a>
<div id="RampModeType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#0}}<div><b>0</b>: {{0}}</div>{{/0}}
{{#1}}<div><b>1</b>: {{1}}</div>{{/1}}
{{#2}}<div><b>2</b>: {{2}}</div>{{/2}}
</div>
`
                );
           }        }

        /**
         * For example:
         *
         * Energy, Reg Up, Reg Down, Spin Reserve, Nonspin Reserve, RUC, Load Folloing Up, and Load Following Down.
         *
         */
        class MarketProductType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MarketProductType;
                if (null == bucket)
                   cim_data.MarketProductType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MarketProductType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MarketProductType";
                base.parse_element (/<cim:MarketProductType.EN>([\s\S]*?)<\/cim:MarketProductType.EN>/g, obj, "EN", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.RU>([\s\S]*?)<\/cim:MarketProductType.RU>/g, obj, "RU", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.RD>([\s\S]*?)<\/cim:MarketProductType.RD>/g, obj, "RD", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.SR>([\s\S]*?)<\/cim:MarketProductType.SR>/g, obj, "SR", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.NR>([\s\S]*?)<\/cim:MarketProductType.NR>/g, obj, "NR", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.RC>([\s\S]*?)<\/cim:MarketProductType.RC>/g, obj, "RC", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.LFU>([\s\S]*?)<\/cim:MarketProductType.LFU>/g, obj, "LFU", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.LFD>([\s\S]*?)<\/cim:MarketProductType.LFD>/g, obj, "LFD", base.to_string, sub, context);
                base.parse_element (/<cim:MarketProductType.REG>([\s\S]*?)<\/cim:MarketProductType.REG>/g, obj, "REG", base.to_string, sub, context);

                var bucket = context.parsed.MarketProductType;
                if (null == bucket)
                   context.parsed.MarketProductType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MarketProductType", "EN", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "RU", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "RD", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "SR", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "NR", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "RC", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "LFU", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "LFD", base.from_string, fields);
                base.export_element (obj, "MarketProductType", "REG", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MarketProductType_collapse" aria-expanded="true" aria-controls="MarketProductType_collapse">MarketProductType</a>
<div id="MarketProductType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#EN}}<div><b>EN</b>: {{EN}}</div>{{/EN}}
{{#RU}}<div><b>RU</b>: {{RU}}</div>{{/RU}}
{{#RD}}<div><b>RD</b>: {{RD}}</div>{{/RD}}
{{#SR}}<div><b>SR</b>: {{SR}}</div>{{/SR}}
{{#NR}}<div><b>NR</b>: {{NR}}</div>{{/NR}}
{{#RC}}<div><b>RC</b>: {{RC}}</div>{{/RC}}
{{#LFU}}<div><b>LFU</b>: {{LFU}}</div>{{/LFU}}
{{#LFD}}<div><b>LFD</b>: {{LFD}}</div>{{/LFD}}
{{#REG}}<div><b>REG</b>: {{REG}}</div>{{/REG}}
</div>
`
                );
           }        }

        /**
         * Market power mitigation test identifier type, for example:
         * 
         * 1 ? Global Price Test
         * 2 ? Global Conduct Test
         * 3 ? Global Impact Test
         * 4 ? Local Price Test
         * 5 ? Local Conduct Test
         *
         * 6 ? Local Impact Test
         *
         */
        class MPMTestIdentifierType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MPMTestIdentifierType;
                if (null == bucket)
                   cim_data.MPMTestIdentifierType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MPMTestIdentifierType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MPMTestIdentifierType";
                base.parse_element (/<cim:MPMTestIdentifierType.1>([\s\S]*?)<\/cim:MPMTestIdentifierType.1>/g, obj, "1", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestIdentifierType.2>([\s\S]*?)<\/cim:MPMTestIdentifierType.2>/g, obj, "2", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestIdentifierType.3>([\s\S]*?)<\/cim:MPMTestIdentifierType.3>/g, obj, "3", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestIdentifierType.4>([\s\S]*?)<\/cim:MPMTestIdentifierType.4>/g, obj, "4", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestIdentifierType.5>([\s\S]*?)<\/cim:MPMTestIdentifierType.5>/g, obj, "5", base.to_string, sub, context);
                base.parse_element (/<cim:MPMTestIdentifierType.6>([\s\S]*?)<\/cim:MPMTestIdentifierType.6>/g, obj, "6", base.to_string, sub, context);

                var bucket = context.parsed.MPMTestIdentifierType;
                if (null == bucket)
                   context.parsed.MPMTestIdentifierType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MPMTestIdentifierType", "1", base.from_string, fields);
                base.export_element (obj, "MPMTestIdentifierType", "2", base.from_string, fields);
                base.export_element (obj, "MPMTestIdentifierType", "3", base.from_string, fields);
                base.export_element (obj, "MPMTestIdentifierType", "4", base.from_string, fields);
                base.export_element (obj, "MPMTestIdentifierType", "5", base.from_string, fields);
                base.export_element (obj, "MPMTestIdentifierType", "6", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MPMTestIdentifierType_collapse" aria-expanded="true" aria-controls="MPMTestIdentifierType_collapse">MPMTestIdentifierType</a>
<div id="MPMTestIdentifierType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#1}}<div><b>1</b>: {{1}}</div>{{/1}}
{{#2}}<div><b>2</b>: {{2}}</div>{{/2}}
{{#3}}<div><b>3</b>: {{3}}</div>{{/3}}
{{#4}}<div><b>4</b>: {{4}}</div>{{/4}}
{{#5}}<div><b>5</b>: {{5}}</div>{{/5}}
{{#6}}<div><b>6</b>: {{6}}</div>{{/6}}
</div>
`
                );
           }        }

        /**
         * Indication of which type of self schedule is being referenced.
         *
         */
        class SelfSchedReferenceType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.SelfSchedReferenceType;
                if (null == bucket)
                   cim_data.SelfSchedReferenceType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.SelfSchedReferenceType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "SelfSchedReferenceType";
                base.parse_element (/<cim:SelfSchedReferenceType.ETC>([\s\S]*?)<\/cim:SelfSchedReferenceType.ETC>/g, obj, "ETC", base.to_string, sub, context);
                base.parse_element (/<cim:SelfSchedReferenceType.TOR>([\s\S]*?)<\/cim:SelfSchedReferenceType.TOR>/g, obj, "TOR", base.to_string, sub, context);

                var bucket = context.parsed.SelfSchedReferenceType;
                if (null == bucket)
                   context.parsed.SelfSchedReferenceType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "SelfSchedReferenceType", "ETC", base.from_string, fields);
                base.export_element (obj, "SelfSchedReferenceType", "TOR", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#SelfSchedReferenceType_collapse" aria-expanded="true" aria-controls="SelfSchedReferenceType_collapse">SelfSchedReferenceType</a>
<div id="SelfSchedReferenceType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ETC}}<div><b>ETC</b>: {{ETC}}</div>{{/ETC}}
{{#TOR}}<div><b>TOR</b>: {{TOR}}</div>{{/TOR}}
</div>
`
                );
           }        }

        /**
         * Load forecast zone types.
         *
         */
        class LoadForecastType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.LoadForecastType;
                if (null == bucket)
                   cim_data.LoadForecastType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.LoadForecastType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "LoadForecastType";
                base.parse_element (/<cim:LoadForecastType.LFZ>([\s\S]*?)<\/cim:LoadForecastType.LFZ>/g, obj, "LFZ", base.to_string, sub, context);
                base.parse_element (/<cim:LoadForecastType.LZMS>([\s\S]*?)<\/cim:LoadForecastType.LZMS>/g, obj, "LZMS", base.to_string, sub, context);

                var bucket = context.parsed.LoadForecastType;
                if (null == bucket)
                   context.parsed.LoadForecastType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "LoadForecastType", "LFZ", base.from_string, fields);
                base.export_element (obj, "LoadForecastType", "LZMS", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#LoadForecastType_collapse" aria-expanded="true" aria-controls="LoadForecastType_collapse">LoadForecastType</a>
<div id="LoadForecastType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#LFZ}}<div><b>LFZ</b>: {{LFZ}}</div>{{/LFZ}}
{{#LZMS}}<div><b>LZMS</b>: {{LZMS}}</div>{{/LZMS}}
</div>
`
                );
           }        }

        /**
         * Trade type.
         *
         */
        class TradeType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TradeType;
                if (null == bucket)
                   cim_data.TradeType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TradeType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "TradeType";
                base.parse_element (/<cim:TradeType.IST>([\s\S]*?)<\/cim:TradeType.IST>/g, obj, "IST", base.to_string, sub, context);
                base.parse_element (/<cim:TradeType.AST>([\s\S]*?)<\/cim:TradeType.AST>/g, obj, "AST", base.to_string, sub, context);
                base.parse_element (/<cim:TradeType.UCT>([\s\S]*?)<\/cim:TradeType.UCT>/g, obj, "UCT", base.to_string, sub, context);

                var bucket = context.parsed.TradeType;
                if (null == bucket)
                   context.parsed.TradeType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "TradeType", "IST", base.from_string, fields);
                base.export_element (obj, "TradeType", "AST", base.from_string, fields);
                base.export_element (obj, "TradeType", "UCT", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TradeType_collapse" aria-expanded="true" aria-controls="TradeType_collapse">TradeType</a>
<div id="TradeType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#IST}}<div><b>IST</b>: {{IST}}</div>{{/IST}}
{{#AST}}<div><b>AST</b>: {{AST}}</div>{{/AST}}
{{#UCT}}<div><b>UCT</b>: {{UCT}}</div>{{/UCT}}
</div>
`
                );
           }        }

        /**
         * Execution types of Market Runs
         *
         */
        class ExecutionType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ExecutionType;
                if (null == bucket)
                   cim_data.ExecutionType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ExecutionType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ExecutionType";
                base.parse_element (/<cim:ExecutionType.DA>([\s\S]*?)<\/cim:ExecutionType.DA>/g, obj, "DA", base.to_string, sub, context);
                base.parse_element (/<cim:ExecutionType.HASP>([\s\S]*?)<\/cim:ExecutionType.HASP>/g, obj, "HASP", base.to_string, sub, context);
                base.parse_element (/<cim:ExecutionType.RTPD>([\s\S]*?)<\/cim:ExecutionType.RTPD>/g, obj, "RTPD", base.to_string, sub, context);
                base.parse_element (/<cim:ExecutionType.RTD>([\s\S]*?)<\/cim:ExecutionType.RTD>/g, obj, "RTD", base.to_string, sub, context);

                var bucket = context.parsed.ExecutionType;
                if (null == bucket)
                   context.parsed.ExecutionType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ExecutionType", "DA", base.from_string, fields);
                base.export_element (obj, "ExecutionType", "HASP", base.from_string, fields);
                base.export_element (obj, "ExecutionType", "RTPD", base.from_string, fields);
                base.export_element (obj, "ExecutionType", "RTD", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ExecutionType_collapse" aria-expanded="true" aria-controls="ExecutionType_collapse">ExecutionType</a>
<div id="ExecutionType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#DA}}<div><b>DA</b>: {{DA}}</div>{{/DA}}
{{#HASP}}<div><b>HASP</b>: {{HASP}}</div>{{/HASP}}
{{#RTPD}}<div><b>RTPD</b>: {{RTPD}}</div>{{/RTPD}}
{{#RTD}}<div><b>RTD</b>: {{RTD}}</div>{{/RTD}}
</div>
`
                );
           }        }

        /**
         * Locational AS Flags indicating whether the Upper or Lower Bound limit of the AS regional procurment is binding
         *
         */
        class ResourceLimitIndicator extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResourceLimitIndicator;
                if (null == bucket)
                   cim_data.ResourceLimitIndicator = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResourceLimitIndicator[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResourceLimitIndicator";
                base.parse_element (/<cim:ResourceLimitIndicator.UPPER>([\s\S]*?)<\/cim:ResourceLimitIndicator.UPPER>/g, obj, "UPPER", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceLimitIndicator.LOWER>([\s\S]*?)<\/cim:ResourceLimitIndicator.LOWER>/g, obj, "LOWER", base.to_string, sub, context);

                var bucket = context.parsed.ResourceLimitIndicator;
                if (null == bucket)
                   context.parsed.ResourceLimitIndicator = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResourceLimitIndicator", "UPPER", base.from_string, fields);
                base.export_element (obj, "ResourceLimitIndicator", "LOWER", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResourceLimitIndicator_collapse" aria-expanded="true" aria-controls="ResourceLimitIndicator_collapse">ResourceLimitIndicator</a>
<div id="ResourceLimitIndicator_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#UPPER}}<div><b>UPPER</b>: {{UPPER}}</div>{{/UPPER}}
{{#LOWER}}<div><b>LOWER</b>: {{LOWER}}</div>{{/LOWER}}
</div>
`
                );
           }        }

        /**
         * For example:
         * 'Y' - Participates in both LMPM and SMPM
         * 'N' - Not included in LMP price measures
         * 'S' - Participates in SMPM price measures
         *
         * 'L' - Participates in LMPM price measures
         *
         */
        class ParticipationCategoryMPM extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ParticipationCategoryMPM;
                if (null == bucket)
                   cim_data.ParticipationCategoryMPM = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ParticipationCategoryMPM[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ParticipationCategoryMPM";
                base.parse_element (/<cim:ParticipationCategoryMPM.Y>([\s\S]*?)<\/cim:ParticipationCategoryMPM.Y>/g, obj, "Y", base.to_string, sub, context);
                base.parse_element (/<cim:ParticipationCategoryMPM.N>([\s\S]*?)<\/cim:ParticipationCategoryMPM.N>/g, obj, "N", base.to_string, sub, context);
                base.parse_element (/<cim:ParticipationCategoryMPM.S>([\s\S]*?)<\/cim:ParticipationCategoryMPM.S>/g, obj, "S", base.to_string, sub, context);
                base.parse_element (/<cim:ParticipationCategoryMPM.L>([\s\S]*?)<\/cim:ParticipationCategoryMPM.L>/g, obj, "L", base.to_string, sub, context);

                var bucket = context.parsed.ParticipationCategoryMPM;
                if (null == bucket)
                   context.parsed.ParticipationCategoryMPM = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ParticipationCategoryMPM", "Y", base.from_string, fields);
                base.export_element (obj, "ParticipationCategoryMPM", "N", base.from_string, fields);
                base.export_element (obj, "ParticipationCategoryMPM", "S", base.from_string, fields);
                base.export_element (obj, "ParticipationCategoryMPM", "L", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ParticipationCategoryMPM_collapse" aria-expanded="true" aria-controls="ParticipationCategoryMPM_collapse">ParticipationCategoryMPM</a>
<div id="ParticipationCategoryMPM_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#Y}}<div><b>Y</b>: {{Y}}</div>{{/Y}}
{{#N}}<div><b>N</b>: {{N}}</div>{{/N}}
{{#S}}<div><b>S</b>: {{S}}</div>{{/S}}
{{#L}}<div><b>L</b>: {{L}}</div>{{/L}}
</div>
`
                );
           }        }

        /**
         * Kind of bill media.
         *
         */
        class MktBillMediaKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MktBillMediaKind;
                if (null == bucket)
                   cim_data.MktBillMediaKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MktBillMediaKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MktBillMediaKind";
                base.parse_element (/<cim:MktBillMediaKind.paper>([\s\S]*?)<\/cim:MktBillMediaKind.paper>/g, obj, "paper", base.to_string, sub, context);
                base.parse_element (/<cim:MktBillMediaKind.electronic>([\s\S]*?)<\/cim:MktBillMediaKind.electronic>/g, obj, "electronic", base.to_string, sub, context);
                base.parse_element (/<cim:MktBillMediaKind.other>([\s\S]*?)<\/cim:MktBillMediaKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.MktBillMediaKind;
                if (null == bucket)
                   context.parsed.MktBillMediaKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MktBillMediaKind", "paper", base.from_string, fields);
                base.export_element (obj, "MktBillMediaKind", "electronic", base.from_string, fields);
                base.export_element (obj, "MktBillMediaKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MktBillMediaKind_collapse" aria-expanded="true" aria-controls="MktBillMediaKind_collapse">MktBillMediaKind</a>
<div id="MktBillMediaKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#paper}}<div><b>paper</b>: {{paper}}</div>{{/paper}}
{{#electronic}}<div><b>electronic</b>: {{electronic}}</div>{{/electronic}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * For example:
         * 'S' - Mitigated by SMPM because of "misconduct"
         * 'L; - Mitigated by LMPM because of "misconduct"
         * 'R' - Modified by LMPM because of RMR rules
         * 'M' - Mitigated because of "misconduct" both by SMPM and LMPM
         * 'B' - Mitigated because of "misconduct" both by SMPM and modified by LMLM because of RMR rules
         *
         * 'O' - original
         *
         */
        class BidMitigationStatus extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BidMitigationStatus;
                if (null == bucket)
                   cim_data.BidMitigationStatus = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BidMitigationStatus[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BidMitigationStatus";
                base.parse_element (/<cim:BidMitigationStatus.S>([\s\S]*?)<\/cim:BidMitigationStatus.S>/g, obj, "S", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationStatus.L>([\s\S]*?)<\/cim:BidMitigationStatus.L>/g, obj, "L", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationStatus.R>([\s\S]*?)<\/cim:BidMitigationStatus.R>/g, obj, "R", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationStatus.M>([\s\S]*?)<\/cim:BidMitigationStatus.M>/g, obj, "M", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationStatus.B>([\s\S]*?)<\/cim:BidMitigationStatus.B>/g, obj, "B", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationStatus.O>([\s\S]*?)<\/cim:BidMitigationStatus.O>/g, obj, "O", base.to_string, sub, context);

                var bucket = context.parsed.BidMitigationStatus;
                if (null == bucket)
                   context.parsed.BidMitigationStatus = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BidMitigationStatus", "S", base.from_string, fields);
                base.export_element (obj, "BidMitigationStatus", "L", base.from_string, fields);
                base.export_element (obj, "BidMitigationStatus", "R", base.from_string, fields);
                base.export_element (obj, "BidMitigationStatus", "M", base.from_string, fields);
                base.export_element (obj, "BidMitigationStatus", "B", base.from_string, fields);
                base.export_element (obj, "BidMitigationStatus", "O", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BidMitigationStatus_collapse" aria-expanded="true" aria-controls="BidMitigationStatus_collapse">BidMitigationStatus</a>
<div id="BidMitigationStatus_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#S}}<div><b>S</b>: {{S}}</div>{{/S}}
{{#L}}<div><b>L</b>: {{L}}</div>{{/L}}
{{#R}}<div><b>R</b>: {{R}}</div>{{/R}}
{{#M}}<div><b>M</b>: {{M}}</div>{{/M}}
{{#B}}<div><b>B</b>: {{B}}</div>{{/B}}
{{#O}}<div><b>O</b>: {{O}}</div>{{/O}}
</div>
`
                );
           }        }

        /**
         * Self schedule breakdown type.
         *
         */
        class SelfScheduleBreakdownType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.SelfScheduleBreakdownType;
                if (null == bucket)
                   cim_data.SelfScheduleBreakdownType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.SelfScheduleBreakdownType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "SelfScheduleBreakdownType";
                base.parse_element (/<cim:SelfScheduleBreakdownType.ETC>([\s\S]*?)<\/cim:SelfScheduleBreakdownType.ETC>/g, obj, "ETC", base.to_string, sub, context);
                base.parse_element (/<cim:SelfScheduleBreakdownType.TOR>([\s\S]*?)<\/cim:SelfScheduleBreakdownType.TOR>/g, obj, "TOR", base.to_string, sub, context);
                base.parse_element (/<cim:SelfScheduleBreakdownType.LPT>([\s\S]*?)<\/cim:SelfScheduleBreakdownType.LPT>/g, obj, "LPT", base.to_string, sub, context);

                var bucket = context.parsed.SelfScheduleBreakdownType;
                if (null == bucket)
                   context.parsed.SelfScheduleBreakdownType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "SelfScheduleBreakdownType", "ETC", base.from_string, fields);
                base.export_element (obj, "SelfScheduleBreakdownType", "TOR", base.from_string, fields);
                base.export_element (obj, "SelfScheduleBreakdownType", "LPT", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#SelfScheduleBreakdownType_collapse" aria-expanded="true" aria-controls="SelfScheduleBreakdownType_collapse">SelfScheduleBreakdownType</a>
<div id="SelfScheduleBreakdownType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ETC}}<div><b>ETC</b>: {{ETC}}</div>{{/ETC}}
{{#TOR}}<div><b>TOR</b>: {{TOR}}</div>{{/TOR}}
{{#LPT}}<div><b>LPT</b>: {{LPT}}</div>{{/LPT}}
</div>
`
                );
           }        }

        /**
         * Kind of Market account.
         *
         */
        class MktAccountKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MktAccountKind;
                if (null == bucket)
                   cim_data.MktAccountKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MktAccountKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MktAccountKind";
                base.parse_element (/<cim:MktAccountKind.normal>([\s\S]*?)<\/cim:MktAccountKind.normal>/g, obj, "normal", base.to_string, sub, context);
                base.parse_element (/<cim:MktAccountKind.reversal>([\s\S]*?)<\/cim:MktAccountKind.reversal>/g, obj, "reversal", base.to_string, sub, context);
                base.parse_element (/<cim:MktAccountKind.statistical>([\s\S]*?)<\/cim:MktAccountKind.statistical>/g, obj, "statistical", base.to_string, sub, context);
                base.parse_element (/<cim:MktAccountKind.estimate>([\s\S]*?)<\/cim:MktAccountKind.estimate>/g, obj, "estimate", base.to_string, sub, context);

                var bucket = context.parsed.MktAccountKind;
                if (null == bucket)
                   context.parsed.MktAccountKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MktAccountKind", "normal", base.from_string, fields);
                base.export_element (obj, "MktAccountKind", "reversal", base.from_string, fields);
                base.export_element (obj, "MktAccountKind", "statistical", base.from_string, fields);
                base.export_element (obj, "MktAccountKind", "estimate", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MktAccountKind_collapse" aria-expanded="true" aria-controls="MktAccountKind_collapse">MktAccountKind</a>
<div id="MktAccountKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#normal}}<div><b>normal</b>: {{normal}}</div>{{/normal}}
{{#reversal}}<div><b>reversal</b>: {{reversal}}</div>{{/reversal}}
{{#statistical}}<div><b>statistical</b>: {{statistical}}</div>{{/statistical}}
{{#estimate}}<div><b>estimate</b>: {{estimate}}</div>{{/estimate}}
</div>
`
                );
           }        }

        /**
         * Congestion Revenue Rights category types
         *
         */
        class CRRCategoryType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CRRCategoryType;
                if (null == bucket)
                   cim_data.CRRCategoryType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CRRCategoryType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CRRCategoryType";
                base.parse_element (/<cim:CRRCategoryType.NSR>([\s\S]*?)<\/cim:CRRCategoryType.NSR>/g, obj, "NSR", base.to_string, sub, context);
                base.parse_element (/<cim:CRRCategoryType.PTP>([\s\S]*?)<\/cim:CRRCategoryType.PTP>/g, obj, "PTP", base.to_string, sub, context);

                var bucket = context.parsed.CRRCategoryType;
                if (null == bucket)
                   context.parsed.CRRCategoryType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CRRCategoryType", "NSR", base.from_string, fields);
                base.export_element (obj, "CRRCategoryType", "PTP", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CRRCategoryType_collapse" aria-expanded="true" aria-controls="CRRCategoryType_collapse">CRRCategoryType</a>
<div id="CRRCategoryType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#NSR}}<div><b>NSR</b>: {{NSR}}</div>{{/NSR}}
{{#PTP}}<div><b>PTP</b>: {{PTP}}</div>{{/PTP}}
</div>
`
                );
           }        }

        /**
         * Types of resource registration status, for example:
         * 
         * Active
         * Mothballed
         * Planned
         *
         * Decommissioned
         *
         */
        class ResourceRegistrationStatus extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResourceRegistrationStatus;
                if (null == bucket)
                   cim_data.ResourceRegistrationStatus = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResourceRegistrationStatus[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResourceRegistrationStatus";
                base.parse_element (/<cim:ResourceRegistrationStatus.Active>([\s\S]*?)<\/cim:ResourceRegistrationStatus.Active>/g, obj, "Active", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceRegistrationStatus.Planned>([\s\S]*?)<\/cim:ResourceRegistrationStatus.Planned>/g, obj, "Planned", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceRegistrationStatus.Mothballed>([\s\S]*?)<\/cim:ResourceRegistrationStatus.Mothballed>/g, obj, "Mothballed", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceRegistrationStatus.Decommissioned>([\s\S]*?)<\/cim:ResourceRegistrationStatus.Decommissioned>/g, obj, "Decommissioned", base.to_string, sub, context);

                var bucket = context.parsed.ResourceRegistrationStatus;
                if (null == bucket)
                   context.parsed.ResourceRegistrationStatus = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResourceRegistrationStatus", "Active", base.from_string, fields);
                base.export_element (obj, "ResourceRegistrationStatus", "Planned", base.from_string, fields);
                base.export_element (obj, "ResourceRegistrationStatus", "Mothballed", base.from_string, fields);
                base.export_element (obj, "ResourceRegistrationStatus", "Decommissioned", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResourceRegistrationStatus_collapse" aria-expanded="true" aria-controls="ResourceRegistrationStatus_collapse">ResourceRegistrationStatus</a>
<div id="ResourceRegistrationStatus_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#Active}}<div><b>Active</b>: {{Active}}</div>{{/Active}}
{{#Planned}}<div><b>Planned</b>: {{Planned}}</div>{{/Planned}}
{{#Mothballed}}<div><b>Mothballed</b>: {{Mothballed}}</div>{{/Mothballed}}
{{#Decommissioned}}<div><b>Decommissioned</b>: {{Decommissioned}}</div>{{/Decommissioned}}
</div>
`
                );
           }        }

        /**
         * ON
         *
         * OFF
         *
         */
        class OnOff extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.OnOff;
                if (null == bucket)
                   cim_data.OnOff = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.OnOff[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "OnOff";
                base.parse_element (/<cim:OnOff.ON>([\s\S]*?)<\/cim:OnOff.ON>/g, obj, "ON", base.to_string, sub, context);
                base.parse_element (/<cim:OnOff.OFF>([\s\S]*?)<\/cim:OnOff.OFF>/g, obj, "OFF", base.to_string, sub, context);

                var bucket = context.parsed.OnOff;
                if (null == bucket)
                   context.parsed.OnOff = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "OnOff", "ON", base.from_string, fields);
                base.export_element (obj, "OnOff", "OFF", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#OnOff_collapse" aria-expanded="true" aria-controls="OnOff_collapse">OnOff</a>
<div id="OnOff_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#ON}}<div><b>ON</b>: {{ON}}</div>{{/ON}}
{{#OFF}}<div><b>OFF</b>: {{OFF}}</div>{{/OFF}}
</div>
`
                );
           }        }

        /**
         * Role types an organisation can play with respect to a congestion revenue right.
         *
         */
        class CRRRoleType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CRRRoleType;
                if (null == bucket)
                   cim_data.CRRRoleType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CRRRoleType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CRRRoleType";
                base.parse_element (/<cim:CRRRoleType.SELLER>([\s\S]*?)<\/cim:CRRRoleType.SELLER>/g, obj, "SELLER", base.to_string, sub, context);
                base.parse_element (/<cim:CRRRoleType.BUYER>([\s\S]*?)<\/cim:CRRRoleType.BUYER>/g, obj, "BUYER", base.to_string, sub, context);
                base.parse_element (/<cim:CRRRoleType.OWNER>([\s\S]*?)<\/cim:CRRRoleType.OWNER>/g, obj, "OWNER", base.to_string, sub, context);

                var bucket = context.parsed.CRRRoleType;
                if (null == bucket)
                   context.parsed.CRRRoleType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CRRRoleType", "SELLER", base.from_string, fields);
                base.export_element (obj, "CRRRoleType", "BUYER", base.from_string, fields);
                base.export_element (obj, "CRRRoleType", "OWNER", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CRRRoleType_collapse" aria-expanded="true" aria-controls="CRRRoleType_collapse">CRRRoleType</a>
<div id="CRRRoleType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#SELLER}}<div><b>SELLER</b>: {{SELLER}}</div>{{/SELLER}}
{{#BUYER}}<div><b>BUYER</b>: {{BUYER}}</div>{{/BUYER}}
{{#OWNER}}<div><b>OWNER</b>: {{OWNER}}</div>{{/OWNER}}
</div>
`
                );
           }        }

        /**
         * Aggregated Nodes Types for example:
         * <ul>
         * <li>SYS - System Zone/Region; </li>
         * </ul>
         * <ul>
         * <li>RUC - RUC Zone; </li>
         * </ul>
         * <ul>
         * <li>LFZ - Load Forecast Zone; </li>
         * </ul>
         * <ul>
         * <li>REG - Market Energy/Ancillary Service Region; </li>
         * </ul>
         * <ul>
         * <li>AGR - Aggregate Generation Resource; </li>
         * </ul>
         * <ul>
         * <li>POD - Point of Delivery; </li>
         * </ul>
         * <ul>
         * <li>ALR - Aggregate Load Resource; </li>
         * </ul>
         * <ul>
         * <li>LTAC - Load TransmissionAccessCharge (TAC) Group;</li>
         * </ul>
         * <ul>
         * <li>ACA - Adjacent Control Area</li>
         * </ul>
         * <ul>
         * <li>ASR - Aggregated System Resource</li>
         * </ul>
         * <ul>
         * <li>ECA - Embedded Control Area</li>
         *
         * </ul>
         *
         */
        class AnodeType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AnodeType;
                if (null == bucket)
                   cim_data.AnodeType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AnodeType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AnodeType";
                base.parse_element (/<cim:AnodeType.SYS>([\s\S]*?)<\/cim:AnodeType.SYS>/g, obj, "SYS", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.RUC>([\s\S]*?)<\/cim:AnodeType.RUC>/g, obj, "RUC", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.LFZ>([\s\S]*?)<\/cim:AnodeType.LFZ>/g, obj, "LFZ", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.REG>([\s\S]*?)<\/cim:AnodeType.REG>/g, obj, "REG", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.AGR>([\s\S]*?)<\/cim:AnodeType.AGR>/g, obj, "AGR", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.POD>([\s\S]*?)<\/cim:AnodeType.POD>/g, obj, "POD", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.ALR>([\s\S]*?)<\/cim:AnodeType.ALR>/g, obj, "ALR", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.LTAC>([\s\S]*?)<\/cim:AnodeType.LTAC>/g, obj, "LTAC", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.ACA>([\s\S]*?)<\/cim:AnodeType.ACA>/g, obj, "ACA", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.ASR>([\s\S]*?)<\/cim:AnodeType.ASR>/g, obj, "ASR", base.to_string, sub, context);
                base.parse_element (/<cim:AnodeType.ECA>([\s\S]*?)<\/cim:AnodeType.ECA>/g, obj, "ECA", base.to_string, sub, context);

                var bucket = context.parsed.AnodeType;
                if (null == bucket)
                   context.parsed.AnodeType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AnodeType", "SYS", base.from_string, fields);
                base.export_element (obj, "AnodeType", "RUC", base.from_string, fields);
                base.export_element (obj, "AnodeType", "LFZ", base.from_string, fields);
                base.export_element (obj, "AnodeType", "REG", base.from_string, fields);
                base.export_element (obj, "AnodeType", "AGR", base.from_string, fields);
                base.export_element (obj, "AnodeType", "POD", base.from_string, fields);
                base.export_element (obj, "AnodeType", "ALR", base.from_string, fields);
                base.export_element (obj, "AnodeType", "LTAC", base.from_string, fields);
                base.export_element (obj, "AnodeType", "ACA", base.from_string, fields);
                base.export_element (obj, "AnodeType", "ASR", base.from_string, fields);
                base.export_element (obj, "AnodeType", "ECA", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AnodeType_collapse" aria-expanded="true" aria-controls="AnodeType_collapse">AnodeType</a>
<div id="AnodeType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#SYS}}<div><b>SYS</b>: {{SYS}}</div>{{/SYS}}
{{#RUC}}<div><b>RUC</b>: {{RUC}}</div>{{/RUC}}
{{#LFZ}}<div><b>LFZ</b>: {{LFZ}}</div>{{/LFZ}}
{{#REG}}<div><b>REG</b>: {{REG}}</div>{{/REG}}
{{#AGR}}<div><b>AGR</b>: {{AGR}}</div>{{/AGR}}
{{#POD}}<div><b>POD</b>: {{POD}}</div>{{/POD}}
{{#ALR}}<div><b>ALR</b>: {{ALR}}</div>{{/ALR}}
{{#LTAC}}<div><b>LTAC</b>: {{LTAC}}</div>{{/LTAC}}
{{#ACA}}<div><b>ACA</b>: {{ACA}}</div>{{/ACA}}
{{#ASR}}<div><b>ASR</b>: {{ASR}}</div>{{/ASR}}
{{#ECA}}<div><b>ECA</b>: {{ECA}}</div>{{/ECA}}
</div>
`
                );
           }        }

        /**
         * Ramp rate curve type.
         *
         */
        class RampRateType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.RampRateType;
                if (null == bucket)
                   cim_data.RampRateType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.RampRateType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "RampRateType";
                base.parse_element (/<cim:RampRateType.OP>([\s\S]*?)<\/cim:RampRateType.OP>/g, obj, "OP", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateType.REG>([\s\S]*?)<\/cim:RampRateType.REG>/g, obj, "REG", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateType.OP_RES>([\s\S]*?)<\/cim:RampRateType.OP_RES>/g, obj, "OP_RES", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateType.LD_DROP>([\s\S]*?)<\/cim:RampRateType.LD_DROP>/g, obj, "LD_DROP", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateType.LD_PICKUP>([\s\S]*?)<\/cim:RampRateType.LD_PICKUP>/g, obj, "LD_PICKUP", base.to_string, sub, context);
                base.parse_element (/<cim:RampRateType.INTERTIE>([\s\S]*?)<\/cim:RampRateType.INTERTIE>/g, obj, "INTERTIE", base.to_string, sub, context);

                var bucket = context.parsed.RampRateType;
                if (null == bucket)
                   context.parsed.RampRateType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "RampRateType", "OP", base.from_string, fields);
                base.export_element (obj, "RampRateType", "REG", base.from_string, fields);
                base.export_element (obj, "RampRateType", "OP_RES", base.from_string, fields);
                base.export_element (obj, "RampRateType", "LD_DROP", base.from_string, fields);
                base.export_element (obj, "RampRateType", "LD_PICKUP", base.from_string, fields);
                base.export_element (obj, "RampRateType", "INTERTIE", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#RampRateType_collapse" aria-expanded="true" aria-controls="RampRateType_collapse">RampRateType</a>
<div id="RampRateType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#OP}}<div><b>OP</b>: {{OP}}</div>{{/OP}}
{{#REG}}<div><b>REG</b>: {{REG}}</div>{{/REG}}
{{#OP_RES}}<div><b>OP_RES</b>: {{OP_RES}}</div>{{/OP_RES}}
{{#LD_DROP}}<div><b>LD_DROP</b>: {{LD_DROP}}</div>{{/LD_DROP}}
{{#LD_PICKUP}}<div><b>LD_PICKUP</b>: {{LD_PICKUP}}</div>{{/LD_PICKUP}}
{{#INTERTIE}}<div><b>INTERTIE</b>: {{INTERTIE}}</div>{{/INTERTIE}}
</div>
`
                );
           }        }

        /**
         * For example:
         * WHOLESALE
         * RETAIL
         *
         * BOTH
         *
         */
        class EnergyPriceIndexType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.EnergyPriceIndexType;
                if (null == bucket)
                   cim_data.EnergyPriceIndexType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.EnergyPriceIndexType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "EnergyPriceIndexType";
                base.parse_element (/<cim:EnergyPriceIndexType.WHOLESALE>([\s\S]*?)<\/cim:EnergyPriceIndexType.WHOLESALE>/g, obj, "WHOLESALE", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyPriceIndexType.RETAIL>([\s\S]*?)<\/cim:EnergyPriceIndexType.RETAIL>/g, obj, "RETAIL", base.to_string, sub, context);
                base.parse_element (/<cim:EnergyPriceIndexType.BOTH>([\s\S]*?)<\/cim:EnergyPriceIndexType.BOTH>/g, obj, "BOTH", base.to_string, sub, context);

                var bucket = context.parsed.EnergyPriceIndexType;
                if (null == bucket)
                   context.parsed.EnergyPriceIndexType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "EnergyPriceIndexType", "WHOLESALE", base.from_string, fields);
                base.export_element (obj, "EnergyPriceIndexType", "RETAIL", base.from_string, fields);
                base.export_element (obj, "EnergyPriceIndexType", "BOTH", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#EnergyPriceIndexType_collapse" aria-expanded="true" aria-controls="EnergyPriceIndexType_collapse">EnergyPriceIndexType</a>
<div id="EnergyPriceIndexType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#WHOLESALE}}<div><b>WHOLESALE</b>: {{WHOLESALE}}</div>{{/WHOLESALE}}
{{#RETAIL}}<div><b>RETAIL</b>: {{RETAIL}}</div>{{/RETAIL}}
{{#BOTH}}<div><b>BOTH</b>: {{BOTH}}</div>{{/BOTH}}
</div>
`
                );
           }        }

        /**
         * Aggregate Node Types for example:
         * AG -  Aggregated Generation
         * CPZ -  Custom Price Zone
         * DPZ -  Default Price Zone
         * LAP - Load Aggregation Point
         * TH -  Trading  Hub
         * SYS - System Zone
         * CA - Control Area
         * 
         * GA - generic aggregation
         * EHV - 500 kV
         * GH - generic hub
         * ZN - zone
         * INT - Interface
         *
         * BUS - Bus
         *
         */
        class ApnodeType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ApnodeType;
                if (null == bucket)
                   cim_data.ApnodeType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ApnodeType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ApnodeType";
                base.parse_element (/<cim:ApnodeType.AG>([\s\S]*?)<\/cim:ApnodeType.AG>/g, obj, "AG", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.CPZ>([\s\S]*?)<\/cim:ApnodeType.CPZ>/g, obj, "CPZ", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.DPZ>([\s\S]*?)<\/cim:ApnodeType.DPZ>/g, obj, "DPZ", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.TH>([\s\S]*?)<\/cim:ApnodeType.TH>/g, obj, "TH", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.SYS>([\s\S]*?)<\/cim:ApnodeType.SYS>/g, obj, "SYS", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.CA>([\s\S]*?)<\/cim:ApnodeType.CA>/g, obj, "CA", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.DCA>([\s\S]*?)<\/cim:ApnodeType.DCA>/g, obj, "DCA", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.GA>([\s\S]*?)<\/cim:ApnodeType.GA>/g, obj, "GA", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.GH>([\s\S]*?)<\/cim:ApnodeType.GH>/g, obj, "GH", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.EHV>([\s\S]*?)<\/cim:ApnodeType.EHV>/g, obj, "EHV", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.ZN>([\s\S]*?)<\/cim:ApnodeType.ZN>/g, obj, "ZN", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.INT>([\s\S]*?)<\/cim:ApnodeType.INT>/g, obj, "INT", base.to_string, sub, context);
                base.parse_element (/<cim:ApnodeType.BUS>([\s\S]*?)<\/cim:ApnodeType.BUS>/g, obj, "BUS", base.to_string, sub, context);

                var bucket = context.parsed.ApnodeType;
                if (null == bucket)
                   context.parsed.ApnodeType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ApnodeType", "AG", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "CPZ", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "DPZ", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "TH", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "SYS", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "CA", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "DCA", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "GA", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "GH", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "EHV", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "ZN", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "INT", base.from_string, fields);
                base.export_element (obj, "ApnodeType", "BUS", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ApnodeType_collapse" aria-expanded="true" aria-controls="ApnodeType_collapse">ApnodeType</a>
<div id="ApnodeType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#AG}}<div><b>AG</b>: {{AG}}</div>{{/AG}}
{{#CPZ}}<div><b>CPZ</b>: {{CPZ}}</div>{{/CPZ}}
{{#DPZ}}<div><b>DPZ</b>: {{DPZ}}</div>{{/DPZ}}
{{#TH}}<div><b>TH</b>: {{TH}}</div>{{/TH}}
{{#SYS}}<div><b>SYS</b>: {{SYS}}</div>{{/SYS}}
{{#CA}}<div><b>CA</b>: {{CA}}</div>{{/CA}}
{{#DCA}}<div><b>DCA</b>: {{DCA}}</div>{{/DCA}}
{{#GA}}<div><b>GA</b>: {{GA}}</div>{{/GA}}
{{#GH}}<div><b>GH</b>: {{GH}}</div>{{/GH}}
{{#EHV}}<div><b>EHV</b>: {{EHV}}</div>{{/EHV}}
{{#ZN}}<div><b>ZN</b>: {{ZN}}</div>{{/ZN}}
{{#INT}}<div><b>INT</b>: {{INT}}</div>{{/INT}}
{{#BUS}}<div><b>BUS</b>: {{BUS}}</div>{{/BUS}}
</div>
`
                );
           }        }

        /**
         * For example:
         * 0 - Fixed ramp rate independent of rate function unit MW output
         * 1 - Static ramp rates as a function of unit MW output only
         *
         * 2 - Dynamic ramp rates as a function of unit MW output and ramping time
         *
         */
        class RampCurveType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.RampCurveType;
                if (null == bucket)
                   cim_data.RampCurveType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.RampCurveType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "RampCurveType";
                base.parse_element (/<cim:RampCurveType.0>([\s\S]*?)<\/cim:RampCurveType.0>/g, obj, "0", base.to_string, sub, context);
                base.parse_element (/<cim:RampCurveType.1>([\s\S]*?)<\/cim:RampCurveType.1>/g, obj, "1", base.to_string, sub, context);
                base.parse_element (/<cim:RampCurveType.2>([\s\S]*?)<\/cim:RampCurveType.2>/g, obj, "2", base.to_string, sub, context);

                var bucket = context.parsed.RampCurveType;
                if (null == bucket)
                   context.parsed.RampCurveType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "RampCurveType", "0", base.from_string, fields);
                base.export_element (obj, "RampCurveType", "1", base.from_string, fields);
                base.export_element (obj, "RampCurveType", "2", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#RampCurveType_collapse" aria-expanded="true" aria-controls="RampCurveType_collapse">RampCurveType</a>
<div id="RampCurveType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#0}}<div><b>0</b>: {{0}}</div>{{/0}}
{{#1}}<div><b>1</b>: {{1}}</div>{{/1}}
{{#2}}<div><b>2</b>: {{2}}</div>{{/2}}
</div>
`
                );
           }        }

        /**
         * Bid self schedule type has two types as the required output of requirements and qualified pre-dispatch.
         *
         */
        class BidTypeRMR extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BidTypeRMR;
                if (null == bucket)
                   cim_data.BidTypeRMR = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BidTypeRMR[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BidTypeRMR";
                base.parse_element (/<cim:BidTypeRMR.REQUIREMENTS>([\s\S]*?)<\/cim:BidTypeRMR.REQUIREMENTS>/g, obj, "REQUIREMENTS", base.to_string, sub, context);
                base.parse_element (/<cim:BidTypeRMR.QUALIFIED_PREDISPATCH>([\s\S]*?)<\/cim:BidTypeRMR.QUALIFIED_PREDISPATCH>/g, obj, "QUALIFIED_PREDISPATCH", base.to_string, sub, context);

                var bucket = context.parsed.BidTypeRMR;
                if (null == bucket)
                   context.parsed.BidTypeRMR = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BidTypeRMR", "REQUIREMENTS", base.from_string, fields);
                base.export_element (obj, "BidTypeRMR", "QUALIFIED_PREDISPATCH", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BidTypeRMR_collapse" aria-expanded="true" aria-controls="BidTypeRMR_collapse">BidTypeRMR</a>
<div id="BidTypeRMR_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#REQUIREMENTS}}<div><b>REQUIREMENTS</b>: {{REQUIREMENTS}}</div>{{/REQUIREMENTS}}
{{#QUALIFIED_PREDISPATCH}}<div><b>QUALIFIED_PREDISPATCH</b>: {{QUALIFIED_PREDISPATCH}}</div>{{/QUALIFIED_PREDISPATCH}}
</div>
`
                );
           }        }

        /**
         * For example:
         * NON_RESPONSE
         * ACCEPT
         * DECLINE
         *
         * PARTIAL.
         *
         */
        class DispatchResponseType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.DispatchResponseType;
                if (null == bucket)
                   cim_data.DispatchResponseType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.DispatchResponseType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "DispatchResponseType";
                base.parse_element (/<cim:DispatchResponseType.NON_RESPONSE>([\s\S]*?)<\/cim:DispatchResponseType.NON_RESPONSE>/g, obj, "NON_RESPONSE", base.to_string, sub, context);
                base.parse_element (/<cim:DispatchResponseType.ACCEPT>([\s\S]*?)<\/cim:DispatchResponseType.ACCEPT>/g, obj, "ACCEPT", base.to_string, sub, context);
                base.parse_element (/<cim:DispatchResponseType.DECLINE>([\s\S]*?)<\/cim:DispatchResponseType.DECLINE>/g, obj, "DECLINE", base.to_string, sub, context);
                base.parse_element (/<cim:DispatchResponseType.PARTIAL>([\s\S]*?)<\/cim:DispatchResponseType.PARTIAL>/g, obj, "PARTIAL", base.to_string, sub, context);

                var bucket = context.parsed.DispatchResponseType;
                if (null == bucket)
                   context.parsed.DispatchResponseType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "DispatchResponseType", "NON_RESPONSE", base.from_string, fields);
                base.export_element (obj, "DispatchResponseType", "ACCEPT", base.from_string, fields);
                base.export_element (obj, "DispatchResponseType", "DECLINE", base.from_string, fields);
                base.export_element (obj, "DispatchResponseType", "PARTIAL", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#DispatchResponseType_collapse" aria-expanded="true" aria-controls="DispatchResponseType_collapse">DispatchResponseType</a>
<div id="DispatchResponseType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#NON_RESPONSE}}<div><b>NON_RESPONSE</b>: {{NON_RESPONSE}}</div>{{/NON_RESPONSE}}
{{#ACCEPT}}<div><b>ACCEPT</b>: {{ACCEPT}}</div>{{/ACCEPT}}
{{#DECLINE}}<div><b>DECLINE</b>: {{DECLINE}}</div>{{/DECLINE}}
{{#PARTIAL}}<div><b>PARTIAL</b>: {{PARTIAL}}</div>{{/PARTIAL}}
</div>
`
                );
           }        }

        /**
         * Kind of invoice line item.
         *
         */
        class MktInvoiceLineItemKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MktInvoiceLineItemKind;
                if (null == bucket)
                   cim_data.MktInvoiceLineItemKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MktInvoiceLineItemKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MktInvoiceLineItemKind";
                base.parse_element (/<cim:MktInvoiceLineItemKind.initial>([\s\S]*?)<\/cim:MktInvoiceLineItemKind.initial>/g, obj, "initial", base.to_string, sub, context);
                base.parse_element (/<cim:MktInvoiceLineItemKind.recalculation>([\s\S]*?)<\/cim:MktInvoiceLineItemKind.recalculation>/g, obj, "recalculation", base.to_string, sub, context);
                base.parse_element (/<cim:MktInvoiceLineItemKind.other>([\s\S]*?)<\/cim:MktInvoiceLineItemKind.other>/g, obj, "other", base.to_string, sub, context);

                var bucket = context.parsed.MktInvoiceLineItemKind;
                if (null == bucket)
                   context.parsed.MktInvoiceLineItemKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MktInvoiceLineItemKind", "initial", base.from_string, fields);
                base.export_element (obj, "MktInvoiceLineItemKind", "recalculation", base.from_string, fields);
                base.export_element (obj, "MktInvoiceLineItemKind", "other", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MktInvoiceLineItemKind_collapse" aria-expanded="true" aria-controls="MktInvoiceLineItemKind_collapse">MktInvoiceLineItemKind</a>
<div id="MktInvoiceLineItemKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#initial}}<div><b>initial</b>: {{initial}}</div>{{/initial}}
{{#recalculation}}<div><b>recalculation</b>: {{recalculation}}</div>{{/recalculation}}
{{#other}}<div><b>other</b>: {{other}}</div>{{/other}}
</div>
`
                );
           }        }

        /**
         * Binding constraint results limit type, For example:
         * MAXIMUM
         *
         * MINIMUM
         *
         */
        class ConstraintLimitType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ConstraintLimitType;
                if (null == bucket)
                   cim_data.ConstraintLimitType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ConstraintLimitType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ConstraintLimitType";
                base.parse_element (/<cim:ConstraintLimitType.MAXIMUM>([\s\S]*?)<\/cim:ConstraintLimitType.MAXIMUM>/g, obj, "MAXIMUM", base.to_string, sub, context);
                base.parse_element (/<cim:ConstraintLimitType.MINIMUM>([\s\S]*?)<\/cim:ConstraintLimitType.MINIMUM>/g, obj, "MINIMUM", base.to_string, sub, context);

                var bucket = context.parsed.ConstraintLimitType;
                if (null == bucket)
                   context.parsed.ConstraintLimitType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ConstraintLimitType", "MAXIMUM", base.from_string, fields);
                base.export_element (obj, "ConstraintLimitType", "MINIMUM", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ConstraintLimitType_collapse" aria-expanded="true" aria-controls="ConstraintLimitType_collapse">ConstraintLimitType</a>
<div id="ConstraintLimitType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#MAXIMUM}}<div><b>MAXIMUM</b>: {{MAXIMUM}}</div>{{/MAXIMUM}}
{{#MINIMUM}}<div><b>MINIMUM</b>: {{MINIMUM}}</div>{{/MINIMUM}}
</div>
`
                );
           }        }

        /**
         * Resource capacity type.
         *
         */
        class ResourceCapacityType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResourceCapacityType;
                if (null == bucket)
                   cim_data.ResourceCapacityType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResourceCapacityType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResourceCapacityType";
                base.parse_element (/<cim:ResourceCapacityType.RU>([\s\S]*?)<\/cim:ResourceCapacityType.RU>/g, obj, "RU", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.RD>([\s\S]*?)<\/cim:ResourceCapacityType.RD>/g, obj, "RD", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.SR>([\s\S]*?)<\/cim:ResourceCapacityType.SR>/g, obj, "SR", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.NR>([\s\S]*?)<\/cim:ResourceCapacityType.NR>/g, obj, "NR", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.MO>([\s\S]*?)<\/cim:ResourceCapacityType.MO>/g, obj, "MO", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.FO>([\s\S]*?)<\/cim:ResourceCapacityType.FO>/g, obj, "FO", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.RA>([\s\S]*?)<\/cim:ResourceCapacityType.RA>/g, obj, "RA", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceCapacityType.RMR>([\s\S]*?)<\/cim:ResourceCapacityType.RMR>/g, obj, "RMR", base.to_string, sub, context);

                var bucket = context.parsed.ResourceCapacityType;
                if (null == bucket)
                   context.parsed.ResourceCapacityType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResourceCapacityType", "RU", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "RD", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "SR", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "NR", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "MO", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "FO", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "RA", base.from_string, fields);
                base.export_element (obj, "ResourceCapacityType", "RMR", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResourceCapacityType_collapse" aria-expanded="true" aria-controls="ResourceCapacityType_collapse">ResourceCapacityType</a>
<div id="ResourceCapacityType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#RU}}<div><b>RU</b>: {{RU}}</div>{{/RU}}
{{#RD}}<div><b>RD</b>: {{RD}}</div>{{/RD}}
{{#SR}}<div><b>SR</b>: {{SR}}</div>{{/SR}}
{{#NR}}<div><b>NR</b>: {{NR}}</div>{{/NR}}
{{#MO}}<div><b>MO</b>: {{MO}}</div>{{/MO}}
{{#FO}}<div><b>FO</b>: {{FO}}</div>{{/FO}}
{{#RA}}<div><b>RA</b>: {{RA}}</div>{{/RA}}
{{#RMR}}<div><b>RMR</b>: {{RMR}}</div>{{/RMR}}
</div>
`
                );
           }        }

        /**
         * Circuit Breaker Status (closed or open) of the circuit breaker.
         *
         */
        class SwitchStatusType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.SwitchStatusType;
                if (null == bucket)
                   cim_data.SwitchStatusType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.SwitchStatusType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "SwitchStatusType";
                base.parse_element (/<cim:SwitchStatusType.Closed>([\s\S]*?)<\/cim:SwitchStatusType.Closed>/g, obj, "Closed", base.to_string, sub, context);
                base.parse_element (/<cim:SwitchStatusType.Open>([\s\S]*?)<\/cim:SwitchStatusType.Open>/g, obj, "Open", base.to_string, sub, context);

                var bucket = context.parsed.SwitchStatusType;
                if (null == bucket)
                   context.parsed.SwitchStatusType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "SwitchStatusType", "Closed", base.from_string, fields);
                base.export_element (obj, "SwitchStatusType", "Open", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#SwitchStatusType_collapse" aria-expanded="true" aria-controls="SwitchStatusType_collapse">SwitchStatusType</a>
<div id="SwitchStatusType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#Closed}}<div><b>Closed</b>: {{Closed}}</div>{{/Closed}}
{{#Open}}<div><b>Open</b>: {{Open}}</div>{{/Open}}
</div>
`
                );
           }        }

        /**
         * Transmission Contract Right type -for example:
         *
         * individual or chain of contract rights
         *
         */
        class TRType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TRType;
                if (null == bucket)
                   cim_data.TRType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TRType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "TRType";
                base.parse_element (/<cim:TRType.CHAIN>([\s\S]*?)<\/cim:TRType.CHAIN>/g, obj, "CHAIN", base.to_string, sub, context);
                base.parse_element (/<cim:TRType.INDIVIDUAL>([\s\S]*?)<\/cim:TRType.INDIVIDUAL>/g, obj, "INDIVIDUAL", base.to_string, sub, context);

                var bucket = context.parsed.TRType;
                if (null == bucket)
                   context.parsed.TRType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "TRType", "CHAIN", base.from_string, fields);
                base.export_element (obj, "TRType", "INDIVIDUAL", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TRType_collapse" aria-expanded="true" aria-controls="TRType_collapse">TRType</a>
<div id="TRType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#CHAIN}}<div><b>CHAIN</b>: {{CHAIN}}</div>{{/CHAIN}}
{{#INDIVIDUAL}}<div><b>INDIVIDUAL</b>: {{INDIVIDUAL}}</div>{{/INDIVIDUAL}}
</div>
`
                );
           }        }

        /**
         * Congestion Revenue Right hedge type
         *
         */
        class CRRHedgeType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CRRHedgeType;
                if (null == bucket)
                   cim_data.CRRHedgeType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CRRHedgeType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CRRHedgeType";
                base.parse_element (/<cim:CRRHedgeType.OBLIGATION>([\s\S]*?)<\/cim:CRRHedgeType.OBLIGATION>/g, obj, "OBLIGATION", base.to_string, sub, context);
                base.parse_element (/<cim:CRRHedgeType.OPTION>([\s\S]*?)<\/cim:CRRHedgeType.OPTION>/g, obj, "OPTION", base.to_string, sub, context);

                var bucket = context.parsed.CRRHedgeType;
                if (null == bucket)
                   context.parsed.CRRHedgeType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CRRHedgeType", "OBLIGATION", base.from_string, fields);
                base.export_element (obj, "CRRHedgeType", "OPTION", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CRRHedgeType_collapse" aria-expanded="true" aria-controls="CRRHedgeType_collapse">CRRHedgeType</a>
<div id="CRRHedgeType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#OBLIGATION}}<div><b>OBLIGATION</b>: {{OBLIGATION}}</div>{{/OBLIGATION}}
{{#OPTION}}<div><b>OPTION</b>: {{OPTION}}</div>{{/OPTION}}
</div>
`
                );
           }        }

        /**
         * MPM Purpose Flag, for example:
         * 
         * Nature of threshold data:
         * 'M' - Mitigation threshold
         *
         * 'R' - Reporting threshold
         *
         */
        class PurposeFlagType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PurposeFlagType;
                if (null == bucket)
                   cim_data.PurposeFlagType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PurposeFlagType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PurposeFlagType";
                base.parse_element (/<cim:PurposeFlagType.M>([\s\S]*?)<\/cim:PurposeFlagType.M>/g, obj, "M", base.to_string, sub, context);
                base.parse_element (/<cim:PurposeFlagType.R>([\s\S]*?)<\/cim:PurposeFlagType.R>/g, obj, "R", base.to_string, sub, context);

                var bucket = context.parsed.PurposeFlagType;
                if (null == bucket)
                   context.parsed.PurposeFlagType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PurposeFlagType", "M", base.from_string, fields);
                base.export_element (obj, "PurposeFlagType", "R", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PurposeFlagType_collapse" aria-expanded="true" aria-controls="PurposeFlagType_collapse">PurposeFlagType</a>
<div id="PurposeFlagType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#M}}<div><b>M</b>: {{M}}</div>{{/M}}
{{#R}}<div><b>R</b>: {{R}}</div>{{/R}}
</div>
`
                );
           }        }

        /**
         * Constraint Ramp type
         *
         */
        class ConstraintRampType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ConstraintRampType;
                if (null == bucket)
                   cim_data.ConstraintRampType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ConstraintRampType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ConstraintRampType";
                base.parse_element (/<cim:ConstraintRampType.FAST>([\s\S]*?)<\/cim:ConstraintRampType.FAST>/g, obj, "FAST", base.to_string, sub, context);
                base.parse_element (/<cim:ConstraintRampType.SLOW>([\s\S]*?)<\/cim:ConstraintRampType.SLOW>/g, obj, "SLOW", base.to_string, sub, context);

                var bucket = context.parsed.ConstraintRampType;
                if (null == bucket)
                   context.parsed.ConstraintRampType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ConstraintRampType", "FAST", base.from_string, fields);
                base.export_element (obj, "ConstraintRampType", "SLOW", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ConstraintRampType_collapse" aria-expanded="true" aria-controls="ConstraintRampType_collapse">ConstraintRampType</a>
<div id="ConstraintRampType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#FAST}}<div><b>FAST</b>: {{FAST}}</div>{{/FAST}}
{{#SLOW}}<div><b>SLOW</b>: {{SLOW}}</div>{{/SLOW}}
</div>
`
                );
           }        }

        /**
         * Type of the CRR, from the possible type definitions in the CRR System (e.g. 'LSE', 'ETC').
         *
         */
        class CRRSegmentType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CRRSegmentType;
                if (null == bucket)
                   cim_data.CRRSegmentType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CRRSegmentType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CRRSegmentType";
                base.parse_element (/<cim:CRRSegmentType.AUC>([\s\S]*?)<\/cim:CRRSegmentType.AUC>/g, obj, "AUC", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.CAP>([\s\S]*?)<\/cim:CRRSegmentType.CAP>/g, obj, "CAP", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.CF>([\s\S]*?)<\/cim:CRRSegmentType.CF>/g, obj, "CF", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.CVR>([\s\S]*?)<\/cim:CRRSegmentType.CVR>/g, obj, "CVR", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.ETC>([\s\S]*?)<\/cim:CRRSegmentType.ETC>/g, obj, "ETC", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.LSE>([\s\S]*?)<\/cim:CRRSegmentType.LSE>/g, obj, "LSE", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.MT>([\s\S]*?)<\/cim:CRRSegmentType.MT>/g, obj, "MT", base.to_string, sub, context);
                base.parse_element (/<cim:CRRSegmentType.TOR>([\s\S]*?)<\/cim:CRRSegmentType.TOR>/g, obj, "TOR", base.to_string, sub, context);

                var bucket = context.parsed.CRRSegmentType;
                if (null == bucket)
                   context.parsed.CRRSegmentType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CRRSegmentType", "AUC", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "CAP", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "CF", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "CVR", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "ETC", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "LSE", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "MT", base.from_string, fields);
                base.export_element (obj, "CRRSegmentType", "TOR", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CRRSegmentType_collapse" aria-expanded="true" aria-controls="CRRSegmentType_collapse">CRRSegmentType</a>
<div id="CRRSegmentType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#AUC}}<div><b>AUC</b>: {{AUC}}</div>{{/AUC}}
{{#CAP}}<div><b>CAP</b>: {{CAP}}</div>{{/CAP}}
{{#CF}}<div><b>CF</b>: {{CF}}</div>{{/CF}}
{{#CVR}}<div><b>CVR</b>: {{CVR}}</div>{{/CVR}}
{{#ETC}}<div><b>ETC</b>: {{ETC}}</div>{{/ETC}}
{{#LSE}}<div><b>LSE</b>: {{LSE}}</div>{{/LSE}}
{{#MT}}<div><b>MT</b>: {{MT}}</div>{{/MT}}
{{#TOR}}<div><b>TOR</b>: {{TOR}}</div>{{/TOR}}
</div>
`
                );
           }        }

        /**
         * Types used for resource AS qualifications
         *
         */
        class ResourceAncillaryServiceType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ResourceAncillaryServiceType;
                if (null == bucket)
                   cim_data.ResourceAncillaryServiceType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ResourceAncillaryServiceType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ResourceAncillaryServiceType";
                base.parse_element (/<cim:ResourceAncillaryServiceType.REGUP>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.REGUP>/g, obj, "REGUP", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.REGDN>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.REGDN>/g, obj, "REGDN", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.RRSPIN>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.RRSPIN>/g, obj, "RRSPIN", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.NONSPIN>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.NONSPIN>/g, obj, "NONSPIN", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.RMR>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.RMR>/g, obj, "RMR", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.BLACKSTART>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.BLACKSTART>/g, obj, "BLACKSTART", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.DSR>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.DSR>/g, obj, "DSR", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.SYNCCOND>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.SYNCCOND>/g, obj, "SYNCCOND", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.PIRP>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.PIRP>/g, obj, "PIRP", base.to_string, sub, context);
                base.parse_element (/<cim:ResourceAncillaryServiceType.RUC>([\s\S]*?)<\/cim:ResourceAncillaryServiceType.RUC>/g, obj, "RUC", base.to_string, sub, context);

                var bucket = context.parsed.ResourceAncillaryServiceType;
                if (null == bucket)
                   context.parsed.ResourceAncillaryServiceType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ResourceAncillaryServiceType", "REGUP", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "REGDN", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "RRSPIN", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "NONSPIN", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "RMR", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "BLACKSTART", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "DSR", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "SYNCCOND", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "PIRP", base.from_string, fields);
                base.export_element (obj, "ResourceAncillaryServiceType", "RUC", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ResourceAncillaryServiceType_collapse" aria-expanded="true" aria-controls="ResourceAncillaryServiceType_collapse">ResourceAncillaryServiceType</a>
<div id="ResourceAncillaryServiceType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#REGUP}}<div><b>REGUP</b>: {{REGUP}}</div>{{/REGUP}}
{{#REGDN}}<div><b>REGDN</b>: {{REGDN}}</div>{{/REGDN}}
{{#RRSPIN}}<div><b>RRSPIN</b>: {{RRSPIN}}</div>{{/RRSPIN}}
{{#NONSPIN}}<div><b>NONSPIN</b>: {{NONSPIN}}</div>{{/NONSPIN}}
{{#RMR}}<div><b>RMR</b>: {{RMR}}</div>{{/RMR}}
{{#BLACKSTART}}<div><b>BLACKSTART</b>: {{BLACKSTART}}</div>{{/BLACKSTART}}
{{#DSR}}<div><b>DSR</b>: {{DSR}}</div>{{/DSR}}
{{#SYNCCOND}}<div><b>SYNCCOND</b>: {{SYNCCOND}}</div>{{/SYNCCOND}}
{{#PIRP}}<div><b>PIRP</b>: {{PIRP}}</div>{{/PIRP}}
{{#RUC}}<div><b>RUC</b>: {{RUC}}</div>{{/RUC}}
</div>
`
                );
           }        }

        /**
         * For example:
         * Initial
         *
         * Final
         *
         */
        class BidMitigationType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BidMitigationType;
                if (null == bucket)
                   cim_data.BidMitigationType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BidMitigationType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "BidMitigationType";
                base.parse_element (/<cim:BidMitigationType.I>([\s\S]*?)<\/cim:BidMitigationType.I>/g, obj, "I", base.to_string, sub, context);
                base.parse_element (/<cim:BidMitigationType.F>([\s\S]*?)<\/cim:BidMitigationType.F>/g, obj, "F", base.to_string, sub, context);

                var bucket = context.parsed.BidMitigationType;
                if (null == bucket)
                   context.parsed.BidMitigationType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "BidMitigationType", "I", base.from_string, fields);
                base.export_element (obj, "BidMitigationType", "F", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BidMitigationType_collapse" aria-expanded="true" aria-controls="BidMitigationType_collapse">BidMitigationType</a>
<div id="BidMitigationType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#I}}<div><b>I</b>: {{I}}</div>{{/I}}
{{#F}}<div><b>F</b>: {{F}}</div>{{/F}}
</div>
`
                );
           }        }

        /**
         * For example:
         *
         * Operating Reserve, Regulation, Contingency
         *
         */
        class ReserveRequirementType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ReserveRequirementType;
                if (null == bucket)
                   cim_data.ReserveRequirementType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ReserveRequirementType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ReserveRequirementType";
                base.parse_element (/<cim:ReserveRequirementType.OPRSV>([\s\S]*?)<\/cim:ReserveRequirementType.OPRSV>/g, obj, "OPRSV", base.to_string, sub, context);
                base.parse_element (/<cim:ReserveRequirementType.CONT>([\s\S]*?)<\/cim:ReserveRequirementType.CONT>/g, obj, "CONT", base.to_string, sub, context);
                base.parse_element (/<cim:ReserveRequirementType.REG>([\s\S]*?)<\/cim:ReserveRequirementType.REG>/g, obj, "REG", base.to_string, sub, context);

                var bucket = context.parsed.ReserveRequirementType;
                if (null == bucket)
                   context.parsed.ReserveRequirementType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ReserveRequirementType", "OPRSV", base.from_string, fields);
                base.export_element (obj, "ReserveRequirementType", "CONT", base.from_string, fields);
                base.export_element (obj, "ReserveRequirementType", "REG", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ReserveRequirementType_collapse" aria-expanded="true" aria-controls="ReserveRequirementType_collapse">ReserveRequirementType</a>
<div id="ReserveRequirementType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#OPRSV}}<div><b>OPRSV</b>: {{OPRSV}}</div>{{/OPRSV}}
{{#CONT}}<div><b>CONT</b>: {{CONT}}</div>{{/CONT}}
{{#REG}}<div><b>REG</b>: {{REG}}</div>{{/REG}}
</div>
`
                );
           }        }

        /**
         * Valid values, for example:
         * INS - Instruction from RTM
         *
         * ACT - Actual instruction after the fact
         *
         */
        class MQSInstructionSource extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.MQSInstructionSource;
                if (null == bucket)
                   cim_data.MQSInstructionSource = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.MQSInstructionSource[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "MQSInstructionSource";
                base.parse_element (/<cim:MQSInstructionSource.INS>([\s\S]*?)<\/cim:MQSInstructionSource.INS>/g, obj, "INS", base.to_string, sub, context);
                base.parse_element (/<cim:MQSInstructionSource.ACT>([\s\S]*?)<\/cim:MQSInstructionSource.ACT>/g, obj, "ACT", base.to_string, sub, context);

                var bucket = context.parsed.MQSInstructionSource;
                if (null == bucket)
                   context.parsed.MQSInstructionSource = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "MQSInstructionSource", "INS", base.from_string, fields);
                base.export_element (obj, "MQSInstructionSource", "ACT", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#MQSInstructionSource_collapse" aria-expanded="true" aria-controls="MQSInstructionSource_collapse">MQSInstructionSource</a>
<div id="MQSInstructionSource_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#INS}}<div><b>INS</b>: {{INS}}</div>{{/INS}}
{{#ACT}}<div><b>ACT</b>: {{ACT}}</div>{{/ACT}}
</div>
`
                );
           }        }

        /**
         * Area's present control mode
         *
         */
        class AreaControlMode extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.AreaControlMode;
                if (null == bucket)
                   cim_data.AreaControlMode = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.AreaControlMode[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "AreaControlMode";
                base.parse_element (/<cim:AreaControlMode.CF>([\s\S]*?)<\/cim:AreaControlMode.CF>/g, obj, "CF", base.to_string, sub, context);
                base.parse_element (/<cim:AreaControlMode.CTL>([\s\S]*?)<\/cim:AreaControlMode.CTL>/g, obj, "CTL", base.to_string, sub, context);
                base.parse_element (/<cim:AreaControlMode.TLB>([\s\S]*?)<\/cim:AreaControlMode.TLB>/g, obj, "TLB", base.to_string, sub, context);
                base.parse_element (/<cim:AreaControlMode.OFF>([\s\S]*?)<\/cim:AreaControlMode.OFF>/g, obj, "OFF", base.to_string, sub, context);

                var bucket = context.parsed.AreaControlMode;
                if (null == bucket)
                   context.parsed.AreaControlMode = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "AreaControlMode", "CF", base.from_string, fields);
                base.export_element (obj, "AreaControlMode", "CTL", base.from_string, fields);
                base.export_element (obj, "AreaControlMode", "TLB", base.from_string, fields);
                base.export_element (obj, "AreaControlMode", "OFF", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#AreaControlMode_collapse" aria-expanded="true" aria-controls="AreaControlMode_collapse">AreaControlMode</a>
<div id="AreaControlMode_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#CF}}<div><b>CF</b>: {{CF}}</div>{{/CF}}
{{#CTL}}<div><b>CTL</b>: {{CTL}}</div>{{/CTL}}
{{#TLB}}<div><b>TLB</b>: {{TLB}}</div>{{/TLB}}
{{#OFF}}<div><b>OFF</b>: {{OFF}}</div>{{/OFF}}
</div>
`
                );
           }        }

        /**
         * To indicate a check out type such as adjusted capacity or dispatch capacity
         *
         */
        class CheckOutType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CheckOutType;
                if (null == bucket)
                   cim_data.CheckOutType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CheckOutType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CheckOutType";
                base.parse_element (/<cim:CheckOutType.PRE_HOUR>([\s\S]*?)<\/cim:CheckOutType.PRE_HOUR>/g, obj, "PRE_HOUR", base.to_string, sub, context);
                base.parse_element (/<cim:CheckOutType.PRE_SCHEDULE>([\s\S]*?)<\/cim:CheckOutType.PRE_SCHEDULE>/g, obj, "PRE_SCHEDULE", base.to_string, sub, context);
                base.parse_element (/<cim:CheckOutType.AFTER_THE_FACT>([\s\S]*?)<\/cim:CheckOutType.AFTER_THE_FACT>/g, obj, "AFTER_THE_FACT", base.to_string, sub, context);

                var bucket = context.parsed.CheckOutType;
                if (null == bucket)
                   context.parsed.CheckOutType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CheckOutType", "PRE_HOUR", base.from_string, fields);
                base.export_element (obj, "CheckOutType", "PRE_SCHEDULE", base.from_string, fields);
                base.export_element (obj, "CheckOutType", "AFTER_THE_FACT", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CheckOutType_collapse" aria-expanded="true" aria-controls="CheckOutType_collapse">CheckOutType</a>
<div id="CheckOutType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#PRE_HOUR}}<div><b>PRE_HOUR</b>: {{PRE_HOUR}}</div>{{/PRE_HOUR}}
{{#PRE_SCHEDULE}}<div><b>PRE_SCHEDULE</b>: {{PRE_SCHEDULE}}</div>{{/PRE_SCHEDULE}}
{{#AFTER_THE_FACT}}<div><b>AFTER_THE_FACT</b>: {{AFTER_THE_FACT}}</div>{{/AFTER_THE_FACT}}
</div>
`
                );
           }        }

        /**
         * Combined Cycle
         * Gas Turbine
         * Hydro Turbine
         * Other
         * Photovoltaic
         * Hydro Pump-Turbine
         * Reciprocating Engine
         * Steam Turbine
         * Synchronous Condenser
         *
         * Wind Turbine
         *
         */
        class UnitType extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.UnitType;
                if (null == bucket)
                   cim_data.UnitType = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.UnitType[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "UnitType";
                base.parse_element (/<cim:UnitType.CCYC>([\s\S]*?)<\/cim:UnitType.CCYC>/g, obj, "CCYC", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.GTUR>([\s\S]*?)<\/cim:UnitType.GTUR>/g, obj, "GTUR", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.HYDR>([\s\S]*?)<\/cim:UnitType.HYDR>/g, obj, "HYDR", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.OTHR>([\s\S]*?)<\/cim:UnitType.OTHR>/g, obj, "OTHR", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.PHOT>([\s\S]*?)<\/cim:UnitType.PHOT>/g, obj, "PHOT", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.PTUR>([\s\S]*?)<\/cim:UnitType.PTUR>/g, obj, "PTUR", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.RECP>([\s\S]*?)<\/cim:UnitType.RECP>/g, obj, "RECP", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.STUR>([\s\S]*?)<\/cim:UnitType.STUR>/g, obj, "STUR", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.SYNC>([\s\S]*?)<\/cim:UnitType.SYNC>/g, obj, "SYNC", base.to_string, sub, context);
                base.parse_element (/<cim:UnitType.WIND>([\s\S]*?)<\/cim:UnitType.WIND>/g, obj, "WIND", base.to_string, sub, context);

                var bucket = context.parsed.UnitType;
                if (null == bucket)
                   context.parsed.UnitType = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "UnitType", "CCYC", base.from_string, fields);
                base.export_element (obj, "UnitType", "GTUR", base.from_string, fields);
                base.export_element (obj, "UnitType", "HYDR", base.from_string, fields);
                base.export_element (obj, "UnitType", "OTHR", base.from_string, fields);
                base.export_element (obj, "UnitType", "PHOT", base.from_string, fields);
                base.export_element (obj, "UnitType", "PTUR", base.from_string, fields);
                base.export_element (obj, "UnitType", "RECP", base.from_string, fields);
                base.export_element (obj, "UnitType", "STUR", base.from_string, fields);
                base.export_element (obj, "UnitType", "SYNC", base.from_string, fields);
                base.export_element (obj, "UnitType", "WIND", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#UnitType_collapse" aria-expanded="true" aria-controls="UnitType_collapse">UnitType</a>
<div id="UnitType_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#CCYC}}<div><b>CCYC</b>: {{CCYC}}</div>{{/CCYC}}
{{#GTUR}}<div><b>GTUR</b>: {{GTUR}}</div>{{/GTUR}}
{{#HYDR}}<div><b>HYDR</b>: {{HYDR}}</div>{{/HYDR}}
{{#OTHR}}<div><b>OTHR</b>: {{OTHR}}</div>{{/OTHR}}
{{#PHOT}}<div><b>PHOT</b>: {{PHOT}}</div>{{/PHOT}}
{{#PTUR}}<div><b>PTUR</b>: {{PTUR}}</div>{{/PTUR}}
{{#RECP}}<div><b>RECP</b>: {{RECP}}</div>{{/RECP}}
{{#STUR}}<div><b>STUR</b>: {{STUR}}</div>{{/STUR}}
{{#SYNC}}<div><b>SYNC</b>: {{SYNC}}</div>{{/SYNC}}
{{#WIND}}<div><b>WIND</b>: {{WIND}}</div>{{/WIND}}
</div>
`
                );
           }        }

        /**
         * For example:
         * Bid Cost
         * Proxy Cost
         *
         * Registered Cost
         *
         */
        class CostBasis extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.CostBasis;
                if (null == bucket)
                   cim_data.CostBasis = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.CostBasis[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "CostBasis";
                base.parse_element (/<cim:CostBasis.BIDC>([\s\S]*?)<\/cim:CostBasis.BIDC>/g, obj, "BIDC", base.to_string, sub, context);
                base.parse_element (/<cim:CostBasis.PRXC>([\s\S]*?)<\/cim:CostBasis.PRXC>/g, obj, "PRXC", base.to_string, sub, context);
                base.parse_element (/<cim:CostBasis.REGC>([\s\S]*?)<\/cim:CostBasis.REGC>/g, obj, "REGC", base.to_string, sub, context);

                var bucket = context.parsed.CostBasis;
                if (null == bucket)
                   context.parsed.CostBasis = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "CostBasis", "BIDC", base.from_string, fields);
                base.export_element (obj, "CostBasis", "PRXC", base.from_string, fields);
                base.export_element (obj, "CostBasis", "REGC", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#CostBasis_collapse" aria-expanded="true" aria-controls="CostBasis_collapse">CostBasis</a>
<div id="CostBasis_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#BIDC}}<div><b>BIDC</b>: {{BIDC}}</div>{{/BIDC}}
{{#PRXC}}<div><b>PRXC</b>: {{PRXC}}</div>{{/PRXC}}
{{#REGC}}<div><b>REGC</b>: {{REGC}}</div>{{/REGC}}
</div>
`
                );
           }        }

        return (
            {
                SelfSchedReferenceType: SelfSchedReferenceType,
                MPMTestMethodType: MPMTestMethodType,
                MarketProductType: MarketProductType,
                RampRateCondition: RampRateCondition,
                AutomaticDispatchMode: AutomaticDispatchMode,
                BidMitigationType: BidMitigationType,
                CheckOutType: CheckOutType,
                TRType: TRType,
                ContractType: ContractType,
                MPMTestIdentifierType: MPMTestIdentifierType,
                BidCalculationBasis: BidCalculationBasis,
                UnitType: UnitType,
                FlagTypeRMR: FlagTypeRMR,
                ResourceAssnType: ResourceAssnType,
                MktAccountKind: MktAccountKind,
                BidTypeRMR: BidTypeRMR,
                EnergyTransactionType: EnergyTransactionType,
                RampCurveType: RampCurveType,
                ReserveRequirementType: ReserveRequirementType,
                SwitchStatusType: SwitchStatusType,
                AnalogLimitType: AnalogLimitType,
                DispatchResponseType: DispatchResponseType,
                YesNo: YesNo,
                MarketProductSelfSchedType: MarketProductSelfSchedType,
                AutomaticDispInstTypeCommitment: AutomaticDispInstTypeCommitment,
                MQSCHGType: MQSCHGType,
                TimeOfUse: TimeOfUse,
                ConstraintLimitType: ConstraintLimitType,
                BidMitigationStatus: BidMitigationStatus,
                EnergyPriceIndexType: EnergyPriceIndexType,
                CostBasis: CostBasis,
                EquipmentStatusType: EquipmentStatusType,
                LoadForecastType: LoadForecastType,
                MktInvoiceLineItemKind: MktInvoiceLineItemKind,
                AnodeType: AnodeType,
                ConstraintRampType: ConstraintRampType,
                ResourceAncillaryServiceType: ResourceAncillaryServiceType,
                PurposeFlagType: PurposeFlagType,
                CRRCategoryType: CRRCategoryType,
                MQSInstructionSource: MQSInstructionSource,
                ApnodeType: ApnodeType,
                CRRSegmentType: CRRSegmentType,
                RampModeType: RampModeType,
                InterTieDirection: InterTieDirection,
                ResourceLimitIndicator: ResourceLimitIndicator,
                RampRateType: RampRateType,
                MPMTestOutcome: MPMTestOutcome,
                BidType: BidType,
                FlowDirectionType: FlowDirectionType,
                CommitmentType: CommitmentType,
                CRRHedgeType: CRRHedgeType,
                ResultsConstraintType: ResultsConstraintType,
                ResourceRegistrationStatus: ResourceRegistrationStatus,
                FuelSource: FuelSource,
                ActionType: ActionType,
                SelfScheduleBreakdownType: SelfScheduleBreakdownType,
                CRRRoleType: CRRRoleType,
                PassIndicatorType: PassIndicatorType,
                MarketType: MarketType,
                EnergyProductType: EnergyProductType,
                ExecutionType: ExecutionType,
                ParticipationCategoryMPM: ParticipationCategoryMPM,
                AreaControlMode: AreaControlMode,
                ResourceCapacityType: ResourceCapacityType,
                OnOff: OnOff,
                MktBillMediaKind: MktBillMediaKind,
                TradeType: TradeType
            }
        );
    }
);