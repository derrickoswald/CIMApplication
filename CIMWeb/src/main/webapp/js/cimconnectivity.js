/**
 * Map connectivity control for CIM Application
 */
"use strict";

define
(
    ["mustache", "cim", "cancelablepromise"],
    /**
     * @summary Connectivity control.
     * @description UI element for editing connectivity.
     * @exports cimconnectivity
     * @version 1.0
     */
    function (mustache, cim, CancelablePromise)
    {
        class CIMConnectivity
        {
            constructor (cimmap, cimedit)
            {
                this._cimmap = cimmap;
                this._cimedit = cimedit;
                this._template =
                    `
                    <div class="card">
                      <div class="card-body" style="min-width:200px;">
                        <h5 class="card-title">Connectivity
                            <button class="close" type="button" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                        </h5>
                        <div class="card-text"></div>
                        <div id="connectivity_footer" class="card-footer" style="display: none;">
                        </div>
                      </div>
                    </div>
                    `;
                this._sizex = 32;
                this._sizey = 40;
                this._radius = 5;
                this._border = 2;
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl";
                this._container.innerHTML = this._template;
                this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                this._cimmap.add_feature_listener (this);
                return (this._container);
            }

            onRemove ()
            {
                this._cimmap.remove_feature_listener (this);
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            getDefaultPosition ()
            {
                return ("bottom-right");
            }

            close ()
            {
                this.abort ();
                this._map.removeControl (this);
            }

            visible ()
            {
                return ("undefined" != typeof (this._container));
            }

            initialize ()
            {
                if (this._cimmap.get_selected_feature ())
                    this.selection_change (this._cimmap.get_selected_feature (), this._cimmap.get_selected_features ());
            }

            popup (html, position)
            {
                const lnglat = position || this._map.getCenter ();
                const popup = new mapboxgl.Popup ();
                popup.setLngLat (lnglat);
                popup.setHTML (html);
                popup.addTo (this._map);
                return (popup);
            }

            poink (x, y)
            {
                let ret = null;
                const width = 4;
                const height = 4;
                const features = this._cimmap.get_map ().queryRenderedFeatures
                (
                    [
                      [x - width / 2, y - height / 2],
                      [x + width / 2, y + height / 2]
                    ],
                    {}
                );
                if ((null != features) && (0 !== features.length))
                {
                    const selection = [];
                    for (let i = 0; i < features.length; i++)
                    {
                        const mrid = features[i].properties.mRID;
                        if (null != mrid && !selection.includes (mrid))
                            selection.push (mrid);
                    }
                    if (selection.length > 0)
                        ret = selection;
                }
                return (ret);
            }

            distance (x1, y1, x2, y2)
            {
                const dx = x2 - x1;
                const dy = y2 - y1;
                return (Math.sqrt (dx * dx + dy * dy));
            }

            // get the terminals for the device
            get_terminals_for_equipment (equipment)
            {
                const ret = this._cimmap.fetch ("Terminal", terminal => terminal.ConductingEquipment === equipment.id);
                // sort by sequenceNumber
                ret.sort ((a, b) => a.sequenceNumber - b.sequenceNumber);

                return (ret);
            }

            // get the connectivity
            get_connectivity_for_equipment_terminals (equipment, terminals)
            {
                const ret = [];
                for (let i = 0; i < terminals.length; i++)
                {
                    const terminal = terminals[i];
                    const connectivity =
                    {
                        ConductingEquipment: equipment,
                        Terminal: terminal,
                        BaseVoltage: ""
                    };
                    if (equipment.BaseVoltage)
                        connectivity.BaseVoltage = equipment.BaseVoltage;
                    else
                        // for PowerTransformer look for the end
                        this._cimmap.forAll ("PowerTransformerEnd", end => { if (end.Terminal === terminal.id) connectivity.BaseVoltage = end.BaseVoltage; });
                    if (terminal.ConnectivityNode)
                        connectivity.ConnectivityNode = terminal.ConnectivityNode;
                    ret.push (connectivity);
                }

//                [
//                    {
//                        ConductingEquipment: equipment,
//                        Terminal: terminal,
//                        BaseVoltage: ""
//                        ConnectivityNode: terminal.ConnectivityNode,
//                    },
//                    :
//                ]
                return (ret);
            }

            get_connectivity_for_equipment (equipment, point)
            {
                // get the terminals for the device
                let terminals = this.get_terminals_for_equipment (equipment);

                // for Conductor objects (with a length attribute) keep only the nearest one
                if (point && terminals.length > 1 && equipment.length)
                {
                    let ordered = [];
                    this._cimmap.forAll ("PositionPoint", point => { if (point.Location === equipment.Location) ordered[point.sequenceNumber] = point; });
                    // here we un-screw up the sequence numbers on the PositionPoint elements
                    if ("undefined" == typeof (ordered[0]))
                        ordered = ordered.slice (1);

                    const first = 0;
                    const last = ordered.length - 1;
                    const x = point[0];
                    const y = point[1];
                    if (this.distance (ordered[first].xPosition, ordered[first].yPosition, x, y) <
                        this.distance (ordered[last].xPosition, ordered[last].yPosition, x, y))
                        // keep the first
                        terminals = terminals.slice (0, 1);
                    else
                        // keep the second
                        terminals = terminals.slice (1, 2);
                }
                const ret = this.get_connectivity_for_equipment_terminals (equipment, terminals).filter (x => x.ConnectivityNode);

                return (ret);
            }

            get_connectivity_for_equipments (equipments, all, point)
            {
                const ret = [];
                const cimmap = this._cimmap;
                const connectivities = Array.prototype.concat.apply ([], equipments.map (e => this.get_connectivity_for_equipment (e, point)));
                // combine equivalent ConnectivityNode
                const list = {};
                for (let i = 0; i < connectivities.length; i++)
                {
                    const connectivity = connectivities[i];
                    if (!list[connectivity.ConnectivityNode])
                        list[connectivity.ConnectivityNode] = [connectivity];
                    else
                        list[connectivity.ConnectivityNode].push (connectivity);
                }
                // add equipment connected but not hovered over
                if (all)
                    for (let id in list)
                        if (list.hasOwnProperty (id))
                            cimmap.forAll ("Terminal",
                                terminal =>
                                {
                                    if (terminal.ConnectivityNode === id)
                                        if (!list[id].find (x => x.ConductingEquipment.id === terminal.ConductingEquipment))
                                        {
                                            const equipment = cimmap.get ("ConductingEquipment", terminal.ConductingEquipment);
                                            if (equipment)
                                            {
                                                const connectivity =
                                                    {
                                                        ConnectivityNode: id,
                                                        ConductingEquipment: equipment,
                                                        Terminal: terminal,
                                                        BaseVoltage: ""
                                                    };
                                                if (equipment.BaseVoltage)
                                                    connectivity.BaseVoltage = equipment.BaseVoltage;
                                                else
                                                    cimmap.forAll ("PowerTransformerEnd", end => { if (end.Terminal === terminal.id) connectivity.BaseVoltage = end.BaseVoltage; });
                                                list[id].push (connectivity);
                                            }
                                        }
                                }
                            );
                for (let id in list)
                    if (list.hasOwnProperty (id))
                        ret.push ({ ConnectivityNode: id, Equipment: list[id] });
//            [
//                {
//                    ConnectivityNode: nodename,
//                    Equipment:
//                    [
//                        {
//                            ConnectivityNode: nodename,
//                            ConductingEquipment: equipment,
//                            Terminal: terminal,
//                            BaseVoltage: "BaseVoltage_400"
//                        },
//                        ...
//                    ]
//                }
//            ]
                return (ret);
            }

            // circle
//            svg ()
//            {
//                return ("<svg width='24' height='24'><circle cx='12px' cy='12px' r='10px' stroke='blue' stroke-width='2px' fill='green' /></svg>");
//            }

            // map marker
//            svg (n, r, fill, stroke, border, font)
//            {
//                // A common approximation is to use four beziers to model a circle, each with control points a distance
//                // d=r*4*(sqrt(2)-1)/3 from the end points (where r is the circle radius), and in a direction
//                // tangent to the circle at the end points.
//                const cpd = r * (Math.sqrt (2.0) - 1.0) * 4.0 / 3.0;
//                const r0 = (r + stroke / 2.0).toFixed (0);
//                const d = (2.0 * r + stroke).toFixed (0);
//                const b2 = (stroke / 2.0).toFixed (0);
//                // drawing from top left
//                const text =
//                [
//                    "<svg width='",
//                    d,
//                    "' height='",
//                    d,
//                    "'><path d='M ",
//                    b2,
//                    ",",
//                    r0,
//                    " C ",
//                    b2,
//                    ",",
//                    (r - cpd + stroke / 2.0).toPrecision (8),
//                    " ",
//                    (r - cpd + stroke / 2.0).toPrecision (8),
//                    ",",
//                     b2,
//                    " ",
//                    r0,
//                    ",",
//                    b2,
//                    " ",
//                    (r + cpd + stroke / 2.0).toPrecision (8),
//                    ",",
//                    b2,
//                    " ",
//                    (2.0 * r + stroke / 2.0).toPrecision (8),
//                    ",",
//                    (r - cpd + stroke / 2.0).toPrecision (8),
//                    " ",
//                    (2.0 * r + stroke / 2.0).toPrecision (8),
//                    ",",
//                    r0,
//                    " L ",
//                    r0,
//                    ",",
//                    (2.0 * r + stroke / 2.0).toPrecision (8),
//                    " Z' style='fill:",
//                    fill,
//                    ";stroke:",
//                    border,
//                    ";stroke-width:",
//                    stroke.toFixed (0),
//                    "px' />",
//                    "<text x='",
//                    r0,
//                    "' y='",
//                    (r + stroke / 2.0 + font / 3.0).toPrecision (8),
//                    "' style='font-style:normal;font-constiant:normal;font-weight:normal;font-stretch:normal;font-size:",
//                    font.toFixed (0),
//                    "px",
//                    ";line-height:1.25;font-family:sans-serif;text-align:center;letter-spacing:0px;word-spacing:0px;text-anchor:middle;fill:#000000;fill-opacity:1;stroke:none'>",
//                    "<tspan x='",
//                    r0,
//                    "' y='",
//                    (r + stroke / 2.0 + font / 3.0).toPrecision (8),
//                    "'>",
//                    n.toString (),
//                    "</tspan></text></svg>"
//                ];
//                return (text.join (""));
//            }

            rectangle_svg (x, y, dx, dy, radius, border, fill, stroke)
            {
                // d="M 6 1 h 20 c 3,0 5,2 5,5 v 20 c 0,3 -2,5 -5,5 h -20 c -3,0 -5,-2 -5,-5 v -20 c 0,-3 2,-5 5,-5 z"
                const h = (dx - (2 * radius) - border).toFixed (0);
                const v = (dy - (2 * radius) - border).toFixed (0);
                const g = (radius - border).toFixed (0);
                const r = radius.toFixed (0);
                const b = border.toFixed (0);

                const text =
                [
                    "<path d='M ",
                    (x + radius + border / 2).toFixed (0),
                    ",",
                    (y + border / 2).toFixed (0),
                    " h ",
                    h,
                    " c ",
                    g,
                    ",0 ",
                    r,
                    ",",
                    b,
                    " ",
                    r,
                    ",",
                    r,
                    " v ",
                    v,
                    " c 0,",
                    g,
                    " -",
                    b,
                    ",",
                    r,
                    " -",
                    r,
                    ",",
                    r,
                    " h -",
                    h,
                    " c -",
                    g,
                    ",0 -",
                    r,
                    ",-",
                    b,
                    " -",
                    r,
                    ",-",
                    r,
                    " v -",
                    v,
                    " c 0,-",
                    g,
                    " ",
                    b,
                    ",-",
                    r,
                    " ",
                    r,
                    ",-",
                    r,
                    " z' style='fill:",
                    fill,
                    ";stroke:",
                    stroke,
                    ";stroke-width:",
                    border.toFixed (0),
                    "px' />"
                ];
                return (text.join (""));
            }

            marker_svg (dx, dy, radius, border, label, fill, stroke, font)
            {
                const w = dx.toFixed (0);
                const h = dy.toFixed (0);
                const e = (dx / 2.0).toPrecision (8);
                const f = ((dy + font) / 2.0).toPrecision (8);
                const factor = 1.33333333333333; // I dunno why this is needed
                const text =
                [
                    "<svg width='",
                    w,
                    "' height='",
                    h,
                    "'>",
                    this.rectangle_svg (0, 0, dx, dy, radius, border, fill, stroke),
                    "<text x='",
                    e,
                    "' y='",
                    f,
                    "' style='font-size:",
                    (font * factor).toFixed (0),
                    "px",
                    ";font-family:sans-serif;text-align:center;text-anchor:middle;fill:#000000;fill-opacity:1;stroke:none'>",
                    "<tspan x='",
                    e,
                    "' y='",
                    f,
                    "'>",
                    label,
                    "</tspan></text></svg>"
                ];
                return (text.join (""));
            }

            marker (ll, n, id)
            {
                const element = document.createElement ("span");
                element.setAttribute ("style", "height: 32px;");
                element.className = "marker";
                element.innerHTML = this.marker_svg (this._sizex, this._sizey, this._radius, this._border, n.toString (), "#ffffff", "#0000ff",  (n < 10) ? 24 : 18);
                // freeze the selection process
                const reset = (function ()
                {
                    if (this._mousemove)
                    {
                        this._map.off ("mousemove", this._mousemove);
                        delete this._mousemove;
                    }
                }).bind (this);
                const set = this.set_buttons.bind (this);
                function marker_event (event)
                {
                    event.preventDefault ();
                    event.stopPropagation ();
                    console.log ("marker event " + id);
                    const item = document.getElementById (id);
                    item.checked = !item.checked;
                    reset (); // stop tracking
                    set (); // set the buttons
                    return (false);
                }
                element.addEventListener ("click", marker_event, { capture: true });
                const m = new mapboxgl.Marker (element, { offset: [ (n - 1) * this._sizex, 0.0] });
                m.setLngLat (ll);
                m.addTo (this._cimmap.get_map ());

                return (m);
            }

            set_buttons ()
            {
                const connect = document.getElementById ("connectivity_connect");
                const disconnect = document.getElementById ("connectivity_disconnect");
                if (connect && disconnect)
                {
                    let connected = false;
                    let target = null;
                    for (let i = 0; i < this._target.length; i++)
                        if (document.getElementById ("target_" + this._target[i].Terminal.id).checked)
                            target = this._target[i];
                    if (target && target.Terminal.ConnectivityNode)
                        connected = document.getElementById ("connectivity_" + target.Terminal.ConnectivityNode).checked;
                    connect.disabled = connected;
                    disconnect.disabled = target && !connected;
                }
            }

            set_gui (cb_success, cb_failure)
            {
                const template =
`
    <button id='connectivity_connect' type='button' class='btn btn-primary' disabled>Connect</button>
    <button id='connectivity_disconnect' type='button' class='btn btn-info' disabled>Disconnect</button>
    <button id='connectivity_cancel' type='button' class='btn btn-danger'>Cancel</button>
`;
                const footer = document.getElementById ("connectivity_footer");
                footer.innerHTML = template;
                footer.style.display = "block";
                const do_connect = this.connect_click.bind (this, cb_success, cb_failure);
                const do_disconnect = this.disconnect_click.bind (this, cb_success, cb_failure);
                const do_cancel = this.cancel_click.bind (this, cb_success, cb_failure);
                document.getElementById ("connectivity_connect").addEventListener ("click", do_connect);
                document.getElementById ("connectivity_disconnect").addEventListener ("click", do_disconnect);
                document.getElementById ("connectivity_cancel").addEventListener ("click", do_cancel);
                let target = null;
                for (let i = 0; i < this._target.length; i++)
                    if (document.getElementById ("target_" + this._target[i].Terminal.id).checked)
                        target = this._target[i];
                if (null == target)
                {
                    target = this._target[0];
                    target.current = true;
                    document.getElementById ("target_" + target.Terminal.id).checked = true;
                    for (let j = 0; j < this._candidates.length; j++)
                        if (this._candidates[j].ConnectivityNode === target.Terminal.ConnectivityNode)
                        {
                            document.getElementById ("connectivity_" + this._candidates[j].ConnectivityNode).checked = true;
                            this._candidates[j].current = true;
                        }
                }
                this.set_buttons ();
            }

            reset_candidates ()
            {
                if (this._candidates)
                {
                    this._candidates.filter (x => x.Marker).map (x => x.Marker.remove ());
                    this._candidates = this._candidates.filter (x => !x.Marker);
                    delete this._anchor;
                }
                this.show_candidates ();
            }

            reset_gui ()
            {
                this._container.getElementsByClassName ("card-text")[0].innerHTML = "";
                const footer = this._container.getElementsByClassName ("card-footer")[0];
                if (this._target)
                {
                    const template =
`
    <button id="connectivity_set" type="button" class="btn btn-primary">Set</button>
`;
                    footer.innerHTML = template;
                    footer.style.display = "block";
                    footer.getElementsByClassName ("btn btn-primary")[0].onclick = this.begin.bind (this);
                }
                else
                {
                    footer.innerHTML = "";
                    footer.style.display = "none";
                }

                if (this._candidates)
                {
                    this._candidates.filter (x => x.Marker).map (x => x.Marker.remove ());
                    this._candidates = this._candidates.filter (x => !x.Marker);
                    delete this._anchor;
                }
                if (this._popup)
                {
                    this._popup.remove ();
                    delete this._popup;
                }
                this.show_connectivity ();
            }

            flick (event)
            {
                const id = event.target.id;
                console.log ("radio event " + id);
                for (let i = 0; i < this._target.length; i++)
                    if (document.getElementById ("target_" + this._target[i].Terminal.id).checked)
                    {
                        this._target[i].current = true;
                        for (let j = 0; j < this._candidates.length; j++)
                        {
                            delete this._candidates[j].current;
                            const other = document.getElementById ("connectivity_" + this._candidates[j].ConnectivityNode);
                            other.checked = false;
                            if (this._candidates[j].ConnectivityNode === this._target[i].Terminal.ConnectivityNode)
                            {
                                this._candidates[j].current = true;
                                other.checked = true;
                            }
                        }
                    }
                    else
                        delete this._target[i].current;
                this.set_buttons ();
            }

            // only used in mustache render
            display_name ()
            {
                function names ()
                {
                    function distinct (array)
                    {
                        const ret = [];
                        array.forEach (x => !ret.includes (x) ? ret.push (x) : null);
                        return (ret);
                    }

                    const ret = [];
                    if (this.name)
                        ret.push (this.name);
                    if (this.mRID)
                        ret.push (this.mRID);
                    ret.push (this.id);
                    if (this.aliasName)
                        ret.push (this.aliasName);
                    return (distinct.call (this, ret));
                }

                return (names.call (this)[0]);
            }

            show_candidates ()
            {
                const template =
                `
                    {{#candidates}}
                    <div class='form-check'>
                      <input id='connectivity_{{ConnectivityNode}}' class='form-check-input' type='radio' name='connectivity_choice' value='{{ConnectivityNode}}'{{#current}} checked{{/current}}>
                      <label class='form-check-label' for='connectivity_{{ConnectivityNode}}'>
                        <h6>{{#Marker}}#{{index}} {{/Marker}}{{#ConnectivityNode}}{{ConnectivityNode}}{{/ConnectivityNode}}</h6>
                          {{#Equipment}}
                            {{#ConductingEquipment}}
                              <div>{{display_name}} ({{cls}}) {{description}}</div>
                            {{/ConductingEquipment}}
                            {{#Terminal}}
                              <div>&centerdot; Terminal #{{Terminal.sequenceNumber}} {{display_name}} {{Terminal.description}} {{BaseVoltage}}</div>
                            {{/Terminal}}
                          {{/Equipment}}
                      </label>
                    </div>
                    {{/candidates}}

                `;
                let index = 1;
                for (let i = 0; this._candidates && i < this._candidates.length; i++)
                    if (this._candidates[i].Marker)
                        this._candidates[i].index = index++;
                const text = mustache.render (template, { candidates: (this._candidates ? this._candidates : []), display_name: this.display_name });
                for (let i = 0; this._candidates && i < this._candidates.length; i++)
                    delete this._candidates[i].index;
                const guts = this._container.getElementsByClassName ("connectivity_candidates")[0];
                guts.innerHTML = text;
                function radio_event (event)
                {
                    const id = event.target.id;
                    console.log ("radio event " + id);
                    for (let i = 0; this._candidates && i < this._candidates.length; i++)
                    {
                        delete this._candidates[i].current;
                        if (id === "connectivity_" + this._candidates[i].ConnectivityNode)
                            this._candidates[i].current = true;
                    }
                    this.set_buttons (); // set the buttons
                    return (false);
                }
                const fn = radio_event.bind (this);
                const radios = guts.getElementsByClassName ("form-check-input");
                for (let i = 0; i < radios.length; i++)
                    radios[i].addEventListener ("change", fn);
            }

            element_svg (equipment)
            {
                return (this.marker_svg (250, 100, 5, 4, this.display_name.call (equipment), "#00000000", "#000000ff",  18));
            }

            show_connectivity ()
            {
//            [
//                {
//                    ConnectivityNode: nodename,
//                    Equipment:
//                    [
//                        {
//                            ConnectivityNode: nodename,
//                            ConductingEquipment: equipment,
//                            Terminal: terminal,
//                            BaseVoltage: "BaseVoltage_400"
//                        }
//                    ]
//                }
//            ]

                const template =
`
{{#equipment}}
  <h6>{{display_name}} ({{cls}})</h6>
  {{#description}}<div>{{description}}</div>{{/description}}
{{/equipment}}
{{{picture}}}
{{#target}}
<div class="form-check">
    <input id="target_{{Terminal.id}}" class="form-check-input" type="radio" name="target_choice" value="{{Terminal.id}}"{{#current}} checked{{/current}}>
    <label class="form-check-label" for="target_{{Terminal.id}}">
{{#Terminal}}
        <h6>Terminal #{{sequenceNumber}} {{display_name}} {{description}}</h6>
        <div>{{#ConnectivityNode}}{{ConnectivityNode}}{{/ConnectivityNode}} {{BaseVoltage}}</div>
{{/Terminal}}
    </label>
</div>
{{/target}}
<hr />
<div class="connectivity_candidates"></div>
`;
                if (this._target)
                {
                    const equipment = this._target[0].ConductingEquipment;
                    let index = 1;
                    for (let i = 0; this._candidates && i < this._candidates.length; i++)
                        if (this._candidates[i].Marker)
                            this._candidates[i].index = index++;
                    const text = mustache.render (template, { equipment: equipment, picture: this.element_svg (equipment), target: this._target, display_name: this.display_name });
                    for (let i = 0; this._candidates && i < this._candidates.length; i++)
                        delete this._candidates[i].index;

                    const guts = this._container.getElementsByClassName ("card-text")[0];
                    guts.innerHTML = text;
                    // add handler to change current target connectivity node
                    const radios = guts.getElementsByClassName ("form-check-input");
                    for (let i = 0; i < radios.length; i++)
                        radios[i].onclick = this.flick.bind (this);
                    this.show_candidates ();
                }
            }

            connectivity_mousemove_listener (obj, event)
            {
                let selection = null;
                const cimmap = this._cimmap;
                // check for out of bounds
                if (this._anchor)
                {
                    const x = event.point.x;
                    const y = event.point.y;
                    const point = this._map.project (this._anchor); // relative to map container
                    const x0 = point.x;
                    const y0 = point.y;
                    const n = this._candidates.filter (x => x.Marker).length;
                    const half = (this._sizex + this._border) / 2.0;
                    if ((x < x0 - half) || (y < y0 - half) || (y > y0 + half) || (x > x0 + (2 * (n - 1) + 1) * half))
                        this.reset_candidates ();
                    else if ((x >= x0 - half) && (y >= y0 - half) && (y <= y0 + half) && (x <= x0 + half))
                        selection = this.poink (event.point.x, event.point.y);
                }
                else
                    selection = this.poink (event.point.x, event.point.y);
                if (selection)
                {
                    // keep only other conducting equipment
                    const candidates = selection.filter (mrid => mrid !== obj.id && this._cimmap.get ("ConductingEquipment", mrid));
                    if (candidates.length > 0)
                    {
                        if (this._popup)
                        {
                            this._popup.remove ();
                            delete this._popup;
                        }
                        const lnglat = event.lngLat;
                        if (0 === this._candidates.filter (x => x.Marker).length)
                            this._anchor = lnglat;
                        const d = this.get_connectivity_for_equipments (candidates.map (x => cimmap.get ("ConductingEquipment", x)), true, [lnglat.lng, lnglat.lat]);
                        const neuveax = d.filter (x => !this._candidates.find (y => y.ConnectivityNode === x.ConnectivityNode));
                        if (0 !== neuveax.length)
                        {
                            const existing = this._candidates.filter (x => x.Marker).length;
                            for (let i = 0; i < neuveax.length; i++)
                                neuveax[i].Marker = this.marker (this._anchor, existing + i + 1, "connectivity_" + neuveax[i].ConnectivityNode);
                            this._candidates = this._candidates.concat (neuveax);
                            this.show_candidates ();
                        }
                    }
                }
            }

            set_listeners (obj)
            {
                if (!this._mousemove)
                {
                    this._mousemove = this.connectivity_mousemove_listener.bind (this, obj);
                    this._cimmap.push_listeners ({ "mousemove": this._mousemove });
                }
            }

            reset_listeners ()
            {
                if (this._mousemove)
                {
                    this._cimmap.pop_listeners ();
                    delete this._mousemove;
                }
            }

            do_connectivity (obj, callback_success, callback_failure)
            {
                const reset = () =>
                {
                    this.reset_gui ();
                    this.reset_listeners ();
                };
                const cb_success = () =>
                {
                    callback_success (obj);
                    reset ();
                };
                const cb_failure = () =>
                {
                    callback_failure ({canceled: true});
                    reset ();
                };
                this.set_gui (cb_success, cb_failure);
                this.set_listeners (obj);
                this._popup = this.popup ("<h1>Pick connectivity</h1>");
            }

            async do_connectivity_wait (obj, callback_success, callback_failure)
            {
                let status = null;
                function cb_success (feature)
                {
                    status = "success";
                    callback_success (feature);
                }
                function cb_failure (error)
                {
                    status = "fail";
                    callback_failure (error);
                }
                function sleep (ms)
                {
                    return (new Promise (resolve => setTimeout (resolve, ms)));
                }
                this.do_connectivity (obj, cb_success, cb_failure);
                do
                    await sleep (500);
                while (null == status);
            }

            set_conducting_equipment  (mrid)
            {
                if (this._target)
                    this.reset_gui ();
                if (mrid)
                {
                    const equipment = this._cimmap.get ("ConductingEquipment", mrid);
                    if (equipment)
                    {
                        this._target = this.get_connectivity_for_equipment_terminals (equipment, this.get_terminals_for_equipment (equipment));
                        this._candidates = this.get_connectivity_for_equipments ([equipment], true);
                        this.reset_gui ();
                    }
                    else
                    {
                        // not ConductingEquipment
                        this.abort ();
                        delete this._target;
                        delete this._candidates;
                        this.reset_gui ();
                    }
                }
            }

            begin ()
            {
                // start tracking
                const obj = this._target[0].ConductingEquipment;
                this._cpromise = new CancelablePromise (new Promise (this.do_connectivity_wait.bind (this, obj)), this.abort.bind (this));
                const cb_success = (equipment) =>
                {
                    console.log ("begin success " + equipment.id);
                    delete this._cpromise;
                    this.set_conducting_equipment (equipment.id);
                };
                const cb_failure = (message) =>
                {
                    console.log ("begin not ok " + message);
                    delete this._cpromise;
                    this.set_conducting_equipment (obj);
                };
                this._cpromise.setPromise (this._cpromise.promise ().then (cb_success, cb_failure));
            }

            connect_click (callback_success, callback_failure, event)
            {
                const data = this._cimmap.get_data (); // what about new items not yet committed
                let target = null;
                for (let i = 0; i < this._target.length; i++)
                    if (document.getElementById ("target_" + this._target[i].Terminal.id).checked)
                        target = this._target[i];
                if (target)
                {
                    let candidate = null;
                    for (let j = 0; j < this._candidates.length; j++)
                        if (document.getElementById ("connectivity_" + this._candidates[j].ConnectivityNode).checked)
                            candidate = this._candidates[j];
                    if (candidate)
                    {
                        if (target.Terminal.ConnectivityNode !== candidate.ConnectivityNode)
                        {
                            const terminal = target.Terminal;
                            const proto = JSON.parse (JSON.stringify (terminal));
                            proto.ConnectivityNode = candidate.ConnectivityNode;
                            this._cimedit.replace (terminal, proto, data);
                            console.log ("connected terminal " + target.Terminal.id + " to " + candidate.ConnectivityNode);
                        }
                    }
                }
                const equipment = this._target[0].ConductingEquipment;
                if (callback_success)
                    callback_success (equipment);
            }

            disconnect_click (callback_success, callback_failure, event)
            {
                const data = this._cimmap.get_data (); // what about new items not yet committed
                let target = null;
                for (let i = 0; i < this._target.length; i++)
                    if (document.getElementById ("target_" + this._target[i].Terminal.id).checked)
                        target = this._target[i];
                if (target)
                {
                    let candidate = null;
                    for (let j = 0; j < this._candidates.length; j++)
                        if (document.getElementById ("connectivity_" + this._candidates[j].ConnectivityNode).checked)
                            candidate = this._candidates[j];
                    if (candidate)
                    {
                        if (target.Terminal.ConnectivityNode === candidate.ConnectivityNode)
                        {
                            const terminal = target.Terminal;
                            const proto = JSON.parse (JSON.stringify (terminal));
                            delete proto.ConnectivityNode;
                            this._cimedit.replace (terminal, proto, data);
                            console.log ("disconnected terminal " + target.Terminal.id);
                        }
                    }
                }

                const equipment = this._target[0].ConductingEquipment;
                if (callback_success)
                    callback_success (equipment);
            }

            cancel_click (callback_success, callback_failure, event)
            {
                if (callback_failure)
                    callback_failure ({canceled: true});
            }

            abort ()
            {
                this.reset_listeners ();
                delete this._anchor;
                delete this._cpromise;
                this.reset_gui ();
            }

            /**
             * Connect the given object at terminal asynchronously.
require(["cimmap"], function(cimmap) { obj = cimmap.get_data ().Element["T1"]; });
require(["cimmap"], function(cimmap) { cimmap.get_connectivity ().connect (obj, null, function (x) { console.log ("success " + x); }, function (y) { console.log ("failure " + y); } ); });
             */
            connect (obj, terminal, callback_success, callback_failure)
            {
                this.set_conducting_equipment (obj.id);
                return (new CancelablePromise (new Promise (this.do_connectivity_wait.bind (this, obj)), this.abort.bind (this)));
            }

            /**
             * Connect the selected object at user selected terminal synchronously.
             */
            selection_change (current_feature, current_selection)
            {
                if (null != current_feature)
                    this.set_conducting_equipment (current_feature);
                else
                {
                    this.abort ();
                    delete this._target;
                    delete this._candidates;
                    this.reset_gui ();
                }
            }
        }

        return (CIMConnectivity);
    }
);