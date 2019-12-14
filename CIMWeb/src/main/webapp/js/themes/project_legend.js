/**
 * Map legend control for CIM Application
 */
"use strict";

define
(
    ["mustache", "../cimquery"],
    /**
     * @summary Project legend control.
     * @description UI element for the project legend.
     * @exports project_legend
     * @version 1.0
     */
    function (mustache, cimquery)
    {
        class ProjectLegend
        {
            constructor (theme)
            {
                this._theme = theme;
                this._template =
                    `
                    <div class="card">
                      <div class="card-body" style="min-width:200px;">
                        <h5 class="card-title">
                          <span class="info_title">Legend</span>
                          <button class="close" type="button" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                          </button>
                        </h5>
                        <h6 class="card-subtitle mb-2">
                        Empty pending improved code.
                        </h6>
                        <div class="card-footer">
                        </div>
                      </div>
                    </div>
                    `;
            }

            onAdd (map)
            {
                this._map = map;
                this._container = document.createElement ("div");
                this._container.className = "mapboxgl-ctrl";
                this._container.innerHTML = this._template;
                // handle close button
                this._container.getElementsByClassName ("close")[0].onclick = this.close.bind (this);
                return (this._container);
            }

            onRemove ()
            {
                this._container.parentNode.removeChild (this._container);
                delete this._container;
                delete this._map;
            }

            close ()
            {
                this._map.removeControl (this);
            }

            getDefaultPosition ()
            {
                return ("bottom-right");
            }

            visible ()
            {
                return ("undefined" != typeof (this._container));
            }

            legend_change_listener (fn)
            {
                this._legend_listener = fn;
            }

            initialize ()
            {
            }
        }

        return (ProjectLegend);
    }
);