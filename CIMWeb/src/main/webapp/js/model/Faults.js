define
(
    ["model/base", "model/Core"],
    /**
     * The package describe faults that may happen to conducting equipment, e.g. tree falling on a power line.
     *
     */
    function (base, Core)
    {

        /**
         * The type of fault connection among phases.
         *
         */
        function parse_PhaseConnectedFaultKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "PhaseConnectedFaultKind";
            /**
             * The fault connects the indicated phases to ground.
             *
             * The line to line fault impedance is not used and assumed infinite. The full ground impedance is connected between each phase specified in the fault and ground, but not between the phases.
             *
             */
            base.parse_element (/<cim:PhaseConnectedFaultKind.lineToGround>([\s\S]*?)<\/cim:PhaseConnectedFaultKind.lineToGround>/g, obj, "lineToGround", base.to_string, sub, context);

            /**
             * The fault connects the specified phases together without a connection to ground.
             *
             * The ground impedance of this fault is ignored. The line to line impedance is connected between each of the phases specified in the fault. For example three times for a three phase fault, one time for a two phase fault.  A single phase fault should not be specified.
             *
             */
            base.parse_element (/<cim:PhaseConnectedFaultKind.lineToLine>([\s\S]*?)<\/cim:PhaseConnectedFaultKind.lineToLine>/g, obj, "lineToLine", base.to_string, sub, context);

            /**
             * The fault connects the indicated phases to ground and to each other.
             *
             * The line to line impedance is connected between each of the phases specified in the fault in a full mesh. For example three times for a three phase fault, one time for a two phase fault. A single phase fault should not be specified. The full ground impedance is connected between each phase specified in the fault and ground.
             *
             */
            base.parse_element (/<cim:PhaseConnectedFaultKind.lineToLineToGround>([\s\S]*?)<\/cim:PhaseConnectedFaultKind.lineToLineToGround>/g, obj, "lineToLineToGround", base.to_string, sub, context);

            bucket = context.parsed.PhaseConnectedFaultKind;
            if (null == bucket)
                context.parsed.PhaseConnectedFaultKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Type of cause of the fault.
         *
         */
        function parse_FaultCauseType (context, sub)
        {
            var obj;
            var bucket;

            obj = Core.parse_IdentifiedObject (context, sub);
            obj.cls = "FaultCauseType";
            bucket = context.parsed.FaultCauseType;
            if (null == bucket)
                context.parsed.FaultCauseType = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A fault that occurs on an AC line segment at some point along the length.
         *
         */
        function parse_LineFault (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_Fault (context, sub);
            obj.cls = "LineFault";
            /**
             * The length to the place where the fault is located starting from terminal with sequence number 1 of the faulted line segment.
             *
             */
            base.parse_element (/<cim:LineFault.lengthFromTerminal1>([\s\S]*?)<\/cim:LineFault.lengthFromTerminal1>/g, obj, "lengthFromTerminal1", base.to_string, sub, context);

            /**
             * The line segment of this line fault.
             *
             */
            base.parse_attribute (/<cim:LineFault.ACLineSegment\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "ACLineSegment", sub, context, true);

            bucket = context.parsed.LineFault;
            if (null == bucket)
                context.parsed.LineFault = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Impedance description for the fault.
         *
         */
        function parse_FaultImpedance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "FaultImpedance";
            /**
             * The resistance of the fault between phases and ground.
             *
             */
            base.parse_element (/<cim:FaultImpedance.rGround>([\s\S]*?)<\/cim:FaultImpedance.rGround>/g, obj, "rGround", base.to_string, sub, context);

            /**
             * The resistance of the fault between phases.
             *
             */
            base.parse_element (/<cim:FaultImpedance.rLineToLine>([\s\S]*?)<\/cim:FaultImpedance.rLineToLine>/g, obj, "rLineToLine", base.to_string, sub, context);

            /**
             * The reactance of the fault between phases and ground.
             *
             */
            base.parse_element (/<cim:FaultImpedance.xGround>([\s\S]*?)<\/cim:FaultImpedance.xGround>/g, obj, "xGround", base.to_string, sub, context);

            /**
             * The reactance of the fault between phases.
             *
             */
            base.parse_element (/<cim:FaultImpedance.xLineToLine>([\s\S]*?)<\/cim:FaultImpedance.xLineToLine>/g, obj, "xLineToLine", base.to_string, sub, context);

            bucket = context.parsed.FaultImpedance;
            if (null == bucket)
                context.parsed.FaultImpedance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A fault applied at the terminal, external to the equipment.
         *
         * This class is not used to specify faults internal to the equipment.
         *
         */
        function parse_EquipmentFault (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_Fault (context, sub);
            obj.cls = "EquipmentFault";
            /**
             * The terminal connecting to the bus to which the fault is applied.
             *
             */
            base.parse_attribute (/<cim:EquipmentFault.Terminal\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Terminal", sub, context, true);

            bucket = context.parsed.EquipmentFault;
            if (null == bucket)
                context.parsed.EquipmentFault = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Abnormal condition causing current flow through conducting equipment, such as caused by equipment failure or short circuits from objects not typically modeled (for example, a tree falling on a line).
         *
         */
        function parse_Fault (context, sub)
        {
            var obj;
            var bucket;

            obj = Core.parse_IdentifiedObject (context, sub);
            obj.cls = "Fault";
            /**
             * The kind of phase fault.
             *
             */
            base.parse_element (/<cim:Fault.kind>([\s\S]*?)<\/cim:Fault.kind>/g, obj, "kind", base.to_string, sub, context);

            /**
             * The phases participating in the fault.
             *
             * The fault connections into these phases are further specified by the type of fault.
             *
             */
            base.parse_element (/<cim:Fault.phases>([\s\S]*?)<\/cim:Fault.phases>/g, obj, "phases", base.to_string, sub, context);

            /**
             * Fault impedance.
             *
             * Its usage is described by 'kind'.
             *
             */
            base.parse_element (/<cim:Fault.impedance>([\s\S]*?)<\/cim:Fault.impedance>/g, obj, "impedance", base.to_string, sub, context);

            /**
             * Equipment carrying this fault.
             *
             */
            base.parse_attribute (/<cim:Fault.FaultyEquipment\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "FaultyEquipment", sub, context, true);

            /**
             * Outage associated with this fault.
             *
             */
            base.parse_attribute (/<cim:Fault.Outage\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Outage", sub, context, true);

            bucket = context.parsed.Fault;
            if (null == bucket)
                context.parsed.Fault = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        return (
            {
                parse_EquipmentFault: parse_EquipmentFault,
                parse_FaultCauseType: parse_FaultCauseType,
                parse_LineFault: parse_LineFault,
                parse_PhaseConnectedFaultKind: parse_PhaseConnectedFaultKind,
                parse_Fault: parse_Fault,
                parse_FaultImpedance: parse_FaultImpedance
            }
        );
    }
);