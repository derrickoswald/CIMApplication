define
(
    ["model/base", "model/StandardModels"],
    /**
     * For conventional power generating units (e.g., thermal, hydro, combustion turbine), a synchronous machine model represents the electrical characteristics of the generator and the mechanical characteristics of the turbine-generator rotational inertia.
     *
     * Large industrial motors or groups of similar motors may be represented by individual motor models which are represented as <b>generators with negative active power</b> in the static (power flow) data.
     *
     */
    function (base, StandardModels)
    {

        /**
         * Synchronous machine detailed modelling types are defined by the combination of the attributes SynchronousMachineTimeConstantReactance.modelType and SynchronousMachineTimeConstantReactance.rotorType.
         * <b>
         * </b><b>Parameter notes:</b>
         * <ol>
         * <li>The �p� in the time-related attribute names is a substitution for a �prime� in the usual parameter notation, e.g. tpdo refers to <b>T'do</b>.</li>
         * </ol>
         * <b>
         * </b>The parameters used for models expressed in time constant reactance form include:
         * <ul>
         * <li>RotatingMachine.ratedS (MVAbase)</li>
         * <li>RotatingMachineDynamics.damping (D)</li>
         * <li>RotatingMachineDynamics.inertia (H)</li>
         * <li>RotatingMachineDynamics.saturationFactor (S1)</li>
         * <li>RotatingMachineDynamics.saturationFactor120 (S12)</li>
         * <li>RotatingMachineDynamics.statorLeakageReactance (Xl)</li>
         * <li>RotatingMachineDynamics.statorResistance (Rs)</li>
         * <li>SynchronousMachineTimeConstantReactance.ks (Ks)</li>
         * <li>SynchronousMachineDetailed.saturationFactorQAxis (S1q)</li>
         * <li>SynchronousMachineDetailed.saturationFactor120QAxis (S12q)</li>
         * <li>SynchronousMachineDetailed.efdBaseRatio</li>
         * <li>SynchronousMachineDetailed.ifdBaseType</li>
         * <li>SynchronousMachineDetailed.ifdBaseValue, if present</li>
         * <li>.xDirectSync (Xd)</li>
         * <li>.xDirectTrans (X'd)</li>
         * <li>.xDirectSubtrans (X''d)</li>
         * <li>.xQuadSync (Xq)</li>
         * <li>.xQuadTrans (X'q)</li>
         * <li>.xQuadSubtrans (X''q)</li>
         * <li>.tpdo (T'do)</li>
         * <li>.tppdo (T''do)</li>
         * <li>.tpqo (T'qo)</li>
         * <li>.tppqo (T''qo)</li>
         * <li>.tc.</li>
         *
         * </ul>
         *
         */
        function parse_SynchronousMachineTimeConstantReactance (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_SynchronousMachineDetailed (context, sub);
            obj.cls = "SynchronousMachineTimeConstantReactance";
            /**
             * Saturation loading correction factor (Ks) (&gt;= 0).
             *
             * Used only by Type J model.  Typical Value = 0.
             *
             */
            obj["ks"] = base.to_float (base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.ks>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.ks>/g, sub, context, true));
            /**
             * Type of synchronous machine model used in Dynamic simulation applications.
             *
             */
            obj["modelType"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.modelType>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.modelType>/g, sub, context, true);
            /**
             * Type of rotor on physical machine.
             *
             */
            obj["rotorType"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.rotorType>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.rotorType>/g, sub, context, true);
            /**
             * Damping time constant for �Canay� reactance.
             *
             * Typical Value = 0.
             *
             */
            obj["tc"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.tc>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.tc>/g, sub, context, true);
            /**
             * Direct-axis transient rotor time constant (T'do) (&gt; T''do).
             *
             * Typical Value = 5.
             *
             */
            obj["tpdo"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.tpdo>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.tpdo>/g, sub, context, true);
            /**
             * Direct-axis subtransient rotor time constant (T''do) (&gt; 0).
             *
             * Typical Value = 0.03.
             *
             */
            obj["tppdo"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.tppdo>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.tppdo>/g, sub, context, true);
            /**
             * Quadrature-axis subtransient rotor time constant (T''qo) (&gt; 0).
             *
             * Typical Value = 0.03.
             *
             */
            obj["tppqo"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.tppqo>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.tppqo>/g, sub, context, true);
            /**
             * Quadrature-axis transient rotor time constant (T'qo) (&gt; T''qo).
             *
             * Typical Value = 0.5.
             *
             */
            obj["tpqo"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.tpqo>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.tpqo>/g, sub, context, true);
            /**
             * Direct-axis subtransient reactance (unsaturated) (X''d) (&gt; Xl).
             *
             * Typical Value = 0.2.
             *
             */
            obj["xDirectSubtrans"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xDirectSubtrans>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xDirectSubtrans>/g, sub, context, true);
            /**
             * Direct-axis synchronous reactance (Xd) (&gt;= X'd).
             *
             * The quotient of a sustained value of that AC component of armature voltage that is produced by the total direct-axis flux due to direct-axis armature current and the value of the AC component of this current, the machine running at rated speed. Typical Value = 1.8.
             *
             */
            obj["xDirectSync"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xDirectSync>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xDirectSync>/g, sub, context, true);
            /**
             * Direct-axis transient reactance (unsaturated) (X'd) (&gt; =X''d).
             *
             * Typical Value = 0.5.
             *
             */
            obj["xDirectTrans"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xDirectTrans>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xDirectTrans>/g, sub, context, true);
            /**
             * Quadrature-axis subtransient reactance (X''q) (&gt; Xl).
             *
             * Typical Value = 0.2.
             *
             */
            obj["xQuadSubtrans"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xQuadSubtrans>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xQuadSubtrans>/g, sub, context, true);
            /**
             * Quadrature-axis synchronous reactance (Xq) (&gt; =X'q).
             *
             * The ratio of the component of reactive armature voltage, due to the quadrature-axis component of armature current, to this component of current, under steady state conditions and at rated frequency.  Typical Value = 1.6.
             *
             */
            obj["xQuadSync"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xQuadSync>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xQuadSync>/g, sub, context, true);
            /**
             * Quadrature-axis transient reactance (X'q) (&gt; =X''q).
             *
             * Typical Value = 0.3.
             *
             */
            obj["xQuadTrans"] = base.parse_element (/<cim:SynchronousMachineTimeConstantReactance.xQuadTrans>([\s\S]*?)<\/cim:SynchronousMachineTimeConstantReactance.xQuadTrans>/g, sub, context, true);
            bucket = context.parsed.SynchronousMachineTimeConstantReactance;
            if (null == bucket)
                context.parsed.SynchronousMachineTimeConstantReactance = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Synchronous machine whose behaviour is described by reference to a standard model expressed in one of the following forms:
         * <ul>
         * <li>simplified (or classical), where a group of generators or motors is not modelled in detail</li>
         * </ul>
         * <ul>
         * <li>detailed, in equivalent circuit form</li>
         * <li>detailed, in time constant reactance form</li>
         * </ul>
         * <font color="#0f0f0f">or by definition of a user-defined model.</font>
         * <font color="#0f0f0f">
         * </font><font color="#0f0f0f"><b>Note:</b>  It is a common practice to represent small generators by a negative load rather than by a dynamic generator model when performing dynamics simulations.
         *
         * In this case a SynchronousMachine in the static model is not represented by anything in the dynamics model, instead it is treated as ordinary load.</font>
         *
         */
        function parse_SynchronousMachineDynamics (context, sub)
        {
            var obj;
            var bucket;

            obj = StandardModels.parse_RotatingMachineDynamics (context, sub);
            obj.cls = "SynchronousMachineDynamics";
            /**
             * Mechanical load model associated with this synchronous machine model.
             *
             */
            obj["MechanicalLoadDynamics"] = base.parse_attribute (/<cim:SynchronousMachineDynamics.MechanicalLoadDynamics\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, sub, context, true);
            /**
             * Excitation system model associated with this synchronous machine model.
             *
             */
            obj["ExcitationSystemDynamics"] = base.parse_attribute (/<cim:SynchronousMachineDynamics.ExcitationSystemDynamics\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, sub, context, true);
            /**
             * Synchronous machine to which synchronous machine dynamics model applies.
             *
             */
            obj["SynchronousMachine"] = base.parse_attribute (/<cim:SynchronousMachineDynamics.SynchronousMachine\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, sub, context, true);
            bucket = context.parsed.SynchronousMachineDynamics;
            if (null == bucket)
                context.parsed.SynchronousMachineDynamics = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * All synchronous machine detailed types use a subset of the same data parameters and input/output variables.
         *
         * The several variations differ in the following ways:
         *
         */
        function parse_SynchronousMachineDetailed (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_SynchronousMachineDynamics (context, sub);
            obj.cls = "SynchronousMachineDetailed";
            /**
             * Ratio (Exciter voltage/Generator voltage) of Efd bases of exciter and generator models.
             *
             * Typical Value = 1.
             *
             */
            obj["efdBaseRatio"] = base.to_float (base.parse_element (/<cim:SynchronousMachineDetailed.efdBaseRatio>([\s\S]*?)<\/cim:SynchronousMachineDetailed.efdBaseRatio>/g, sub, context, true));
            /**
             * Excitation base system mode.
             *
             * It should be equal to the value of WLMDV given by the user. WLMDV is the per unit ratio between the field voltage and the excitation current: Efd = WLMDV*Ifd. Typical Value = ifag.
             *
             */
            obj["ifdBaseType"] = base.parse_element (/<cim:SynchronousMachineDetailed.ifdBaseType>([\s\S]*?)<\/cim:SynchronousMachineDetailed.ifdBaseType>/g, sub, context, true);
            /**
             * Q-axis saturation factor at 120% of rated terminal voltage (S12q) (&gt;=S1q).
             *
             * Typical Value = 0.12.
             *
             */
            obj["saturationFactor120QAxis"] = base.to_float (base.parse_element (/<cim:SynchronousMachineDetailed.saturationFactor120QAxis>([\s\S]*?)<\/cim:SynchronousMachineDetailed.saturationFactor120QAxis>/g, sub, context, true));
            /**
             * Q-axis saturation factor at rated terminal voltage (S1q) (&gt;= 0).
             *
             * Typical Value = 0.02.
             *
             */
            obj["saturationFactorQAxis"] = base.to_float (base.parse_element (/<cim:SynchronousMachineDetailed.saturationFactorQAxis>([\s\S]*?)<\/cim:SynchronousMachineDetailed.saturationFactorQAxis>/g, sub, context, true));
            bucket = context.parsed.SynchronousMachineDetailed;
            if (null == bucket)
                context.parsed.SynchronousMachineDetailed = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * The electrical equations for all variations of the synchronous models are based on the SynchronousEquivalentCircuit diagram for the direct and quadrature axes.
         * 
         * <b>Equations for conversion between Equivalent Circuit and Time Constant Reactance forms:</b>
         * <b>Xd</b> = <b>Xad</b> + <b>Xl</b>
         * <b>X�d</b> = <b>Xl</b> + <b>Xad</b> * <b>Xfd</b> / (<b>Xad</b> + <b>Xfd</b>)
         * <b>X�d</b> = <b>Xl</b> + <b>Xad</b> * <b>Xfd </b>* <b>X1d</b> / (<b>Xad</b> * <b>Xfd</b> + <b>Xad</b> * <b>X1d</b> + <b>Xfd</b> * <b>X1d</b>)
         * <b>Xq</b> = <b>Xaq</b> + <b>Xl</b>
         * <b>X�q</b> = <b>Xl</b> + <b>Xaq</b> * <b>X1q</b> / (<b>Xaq</b>+ <b>X1q</b>)
         * <b>X�q</b> = <b>Xl</b> + <b>Xaq</b> *<b> X1q</b>* <b>X2q</b> / (<b>Xaq</b> * <b>X1q</b> + <b>Xaq</b> * <b>X2q</b> + <b>X1q</b> * <b>X2q</b>)
         * <b>T�do</b> = (<b>Xad</b> + <b>Xfd</b>) / (<b>omega</b><b><sub>0</sub></b> * <b>Rfd</b>)
         * <b>T�do</b> = (<b>Xad</b> * <b>Xfd</b> + <b>Xad</b> * <b>X1d</b> + <b>Xfd</b> * <b>X1d</b>) / (<b>omega</b><b><sub>0</sub></b> * <b>R1d</b> * (<b>Xad</b> + <b>Xfd</b>)
         * <b>T�qo</b> = (<b>Xaq</b> + <b>X1q</b>) / (<b>omega</b><b><sub>0</sub></b> * <b>R1q</b>)
         * <b>T�qo</b> = (<b>Xaq</b> * <b>X1q</b> + <b>Xaq</b> * <b>X2q</b> + <b>X1q</b> * <b>X2q</b>)/ (<b>omega</b><b><sub>0</sub></b> * <b>R2q</b> * (<b>Xaq</b> + <b>X1q</b>)
         * <b>
         * </b>Same equations using CIM attributes from SynchronousMachineTimeConstantReactance class on left of = sign and SynchronousMachineEquivalentCircuit class on right (except as noted):
         * xDirectSync = xad + RotatingMachineDynamics.statorLeakageReactance
         * xDirectTrans = RotatingMachineDynamics.statorLeakageReactance + xad * xfd / (xad + xfd)
         * xDirectSubtrans = RotatingMachineDynamics.statorLeakageReactance + xad * xfd * x1d / (xad * xfd + xad * x1d + xfd * x1d)
         * xQuadSync = xaq + RotatingMachineDynamics.statorLeakageReactance
         * xQuadTrans = RotatingMachineDynamics.statorLeakageReactance + xaq * x1q / (xaq+ x1q)
         * xQuadSubtrans = RotatingMachineDynamics.statorLeakageReactance + xaq * x1q* x2q / (xaq * x1q + xaq * x2q + x1q * x2q)
         * tpdo = (xad + xfd) / (2*pi*nominal frequency * rfd)
         * tppdo = (xad * xfd + xad * x1d + xfd * x1d) / (2*pi*nominal frequency * r1d * (xad + xfd)
         * tpqo = (xaq + x1q) / (2*pi*nominal frequency * r1q)
         * tppqo = (xaq * x1q + xaq * x2q + x1q * x2q)/ (2*pi*nominal frequency * r2q * (xaq + x1q).
         *
         * Are only valid for a simplified model where "Canay" reactance is zero.
         *
         */
        function parse_SynchronousMachineEquivalentCircuit (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_SynchronousMachineDetailed (context, sub);
            obj.cls = "SynchronousMachineEquivalentCircuit";
            /**
             * D-axis damper 1 winding resistance.
             *
             */
            obj["r1d"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.r1d>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.r1d>/g, sub, context, true);
            /**
             * Q-axis damper 1 winding resistance.
             *
             */
            obj["r1q"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.r1q>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.r1q>/g, sub, context, true);
            /**
             * Q-axis damper 2 winding resistance.
             *
             */
            obj["r2q"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.r2q>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.r2q>/g, sub, context, true);
            /**
             * Field winding resistance.
             *
             */
            obj["rfd"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.rfd>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.rfd>/g, sub, context, true);
            /**
             * D-axis damper 1 winding leakage reactance.
             *
             */
            obj["x1d"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.x1d>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.x1d>/g, sub, context, true);
            /**
             * Q-axis damper 1 winding leakage reactance.
             *
             */
            obj["x1q"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.x1q>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.x1q>/g, sub, context, true);
            /**
             * Q-axis damper 2 winding leakage reactance.
             *
             */
            obj["x2q"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.x2q>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.x2q>/g, sub, context, true);
            /**
             * D-axis mutual reactance.
             *
             */
            obj["xad"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.xad>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.xad>/g, sub, context, true);
            /**
             * Q-axis mutual reactance.
             *
             */
            obj["xaq"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.xaq>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.xaq>/g, sub, context, true);
            /**
             * Differential mutual (�Canay�) reactance.
             *
             */
            obj["xf1d"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.xf1d>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.xf1d>/g, sub, context, true);
            /**
             * Field winding leakage reactance.
             *
             */
            obj["xfd"] = base.parse_element (/<cim:SynchronousMachineEquivalentCircuit.xfd>([\s\S]*?)<\/cim:SynchronousMachineEquivalentCircuit.xfd>/g, sub, context, true);
            bucket = context.parsed.SynchronousMachineEquivalentCircuit;
            if (null == bucket)
                context.parsed.SynchronousMachineEquivalentCircuit = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Type of rotor on physical machine.
         *
         */
        function parse_RotorKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "RotorKind";
            /**
             * Round rotor type of synchronous machine.
             *
             */
            obj["roundRotor"] = base.parse_element (/<cim:RotorKind.roundRotor>([\s\S]*?)<\/cim:RotorKind.roundRotor>/g, sub, context, true);
            /**
             * Salient pole type of synchronous machine.
             *
             */
            obj["salientPole"] = base.parse_element (/<cim:RotorKind.salientPole>([\s\S]*?)<\/cim:RotorKind.salientPole>/g, sub, context, true);
            bucket = context.parsed.RotorKind;
            if (null == bucket)
                context.parsed.RotorKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * The simplified model represents a synchronous generator as a constant internal voltage behind an impedance (<b>Rs</b> + <b>jXp</b>) as shown in the Simplified diagram.
         *
         * Since internal voltage is held constant, there is no <b>Efd</b> input and any excitation system model will be ignored.  There is also no <b>Ifd</b> output.
         *
         */
        function parse_SynchronousMachineSimplified (context, sub)
        {
            var obj;
            var bucket;

            obj = parse_SynchronousMachineDynamics (context, sub);
            obj.cls = "SynchronousMachineSimplified";
            bucket = context.parsed.SynchronousMachineSimplified;
            if (null == bucket)
                context.parsed.SynchronousMachineSimplified = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Type of synchronous machine model used in Dynamic simulation applications.
         *
         */
        function parse_SynchronousMachineModelKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "SynchronousMachineModelKind";
            /**
             * Subtransient synchronous machine model.
             *
             */
            obj["subtransient"] = base.parse_element (/<cim:SynchronousMachineModelKind.subtransient>([\s\S]*?)<\/cim:SynchronousMachineModelKind.subtransient>/g, sub, context, true);
            /**
             * WECC Type F variant of subtransient synchronous machine model.
             *
             */
            obj["subtransientTypeF"] = base.parse_element (/<cim:SynchronousMachineModelKind.subtransientTypeF>([\s\S]*?)<\/cim:SynchronousMachineModelKind.subtransientTypeF>/g, sub, context, true);
            /**
             * WECC Type J variant of subtransient synchronous machine model.
             *
             */
            obj["subtransientTypeJ"] = base.parse_element (/<cim:SynchronousMachineModelKind.subtransientTypeJ>([\s\S]*?)<\/cim:SynchronousMachineModelKind.subtransientTypeJ>/g, sub, context, true);
            /**
             * Simplified version of subtransient synchronous machine model where magnetic coupling between the direct and quadrature axes is ignored.
             *
             */
            obj["subtransientSimplified"] = base.parse_element (/<cim:SynchronousMachineModelKind.subtransientSimplified>([\s\S]*?)<\/cim:SynchronousMachineModelKind.subtransientSimplified>/g, sub, context, true);
            /**
             * Simplified version of a subtransient synchronous machine model with no damper circuit on d-axis.
             *
             */
            obj["subtransientSimplifiedDirectAxis"] = base.parse_element (/<cim:SynchronousMachineModelKind.subtransientSimplifiedDirectAxis>([\s\S]*?)<\/cim:SynchronousMachineModelKind.subtransientSimplifiedDirectAxis>/g, sub, context, true);
            bucket = context.parsed.SynchronousMachineModelKind;
            if (null == bucket)
                context.parsed.SynchronousMachineModelKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Excitation base system mode.
         *
         */
        function parse_IfdBaseKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "IfdBaseKind";
            /**
             * Air gap line mode.  ifdBaseValue is computed, not defined by the user, in this mode.
             *
             */
            obj["ifag"] = base.parse_element (/<cim:IfdBaseKind.ifag>([\s\S]*?)<\/cim:IfdBaseKind.ifag>/g, sub, context, true);
            /**
             * No load system with saturation mode.  ifdBaseValue is computed, not defined by the user, in this mode.
             *
             */
            obj["ifnl"] = base.parse_element (/<cim:IfdBaseKind.ifnl>([\s\S]*?)<\/cim:IfdBaseKind.ifnl>/g, sub, context, true);
            /**
             * Full load system mode.  ifdBaseValue is computed, not defined by the user, in this mode.
             *
             */
            obj["iffl"] = base.parse_element (/<cim:IfdBaseKind.iffl>([\s\S]*?)<\/cim:IfdBaseKind.iffl>/g, sub, context, true);
            bucket = context.parsed.IfdBaseKind;
            if (null == bucket)
                context.parsed.IfdBaseKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        return (
            {
                parse_SynchronousMachineEquivalentCircuit: parse_SynchronousMachineEquivalentCircuit,
                parse_SynchronousMachineSimplified: parse_SynchronousMachineSimplified,
                parse_SynchronousMachineDetailed: parse_SynchronousMachineDetailed,
                parse_SynchronousMachineDynamics: parse_SynchronousMachineDynamics,
                parse_RotorKind: parse_RotorKind,
                parse_IfdBaseKind: parse_IfdBaseKind,
                parse_SynchronousMachineTimeConstantReactance: parse_SynchronousMachineTimeConstantReactance,
                parse_SynchronousMachineModelKind: parse_SynchronousMachineModelKind
            }
        );
    }
);