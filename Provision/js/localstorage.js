/**
 * @fileOverview Access local storage.
 * @name localstorage
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    [],
    /**
     * @summary Set/Retrieve properties to/from localStorage.
     * @description Use local storage for remembering properties.
     * @name localstorage
     * @exports localstorage
     * @version 1.0
     */
    function ()
    {
        /**
         * Check if local storage is supported.
         * @return <code>true</code> if the browser supports local storage.
         * @function haslLocalStorage
         * @memberOf module:localstorage
         */
        function haslLocalStorage ()
        {
            var ret = false;

            try
            {
                ret = (("localStorage" in window) && (null != window["localStorage"]));
            }
            catch (e)
            {
            }

            return (ret);
          }

        /**
         * Store a property in local storage if possible.
         * @param {string} property the property name
         * @param {string} value the property value
         * @function storeProperty
         * @memberOf module:localstorage
         */
        function storeProperty (property, value)
        {
            if (haslLocalStorage ())
                localStorage.setItem (property, value);
        }

        /**
         * Retrieve a property from local storage if possible.
         * @param {string} property the property name
         * @function loadProperty
         * @memberOf module:localstorage
         */
        function loadProperty (property)
        {
            return (haslLocalStorage () ? localStorage.getItem (property) : null);
        }

        /**
         * Deletes the property from local storage.
         * @param {string} property the property name
         * @function clearProperty
         * @memberOf module:localstorage
         */
        function clearProperty (property)
        {
            if (haslLocalStorage ())
                localStorage.removeItem (property);
        }

        return (
                {
                	storeProperty: storeProperty,
                	loadProperty: loadProperty,
                	clearProperty: clearProperty
                }
            );
        }
    );