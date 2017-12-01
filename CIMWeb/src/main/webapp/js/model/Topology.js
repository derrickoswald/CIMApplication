define
(
    ["model/base", "model/Core"],
    /**
     * An extension to the Core Package that in association with the Terminal class models Connectivity, that is the physical definition of how equipment is connected together.
     *
     * In addition it models Topology, that is the logical definition of how equipment is connected via closed switches. The Topology definition is independent of the other electrical characteristics.
     *
     */
    function (base, Core)
    {

        /**
         * Used to apply user standard names to topology buses.
         *
         * Typically used for "bus/branch" case generation. Associated with one or more terminals that are normally connected with the bus name.    The associated terminals are normally connected by non-retained switches. For a ring bus station configuration, all busbar terminals in the ring are typically associated.   For a breaker and a half scheme, both busbars would normally be associated.  For a ring bus, all busbars would normally be associated.  For a "straight" busbar configuration, normally only the main terminal at the busbar would be associated.
         *
         */
        class BusNameMarker extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.BusNameMarker;
                if (null == bucket)
                   cim_data.BusNameMarker = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.BusNameMarker[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "BusNameMarker";
                base.parse_element (/<cim:BusNameMarker.priority>([\s\S]*?)<\/cim:BusNameMarker.priority>/g, obj, "priority", base.to_string, sub, context);
                base.parse_attribute (/<cim:BusNameMarker.ReportingGroup\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ReportingGroup", sub, context);

                var bucket = context.parsed.BusNameMarker;
                if (null == bucket)
                   context.parsed.BusNameMarker = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "BusNameMarker", "priority", base.from_string, fields);
                base.export_attribute (obj, "BusNameMarker", "ReportingGroup", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#BusNameMarker_collapse" aria-expanded="true" aria-controls="BusNameMarker_collapse">BusNameMarker</a>
<div id="BusNameMarker_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#priority}}<div><b>priority</b>: {{priority}}</div>{{/priority}}
{{#ReportingGroup}}<div><b>ReportingGroup</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ReportingGroup}}&quot;);})'>{{ReportingGroup}}</a></div>{{/ReportingGroup}}
</div>
`
                );
           }        }

        /**
         * DC bus.
         *
         */
        class DCTopologicalNode extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.DCTopologicalNode;
                if (null == bucket)
                   cim_data.DCTopologicalNode = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.DCTopologicalNode[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "DCTopologicalNode";
                base.parse_attribute (/<cim:DCTopologicalNode.DCEquipmentContainer\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "DCEquipmentContainer", sub, context);
                base.parse_attribute (/<cim:DCTopologicalNode.DCTopologicalIsland\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "DCTopologicalIsland", sub, context);

                var bucket = context.parsed.DCTopologicalNode;
                if (null == bucket)
                   context.parsed.DCTopologicalNode = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "DCTopologicalNode", "DCEquipmentContainer", fields);
                base.export_attribute (obj, "DCTopologicalNode", "DCTopologicalIsland", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#DCTopologicalNode_collapse" aria-expanded="true" aria-controls="DCTopologicalNode_collapse">DCTopologicalNode</a>
<div id="DCTopologicalNode_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#DCEquipmentContainer}}<div><b>DCEquipmentContainer</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{DCEquipmentContainer}}&quot;);})'>{{DCEquipmentContainer}}</a></div>{{/DCEquipmentContainer}}
{{#DCTopologicalIsland}}<div><b>DCTopologicalIsland</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{DCTopologicalIsland}}&quot;);})'>{{DCTopologicalIsland}}</a></div>{{/DCTopologicalIsland}}
</div>
`
                );
           }        }

        /**
         * For a detailed substation model a topological node is a set of connectivity nodes that, in the current network state, are connected together through any type of closed switches, including  jumpers.
         *
         * Topological nodes change as the current network state changes (i.e., switches, breakers, etc. change state).
         *
         */
        class TopologicalNode extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TopologicalNode;
                if (null == bucket)
                   cim_data.TopologicalNode = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TopologicalNode[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "TopologicalNode";
                base.parse_element (/<cim:TopologicalNode.pInjection>([\s\S]*?)<\/cim:TopologicalNode.pInjection>/g, obj, "pInjection", base.to_string, sub, context);
                base.parse_element (/<cim:TopologicalNode.qInjection>([\s\S]*?)<\/cim:TopologicalNode.qInjection>/g, obj, "qInjection", base.to_string, sub, context);
                base.parse_attribute (/<cim:TopologicalNode.AngleRefTopologicalIsland\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AngleRefTopologicalIsland", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.SvVoltage\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "SvVoltage", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.ReportingGroup\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ReportingGroup", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.SvInjection\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "SvInjection", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.BaseVoltage\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "BaseVoltage", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.TopologicalIsland\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "TopologicalIsland", sub, context);
                base.parse_attribute (/<cim:TopologicalNode.ConnectivityNodeContainer\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ConnectivityNodeContainer", sub, context);

                var bucket = context.parsed.TopologicalNode;
                if (null == bucket)
                   context.parsed.TopologicalNode = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_element (obj, "TopologicalNode", "pInjection", base.from_string, fields);
                base.export_element (obj, "TopologicalNode", "qInjection", base.from_string, fields);
                base.export_attribute (obj, "TopologicalNode", "AngleRefTopologicalIsland", fields);
                base.export_attribute (obj, "TopologicalNode", "SvVoltage", fields);
                base.export_attribute (obj, "TopologicalNode", "ReportingGroup", fields);
                base.export_attribute (obj, "TopologicalNode", "SvInjection", fields);
                base.export_attribute (obj, "TopologicalNode", "BaseVoltage", fields);
                base.export_attribute (obj, "TopologicalNode", "TopologicalIsland", fields);
                base.export_attribute (obj, "TopologicalNode", "ConnectivityNodeContainer", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TopologicalNode_collapse" aria-expanded="true" aria-controls="TopologicalNode_collapse">TopologicalNode</a>
<div id="TopologicalNode_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#pInjection}}<div><b>pInjection</b>: {{pInjection}}</div>{{/pInjection}}
{{#qInjection}}<div><b>qInjection</b>: {{qInjection}}</div>{{/qInjection}}
{{#AngleRefTopologicalIsland}}<div><b>AngleRefTopologicalIsland</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AngleRefTopologicalIsland}}&quot;);})'>{{AngleRefTopologicalIsland}}</a></div>{{/AngleRefTopologicalIsland}}
{{#SvVoltage}}<div><b>SvVoltage</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{SvVoltage}}&quot;);})'>{{SvVoltage}}</a></div>{{/SvVoltage}}
{{#ReportingGroup}}<div><b>ReportingGroup</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ReportingGroup}}&quot;);})'>{{ReportingGroup}}</a></div>{{/ReportingGroup}}
{{#SvInjection}}<div><b>SvInjection</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{SvInjection}}&quot;);})'>{{SvInjection}}</a></div>{{/SvInjection}}
{{#BaseVoltage}}<div><b>BaseVoltage</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{BaseVoltage}}&quot;);})'>{{BaseVoltage}}</a></div>{{/BaseVoltage}}
{{#TopologicalIsland}}<div><b>TopologicalIsland</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{TopologicalIsland}}&quot;);})'>{{TopologicalIsland}}</a></div>{{/TopologicalIsland}}
{{#ConnectivityNodeContainer}}<div><b>ConnectivityNodeContainer</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{ConnectivityNodeContainer}}&quot;);})'>{{ConnectivityNodeContainer}}</a></div>{{/ConnectivityNodeContainer}}
</div>
`
                );
           }        }

        /**
         * An electrically connected subset of the network.
         *
         * Topological islands can change as the current network state changes: e.g. due to
         *
         */
        class TopologicalIsland extends Core.IdentifiedObject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.TopologicalIsland;
                if (null == bucket)
                   cim_data.TopologicalIsland = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.TopologicalIsland[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = Core.IdentifiedObject.prototype.parse.call (this, context, sub);
                obj.cls = "TopologicalIsland";
                base.parse_attribute (/<cim:TopologicalIsland.AngleRefTopologicalNode\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "AngleRefTopologicalNode", sub, context);

                var bucket = context.parsed.TopologicalIsland;
                if (null == bucket)
                   context.parsed.TopologicalIsland = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = Core.IdentifiedObject.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "TopologicalIsland", "AngleRefTopologicalNode", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#TopologicalIsland_collapse" aria-expanded="true" aria-controls="TopologicalIsland_collapse">TopologicalIsland</a>
<div id="TopologicalIsland_collapse" class="collapse in" style="margin-left: 10px;">
`
      + Core.IdentifiedObject.prototype.template.call (this) +
`
{{#AngleRefTopologicalNode}}<div><b>AngleRefTopologicalNode</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{AngleRefTopologicalNode}}&quot;);})'>{{AngleRefTopologicalNode}}</a></div>{{/AngleRefTopologicalNode}}
</div>
`
                );
           }        }

        return (
            {
                DCTopologicalNode: DCTopologicalNode,
                TopologicalNode: TopologicalNode,
                TopologicalIsland: TopologicalIsland,
                BusNameMarker: BusNameMarker
            }
        );
    }
);