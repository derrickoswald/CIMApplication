define
(
    ["model/base"],
    /**
     * The package describes how power system model data is managed and evolve over time in projects.
     *
     */
    function (base)
    {

        class PowerSystemProjectSchedule extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PowerSystemProjectSchedule;
                if (null == bucket)
                   cim_data.PowerSystemProjectSchedule = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PowerSystemProjectSchedule[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PowerSystemProjectSchedule";
                base.parse_element (/<cim:PowerSystemProjectSchedule.actualEnd>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.actualEnd>/g, obj, "actualEnd", base.to_datetime, sub, context);
                base.parse_element (/<cim:PowerSystemProjectSchedule.actualStart>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.actualStart>/g, obj, "actualStart", base.to_datetime, sub, context);
                base.parse_element (/<cim:PowerSystemProjectSchedule.scheduledEnd>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.scheduledEnd>/g, obj, "scheduledEnd", base.to_datetime, sub, context);
                base.parse_element (/<cim:PowerSystemProjectSchedule.scheduledStart>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.scheduledStart>/g, obj, "scheduledStart", base.to_datetime, sub, context);
                base.parse_element (/<cim:PowerSystemProjectSchedule.status>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProjectSchedule.stepType>([\s\S]*?)<\/cim:PowerSystemProjectSchedule.stepType>/g, obj, "stepType", base.to_string, sub, context);
                base.parse_attribute (/<cim:PowerSystemProjectSchedule.\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "", sub, context);

                var bucket = context.parsed.PowerSystemProjectSchedule;
                if (null == bucket)
                   context.parsed.PowerSystemProjectSchedule = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PowerSystemProjectSchedule", "actualEnd", base.from_datetime, fields);
                base.export_element (obj, "PowerSystemProjectSchedule", "actualStart", base.from_datetime, fields);
                base.export_element (obj, "PowerSystemProjectSchedule", "scheduledEnd", base.from_datetime, fields);
                base.export_element (obj, "PowerSystemProjectSchedule", "scheduledStart", base.from_datetime, fields);
                base.export_element (obj, "PowerSystemProjectSchedule", "status", base.from_string, fields);
                base.export_element (obj, "PowerSystemProjectSchedule", "stepType", base.from_string, fields);
                base.export_attribute (obj, "PowerSystemProjectSchedule", "", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PowerSystemProjectSchedule_collapse" aria-expanded="true" aria-controls="PowerSystemProjectSchedule_collapse">PowerSystemProjectSchedule</a>
<div id="PowerSystemProjectSchedule_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#actualEnd}}<div><b>actualEnd</b>: {{actualEnd}}</div>{{/actualEnd}}
{{#actualStart}}<div><b>actualStart</b>: {{actualStart}}</div>{{/actualStart}}
{{#scheduledEnd}}<div><b>scheduledEnd</b>: {{scheduledEnd}}</div>{{/scheduledEnd}}
{{#scheduledStart}}<div><b>scheduledStart</b>: {{scheduledStart}}</div>{{/scheduledStart}}
{{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
{{#stepType}}<div><b>stepType</b>: {{stepType}}</div>{{/stepType}}
{{#}}<div><b></b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{}}&quot;);})'>{{}}</a></div>{{/}}
</div>
`
                );
           }        }

        /**
         * State of the project
         *
         */
        class StepKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.StepKind;
                if (null == bucket)
                   cim_data.StepKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.StepKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "StepKind";
                base.parse_element (/<cim:StepKind.planning>([\s\S]*?)<\/cim:StepKind.planning>/g, obj, "planning", base.to_string, sub, context);
                base.parse_element (/<cim:StepKind.design and construction>([\s\S]*?)<\/cim:StepKind.design and construction>/g, obj, "design and construction", base.to_string, sub, context);
                base.parse_element (/<cim:StepKind.commissioning>([\s\S]*?)<\/cim:StepKind.commissioning>/g, obj, "commissioning", base.to_string, sub, context);
                base.parse_element (/<cim:StepKind.... list incomplete, more to come>([\s\S]*?)<\/cim:StepKind.... list incomplete, more to come>/g, obj, "... list incomplete, more to come", base.to_string, sub, context);
                base.parse_element (/<cim:StepKind.revision>([\s\S]*?)<\/cim:StepKind.revision>/g, obj, "revision", base.to_string, sub, context);

                var bucket = context.parsed.StepKind;
                if (null == bucket)
                   context.parsed.StepKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "StepKind", "planning", base.from_string, fields);
                base.export_element (obj, "StepKind", "design and construction", base.from_string, fields);
                base.export_element (obj, "StepKind", "commissioning", base.from_string, fields);
                base.export_element (obj, "StepKind", "... list incomplete, more to come", base.from_string, fields);
                base.export_element (obj, "StepKind", "revision", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#StepKind_collapse" aria-expanded="true" aria-controls="StepKind_collapse">StepKind</a>
<div id="StepKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#planning}}<div><b>planning</b>: {{planning}}</div>{{/planning}}
{{#design and construction}}<div><b>design and construction</b>: {{design and construction}}</div>{{/design and construction}}
{{#commissioning}}<div><b>commissioning</b>: {{commissioning}}</div>{{/commissioning}}
{{#... list incomplete, more to come}}<div><b>... list incomplete, more to come</b>: {{... list incomplete, more to come}}</div>{{/... list incomplete, more to come}}
{{#revision}}<div><b>revision</b>: {{revision}}</div>{{/revision}}
</div>
`
                );
           }        }

        /**
         * A (document/collection) that describe a set of changes to the network.
         *
         */
        class PowerSystemProject extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PowerSystemProject;
                if (null == bucket)
                   cim_data.PowerSystemProject = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PowerSystemProject[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "PowerSystemProject";
                base.parse_element (/<cim:PowerSystemProject.name>([\s\S]*?)<\/cim:PowerSystemProject.name>/g, obj, "name", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProject.priority>([\s\S]*?)<\/cim:PowerSystemProject.priority>/g, obj, "priority", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProject.state>([\s\S]*?)<\/cim:PowerSystemProject.state>/g, obj, "state", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProject.type>([\s\S]*?)<\/cim:PowerSystemProject.type>/g, obj, "type", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProject.version>([\s\S]*?)<\/cim:PowerSystemProject.version>/g, obj, "version", base.to_string, sub, context);
                base.parse_element (/<cim:PowerSystemProject.description>([\s\S]*?)<\/cim:PowerSystemProject.description>/g, obj, "description", base.to_string, sub, context);
                base.parse_attribute (/<cim:PowerSystemProject.\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "", sub, context);
                base.parse_attribute (/<cim:PowerSystemProject.Project\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Project", sub, context);

                var bucket = context.parsed.PowerSystemProject;
                if (null == bucket)
                   context.parsed.PowerSystemProject = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "PowerSystemProject", "name", base.from_string, fields);
                base.export_element (obj, "PowerSystemProject", "priority", base.from_string, fields);
                base.export_element (obj, "PowerSystemProject", "state", base.from_string, fields);
                base.export_element (obj, "PowerSystemProject", "type", base.from_string, fields);
                base.export_element (obj, "PowerSystemProject", "version", base.from_string, fields);
                base.export_element (obj, "PowerSystemProject", "description", base.from_string, fields);
                base.export_attribute (obj, "PowerSystemProject", "", fields);
                base.export_attribute (obj, "PowerSystemProject", "Project", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PowerSystemProject_collapse" aria-expanded="true" aria-controls="PowerSystemProject_collapse">PowerSystemProject</a>
<div id="PowerSystemProject_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#name}}<div><b>name</b>: {{name}}</div>{{/name}}
{{#priority}}<div><b>priority</b>: {{priority}}</div>{{/priority}}
{{#state}}<div><b>state</b>: {{state}}</div>{{/state}}
{{#type}}<div><b>type</b>: {{type}}</div>{{/type}}
{{#version}}<div><b>version</b>: {{version}}</div>{{/version}}
{{#description}}<div><b>description</b>: {{description}}</div>{{/description}}
{{#}}<div><b></b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{}}&quot;);})'>{{}}</a></div>{{/}}
{{#Project}}<div><b>Project</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Project}}&quot;);})'>{{Project}}</a></div>{{/Project}}
</div>
`
                );
           }        }

        /**
         * The ProjectSteps are ordered by the actualStart and actualEnds so that  a dependent ProjectStep will have a actualStart after an actualEnd.
         *
         */
        class ProjectStep extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ProjectStep;
                if (null == bucket)
                   cim_data.ProjectStep = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ProjectStep[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ProjectStep";
                base.parse_element (/<cim:ProjectStep.actualEnd>([\s\S]*?)<\/cim:ProjectStep.actualEnd>/g, obj, "actualEnd", base.to_datetime, sub, context);
                base.parse_element (/<cim:ProjectStep.actualStart>([\s\S]*?)<\/cim:ProjectStep.actualStart>/g, obj, "actualStart", base.to_datetime, sub, context);
                base.parse_element (/<cim:ProjectStep.scheduledEnd>([\s\S]*?)<\/cim:ProjectStep.scheduledEnd>/g, obj, "scheduledEnd", base.to_datetime, sub, context);
                base.parse_element (/<cim:ProjectStep.scheduledStart>([\s\S]*?)<\/cim:ProjectStep.scheduledStart>/g, obj, "scheduledStart", base.to_datetime, sub, context);
                base.parse_element (/<cim:ProjectStep.status>([\s\S]*?)<\/cim:ProjectStep.status>/g, obj, "status", base.to_string, sub, context);
                base.parse_element (/<cim:ProjectStep.stepType>([\s\S]*?)<\/cim:ProjectStep.stepType>/g, obj, "stepType", base.to_string, sub, context);

                var bucket = context.parsed.ProjectStep;
                if (null == bucket)
                   context.parsed.ProjectStep = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ProjectStep", "actualEnd", base.from_datetime, fields);
                base.export_element (obj, "ProjectStep", "actualStart", base.from_datetime, fields);
                base.export_element (obj, "ProjectStep", "scheduledEnd", base.from_datetime, fields);
                base.export_element (obj, "ProjectStep", "scheduledStart", base.from_datetime, fields);
                base.export_element (obj, "ProjectStep", "status", base.from_string, fields);
                base.export_element (obj, "ProjectStep", "stepType", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ProjectStep_collapse" aria-expanded="true" aria-controls="ProjectStep_collapse">ProjectStep</a>
<div id="ProjectStep_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#actualEnd}}<div><b>actualEnd</b>: {{actualEnd}}</div>{{/actualEnd}}
{{#actualStart}}<div><b>actualStart</b>: {{actualStart}}</div>{{/actualStart}}
{{#scheduledEnd}}<div><b>scheduledEnd</b>: {{scheduledEnd}}</div>{{/scheduledEnd}}
{{#scheduledStart}}<div><b>scheduledStart</b>: {{scheduledStart}}</div>{{/scheduledStart}}
{{#status}}<div><b>status</b>: {{status}}</div>{{/status}}
{{#stepType}}<div><b>stepType</b>: {{stepType}}</div>{{/stepType}}
</div>
`
                );
           }        }

        class ProjectStepStatusKind extends base.Element
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.ProjectStepStatusKind;
                if (null == bucket)
                   cim_data.ProjectStepStatusKind = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.ProjectStepStatusKind[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = base.Element.prototype.parse.call (this, context, sub);
                obj.cls = "ProjectStepStatusKind";
                base.parse_element (/<cim:ProjectStepStatusKind.cancelled>([\s\S]*?)<\/cim:ProjectStepStatusKind.cancelled>/g, obj, "cancelled", base.to_string, sub, context);
                base.parse_element (/<cim:ProjectStepStatusKind.inProgress>([\s\S]*?)<\/cim:ProjectStepStatusKind.inProgress>/g, obj, "inProgress", base.to_string, sub, context);
                base.parse_element (/<cim:ProjectStepStatusKind.inactive>([\s\S]*?)<\/cim:ProjectStepStatusKind.inactive>/g, obj, "inactive", base.to_string, sub, context);
                base.parse_element (/<cim:ProjectStepStatusKind.approved>([\s\S]*?)<\/cim:ProjectStepStatusKind.approved>/g, obj, "approved", base.to_string, sub, context);

                var bucket = context.parsed.ProjectStepStatusKind;
                if (null == bucket)
                   context.parsed.ProjectStepStatusKind = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = [];

                base.export_element (obj, "ProjectStepStatusKind", "cancelled", base.from_string, fields);
                base.export_element (obj, "ProjectStepStatusKind", "inProgress", base.from_string, fields);
                base.export_element (obj, "ProjectStepStatusKind", "inactive", base.from_string, fields);
                base.export_element (obj, "ProjectStepStatusKind", "approved", base.from_string, fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#ProjectStepStatusKind_collapse" aria-expanded="true" aria-controls="ProjectStepStatusKind_collapse">ProjectStepStatusKind</a>
<div id="ProjectStepStatusKind_collapse" class="collapse in" style="margin-left: 10px;">
`
      + base.Element.prototype.template.call (this) +
`
{{#cancelled}}<div><b>cancelled</b>: {{cancelled}}</div>{{/cancelled}}
{{#inProgress}}<div><b>inProgress</b>: {{inProgress}}</div>{{/inProgress}}
{{#inactive}}<div><b>inactive</b>: {{inactive}}</div>{{/inactive}}
{{#approved}}<div><b>approved</b>: {{approved}}</div>{{/approved}}
</div>
`
                );
           }        }

        /**
         * A collection of dependent projects.
         *
         */
        class PowerSystemSubProject extends PowerSystemProject
        {
            constructor (template, cim_data)
            {
                super (template, cim_data);
                this._id = template.id;
                var bucket = cim_data.PowerSystemSubProject;
                if (null == bucket)
                   cim_data.PowerSystemSubProject = bucket = {};
                bucket[this._id] = template;
            }

            remove (cim_data)
            {
               super.remove (cim_data);
               delete cim_data.PowerSystemSubProject[this._id];
            }

            parse (context, sub)
            {
                var obj;

                obj = PowerSystemProject.prototype.parse.call (this, context, sub);
                obj.cls = "PowerSystemSubProject";
                base.parse_attribute (/<cim:PowerSystemSubProject.Project\s+rdf:resource\s*?=\s*?("|')([\s\S]*?)\1\s*?\/>/g, obj, "Project", sub, context);

                var bucket = context.parsed.PowerSystemSubProject;
                if (null == bucket)
                   context.parsed.PowerSystemSubProject = bucket = {};
                bucket[obj.id] = obj;

                return (obj);
            }

            export (obj, full)
            {
                var fields = PowerSystemProject.prototype.export.call (this, obj, false);

                base.export_attribute (obj, "PowerSystemSubProject", "Project", fields);
                if (full)
                    base.Element.prototype.export.call (this, obj, fields)

                return (fields);
            }


            template ()
            {
                return (
`
<a data-toggle="collapse" href="#PowerSystemSubProject_collapse" aria-expanded="true" aria-controls="PowerSystemSubProject_collapse">PowerSystemSubProject</a>
<div id="PowerSystemSubProject_collapse" class="collapse in" style="margin-left: 10px;">
`
      + PowerSystemProject.prototype.template.call (this) +
`
{{#Project}}<div><b>Project</b>: <a href='#' onclick='require([&quot;cimmap&quot;], function(cimmap) {cimmap.select (&quot;{{Project}}&quot;);})'>{{Project}}</a></div>{{/Project}}
</div>
`
                );
           }        }

        return (
            {
                PowerSystemProjectSchedule: PowerSystemProjectSchedule,
                StepKind: StepKind,
                PowerSystemProject: PowerSystemProject,
                ProjectStepStatusKind: ProjectStepStatusKind,
                ProjectStep: ProjectStep,
                PowerSystemSubProject: PowerSystemSubProject
            }
        );
    }
);