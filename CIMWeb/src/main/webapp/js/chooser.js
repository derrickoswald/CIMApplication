/**
 * @fileOverview Multi-text input field component.
 * @name chooser
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache"],
    /**
     * @summary UI component to allow multiple text values.
     * @version 1.0
     * @name chooser
     * @exports chooser
     */
    function (mustache)
    {
        /**
         * Object that makes a chooser field set.
         * @param {string} target_list DOM element name to contain the input set
         * @param {string} label User visible label for the input set
         * @param {string} placeholder User visible placeholder text in an empty input field
         * @param {array} choices list of user visible items for the drop down menu
         * (if not supplied, null or an empty array, the input field is a plain text input with no dropdown)
         * @param {string} help_markup markup to add as help text
         * @class
         * @memberOf module:chooser
         */
        class Chooser
        {
            constructor (target_list, label, placeholder, choices, help_markup)
            {
                /**
                 * Unique DOM element name for entire input set.
                 * The DIV with this id is filled with the chooser from the template.
                 * This is also used as an id pattern for this input set,
                 * suffixed with the index value to create unique element ids for input elements.
                 * @memberOf chooser.Chooser#
                 */
                this.list_name = target_list;

                /**
                 * Label for this input group.
                 * I18N
                 * @memberOf Chooser#
                 */
                this.input_label = label;

                /**
                 * Placeholder text for each input element.
                 * I18N
                 * @memberOf Chooser#
                 */
                this.prompt = placeholder;

                /**
                 * List of user entered data.
                 * Each element is an object with property 'value' as the user chosen string.
                 * At lifecycle end, objects with empty strings need to be removed by the caller.
                 * @memberOf Chooser#
                 */
                this.items = [];

                /**
                 * List of predefined values made available in the drop-down list.
                 * Each entry is a string.
                 * @memberOf Chooser#
                 */
                this.values = choices;

                /**
                 * Help text
                 * @memberOf Chooser#
                 */
                this.help = help_markup;

                /**
                 * DOM element attribute name that stores the index value of the input group.
                 * @memberOf Chooser#
                 */
                this.data_source = "data_source";

                /**
                 * DOM element attribute name that stores the id of the input element.
                 * @memberOf Chooser#
                 */
                this.data_target = "data_target";

                /**
                 * Mustache template to generate the list item DOM elements.
                 * @memberOf Chooser#
                 */
                this.template =
                    `
                     {{#items}}
                        <label for="${this.list_name}_{{index}}" ${this.data_source}="{{index}}">
                        {{#first}}${this.input_label}{{/first}}
                        </label>
                        <div class="input-group" ${this.data_source}="{{index}}">
                            {{#hasvalues}}
                                <span class="dropdown" style="flex: 1 1 auto;">
                                    <input id="${this.list_name}_{{index}}" type="text" class="form-control dropdown-toggle" ${this.data_source}="{{index}}" data-toggle="dropdown" placeholder="${this.prompt}" aria-label="${this.input_label}" value="{{value}}"{{#last}} aria-describedby="${this.list_name}_help"{{/last}}>
                                    <ul class="dropdown-menu" ${this.data_target}="${this.list_name}_{{index}}" role="menu" aria-labelledby="${this.list_name}_{{index}}" >
                                        {{#values}}
                                        <li role="presentation">
                                            <a class="dropdown-item" role="menuitem" tabindex="-1" href="#">{{.}}</a>
                                        </li>
                                        {{/values}}
                                    </ul>
                                </span>
                            {{/hasvalues}}
                            {{^values}}
                                <input type="text" class="form-control" ${this.data_source}="{{index}}" placeholder="${this.prompt}" aria-label="${this.input_label}" value="{{value}}"{{#last}} aria-describedby="${this.list_name}_help"{{/last}}>
                            {{/values}}
                            <span class="input-group-addon btn btn-default" ${this.data_source}="{{index}}">
                                <i class="fa {{glyph}}"></i>
                            </span>
                        </div>
                        {{#last}}
                            {{{help}}}
                        {{/last}}
                    {{/items}}
                    `;

                /**
                 * Context for mustache rendering.
                 * @memberOf Chooser#
                 */
                var temp = this.context =
                {
                    items: this.items, // list of user entered values
                    values: this.values, // list of pre-composed values for the drop-down list
                    hasvalues: (("undefined" != typeof (this.values)) && (null != this.values) && (0 != this.values.length)), // boolean to turn on values processing
                    first: function ()
                    {
                        return (0 == temp.items.indexOf (this));
                    },
                    glyph: function ()
                    {
                        return ((temp.items.length - 1 === temp.items.indexOf (this)) ? "fa-plus" : "fa-minus");
                    },
                    index: function ()
                    {
                        return (temp.items.indexOf (this));
                    },
                    last: function ()
                    {
                        return (temp.items.length - 1 == temp.items.indexOf (this));
                    },
                    help: this.help
                };
            }

            /**
             * Event handler for user pressing Enter in an input field.
             * @param {object} event keypress event from the input element
             * @function enter
             * @memberOf module:chooser.Chooser.prototype
             */
            enter (event)
            {
                var keycode = (event.keyCode ? event.keyCode : event.which);
                if (keycode == '13')
                {
                    this.changed (event);
                    this.add (null);
                }
            };

            /**
             * Event handler for user entering a value by key entry.
             * @param {object} event change event from the input element
             * @function changed
             * @memberOf module:chooser.Chooser.prototype
             */
            changed (event)
            {
                var index;

                index = Number (event.target.getAttribute (this.data_source));
                this.context.items[index].value = event.target.value;
            };

            /**
             * Event handler for drop-down list item selected.
             * @param {object} event click event from the link list item
             * @function clicked
             * @memberOf module:chooser.Chooser.prototype
             */
            clicked (event)
            {
                var link;
                var value;
                var target;
                var index;

                // stop the normal link action
                event.preventDefault ();

                link = event.target;
                value = link.innerHTML;
                while (link && (null === (target = link.getAttribute (this.data_target))))
                    link = link.parentElement;

                // fill in the input field with the chosen drop-down list item
                target = document.getElementById (target);
                target.value = value;

                // update the value list
                index = Number (target.getAttribute (this.data_source));
                this.context.items[index].value = value;

                return (true);
            };

            /**
             * Set the focus to the given input item.
             * @param {number} index The index (data_source attribute value) of the item to focus on
             * @function focus
             * @memberOf module:chooser.Chooser.prototype
             */
            focus (index)
            {
                var list;
                var inputs;

                list = document.getElementById (this.list_name);
                inputs = list.getElementsByTagName ("input");
                inputs[index].focus ();
            };

            /**
             * Event handler for clicking the plus icon.
             * @param {object} event click event from the input group addon item
             * @function add
             * @memberOf module:chooser.Chooser.prototype
             */
            add (event)
            {
                // add the next input element
                this.context.items.push ({ value: "" });

                // update the DOM
                this.render ();

                // set focus to the new input element
                this.focus (this.context.items.length - 1);
            };

            /**
             * Event handler for clicking the minus icon.
             * @param {object} event click event from the input group addon item
             * @function remove
             * @memberOf module:chooser.Chooser.prototype
             */
            remove (event)
            {
                var link;
                var index;

                // get the index
                link = event.target;
                while (link && (null === (index = link.getAttribute (this.data_source))))
                    link = link.parentElement;

                // remove it from the list
                this.context.items.splice (Number (index), 1);

                // re-render the items
                this.render ();

                // set focus to the new input element
                this.focus (this.context.items.length - 1);
            };

            /**
             * Render the chooser field set.
             * @function render
             * @memberOf module:chooser.Chooser.prototype
             */
            render ()
            {
                var list;
                var change;
                var keypress;
                var inputs;
                var click;
                var links;
                var added;
                var removed;
                var spans;

                // ensure there is at least one item to render
                if (0 === this.context.items.length)
                    this.context.items.push ({ value: "" });

                // re-render and inject the new elements into the DOM
                list = document.getElementById (this.list_name);
                list.innerHTML = mustache.render (this.template, this.context);

                // handle edit events
                change = this.changed.bind (this);
                keypress = this.enter.bind (this);
                inputs = list.getElementsByTagName ("input");
                for (var i = 0; i < inputs.length; i++)
                {
                    inputs[i].addEventListener ("change", change);
                    inputs[i].addEventListener ("keypress", keypress);
                }

                // handle drop down chosen events
                click = this.clicked.bind (this);
                links = list.getElementsByTagName ("a");
                for (var j = 0; j < links.length; j++)
                    links[j].addEventListener ("click", click);

                // handle add and remove events on the input group addon button
                added = this.add.bind (this);
                removed = this.remove.bind (this);
                spans = list.getElementsByTagName ("span");
                for (var k = 0; k < spans.length; k++)
                    if (spans[k].classList.contains ("input-group-addon"))
                        spans[k].addEventListener ("click", (this.items.length - 1 === Number (spans[k].getAttribute (this.data_source))) ? added : removed);
            };
        }

        return (Chooser);
    }
);
