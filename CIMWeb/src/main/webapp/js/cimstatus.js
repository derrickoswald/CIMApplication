/**
 * @fileOverview Spark status display.
 * @name cimstatus
 * @author Derrick Oswald
 * @version 1.0
 */
"use strict";
define
(
        ["util", "mustache"],
        /**
         * @summary Display job status.
         * @version 1.0
         * @exports status
         */
        function (util, mustache)
        {
            /**
             * Object that monitors job status and displays progress.
             * @param {string} modal DOM element
             * @param {string} target DOM element name to hold progress elements
             * @param {string} Spark group ID to track, if unspecified track only active jobs
             * @class
             */
            class Status
            {
                constructor (modal, target, group)
                {
                    /**
                     * DOM element attribute name of modal dialog.
                     */
                    this.modal = modal;

                    /**
                     * DOM element attribute name that holds progress elements.
                     */
                    this.target = target;

                    /**
                     * Spark job id group, if any.
                     */
                    this.group = group;

                    /**
                     * Mustache template to generate the progress DOM elements.
                     */
                    this.template =
                        `<ol>
                            {{#jobs}}
                            <li value="{{id}}">{{status}}
                                <ol>
                                    {{#stages}}
                                    <li value="{{id}}">
                                        <div>{{name}}</div>
                                        <div>@{{datetime}} #{{attempt}}</div>
                                        <div>{{percent}}</div>
                                    </li>
                                    {{/stages}}
                                </ol>
                            </li>
                            {{/jobs}}
                        </ol>
                        `;

                    this.set ("");
                }

                set (text)
                {
                    const target = document.getElementById (this.target);
                    if (target)
                        target.innerHTML = text;
                }

                /**
                 * Render the chooser field set.
                 */
                render (result)
                {

                    const options = {
                        year: 'numeric', month: 'numeric', day: 'numeric',
                        hour: 'numeric', minute: 'numeric', second: 'numeric',
                        hour12: false
                    };
                    const datetimeformat = new Intl.DateTimeFormat("default", options);

                    function datetime ()
                    {
                        return (datetimeformat.format(new Date (this.time)));
                    }

                    function percent ()
                    {
                        const completed = this.tasks.completed;
                        const total = this.tasks.total;
                        return (`tasks ${completed}/${total}`);
                    }

                    const jobs = result.sort ((a, b) => b.id - a.id);
                    jobs.map (j => { if (j.stages) j.stages.sort ((a, b) => a.id - b.id) });
                    this.set (
                        mustache.render (
                            this.template,
                            {
                                jobs: jobs,
                                datetime: datetime,
                                percent: percent
                            }
                        )
                    );
                };

                run ()
                {
                    const modal = document.getElementById (this.modal);
                    if ((null == modal) || modal.hidden)
                    {
                        window.clearInterval (this.intervalID);
                        this.set ("");
                    }
                    else
                    {
                        const url = util.home () + "cim/status" + (this.group ? ";group=" + this.group : "");
                        util.makeRequest ("GET", url)
                                .then (xmlhttp => JSON.parse (xmlhttp.responseText))
                                .then (response => this.render (response.result));
                    }
                }

                start ()
                {
                    const modal = document.getElementById (this.modal);
                    if (null != modal)
                    {
                        $ (`#${this.modal}`).modal ("show");
                        this.intervalID = window.setInterval (this.run.bind (this), 5000);
                    }
                }

                stop ()
                {
                    const modal = document.getElementById (this.modal);
                    if (null != modal)
                    {
                        window.clearInterval (this.intervalID);
                        $(`#${this.modal}`).modal ("hide");
                    }
                }
            }

            return (Status);
        }
);