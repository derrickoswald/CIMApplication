define
(
    ["model/base"],
    /**
     * The domain package define primitive datatypes that are used by classes in other packages.
     *
     * Stereotypes are used to describe the datatypes. The following stereotypes are defined:
     *
     */
    function (base)
    {

        /**
         * Capacitance per unit of length.
         *
         */
        function parse_CapacitancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "CapacitancePerLength";
            base.parse_element (/<cim:CapacitancePerLength.value>([\s\S]*?)<\/cim:CapacitancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            base.parse_element (/<cim:CapacitancePerLength.unit>([\s\S]*?)<\/cim:CapacitancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:CapacitancePerLength.multiplier>([\s\S]*?)<\/cim:CapacitancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CapacitancePerLength.denominatorUnit>([\s\S]*?)<\/cim:CapacitancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:CapacitancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:CapacitancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            bucket = context.parsed.CapacitancePerLength;
            if (null == bucket)
                context.parsed.CapacitancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Cost per unit volume.
         *
         */
        function parse_CostPerVolume (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "CostPerVolume";
            base.parse_element (/<cim:CostPerVolume.value>([\s\S]*?)<\/cim:CostPerVolume.value>/g, obj, "value", base.to_float, sub, context);

            base.parse_element (/<cim:CostPerVolume.denominatorMultiplier>([\s\S]*?)<\/cim:CostPerVolume.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerVolume.denominatorUnit>([\s\S]*?)<\/cim:CostPerVolume.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerVolume.multiplier>([\s\S]*?)<\/cim:CostPerVolume.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerVolume.unit>([\s\S]*?)<\/cim:CostPerVolume.unit>/g, obj, "unit", base.to_string, sub, context);

            bucket = context.parsed.CostPerVolume;
            if (null == bucket)
                context.parsed.CostPerVolume = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A floating point number.
         *
         * The range is unspecified and not limited.
         *
         */
        function parse_Float (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Float";
            bucket = context.parsed.Float;
            if (null == bucket)
                context.parsed.Float = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * The unit multipliers defined for the CIM.
         *
         */
        function parse_UnitMultiplier (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "UnitMultiplier";
            /**
             * Pico 10**-12.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.p>([\s\S]*?)<\/cim:UnitMultiplier.p>/g, obj, "p", base.to_string, sub, context);

            /**
             * Nano 10**-9.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.n>([\s\S]*?)<\/cim:UnitMultiplier.n>/g, obj, "n", base.to_string, sub, context);

            /**
             * Micro 10**-6.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.micro>([\s\S]*?)<\/cim:UnitMultiplier.micro>/g, obj, "micro", base.to_string, sub, context);

            /**
             * Milli 10**-3.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.m>([\s\S]*?)<\/cim:UnitMultiplier.m>/g, obj, "m", base.to_string, sub, context);

            /**
             * Centi 10**-2.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.c>([\s\S]*?)<\/cim:UnitMultiplier.c>/g, obj, "c", base.to_string, sub, context);

            /**
             * Deci 10**-1.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.d>([\s\S]*?)<\/cim:UnitMultiplier.d>/g, obj, "d", base.to_string, sub, context);

            /**
             * Kilo 10**3.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.k>([\s\S]*?)<\/cim:UnitMultiplier.k>/g, obj, "k", base.to_string, sub, context);

            /**
             * Mega 10**6.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.M>([\s\S]*?)<\/cim:UnitMultiplier.M>/g, obj, "M", base.to_string, sub, context);

            /**
             * Giga 10**9.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.G>([\s\S]*?)<\/cim:UnitMultiplier.G>/g, obj, "G", base.to_string, sub, context);

            /**
             * Tera 10**12.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.T>([\s\S]*?)<\/cim:UnitMultiplier.T>/g, obj, "T", base.to_string, sub, context);

            /**
             * No multiplier or equivalently multiply by 1.
             *
             */
            base.parse_element (/<cim:UnitMultiplier.none>([\s\S]*?)<\/cim:UnitMultiplier.none>/g, obj, "none", base.to_string, sub, context);

            bucket = context.parsed.UnitMultiplier;
            if (null == bucket)
                context.parsed.UnitMultiplier = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Product of RMS value of the voltage and the RMS value of the quadrature component of the current.
         *
         */
        function parse_ReactivePower (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ReactivePower";
            base.parse_element (/<cim:ReactivePower.multiplier>([\s\S]*?)<\/cim:ReactivePower.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ReactivePower.unit>([\s\S]*?)<\/cim:ReactivePower.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ReactivePower.value>([\s\S]*?)<\/cim:ReactivePower.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ReactivePower;
            if (null == bucket)
                context.parsed.ReactivePower = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Interval between two times specified as mont and date.
         *
         */
        function parse_MonthDayInterval (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "MonthDayInterval";
            /**
             * End time of this interval.
             *
             */
            base.parse_element (/<cim:MonthDayInterval.end>([\s\S]*?)<\/cim:MonthDayInterval.end>/g, obj, "end", base.to_string, sub, context);

            /**
             * Start time of this interval.
             *
             */
            base.parse_element (/<cim:MonthDayInterval.start>([\s\S]*?)<\/cim:MonthDayInterval.start>/g, obj, "start", base.to_string, sub, context);

            bucket = context.parsed.MonthDayInterval;
            if (null == bucket)
                context.parsed.MonthDayInterval = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Reactance (imaginary part of impedance), at rated frequency.
         *
         */
        function parse_Reactance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Reactance";
            base.parse_element (/<cim:Reactance.multiplier>([\s\S]*?)<\/cim:Reactance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Reactance.unit>([\s\S]*?)<\/cim:Reactance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Reactance.value>([\s\S]*?)<\/cim:Reactance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Reactance;
            if (null == bucket)
                context.parsed.Reactance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Resistance (real part of impedance) per unit of length.
         *
         */
        function parse_ResistancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ResistancePerLength";
            base.parse_element (/<cim:ResistancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:ResistancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ResistancePerLength.denominatorUnit>([\s\S]*?)<\/cim:ResistancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ResistancePerLength.multiplier>([\s\S]*?)<\/cim:ResistancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ResistancePerLength.unit>([\s\S]*?)<\/cim:ResistancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ResistancePerLength.value>([\s\S]*?)<\/cim:ResistancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ResistancePerLength;
            if (null == bucket)
                context.parsed.ResistancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Phase angle in radians.
         *
         */
        function parse_AngleRadians (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "AngleRadians";
            base.parse_element (/<cim:AngleRadians.multiplier>([\s\S]*?)<\/cim:AngleRadians.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:AngleRadians.unit>([\s\S]*?)<\/cim:AngleRadians.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:AngleRadians.value>([\s\S]*?)<\/cim:AngleRadians.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.AngleRadians;
            if (null == bucket)
                context.parsed.AngleRadians = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Electrical voltage, can be both AC and DC.
         *
         */
        function parse_Voltage (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Voltage";
            base.parse_element (/<cim:Voltage.multiplier>([\s\S]*?)<\/cim:Voltage.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Voltage.unit>([\s\S]*?)<\/cim:Voltage.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Voltage.value>([\s\S]*?)<\/cim:Voltage.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Voltage;
            if (null == bucket)
                context.parsed.Voltage = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Rate of change of active power per time.
         *
         */
        function parse_ActivePowerChangeRate (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ActivePowerChangeRate";
            base.parse_element (/<cim:ActivePowerChangeRate.denominatorMultiplier>([\s\S]*?)<\/cim:ActivePowerChangeRate.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerChangeRate.denominatorUnit>([\s\S]*?)<\/cim:ActivePowerChangeRate.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerChangeRate.multiplier>([\s\S]*?)<\/cim:ActivePowerChangeRate.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerChangeRate.unit>([\s\S]*?)<\/cim:ActivePowerChangeRate.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerChangeRate.value>([\s\S]*?)<\/cim:ActivePowerChangeRate.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ActivePowerChangeRate;
            if (null == bucket)
                context.parsed.ActivePowerChangeRate = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        function parse_ActivePowerPerCurrentFlow (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ActivePowerPerCurrentFlow";
            base.parse_element (/<cim:ActivePowerPerCurrentFlow.multiplier>([\s\S]*?)<\/cim:ActivePowerPerCurrentFlow.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerCurrentFlow.unit>([\s\S]*?)<\/cim:ActivePowerPerCurrentFlow.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerCurrentFlow.value>([\s\S]*?)<\/cim:ActivePowerPerCurrentFlow.value>/g, obj, "value", base.to_float, sub, context);

            base.parse_element (/<cim:ActivePowerPerCurrentFlow.demoninatorUnit>([\s\S]*?)<\/cim:ActivePowerPerCurrentFlow.demoninatorUnit>/g, obj, "demoninatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerCurrentFlow.denominatorMultiplier>([\s\S]*?)<\/cim:ActivePowerPerCurrentFlow.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            bucket = context.parsed.ActivePowerPerCurrentFlow;
            if (null == bucket)
                context.parsed.ActivePowerPerCurrentFlow = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Cost, in units of currency, per elapsed time.
         *
         */
        function parse_CostRate (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "CostRate";
            base.parse_element (/<cim:CostRate.denominatorMultiplier>([\s\S]*?)<\/cim:CostRate.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostRate.denominatorUnit>([\s\S]*?)<\/cim:CostRate.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:CostRate.multiplier>([\s\S]*?)<\/cim:CostRate.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostRate.unit>([\s\S]*?)<\/cim:CostRate.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:CostRate.value>([\s\S]*?)<\/cim:CostRate.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.CostRate;
            if (null == bucket)
                context.parsed.CostRate = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Reservoir water level referred to a given datum such as mean sea level.
         *
         */
        function parse_WaterLevel (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "WaterLevel";
            base.parse_element (/<cim:WaterLevel.multiplier>([\s\S]*?)<\/cim:WaterLevel.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:WaterLevel.unit>([\s\S]*?)<\/cim:WaterLevel.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:WaterLevel.value>([\s\S]*?)<\/cim:WaterLevel.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.WaterLevel;
            if (null == bucket)
                context.parsed.WaterLevel = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * An integer number.
         *
         * The range is unspecified and not limited.
         *
         */
        function parse_Integer (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Integer";
            bucket = context.parsed.Integer;
            if (null == bucket)
                context.parsed.Integer = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Ratio of current to voltage.
         *
         */
        function parse_Admittance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Admittance";
            base.parse_element (/<cim:Admittance.multiplier>([\s\S]*?)<\/cim:Admittance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Admittance.unit>([\s\S]*?)<\/cim:Admittance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Admittance.value>([\s\S]*?)<\/cim:Admittance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Admittance;
            if (null == bucket)
                context.parsed.Admittance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Cost, in units of currency, per quantity of electrical energy generated.
         *
         */
        function parse_CostPerEnergyUnit (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "CostPerEnergyUnit";
            base.parse_element (/<cim:CostPerEnergyUnit.denominatorMultiplier>([\s\S]*?)<\/cim:CostPerEnergyUnit.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerEnergyUnit.denominatorUnit>([\s\S]*?)<\/cim:CostPerEnergyUnit.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerEnergyUnit.multiplier>([\s\S]*?)<\/cim:CostPerEnergyUnit.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerEnergyUnit.unit>([\s\S]*?)<\/cim:CostPerEnergyUnit.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:CostPerEnergyUnit.value>([\s\S]*?)<\/cim:CostPerEnergyUnit.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.CostPerEnergyUnit;
            if (null == bucket)
                context.parsed.CostPerEnergyUnit = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Reactance (imaginary part of impedance) per unit of length, at rated frequency.
         *
         */
        function parse_ReactancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ReactancePerLength";
            base.parse_element (/<cim:ReactancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:ReactancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ReactancePerLength.denominatorUnit>([\s\S]*?)<\/cim:ReactancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ReactancePerLength.multiplier>([\s\S]*?)<\/cim:ReactancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ReactancePerLength.unit>([\s\S]*?)<\/cim:ReactancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ReactancePerLength.value>([\s\S]*?)<\/cim:ReactancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ReactancePerLength;
            if (null == bucket)
                context.parsed.ReactancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Duration as "PnYnMnDTnHnMnS" which conforms to ISO 8601, where nY expresses a number of years, nM a number of months, nD a number of days.
         *
         * The letter T separates the date expression from the time expression and, after it, nH identifies a number of hours, nM a number of minutes and nS a number of seconds. The number of seconds could be expressed as a decimal number, but all other numbers are integers.
         *
         */
        function parse_Duration (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Duration";
            bucket = context.parsed.Duration;
            if (null == bucket)
                context.parsed.Duration = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Time as "hh:mm:ss.sss", which conforms with ISO 8601.
         *
         * UTC time zone is specified as "hh:mm:ss.sssZ". A local timezone relative UTC is specified as "hh:mm:ss.sss�hh:mm". The second component (shown here as "ss.sss") could have any number of digits in its fractional part to allow any kind of precision beyond seconds.
         *
         */
        function parse_Time (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Time";
            bucket = context.parsed.Time;
            if (null == bucket)
                context.parsed.Time = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Per-unit active power variation with frequency referenced on the system apparent power base.
         *
         * Typical values are in range 1.0 - 2.0.
         *
         */
        function parse_Damping (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Damping";
            base.parse_element (/<cim:Damping.denominatorMultiplier>([\s\S]*?)<\/cim:Damping.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Damping.denominatorUnit>([\s\S]*?)<\/cim:Damping.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:Damping.multiplier>([\s\S]*?)<\/cim:Damping.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Damping.unit>([\s\S]*?)<\/cim:Damping.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Damping.value>([\s\S]*?)<\/cim:Damping.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Damping;
            if (null == bucket)
                context.parsed.Damping = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Per Unit - a positive or negative value referred to a defined base.
         *
         * Values typically range from -10 to +10.
         *
         */
        function parse_PU (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "PU";
            base.parse_element (/<cim:PU.multiplier>([\s\S]*?)<\/cim:PU.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:PU.unit>([\s\S]*?)<\/cim:PU.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:PU.value>([\s\S]*?)<\/cim:PU.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.PU;
            if (null == bucket)
                context.parsed.PU = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Monetary currencies.
         *
         * Apologies for this list not being exhaustive.
         *
         */
        function parse_Currency (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Currency";
            /**
             * US dollar
             *
             */
            base.parse_element (/<cim:Currency.USD>([\s\S]*?)<\/cim:Currency.USD>/g, obj, "USD", base.to_string, sub, context);

            /**
             * European euro
             *
             */
            base.parse_element (/<cim:Currency.EUR>([\s\S]*?)<\/cim:Currency.EUR>/g, obj, "EUR", base.to_string, sub, context);

            /**
             * Australian dollar
             *
             */
            base.parse_element (/<cim:Currency.AUD>([\s\S]*?)<\/cim:Currency.AUD>/g, obj, "AUD", base.to_string, sub, context);

            /**
             * Canadian dollar
             *
             */
            base.parse_element (/<cim:Currency.CAD>([\s\S]*?)<\/cim:Currency.CAD>/g, obj, "CAD", base.to_string, sub, context);

            /**
             * Swiss francs
             *
             */
            base.parse_element (/<cim:Currency.CHF>([\s\S]*?)<\/cim:Currency.CHF>/g, obj, "CHF", base.to_string, sub, context);

            /**
             * Chinese yuan renminbi
             *
             */
            base.parse_element (/<cim:Currency.CNY>([\s\S]*?)<\/cim:Currency.CNY>/g, obj, "CNY", base.to_string, sub, context);

            /**
             * Danish crown
             *
             */
            base.parse_element (/<cim:Currency.DKK>([\s\S]*?)<\/cim:Currency.DKK>/g, obj, "DKK", base.to_string, sub, context);

            /**
             * British pound
             *
             */
            base.parse_element (/<cim:Currency.GBP>([\s\S]*?)<\/cim:Currency.GBP>/g, obj, "GBP", base.to_string, sub, context);

            /**
             * Japanese yen
             *
             */
            base.parse_element (/<cim:Currency.JPY>([\s\S]*?)<\/cim:Currency.JPY>/g, obj, "JPY", base.to_string, sub, context);

            /**
             * Norwegian crown
             *
             */
            base.parse_element (/<cim:Currency.NOK>([\s\S]*?)<\/cim:Currency.NOK>/g, obj, "NOK", base.to_string, sub, context);

            /**
             * Russian ruble
             *
             */
            base.parse_element (/<cim:Currency.RUR>([\s\S]*?)<\/cim:Currency.RUR>/g, obj, "RUR", base.to_string, sub, context);

            /**
             * Swedish crown
             *
             */
            base.parse_element (/<cim:Currency.SEK>([\s\S]*?)<\/cim:Currency.SEK>/g, obj, "SEK", base.to_string, sub, context);

            /**
             * India rupees
             *
             */
            base.parse_element (/<cim:Currency.INR>([\s\S]*?)<\/cim:Currency.INR>/g, obj, "INR", base.to_string, sub, context);

            /**
             * Another type of currency.
             *
             */
            base.parse_element (/<cim:Currency.other>([\s\S]*?)<\/cim:Currency.other>/g, obj, "other", base.to_string, sub, context);

            bucket = context.parsed.Currency;
            if (null == bucket)
                context.parsed.Currency = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Product of RMS value of the voltage and the RMS value of the in-phase component of the current.
         *
         */
        function parse_ActivePower (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ActivePower";
            base.parse_element (/<cim:ActivePower.multiplier>([\s\S]*?)<\/cim:ActivePower.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePower.unit>([\s\S]*?)<\/cim:ActivePower.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePower.value>([\s\S]*?)<\/cim:ActivePower.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ActivePower;
            if (null == bucket)
                context.parsed.ActivePower = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Capacitive part of reactance (imaginary part of impedance), at rated frequency.
         *
         */
        function parse_Capacitance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Capacitance";
            base.parse_element (/<cim:Capacitance.multiplier>([\s\S]*?)<\/cim:Capacitance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Capacitance.unit>([\s\S]*?)<\/cim:Capacitance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Capacitance.value>([\s\S]*?)<\/cim:Capacitance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Capacitance;
            if (null == bucket)
                context.parsed.Capacitance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Factor by which voltage must be multiplied to give corresponding power lost from a circuit.
         *
         * Real part of admittance.
         *
         */
        function parse_Conductance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Conductance";
            base.parse_element (/<cim:Conductance.multiplier>([\s\S]*?)<\/cim:Conductance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Conductance.unit>([\s\S]*?)<\/cim:Conductance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Conductance.value>([\s\S]*?)<\/cim:Conductance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Conductance;
            if (null == bucket)
                context.parsed.Conductance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Inductance per unit of length.
         *
         */
        function parse_InductancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "InductancePerLength";
            base.parse_element (/<cim:InductancePerLength.value>([\s\S]*?)<\/cim:InductancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            base.parse_element (/<cim:InductancePerLength.unit>([\s\S]*?)<\/cim:InductancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:InductancePerLength.multiplier>([\s\S]*?)<\/cim:InductancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:InductancePerLength.denominatorUnit>([\s\S]*?)<\/cim:InductancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:InductancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:InductancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            bucket = context.parsed.InductancePerLength;
            if (null == bucket)
                context.parsed.InductancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Volume per time.
         *
         */
        function parse_VolumeFlowRate (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "VolumeFlowRate";
            base.parse_element (/<cim:VolumeFlowRate.denominatorMultiplier>([\s\S]*?)<\/cim:VolumeFlowRate.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:VolumeFlowRate.denominatorUnit>([\s\S]*?)<\/cim:VolumeFlowRate.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:VolumeFlowRate.multiplier>([\s\S]*?)<\/cim:VolumeFlowRate.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:VolumeFlowRate.unit>([\s\S]*?)<\/cim:VolumeFlowRate.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:VolumeFlowRate.value>([\s\S]*?)<\/cim:VolumeFlowRate.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.VolumeFlowRate;
            if (null == bucket)
                context.parsed.VolumeFlowRate = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Date and time as "yyyy-mm-ddThh:mm:ss.sss", which conforms with ISO 8601.
         *
         * UTC time zone is specified as "yyyy-mm-ddThh:mm:ss.sssZ". A local timezone relative UTC is specified as "yyyy-mm-ddThh:mm:ss.sss-hh:mm". The second component (shown here as "ss.sss") could have any number of digits in its fractional part to allow any kind of precision beyond seconds.
         *
         */
        function parse_DateTime (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "DateTime";
            bucket = context.parsed.DateTime;
            if (null == bucket)
                context.parsed.DateTime = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * MonthDay format as "--mm-dd", which conforms with XSD data type gMonthDay.
         *
         */
        function parse_MonthDay (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "MonthDay";
            bucket = context.parsed.MonthDay;
            if (null == bucket)
                context.parsed.MonthDay = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Electrical current with sign convention: positive flow is out of the conducting equipment into the connectivity node.
         *
         * Can be both AC and DC.
         *
         */
        function parse_CurrentFlow (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "CurrentFlow";
            base.parse_element (/<cim:CurrentFlow.multiplier>([\s\S]*?)<\/cim:CurrentFlow.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:CurrentFlow.unit>([\s\S]*?)<\/cim:CurrentFlow.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:CurrentFlow.value>([\s\S]*?)<\/cim:CurrentFlow.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.CurrentFlow;
            if (null == bucket)
                context.parsed.CurrentFlow = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Active power in kilowatts.
         *
         */
        function parse_KiloActivePower (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "KiloActivePower";
            base.parse_element (/<cim:KiloActivePower.multiplier>([\s\S]*?)<\/cim:KiloActivePower.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:KiloActivePower.unit>([\s\S]*?)<\/cim:KiloActivePower.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:KiloActivePower.value>([\s\S]*?)<\/cim:KiloActivePower.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.KiloActivePower;
            if (null == bucket)
                context.parsed.KiloActivePower = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Ratio of voltage to current.
         *
         */
        function parse_Impedance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Impedance";
            base.parse_element (/<cim:Impedance.multiplier>([\s\S]*?)<\/cim:Impedance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Impedance.unit>([\s\S]*?)<\/cim:Impedance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Impedance.value>([\s\S]*?)<\/cim:Impedance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Impedance;
            if (null == bucket)
                context.parsed.Impedance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Percentage on a defined base.
         *
         * For example, specify as 100 to indicate at the defined base.
         *
         */
        function parse_PerCent (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "PerCent";
            base.parse_element (/<cim:PerCent.multiplier>([\s\S]*?)<\/cim:PerCent.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:PerCent.unit>([\s\S]*?)<\/cim:PerCent.unit>/g, obj, "unit", base.to_string, sub, context);

            /**
             * Normally 0 - 100 on a defined base
             *
             */
            base.parse_element (/<cim:PerCent.value>([\s\S]*?)<\/cim:PerCent.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.PerCent;
            if (null == bucket)
                context.parsed.PerCent = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Time, in seconds.
         *
         */
        function parse_Seconds (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Seconds";
            base.parse_element (/<cim:Seconds.multiplier>([\s\S]*?)<\/cim:Seconds.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Seconds.unit>([\s\S]*?)<\/cim:Seconds.unit>/g, obj, "unit", base.to_string, sub, context);

            /**
             * Time, in seconds
             *
             */
            base.parse_element (/<cim:Seconds.value>([\s\S]*?)<\/cim:Seconds.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Seconds;
            if (null == bucket)
                context.parsed.Seconds = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Active power variation with frequency.
         *
         */
        function parse_ActivePowerPerFrequency (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ActivePowerPerFrequency";
            base.parse_element (/<cim:ActivePowerPerFrequency.denominatorMultiplier>([\s\S]*?)<\/cim:ActivePowerPerFrequency.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerFrequency.denominatorUnit>([\s\S]*?)<\/cim:ActivePowerPerFrequency.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerFrequency.multiplier>([\s\S]*?)<\/cim:ActivePowerPerFrequency.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerFrequency.unit>([\s\S]*?)<\/cim:ActivePowerPerFrequency.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ActivePowerPerFrequency.value>([\s\S]*?)<\/cim:ActivePowerPerFrequency.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ActivePowerPerFrequency;
            if (null == bucket)
                context.parsed.ActivePowerPerFrequency = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Amount of money.
         *
         */
        function parse_Money (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Money";
            base.parse_element (/<cim:Money.multiplier>([\s\S]*?)<\/cim:Money.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Money.unit>([\s\S]*?)<\/cim:Money.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Money.value>([\s\S]*?)<\/cim:Money.value>/g, obj, "value", base.to_string, sub, context);

            bucket = context.parsed.Money;
            if (null == bucket)
                context.parsed.Money = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Interval between two date and time points.
         *
         */
        function parse_DateTimeInterval (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "DateTimeInterval";
            /**
             * End date and time of this interval.
             *
             */
            base.parse_element (/<cim:DateTimeInterval.end>([\s\S]*?)<\/cim:DateTimeInterval.end>/g, obj, "end", base.to_datetime, sub, context);

            /**
             * Start date and time of this interval.
             *
             */
            base.parse_element (/<cim:DateTimeInterval.start>([\s\S]*?)<\/cim:DateTimeInterval.start>/g, obj, "start", base.to_datetime, sub, context);

            bucket = context.parsed.DateTimeInterval;
            if (null == bucket)
                context.parsed.DateTimeInterval = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Product of the RMS value of the voltage and the RMS value of the current.
         *
         */
        function parse_ApparentPower (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ApparentPower";
            base.parse_element (/<cim:ApparentPower.multiplier>([\s\S]*?)<\/cim:ApparentPower.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ApparentPower.unit>([\s\S]*?)<\/cim:ApparentPower.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ApparentPower.value>([\s\S]*?)<\/cim:ApparentPower.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ApparentPower;
            if (null == bucket)
                context.parsed.ApparentPower = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Volume.
         *
         */
        function parse_Volume (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Volume";
            base.parse_element (/<cim:Volume.multiplier>([\s\S]*?)<\/cim:Volume.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Volume.unit>([\s\S]*?)<\/cim:Volume.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Volume.value>([\s\S]*?)<\/cim:Volume.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Volume;
            if (null == bucket)
                context.parsed.Volume = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Measurement of angle in degrees.
         *
         */
        function parse_AngleDegrees (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "AngleDegrees";
            base.parse_element (/<cim:AngleDegrees.multiplier>([\s\S]*?)<\/cim:AngleDegrees.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:AngleDegrees.unit>([\s\S]*?)<\/cim:AngleDegrees.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:AngleDegrees.value>([\s\S]*?)<\/cim:AngleDegrees.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.AngleDegrees;
            if (null == bucket)
                context.parsed.AngleDegrees = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Date as "yyyy-mm-dd", which conforms with ISO 8601.
         *
         * UTC time zone is specified as "yyyy-mm-ddZ". A local timezone relative UTC is specified as "yyyy-mm-dd(+/-)hh:mm".
         *
         */
        function parse_Date (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Date";
            bucket = context.parsed.Date;
            if (null == bucket)
                context.parsed.Date = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        function parse_DecimalQuantity (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "DecimalQuantity";
            base.parse_element (/<cim:DecimalQuantity.value>([\s\S]*?)<\/cim:DecimalQuantity.value>/g, obj, "value", base.to_string, sub, context);

            base.parse_element (/<cim:DecimalQuantity.unit>([\s\S]*?)<\/cim:DecimalQuantity.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:DecimalQuantity.multiplier>([\s\S]*?)<\/cim:DecimalQuantity.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            /**
             * Quantity with decimal value and associated unit or currency information.
             *
             */
            base.parse_element (/<cim:DecimalQuantity.currency>([\s\S]*?)<\/cim:DecimalQuantity.currency>/g, obj, "currency", base.to_string, sub, context);

            bucket = context.parsed.DecimalQuantity;
            if (null == bucket)
                context.parsed.DecimalQuantity = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Real electrical energy.
         *
         */
        function parse_RealEnergy (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "RealEnergy";
            base.parse_element (/<cim:RealEnergy.multiplier>([\s\S]*?)<\/cim:RealEnergy.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:RealEnergy.unit>([\s\S]*?)<\/cim:RealEnergy.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:RealEnergy.value>([\s\S]*?)<\/cim:RealEnergy.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.RealEnergy;
            if (null == bucket)
                context.parsed.RealEnergy = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Decimal is the base-10 notational system for representing real numbers.
         *
         */
        function parse_Decimal (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Decimal";
            bucket = context.parsed.Decimal;
            if (null == bucket)
                context.parsed.Decimal = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Unit of length.
         *
         * Never negative.
         *
         */
        function parse_Length (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Length";
            base.parse_element (/<cim:Length.multiplier>([\s\S]*?)<\/cim:Length.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Length.unit>([\s\S]*?)<\/cim:Length.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Length.value>([\s\S]*?)<\/cim:Length.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Length;
            if (null == bucket)
                context.parsed.Length = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Value of temperature in degrees Celsius.
         *
         */
        function parse_Temperature (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Temperature";
            base.parse_element (/<cim:Temperature.multiplier>([\s\S]*?)<\/cim:Temperature.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Temperature.unit>([\s\S]*?)<\/cim:Temperature.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Temperature.value>([\s\S]*?)<\/cim:Temperature.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Temperature;
            if (null == bucket)
                context.parsed.Temperature = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Quantity with float value and associated unit information.
         *
         */
        function parse_FloatQuantity (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "FloatQuantity";
            base.parse_element (/<cim:FloatQuantity.multiplier>([\s\S]*?)<\/cim:FloatQuantity.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:FloatQuantity.unit>([\s\S]*?)<\/cim:FloatQuantity.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:FloatQuantity.value>([\s\S]*?)<\/cim:FloatQuantity.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.FloatQuantity;
            if (null == bucket)
                context.parsed.FloatQuantity = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Imaginary part of admittance.
         *
         */
        function parse_Susceptance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Susceptance";
            base.parse_element (/<cim:Susceptance.multiplier>([\s\S]*?)<\/cim:Susceptance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Susceptance.unit>([\s\S]*?)<\/cim:Susceptance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Susceptance.value>([\s\S]*?)<\/cim:Susceptance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Susceptance;
            if (null == bucket)
                context.parsed.Susceptance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Cycles per second.
         *
         */
        function parse_Frequency (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Frequency";
            base.parse_element (/<cim:Frequency.multiplier>([\s\S]*?)<\/cim:Frequency.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Frequency.unit>([\s\S]*?)<\/cim:Frequency.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Frequency.value>([\s\S]*?)<\/cim:Frequency.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Frequency;
            if (null == bucket)
                context.parsed.Frequency = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Area.
         *
         */
        function parse_Area (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Area";
            base.parse_element (/<cim:Area.value>([\s\S]*?)<\/cim:Area.value>/g, obj, "value", base.to_float, sub, context);

            base.parse_element (/<cim:Area.unit>([\s\S]*?)<\/cim:Area.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Area.multiplier>([\s\S]*?)<\/cim:Area.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            bucket = context.parsed.Area;
            if (null == bucket)
                context.parsed.Area = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Time in minutes.
         *
         */
        function parse_Minutes (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Minutes";
            base.parse_element (/<cim:Minutes.multiplier>([\s\S]*?)<\/cim:Minutes.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Minutes.unit>([\s\S]*?)<\/cim:Minutes.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Minutes.value>([\s\S]*?)<\/cim:Minutes.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Minutes;
            if (null == bucket)
                context.parsed.Minutes = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Interval between two dates.
         *
         */
        function parse_DateInterval (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "DateInterval";
            /**
             * End date of this interval.
             *
             */
            base.parse_element (/<cim:DateInterval.end>([\s\S]*?)<\/cim:DateInterval.end>/g, obj, "end", base.to_string, sub, context);

            /**
             * Start date of this interval.
             *
             */
            base.parse_element (/<cim:DateInterval.start>([\s\S]*?)<\/cim:DateInterval.start>/g, obj, "start", base.to_string, sub, context);

            bucket = context.parsed.DateInterval;
            if (null == bucket)
                context.parsed.DateInterval = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Voltage variation with reactive power.
         *
         */
        function parse_VoltagePerReactivePower (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "VoltagePerReactivePower";
            base.parse_element (/<cim:VoltagePerReactivePower.denominatorMultiplier>([\s\S]*?)<\/cim:VoltagePerReactivePower.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:VoltagePerReactivePower.denominatorUnit>([\s\S]*?)<\/cim:VoltagePerReactivePower.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:VoltagePerReactivePower.multiplier>([\s\S]*?)<\/cim:VoltagePerReactivePower.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:VoltagePerReactivePower.unit>([\s\S]*?)<\/cim:VoltagePerReactivePower.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:VoltagePerReactivePower.value>([\s\S]*?)<\/cim:VoltagePerReactivePower.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.VoltagePerReactivePower;
            if (null == bucket)
                context.parsed.VoltagePerReactivePower = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Time specified in hours.
         *
         */
        function parse_Hours (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Hours";
            base.parse_element (/<cim:Hours.multiplier>([\s\S]*?)<\/cim:Hours.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Hours.unit>([\s\S]*?)<\/cim:Hours.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Hours.value>([\s\S]*?)<\/cim:Hours.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Hours;
            if (null == bucket)
                context.parsed.Hours = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Distance per unit of time.
         *
         */
        function parse_Speed (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Speed";
            base.parse_element (/<cim:Speed.denominatorMultiplier>([\s\S]*?)<\/cim:Speed.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Speed.denominatorUnit>([\s\S]*?)<\/cim:Speed.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:Speed.multiplier>([\s\S]*?)<\/cim:Speed.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Speed.unit>([\s\S]*?)<\/cim:Speed.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Speed.value>([\s\S]*?)<\/cim:Speed.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Speed;
            if (null == bucket)
                context.parsed.Speed = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Unit of displacement relative a reference position, hence can be negative.
         *
         */
        function parse_Displacement (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Displacement";
            base.parse_element (/<cim:Displacement.multiplier>([\s\S]*?)<\/cim:Displacement.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Displacement.unit>([\s\S]*?)<\/cim:Displacement.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Displacement.value>([\s\S]*?)<\/cim:Displacement.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Displacement;
            if (null == bucket)
                context.parsed.Displacement = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A string consisting of a sequence of characters.
         *
         * The character encoding is UTF-8. The string length is unspecified and unlimited.
         *
         */
        function parse_String (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "String";
            bucket = context.parsed.String;
            if (null == bucket)
                context.parsed.String = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * The units defined for usage in the CIM.
         *
         */
        function parse_UnitSymbol (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "UnitSymbol";
            /**
             * Apparent power in volt ampere.
             *
             */
            base.parse_element (/<cim:UnitSymbol.VA>([\s\S]*?)<\/cim:UnitSymbol.VA>/g, obj, "VA", base.to_string, sub, context);

            /**
             * Active power in watt.
             *
             */
            base.parse_element (/<cim:UnitSymbol.W>([\s\S]*?)<\/cim:UnitSymbol.W>/g, obj, "W", base.to_string, sub, context);

            /**
             * Reactive power in volt ampere reactive.
             *
             */
            base.parse_element (/<cim:UnitSymbol.VAr>([\s\S]*?)<\/cim:UnitSymbol.VAr>/g, obj, "VAr", base.to_string, sub, context);

            /**
             * Apparent energy in volt ampere hours.
             *
             */
            base.parse_element (/<cim:UnitSymbol.VAh>([\s\S]*?)<\/cim:UnitSymbol.VAh>/g, obj, "VAh", base.to_string, sub, context);

            /**
             * Real energy in what hours.
             *
             */
            base.parse_element (/<cim:UnitSymbol.Wh>([\s\S]*?)<\/cim:UnitSymbol.Wh>/g, obj, "Wh", base.to_string, sub, context);

            /**
             * Reactive energy in volt ampere reactive hours.
             *
             */
            base.parse_element (/<cim:UnitSymbol.VArh>([\s\S]*?)<\/cim:UnitSymbol.VArh>/g, obj, "VArh", base.to_string, sub, context);

            /**
             * Voltage in volt.
             *
             */
            base.parse_element (/<cim:UnitSymbol.V>([\s\S]*?)<\/cim:UnitSymbol.V>/g, obj, "V", base.to_string, sub, context);

            /**
             * Resistance in ohm.
             *
             */
            base.parse_element (/<cim:UnitSymbol.ohm>([\s\S]*?)<\/cim:UnitSymbol.ohm>/g, obj, "ohm", base.to_string, sub, context);

            /**
             * Current in ampere.
             *
             */
            base.parse_element (/<cim:UnitSymbol.A>([\s\S]*?)<\/cim:UnitSymbol.A>/g, obj, "A", base.to_string, sub, context);

            /**
             * Capacitance in farad.
             *
             */
            base.parse_element (/<cim:UnitSymbol.F>([\s\S]*?)<\/cim:UnitSymbol.F>/g, obj, "F", base.to_string, sub, context);

            /**
             * Inductance in henry.
             *
             */
            base.parse_element (/<cim:UnitSymbol.H>([\s\S]*?)<\/cim:UnitSymbol.H>/g, obj, "H", base.to_string, sub, context);

            /**
             * Relative temperature in degrees Celsius.
             *
             * In the SI unit system the symbol is �C. Electric charge is measured in coulomb that has the unit symbol C. To distinguish degree Celsius form coulomb the symbol used in the UML is degC. Reason for not using �C is the special character � is difficult to manage in software.
             *
             */
            base.parse_element (/<cim:UnitSymbol.degC>([\s\S]*?)<\/cim:UnitSymbol.degC>/g, obj, "degC", base.to_string, sub, context);

            /**
             * Time in seconds.
             *
             */
            base.parse_element (/<cim:UnitSymbol.s>([\s\S]*?)<\/cim:UnitSymbol.s>/g, obj, "s", base.to_string, sub, context);

            /**
             * Time in minutes.
             *
             */
            base.parse_element (/<cim:UnitSymbol.min>([\s\S]*?)<\/cim:UnitSymbol.min>/g, obj, "min", base.to_string, sub, context);

            /**
             * Time in hours.
             *
             */
            base.parse_element (/<cim:UnitSymbol.h>([\s\S]*?)<\/cim:UnitSymbol.h>/g, obj, "h", base.to_string, sub, context);

            /**
             * Plane angle in degrees.
             *
             */
            base.parse_element (/<cim:UnitSymbol.deg>([\s\S]*?)<\/cim:UnitSymbol.deg>/g, obj, "deg", base.to_string, sub, context);

            /**
             * Plane angle in radians.
             *
             */
            base.parse_element (/<cim:UnitSymbol.rad>([\s\S]*?)<\/cim:UnitSymbol.rad>/g, obj, "rad", base.to_string, sub, context);

            /**
             * Energy in joule.
             *
             */
            base.parse_element (/<cim:UnitSymbol.J>([\s\S]*?)<\/cim:UnitSymbol.J>/g, obj, "J", base.to_string, sub, context);

            /**
             * Force in newton.
             *
             */
            base.parse_element (/<cim:UnitSymbol.N>([\s\S]*?)<\/cim:UnitSymbol.N>/g, obj, "N", base.to_string, sub, context);

            /**
             * Conductance in siemens.
             *
             */
            base.parse_element (/<cim:UnitSymbol.S>([\s\S]*?)<\/cim:UnitSymbol.S>/g, obj, "S", base.to_string, sub, context);

            /**
             * Dimension less quantity, e.g. count, per unit, etc.
             *
             */
            base.parse_element (/<cim:UnitSymbol.none>([\s\S]*?)<\/cim:UnitSymbol.none>/g, obj, "none", base.to_string, sub, context);

            /**
             * Frequency in hertz.
             *
             */
            base.parse_element (/<cim:UnitSymbol.Hz>([\s\S]*?)<\/cim:UnitSymbol.Hz>/g, obj, "Hz", base.to_string, sub, context);

            /**
             * Mass in gram.
             *
             */
            base.parse_element (/<cim:UnitSymbol.g>([\s\S]*?)<\/cim:UnitSymbol.g>/g, obj, "g", base.to_string, sub, context);

            /**
             * Pressure in pascal (n/m2).
             *
             */
            base.parse_element (/<cim:UnitSymbol.Pa>([\s\S]*?)<\/cim:UnitSymbol.Pa>/g, obj, "Pa", base.to_string, sub, context);

            /**
             * Length in meter.
             *
             */
            base.parse_element (/<cim:UnitSymbol.m>([\s\S]*?)<\/cim:UnitSymbol.m>/g, obj, "m", base.to_string, sub, context);

            /**
             * Area in square meters.
             *
             */
            base.parse_element (/<cim:UnitSymbol.m2>([\s\S]*?)<\/cim:UnitSymbol.m2>/g, obj, "m2", base.to_string, sub, context);

            /**
             * Volume in cubic meters.
             *
             */
            base.parse_element (/<cim:UnitSymbol.m3>([\s\S]*?)<\/cim:UnitSymbol.m3>/g, obj, "m3", base.to_string, sub, context);

            bucket = context.parsed.UnitSymbol;
            if (null == bucket)
                context.parsed.UnitSymbol = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Number of revolutions per second.
         *
         */
        function parse_RotationSpeed (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "RotationSpeed";
            base.parse_element (/<cim:RotationSpeed.denominatorMultiplier>([\s\S]*?)<\/cim:RotationSpeed.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:RotationSpeed.denominatorUnit>([\s\S]*?)<\/cim:RotationSpeed.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:RotationSpeed.multiplier>([\s\S]*?)<\/cim:RotationSpeed.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:RotationSpeed.unit>([\s\S]*?)<\/cim:RotationSpeed.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:RotationSpeed.value>([\s\S]*?)<\/cim:RotationSpeed.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.RotationSpeed;
            if (null == bucket)
                context.parsed.RotationSpeed = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Resistance (real part of impedance).
         *
         */
        function parse_Resistance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Resistance";
            base.parse_element (/<cim:Resistance.multiplier>([\s\S]*?)<\/cim:Resistance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Resistance.unit>([\s\S]*?)<\/cim:Resistance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Resistance.value>([\s\S]*?)<\/cim:Resistance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Resistance;
            if (null == bucket)
                context.parsed.Resistance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * The weight of an object.
         *
         */
        function parse_Weight (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Weight";
            base.parse_element (/<cim:Weight.multiplier>([\s\S]*?)<\/cim:Weight.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Weight.unit>([\s\S]*?)<\/cim:Weight.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Weight.value>([\s\S]*?)<\/cim:Weight.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Weight;
            if (null == bucket)
                context.parsed.Weight = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Pressure in Pascal.
         *
         */
        function parse_Pressure (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Pressure";
            base.parse_element (/<cim:Pressure.multiplier>([\s\S]*?)<\/cim:Pressure.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Pressure.unit>([\s\S]*?)<\/cim:Pressure.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Pressure.value>([\s\S]*?)<\/cim:Pressure.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Pressure;
            if (null == bucket)
                context.parsed.Pressure = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Real part of admittance per unit of length.
         *
         */
        function parse_ConductancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ConductancePerLength";
            base.parse_element (/<cim:ConductancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:ConductancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ConductancePerLength.denominatorUnit>([\s\S]*?)<\/cim:ConductancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:ConductancePerLength.multiplier>([\s\S]*?)<\/cim:ConductancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:ConductancePerLength.unit>([\s\S]*?)<\/cim:ConductancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:ConductancePerLength.value>([\s\S]*?)<\/cim:ConductancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.ConductancePerLength;
            if (null == bucket)
                context.parsed.ConductancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A type with the value space "true" and "false".
         *
         */
        function parse_Boolean (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Boolean";
            bucket = context.parsed.Boolean;
            if (null == bucket)
                context.parsed.Boolean = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Imaginary part of admittance per unit of length.
         *
         */
        function parse_SusceptancePerLength (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "SusceptancePerLength";
            base.parse_element (/<cim:SusceptancePerLength.denominatorMultiplier>([\s\S]*?)<\/cim:SusceptancePerLength.denominatorMultiplier>/g, obj, "denominatorMultiplier", base.to_string, sub, context);

            base.parse_element (/<cim:SusceptancePerLength.denominatorUnit>([\s\S]*?)<\/cim:SusceptancePerLength.denominatorUnit>/g, obj, "denominatorUnit", base.to_string, sub, context);

            base.parse_element (/<cim:SusceptancePerLength.multiplier>([\s\S]*?)<\/cim:SusceptancePerLength.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:SusceptancePerLength.unit>([\s\S]*?)<\/cim:SusceptancePerLength.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:SusceptancePerLength.value>([\s\S]*?)<\/cim:SusceptancePerLength.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.SusceptancePerLength;
            if (null == bucket)
                context.parsed.SusceptancePerLength = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Quantity with string value (when it is not important whether it is an integral or a floating point number) and associated unit information.
         *
         */
        function parse_StringQuantity (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "StringQuantity";
            base.parse_element (/<cim:StringQuantity.multiplier>([\s\S]*?)<\/cim:StringQuantity.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:StringQuantity.unit>([\s\S]*?)<\/cim:StringQuantity.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:StringQuantity.value>([\s\S]*?)<\/cim:StringQuantity.value>/g, obj, "value", base.to_string, sub, context);

            bucket = context.parsed.StringQuantity;
            if (null == bucket)
                context.parsed.StringQuantity = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Quantity with integer value and associated unit information.
         *
         */
        function parse_IntegerQuantity (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "IntegerQuantity";
            base.parse_element (/<cim:IntegerQuantity.multiplier>([\s\S]*?)<\/cim:IntegerQuantity.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:IntegerQuantity.unit>([\s\S]*?)<\/cim:IntegerQuantity.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:IntegerQuantity.value>([\s\S]*?)<\/cim:IntegerQuantity.value>/g, obj, "value", base.to_string, sub, context);

            bucket = context.parsed.IntegerQuantity;
            if (null == bucket)
                context.parsed.IntegerQuantity = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Interval between two times.
         *
         */
        function parse_TimeInterval (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "TimeInterval";
            /**
             * End time of this interval.
             *
             */
            base.parse_element (/<cim:TimeInterval.end>([\s\S]*?)<\/cim:TimeInterval.end>/g, obj, "end", base.to_string, sub, context);

            /**
             * Start time of this interval.
             *
             */
            base.parse_element (/<cim:TimeInterval.start>([\s\S]*?)<\/cim:TimeInterval.start>/g, obj, "start", base.to_string, sub, context);

            bucket = context.parsed.TimeInterval;
            if (null == bucket)
                context.parsed.TimeInterval = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Inductive part of reactance (imaginary part of impedance), at rated frequency.
         *
         */
        function parse_Inductance (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "Inductance";
            base.parse_element (/<cim:Inductance.multiplier>([\s\S]*?)<\/cim:Inductance.multiplier>/g, obj, "multiplier", base.to_string, sub, context);

            base.parse_element (/<cim:Inductance.unit>([\s\S]*?)<\/cim:Inductance.unit>/g, obj, "unit", base.to_string, sub, context);

            base.parse_element (/<cim:Inductance.value>([\s\S]*?)<\/cim:Inductance.value>/g, obj, "value", base.to_float, sub, context);

            bucket = context.parsed.Inductance;
            if (null == bucket)
                context.parsed.Inductance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        return (
            {
                parse_Reactance: parse_Reactance,
                parse_Duration: parse_Duration,
                parse_MonthDayInterval: parse_MonthDayInterval,
                parse_Volume: parse_Volume,
                parse_PU: parse_PU,
                parse_Seconds: parse_Seconds,
                parse_AngleDegrees: parse_AngleDegrees,
                parse_String: parse_String,
                parse_Frequency: parse_Frequency,
                parse_Capacitance: parse_Capacitance,
                parse_DateTimeInterval: parse_DateTimeInterval,
                parse_DateTime: parse_DateTime,
                parse_WaterLevel: parse_WaterLevel,
                parse_ReactancePerLength: parse_ReactancePerLength,
                parse_Conductance: parse_Conductance,
                parse_DateInterval: parse_DateInterval,
                parse_Pressure: parse_Pressure,
                parse_Hours: parse_Hours,
                parse_CostPerEnergyUnit: parse_CostPerEnergyUnit,
                parse_Displacement: parse_Displacement,
                parse_TimeInterval: parse_TimeInterval,
                parse_VolumeFlowRate: parse_VolumeFlowRate,
                parse_CostPerVolume: parse_CostPerVolume,
                parse_Money: parse_Money,
                parse_Time: parse_Time,
                parse_Boolean: parse_Boolean,
                parse_ConductancePerLength: parse_ConductancePerLength,
                parse_ActivePowerPerCurrentFlow: parse_ActivePowerPerCurrentFlow,
                parse_AngleRadians: parse_AngleRadians,
                parse_StringQuantity: parse_StringQuantity,
                parse_Admittance: parse_Admittance,
                parse_Temperature: parse_Temperature,
                parse_Integer: parse_Integer,
                parse_DecimalQuantity: parse_DecimalQuantity,
                parse_Minutes: parse_Minutes,
                parse_MonthDay: parse_MonthDay,
                parse_Area: parse_Area,
                parse_ActivePower: parse_ActivePower,
                parse_Date: parse_Date,
                parse_CapacitancePerLength: parse_CapacitancePerLength,
                parse_Impedance: parse_Impedance,
                parse_ResistancePerLength: parse_ResistancePerLength,
                parse_Inductance: parse_Inductance,
                parse_ReactivePower: parse_ReactivePower,
                parse_PerCent: parse_PerCent,
                parse_CostRate: parse_CostRate,
                parse_VoltagePerReactivePower: parse_VoltagePerReactivePower,
                parse_ActivePowerChangeRate: parse_ActivePowerChangeRate,
                parse_Susceptance: parse_Susceptance,
                parse_ActivePowerPerFrequency: parse_ActivePowerPerFrequency,
                parse_Float: parse_Float,
                parse_Decimal: parse_Decimal,
                parse_InductancePerLength: parse_InductancePerLength,
                parse_FloatQuantity: parse_FloatQuantity,
                parse_ApparentPower: parse_ApparentPower,
                parse_Damping: parse_Damping,
                parse_SusceptancePerLength: parse_SusceptancePerLength,
                parse_Length: parse_Length,
                parse_IntegerQuantity: parse_IntegerQuantity,
                parse_Resistance: parse_Resistance,
                parse_Currency: parse_Currency,
                parse_KiloActivePower: parse_KiloActivePower,
                parse_RotationSpeed: parse_RotationSpeed,
                parse_Voltage: parse_Voltage,
                parse_Weight: parse_Weight,
                parse_RealEnergy: parse_RealEnergy,
                parse_Speed: parse_Speed,
                parse_UnitSymbol: parse_UnitSymbol,
                parse_UnitMultiplier: parse_UnitMultiplier,
                parse_CurrentFlow: parse_CurrentFlow
            }
        );
    }
);