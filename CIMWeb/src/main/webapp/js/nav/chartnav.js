/**
 * Chart navigation button for CIM Application.
 */
"use strict";

define
(
    [],
    /**
     * @summary Chart navigation button.
     * @description Toggles the chart control on and off.
     * @exports ChartNav
     * @version 2.0
     */
    function ()
    {
        class ChartNav extends HTMLButtonElement
        {
            constructor ()
            {
                super ();
            }

            connectedCallback ()
            {
                this.setAttribute ("class", "mapboxgl-ctrl-icon");
                this.setAttribute ("aria-label", "Chart");
                this.setAttribute ("title", "Toggle chart panel");
                this.setAttribute ("type", "button");
                this.style["background-image"] = "url('data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyBpZD0ic3ZnNiIgdmVyc2lvbj0iMS4xIiB2aWV3Qm94PSIwIDAgMjAgMjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiA8c3R5bGUgaWQ9InN0eWxlODM3Ii8+CiA8cGF0aCBpZD0icGF0aDE2NTMiIGQ9Im00IDE2LjVoMTIiIGZpbGw9Im5vbmUiIHN0cm9rZT0iI2NjYyIgc3Ryb2tlLXdpZHRoPSIxcHgiLz4KIDxwYXRoIGlkPSJwYXRoMTY1NSIgZD0ibTQuNSA0djEyIiBmaWxsPSJub25lIiBzdHJva2U9IiNjY2MiIHN0cm9rZS13aWR0aD0iMXB4Ii8+CiA8cGF0aCBpZD0icGF0aDg4NCIgZD0ibTYuNDcxMyAxNXYtMyIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMzMzIiBzdHJva2Utd2lkdGg9IjFweCIvPgogPHBhdGggaWQ9InBhdGg4ODYiIGQ9Im04LjUzMDYgMTV2LTUiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzMzMyIgc3Ryb2tlLXdpZHRoPSIxcHgiLz4KIDxwYXRoIGlkPSJwYXRoODg4IiBkPSJtMTAuNDgzIDE1di00IiBmaWxsPSJub25lIiBzdHJva2U9IiMzMzMiIHN0cm9rZS13aWR0aD0iMXB4Ii8+CiA8cGF0aCBpZD0icGF0aDg5MCIgZD0ibTEyLjQ5OCAxNXYtNiIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMzMzIiBzdHJva2Utd2lkdGg9IjFweCIvPgogPHBhdGggaWQ9InBhdGg4OTIiIGQ9Im0xNC41NTMgMTV2LTgiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzMzMyIgc3Ryb2tlLXdpZHRoPSIxcHgiLz4KIDxwYXRoIGlkPSJwYXRoODk2IiBkPSJtNiAxMCAzLTMgMS41MTE2IDEuNTIzMSAzLjA5OTItMy4xMTIxIiBmaWxsPSJub25lIiBzdHJva2U9IiMzMzMiIHN0cm9rZS13aWR0aD0iMXB4Ii8+CiA8cGF0aCBpZD0icGF0aDEwMjIiIHRyYW5zZm9ybT0icm90YXRlKDEwNSAyMC41OTcgMi45NDk3KSIgZD0ibTI0LjIzNCA5LjM5MzYtMC41NzUxNS0wLjk5NjE5IDAuNTc1MTUtMWUtN2gwLjU3NTE1bC0wLjI4NzU3IDAuNDk4MDl6IiBzdHJva2U9IiMzMzMiIHN0cm9rZS1kYXNob2Zmc2V0PSIxMCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2Utd2lkdGg9Ii42NjQxMiIvPgo8L3N2Zz4K')";
                $(this).tooltip ({ placement: "left" });
                this.addEventListener ("click", function () { $(this).tooltip ("hide"); }.bind (this));
            }
        }

        customElements.define ("chart-nav-button", ChartNav, { extends: "button" });

        return (ChartNav);
    }
);
