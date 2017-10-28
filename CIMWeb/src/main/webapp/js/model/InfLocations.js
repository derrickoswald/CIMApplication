define
(
    ["model/base", "model/Common", "model/Core"],
    function (base, Common, Core)
    {

        /**
         * This class is used for handling the accompanying annotations, time stamp, author, etc. of designs, drawings and maps.
         *
         * A red line can be associated with any Location object.
         *
         */
        function parse_RedLine (context, sub)
        {
            var obj;
            var bucket;

            obj = Core.parse_IdentifiedObject (context, sub);
            obj.cls = "RedLine";
            base.parse_element (/<cim:RedLine.status>([\s\S]*?)<\/cim:RedLine.status>/g, obj, "status", base.to_string, sub, context);

            bucket = context.parsed.RedLine;
            if (null == bucket)
                context.parsed.RedLine = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Area divided off from other areas.
         *
         * It may be part of the electrical network, a land area where special restrictions apply, weather areas, etc. For weather, it is an area where a set of relatively homogenous weather measurements apply.
         *
         */
        function parse_Zone (context, sub)
        {
            var obj;
            var bucket;

            obj = Common.parse_Location (context, sub);
            obj.cls = "Zone";
            /**
             * Kind of this zone.
             *
             */
            base.parse_element (/<cim:Zone.kind>([\s\S]*?)<\/cim:Zone.kind>/g, obj, "kind", base.to_string, sub, context);

            bucket = context.parsed.Zone;
            if (null == bucket)
                context.parsed.Zone = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Kind of zone.
         *
         */
        function parse_ZoneKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "ZoneKind";
            base.parse_element (/<cim:ZoneKind.electricalNetwork>([\s\S]*?)<\/cim:ZoneKind.electricalNetwork>/g, obj, "electricalNetwork", base.to_string, sub, context);

            base.parse_element (/<cim:ZoneKind.specialRestrictionLand>([\s\S]*?)<\/cim:ZoneKind.specialRestrictionLand>/g, obj, "specialRestrictionLand", base.to_string, sub, context);

            base.parse_element (/<cim:ZoneKind.weatherZone>([\s\S]*?)<\/cim:ZoneKind.weatherZone>/g, obj, "weatherZone", base.to_string, sub, context);

            base.parse_element (/<cim:ZoneKind.other>([\s\S]*?)<\/cim:ZoneKind.other>/g, obj, "other", base.to_string, sub, context);

            bucket = context.parsed.ZoneKind;
            if (null == bucket)
                context.parsed.ZoneKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A grant provides a right, as defined by type, for a parcel of land.
         *
         * Note that the association to Location, Asset, Organisation, etc. for the Grant is inherited from Agreement, a type of Document.
         *
         */
        function parse_LocationGrant (context, sub)
        {
            var obj;
            var bucket;

            obj = Common.parse_Agreement (context, sub);
            obj.cls = "LocationGrant";
            /**
             * Property related information that describes the Grant's land parcel.
             *
             * For example, it may be a deed book number, deed book page number, and parcel number.
             *
             */
            base.parse_element (/<cim:LocationGrant.propertyData>([\s\S]*?)<\/cim:LocationGrant.propertyData>/g, obj, "propertyData", base.to_string, sub, context);

            /**
             * Land property this location grant applies to.
             *
             */
            base.parse_attribute (/<cim:LocationGrant.LandProperty\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "LandProperty", sub, context, true);

            bucket = context.parsed.LocationGrant;
            if (null == bucket)
                context.parsed.LocationGrant = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Route that is followed, for example by service crews.
         *
         */
        function parse_Route (context, sub)
        {
            var obj;
            var bucket;

            obj = Core.parse_IdentifiedObject (context, sub);
            obj.cls = "Route";
            base.parse_element (/<cim:Route.status>([\s\S]*?)<\/cim:Route.status>/g, obj, "status", base.to_string, sub, context);

            /**
             * Classification by utility's work management standards and practices.
             *
             */
            base.parse_element (/<cim:Route.type>([\s\S]*?)<\/cim:Route.type>/g, obj, "type", base.to_string, sub, context);

            bucket = context.parsed.Route;
            if (null == bucket)
                context.parsed.Route = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Kind of (land) property.
         *
         */
        function parse_LandPropertyKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "LandPropertyKind";
            /**
             * Site enclosed within a building.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.building>([\s\S]*?)<\/cim:LandPropertyKind.building>/g, obj, "building", base.to_string, sub, context);

            /**
             * Site with a customer.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.customerPremise>([\s\S]*?)<\/cim:LandPropertyKind.customerPremise>/g, obj, "customerPremise", base.to_string, sub, context);

            /**
             * Storehouse for supplies that also serves as a station for supporting crews.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.depot>([\s\S]*?)<\/cim:LandPropertyKind.depot>/g, obj, "depot", base.to_string, sub, context);

            /**
             * Place of storage (e.g., a warehouse) to put aside, or accumulate, material and equipment for use when needed.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.store>([\s\S]*?)<\/cim:LandPropertyKind.store>/g, obj, "store", base.to_string, sub, context);

            /**
             * Transmission network switchyard.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.substation>([\s\S]*?)<\/cim:LandPropertyKind.substation>/g, obj, "substation", base.to_string, sub, context);

            /**
             * Substation where the distribution and transmission networks meet and hence have mixed ownership and mixed operational control.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.gridSupplyPoint>([\s\S]*?)<\/cim:LandPropertyKind.gridSupplyPoint>/g, obj, "gridSupplyPoint", base.to_string, sub, context);

            /**
             * Property owned or used by an external party that is not a customer.
             *
             */
            base.parse_element (/<cim:LandPropertyKind.external>([\s\S]*?)<\/cim:LandPropertyKind.external>/g, obj, "external", base.to_string, sub, context);

            bucket = context.parsed.LandPropertyKind;
            if (null == bucket)
                context.parsed.LandPropertyKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Demographic kind of a land property.
         *
         */
        function parse_DemographicKind (context, sub)
        {
            var obj;
            var bucket;

            obj = base.parse_Element (context, sub);
            obj.cls = "DemographicKind";
            base.parse_element (/<cim:DemographicKind.urban>([\s\S]*?)<\/cim:DemographicKind.urban>/g, obj, "urban", base.to_string, sub, context);

            base.parse_element (/<cim:DemographicKind.rural>([\s\S]*?)<\/cim:DemographicKind.rural>/g, obj, "rural", base.to_string, sub, context);

            base.parse_element (/<cim:DemographicKind.other>([\s\S]*?)<\/cim:DemographicKind.other>/g, obj, "other", base.to_string, sub, context);

            bucket = context.parsed.DemographicKind;
            if (null == bucket)
                context.parsed.DemographicKind = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * Information about a particular piece of (land) property such as its use.
         *
         * Ownership of the property may be determined through associations to Organisations and/or ErpPersons.
         *
         */
        function parse_LandProperty (context, sub)
        {
            var obj;
            var bucket;

            obj = Core.parse_IdentifiedObject (context, sub);
            obj.cls = "LandProperty";
            /**
             * Demographics around the site.
             *
             */
            base.parse_element (/<cim:LandProperty.demographicKind>([\s\S]*?)<\/cim:LandProperty.demographicKind>/g, obj, "demographicKind", base.to_string, sub, context);

            /**
             * Reference allocated by the governing organisation (such as municipality) to this piece of land that has a formal reference to Surveyor General's records.
             *
             * The governing organisation is specified in associated Organisation.
             *
             */
            base.parse_element (/<cim:LandProperty.externalRecordReference>([\s\S]*?)<\/cim:LandProperty.externalRecordReference>/g, obj, "externalRecordReference", base.to_string, sub, context);

            /**
             * Kind of (land) property, categorised according to its main functional use from the utility's perspective.
             *
             */
            base.parse_element (/<cim:LandProperty.kind>([\s\S]*?)<\/cim:LandProperty.kind>/g, obj, "kind", base.to_string, sub, context);

            base.parse_element (/<cim:LandProperty.status>([\s\S]*?)<\/cim:LandProperty.status>/g, obj, "status", base.to_string, sub, context);

            bucket = context.parsed.LandProperty;
            if (null == bucket)
                context.parsed.LandProperty = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        /**
         * A right-of-way (ROW) is for land where it is lawful to use for a public road, an electric power line, etc.
         *
         * Note that the association to Location, Asset, Organisation, etc. for the Grant is inherited from Agreement, a type of Document.
         *
         */
        function parse_RightOfWay (context, sub)
        {
            var obj;
            var bucket;

            obj = Common.parse_Agreement (context, sub);
            obj.cls = "RightOfWay";
            /**
             * Property related information that describes the ROW's land parcel.
             *
             * For example, it may be a deed book number, deed book page number, and parcel number.
             *
             */
            base.parse_element (/<cim:RightOfWay.propertyData>([\s\S]*?)<\/cim:RightOfWay.propertyData>/g, obj, "propertyData", base.to_string, sub, context);

            bucket = context.parsed.RightOfWay;
            if (null == bucket)
                context.parsed.RightOfWay = bucket = {};
            bucket[obj.id] = obj;

            return (obj);
        }

        return (
            {
                parse_LandPropertyKind: parse_LandPropertyKind,
                parse_Route: parse_Route,
                parse_LandProperty: parse_LandProperty,
                parse_DemographicKind: parse_DemographicKind,
                parse_ZoneKind: parse_ZoneKind,
                parse_RightOfWay: parse_RightOfWay,
                parse_RedLine: parse_RedLine,
                parse_Zone: parse_Zone,
                parse_LocationGrant: parse_LocationGrant
            }
        );
    }
);