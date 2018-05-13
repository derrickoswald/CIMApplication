/**
 * Clock utility.
 */
"use strict";

define
(
    [],
    /**
     * @summary Create a clock widget and allow setting the time.
     * @description Make an SVG component and code to set the time.
     * @name clock
     * @exports clock
     * @version 1.0
     */
    function ()
    {
        class Clock
        {
            constructor()
            {
                this._svg =
                    `
<svg
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:cc="http://creativecommons.org/ns#"
    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    xmlns:svg="http://www.w3.org/2000/svg"
    xmlns="http://www.w3.org/2000/svg"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    id="svg84"
    xml:space="preserve"
    enable-background="new 0 0 384 384"
    viewBox="0 0 384 384"
    height="150px"
    width="150px"
    y="0px"
    x="0px"
    class="iconic-clock"
    version="1.1">
    <metadata id="metadata88">
        <rdf:RDF>
            <cc:Work rdf:about="">
                <dc:format>image/svg+xml</dc:format>
                <dc:type rdf:resource="http://purl.org/dc/dcmitype/StillImage" />
                <dc:title></dc:title>
            </cc:Work>
        </rdf:RDF>
    </metadata>
    <defs id="defs80"></defs>
    <circle
         r="188"
         cy="192"
         cx="192"
         id="path5324"
         style="fill:#ffffff;fill-opacity:1;stroke:#000000;stroke-opacity:1" />
    <g id="g4268">
        <g id="g4254">
            <path
                 id="large_marker-12"
                 d="m 186.5,31.57013 h 11 v 39 h -11 z" />
            <path
                 id="large_marker-6"
                 d="m 186.5,314.5 h 11 v 39 h -11 z" />
        </g>
        <g id="g4260" transform="rotate(-90,192,192.53506)">
            <path
                 d="m 186.5,31.57013 h 11 v 39 h -11 z"
                 id="large_marker-9" />
            <path
                 d="m 186.5,314.5 h 11 v 39 h -11 z"
                 id="large_marker-3" />
        </g>
    </g>
    <g id="g4282" transform="rotate(-30,192.00001,192.53507)">
        <g id="g4274">
            <path
                 d="m 186.5,31.57013 h 11 v 39 h -11 z"
                 id="path4270" />
            <path
                 d="m 186.5,314.5 h 11 v 39 h -11 z"
                 id="path4272" />
        </g>
        <g id="g4280" transform="rotate(-90,192,192.53506)">
            <path
                 id="path4276"
                 d="m 186.5,31.57013 h 11 v 39 h -11 z" />
            <path
                 id="path4278"
                 d="m 186.5,314.5 h 11 v 39 h -11 z" />
        </g>
    </g>
    <g id="g4296" transform="rotate(-60,192,192.53506)">
        <g id="g4288">
            <path
                 d="m 186.5,31.57013 h 11 v 39 h -11 z"
                 id="path4284" />
            <path
                 d="m 186.5,314.5 h 11 v 39 h -11 z"
                 id="path4286" />
        </g>
        <g id="g4294" transform="rotate(-90,192,192.53506)">
            <path
                 id="path4290"
                 d="m 186.5,31.57013 h 11 v 39 h -11 z" />
            <path
                 id="path4292"
                 d="m 186.5,314.5 h 11 v 39 h -11 z" />
        </g>
    </g>
    <g id="g4421">
        <g id="g4342">
            <g id="seconds_markers">
                <rect
                     id="rect4298"
                     width="3.8233767"
                     height="11.220779"
                     x="168.72151"
                     y="360.67224"
                     transform="rotate(-5.9337046)" />
                <rect
                     transform="rotate(-12.027733)"
                     y="377.63562"
                     x="144.95094"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4300" />
                <rect
                     id="rect4302"
                     width="3.8233767"
                     height="11.220779"
                     x="120.67159"
                     y="391.63086"
                     transform="rotate(-17.966555)" />
                <rect
                     transform="rotate(-23.947668)"
                     y="403.0719"
                     x="94.731041"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4304" />
            </g>
            <g id="g4330">
                <rect
                     transform="rotate(173.06475)"
                     y="-64.942497"
                     x="-171.39021"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4312" />
                <rect
                     id="rect4314"
                     width="3.8233767"
                     height="11.220779"
                     x="-149.01649"
                     y="-78.926369"
                     transform="rotate(167.97227)" />
                <rect
                     transform="rotate(162.03344)"
                     y="-92.896523"
                     x="-124.4792"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4316" />
                <rect
                     id="rect4318"
                     width="3.8233767"
                     height="11.220779"
                     x="-98.597176"
                     y="-104.36272"
                     transform="rotate(156.05233)" />
            </g>
        </g>
        <g id="g4364" transform="rotate(-30,191.32731,192.54607)">
            <g id="g4352">
                <rect
                     transform="rotate(-5.9337046)"
                     y="360.67224"
                     x="168.72151"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4344" />
                <rect
                     id="rect4346"
                     width="3.8233767"
                     height="11.220779"
                     x="144.95094"
                     y="377.63562"
                     transform="rotate(-12.027733)" />
                <rect
                     transform="rotate(-17.966555)"
                     y="391.63086"
                     x="120.67159"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4348" />
                <rect
                     id="rect4350"
                     width="3.8233767"
                     height="11.220779"
                     x="94.731041"
                     y="403.0719"
                     transform="rotate(-23.947668)" />
            </g>
            <g id="g4362">
                <rect
                     id="rect4354"
                     width="3.8233767"
                     height="11.220779"
                     x="-171.39021"
                     y="-64.942497"
                     transform="rotate(173.06475)" />
                <rect
                     transform="rotate(167.97227)"
                     y="-78.926369"
                     x="-149.01649"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4356" />
                <rect
                     id="rect4358"
                     width="3.8233767"
                     height="11.220779"
                     x="-124.4792"
                     y="-92.896523"
                     transform="rotate(162.03344)" />
                <rect
                     transform="rotate(156.05233)"
                     y="-104.36272"
                     x="-98.597176"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4360" />
            </g>
        </g>
        <g id="g4386" transform="rotate(-60,191.35861,192.52347)">
            <g id="g4374">
                <rect
                     id="rect4366"
                     width="3.8233767"
                     height="11.220779"
                     x="168.72151"
                     y="360.67224"
                     transform="rotate(-5.9337046)" />
                <rect
                     transform="rotate(-12.027733)"
                     y="377.63562"
                     x="144.95094"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4368" />
                <rect
                     id="rect4370"
                     width="3.8233767"
                     height="11.220779"
                     x="120.67159"
                     y="391.63086"
                     transform="rotate(-17.966555)" />
                <rect
                     transform="rotate(-23.947668)"
                     y="403.0719"
                     x="94.731041"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4372" />
            </g>
            <g id="g4384">
                <rect
                     transform="rotate(173.06475)"
                     y="-64.942497"
                     x="-171.39021"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4376" />
                <rect
                     id="rect4378"
                     width="3.8233767"
                     height="11.220779"
                     x="-149.01649"
                     y="-78.926369"
                     transform="rotate(167.97227)" />
                <rect
                     transform="rotate(162.03344)"
                     y="-92.896523"
                     x="-124.4792"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4380" />
                <rect
                     id="rect4382"
                     width="3.8233767"
                     height="11.220779"
                     x="-98.597176"
                     y="-104.36272"
                     transform="rotate(156.05233)" />
            </g>
        </g>
    </g>
    <g id="g4489" transform="rotate(-90,191.44413,192.54607)">
        <g id="g4443">
            <g id="g4431">
                <rect
                     transform="rotate(-5.9337046)"
                     y="360.67224"
                     x="168.72151"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4423" />
                <rect
                     id="rect4425"
                     width="3.8233767"
                     height="11.220779"
                     x="144.95094"
                     y="377.63562"
                     transform="rotate(-12.027733)" />
                <rect
                     transform="rotate(-17.966555)"
                     y="391.63086"
                     x="120.67159"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4427" />
                <rect
                     id="rect4429"
                     width="3.8233767"
                     height="11.220779"
                     x="94.731041"
                     y="403.0719"
                     transform="rotate(-23.947668)" />
            </g>
            <g id="g4441">
                <rect
                     id="rect4433"
                     width="3.8233767"
                     height="11.220779"
                     x="-171.39021"
                     y="-64.942497"
                     transform="rotate(173.06475)" />
                <rect
                     transform="rotate(167.97227)"
                     y="-78.926369"
                     x="-149.01649"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4435" />
                <rect
                     id="rect4437"
                     width="3.8233767"
                     height="11.220779"
                     x="-124.4792"
                     y="-92.896523"
                     transform="rotate(162.03344)" />
                <rect
                     transform="rotate(156.05233)"
                     y="-104.36272"
                     x="-98.597176"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4439" />
            </g>
        </g>
        <g id="g4465" transform="rotate(-30,191.32731,192.54607)">
            <g id="g4453">
                <rect
                     id="rect4445"
                     width="3.8233767"
                     height="11.220779"
                     x="168.72151"
                     y="360.67224"
                     transform="rotate(-5.9337046)" />
                <rect
                     transform="rotate(-12.027733)"
                     y="377.63562"
                     x="144.95094"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4447" />
                <rect
                     id="rect4449"
                     width="3.8233767"
                     height="11.220779"
                     x="120.67159"
                     y="391.63086"
                     transform="rotate(-17.966555)" />
                <rect
                     transform="rotate(-23.947668)"
                     y="403.0719"
                     x="94.731041"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4451" />
            </g>
            <g id="g4463">
                <rect
                     transform="rotate(173.06475)"
                     y="-64.942497"
                     x="-171.39021"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4455" />
                <rect
                     id="rect4457"
                     width="3.8233767"
                     height="11.220779"
                     x="-149.01649"
                     y="-78.926369"
                     transform="rotate(167.97227)" />
                <rect
                     transform="rotate(162.03344)"
                     y="-92.896523"
                     x="-124.4792"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4459" />
                <rect
                     id="rect4461"
                     width="3.8233767"
                     height="11.220779"
                     x="-98.597176"
                     y="-104.36272"
                     transform="rotate(156.05233)" />
            </g>
        </g>
        <g id="g4487" transform="rotate(-60,191.35861,192.52347)">
            <g id="g4475">
                <rect
                     transform="rotate(-5.9337046)"
                     y="360.67224"
                     x="168.72151"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4467" />
                <rect
                     id="rect4469"
                     width="3.8233767"
                     height="11.220779"
                     x="144.95094"
                     y="377.63562"
                     transform="rotate(-12.027733)" />
                <rect
                     transform="rotate(-17.966555)"
                     y="391.63086"
                     x="120.67159"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4471" />
                <rect
                     id="rect4473"
                     width="3.8233767"
                     height="11.220779"
                     x="94.731041"
                     y="403.0719"
                     transform="rotate(-23.947668)" />
            </g>
            <g id="g4485">
                <rect
                     id="rect4477"
                     width="3.8233767"
                     height="11.220779"
                     x="-171.39021"
                     y="-64.942497"
                     transform="rotate(173.06475)" />
                <rect
                     transform="rotate(167.97227)"
                     y="-78.926369"
                     x="-149.01649"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4479" />
                <rect
                     id="rect4481"
                     width="3.8233767"
                     height="11.220779"
                     x="-124.4792"
                     y="-92.896523"
                     transform="rotate(162.03344)" />
                <rect
                     transform="rotate(156.05233)"
                     y="-104.36272"
                     x="-98.597176"
                     height="11.220779"
                     width="3.8233767"
                     id="rect4483" />
            </g>
        </g>
    </g>
    <path
         id="iconic-clock-hour-hand"
         d="m 184,80 h 16 l 3,146 h -22 z"
         style="fill:#000000;fill-opacity:1;stroke:none;stroke-width:0.90545458;stroke-opacity:1" />
    <path
         id="iconic-clock-minute-hand"
         d="m 186.5,35 h 11 l 4,191 H 182 Z"
         style="fill:#000000;fill-opacity:1;stroke:none;stroke-width:1.25828326;stroke-opacity:1" />
    <g id="iconic-clock-second-hand">
        <circle
             style="fill:#b40000;fill-opacity:1;stroke:none;stroke-opacity:1"
             id="path5446"
             cx="192.2271"
             cy="194.1188"
             r="5.9337873" />
        <line
             style="fill:#b40000;fill-opacity:1;stroke:#b40000;stroke-width:4.42289162;stroke-miterlimit:10;stroke-opacity:1"
             id="line75"
             y2="97.92057"
             x2="192"
             y1="241.32986"
             x1="192"
             stroke-miterlimit="10" />
        <circle
             id="path4491"
             cx="192"
             cy="86"
             r="13"
             style="fill:#b40000;fill-opacity:1" />
    </g>
</svg>
                    `;
            }

            getSVG ()
            {
                return (this._svg);
            }

            setTime (date)
            {
                var seconds = date.getSeconds ();
                var minutes = date.getMinutes ();
                var hours = date.getHours ();
                minutes = (minutes * 60) + seconds;
                hours = (hours > 12) ? hours - 12 : hours;
                hours = (hours * 3600) + minutes;
                document.querySelector ("#iconic-clock-second-hand").setAttribute ("transform", "rotate ("+360*(seconds/60)+", 192, 192)");
                document.querySelector ("#iconic-clock-minute-hand").setAttribute("transform", "rotate ("+360*(minutes/3600)+", 192, 192)");
                document.querySelector ("#iconic-clock-hour-hand").setAttribute("transform", "rotate ("+360*(hours/43200)+", 192, 192)");
            }
        }

        return (Clock);
    }
)
