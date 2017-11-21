/**
 * @fileOverview Simulate with gridlabd.
 * @name cimsimulate
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["mustache", "util", "cimfiles", "cimquery", "cim", "chooser"],
    /**
     * @summary Functions to simulate using CIM data in memory.
     * @name cimsimulate
     * @exports cimsimulate
     * @version 1.0
     */
    function (mustache, util, cimfiles, cimquery, cim, chooser)
    {
        // The island RDF export.
        var TheRDF;

        // User specified player object queries
        var PlayerChooser;
        var PlayerChoices = [
            {
                title: "Constant power for all EnergyConsumer with PSRType == 'PSRType_HouseService' from /data with cosΦ = 1.0",
                sql: "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                directory: "/data/", // hard coded random loads
                phi: 1.0,
                execute: cosphi
            },
            {
                title: "Constant power for all EnergyConsumer with PSRType == 'PSRType_HouseService' from /data with cosΦ = 0.95",
                sql: "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                directory: "/data/", // hard coded random loads
                phi: 0.95,
                execute: cosphi
            },
            {
                title: "Constant power for all EnergyConsumer with PSRType == 'PSRType_HouseService' from /data with cosΦ = 0.9",
                sql: "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                directory: "/data/", // hard coded random loads
                phi: 0.9,
                execute: cosphi
            },
            {
                title: "Constant power for all EnergyConsumer with PSRType == 'PSRType_HouseService' from table 'data'",
                sql: "select concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name, t.TopologicalNode parent, 'constant_power' property, 'Watt' unit from EnergyConsumer c, Terminal t where c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment",
                execute: makeplayers
            },
        ];

        // User specified recorder object queries
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Node_Parameters
        // see http://gridlabd.me.uvic.ca/wiki/index.php/Power_Flow_User_Guide#Link_Parameters
        var RecorderChooser;
        var RecorderChoices = [
            {
                title: "All node voltages",
                sql: "select concat (n.IdentifiedObject.mRID, '_voltage_recorder') name, n.IdentifiedObject.mRID parent, 'voltage' property, 'Volts' unit, Double(900.0) interval, concat ('output_data/', n.IdentifiedObject.mRID, '_voltage.csv') file from TopologicalNode n",
                target_directory: "output_data/",
                execute: outfile
            },
            {
                title: "All cable currents",
                sql: "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_current_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current_in' property, 'Amperes' unit, Double(900.0) interval, concat ('output_data/', a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID,  '_current.csv') file from ACLineSegment a",
                target_directory: "output_data/",
                execute: outfile
            },
            {
                title: "All cable power losses",
                sql: "select concat (a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_losses_recorder') name, a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'power_losses' property, 'Volt-Amperes' unit, Double(900.0) interval, concat ('output_data/', a.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID,  '_losses.csv') file from ACLineSegment a",
                target_directory: "output_data/",
                execute: outfile
            },
            {
                title: "All transformer power flows",
                sql: "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_power_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'power_out' property, 'Volt-Amperes' unit, Double(900.0) interval, concat ('output_data/', p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID ,  '_power.csv') file  from PowerTransformer p",
                target_directory: "output_data/",
                execute: outfile
            },
            {
                title: "All transformer output currents",
                sql: "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_current_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'current_out' property, 'Amperes' unit, Double(900.0) interval, concat ('output_data/', p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID ,  '_current.csv') file  from PowerTransformer p",
                target_directory: "output_data/",
                execute: outfile
            },
            {
                title: "All transformer power losses",
                sql: "select concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID , '_losses_recorder') name, p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent, 'power_losses' property, 'Volt-Amperes' unit, Double(900.0) interval, concat ('output_data/', p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID ,  '_losses.csv') file  from PowerTransformer p",
                target_directory: "output_data/",
                execute: outfile
            }
        ];

        // The simulation details.
        // provisional schema:
        // {
        //     name: <the name of the simulation and this JSON file>
        //     description: <textual description suitable for GUI display>,
        //     island: <topological node of the island to simulate>,
        //     station: <station of the island to simulate>,
        //     cim: <CIM RDF file containing the island>,
        //     glm: <generated GridLAB-D Model file>
        //     player_choices: [
        //        <the titles of player selections from the drop down menu>,
        //        ...
        //     ],
        //     players: [
        //         {
        //            "name": "HAS2987_load",
        //            "parent": "HAS2987_fuse_topo",
        //            "property": "constant_power",
        //            "unit": "Watt",
        //            "file": "input_data/HAS2987.csv"
        //         },
        //         ...
        //     ],
        //     recorder_choices: [
        //        <the titles of recorder selections from the drop down menu>,
        //        ...
        //     ],
        //     recorders: [
        //         {
        //            "name": "TRA2755_losses_recorder",
        //            "parent": "TRA2755",
        //            "property": "power_losses",
        //            "unit": "Volt-Amperes",
        //            "interval": 900,
        //            "file": "output_data/TRA2755_losses.csv"
        //        },
        //     ...
        //     ]
        // }
        var TheSimulation;

        // accessors
        function getName ()
        {
            return ((null != TheSimulation) ? TheSimulation.name : "");
        }
        function getDescription ()
        {
            return ((null != TheSimulation) ? TheSimulation.description : "");
        }
        function getIsland ()
        {
            return ((null != TheSimulation) ? TheSimulation.island : "");
        }
        function getStation ()
        {
            return ((null != TheSimulation) ? TheSimulation.station : "");
        }
        function getCIM ()
        {
            return ((null != TheSimulation) ? TheSimulation.cim : "");
        }
        function getGLM ()
        {
            return ((null != TheSimulation) ? TheSimulation.glm : "");
        }

        /**
         * @summary Call the gridlab RESTful service to export the simulation.
         * @description Invokes the server side gridlab function.
         * @param {string} simulation - the simulation file name.
         * @function exportSimulation
         * @memberOf module:cimsimulate
         */
        function exportSimulation (simulation, callback)
        {
            var url;
            var xmlhttp;

            simulation = simulation.startsWith ("/") ? simulation : "/" + simulation;
            url = util.home () + "cim/gridlab" + simulation;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        callback (xmlhttp.responseText);
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ();
        }

        /**
         * @summary Call the gridlab RESTful service to run the simulation.
         * @description Invokes the server side gridlab function.
         * @param {string} simulation - the simulation file name.
         * @function runSimulation
         * @memberOf module:cimsimulate
         */
        function runSimulation (simulation, callback)
        {
            var url;
            var xmlhttp;

            simulation = simulation.startsWith ("/") ? simulation : "/" + simulation;
            url = util.home () + "cim/gridlab" + simulation;
            xmlhttp = util.createCORSRequest ("POST", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                        callback (JSON.parse (xmlhttp.responseText))
                    else
                        alert ("status: " + xmlhttp.status);
            };
            xmlhttp.send ("your lucky number is " + Math.floor (Math.random () * (1000000 - 0)) + 0);
        }

        /**
         * @summary Save the simulation .json, and the GridLAB-D model .glm files.
         * @description Store the simulation .json gathered from the form data.
         * @function do_save
         * @memberOf module:cimsimulate
         */
        function do_save ()
        {
            if (null != TheSimulation)
            {
                var name = "/" + getStation () + "/" + encodeURIComponent (getName ()) + ".json";
                var glm_name = "/" + getStation () + "/" + encodeURIComponent (getName ()) + "/" + encodeURIComponent (getName ()) + ".glm";
                TheSimulation.glm = glm_name;
                var simulation = jsonify (TheSimulation);
                function callback (response)
                {
                    if (response.status == "OK")
                        exportSimulation (name,
                            function (glm)
                            {
                                cimfiles.put (glm_name, glm,
                                    function (response)
                                    {
                                        if (response.status == "OK")
                                        {
                                            document.getElementById ("cim").innerHTML = "<pre>\n" +  simulation + "\n</pre>";
                                            alert ("simulation " + name + " saved");
                                        }
                                        else
                                            alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                                    }
                                );
                            }
                        );
                    else
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }
                cimfiles.put (name, simulation, callback)
            }
        }

        /**
         * @summary Run the simulation using GridLAB-D to generate the output files.
         * @description Execute gridlabd for the current simulation.
         * @function do_simulate
         * @memberOf module:cimsimulate
         */
        function do_simulate ()
        {
            if (null != TheSimulation)
            {
                var name = "/" + getStation () + "/" + encodeURIComponent (getName ()) + ".json";
                runSimulation (name, function (result) { alert (JSON.stringify (result, null, 4)); })
            }
        }

        function htmlify (str)
        {
            return (str.replace (/</g, "&lt;").replace (/>/g, "&gt;"))
        }

        function jsonify (data)
        {
            return (JSON.stringify (data, null, 4))
        }

        /**
         * @summary Read the file contents in Spark.
         * @description Trigger CIMReader to read in the file.
         * @param {string} path - the file to load
         * @param {function} callback - the function accepting the load JSON with signature: function (response)
         * @function do_load
         * @memberOf module:cimsimulate
         */
        function do_load (path, callback)
        {
            var url;
            var xmlhttp;

            path = path.startsWith ("/") ? path : "/" + path;
            url = util.home () + "cim/load" + path;
            xmlhttp = util.createCORSRequest ("GET", url);
            xmlhttp.onreadystatechange = function ()
            {
                var resp;

                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status == "OK")
                            callback (resp);
                        else
                            alert (resp.message);
                    }
                    else
                        alert ("status: " + xmlhttp.status + ": " + xmlhttp.responseText);
            };
            xmlhttp.send ();
        }

        /**
         * Fake complex load data from simple meter data.
         * @param {object} choice - the user selected player file entry with output directory, sql query and cosΦ.
         * @param {function} callback - the function accepting the player array: function (response)
         * @function cosphi
         * @memberOf module:cimsimulate
         */
        function cosphi (choice, callback)
        {
            function generate_file (source, target, phi, done)
            {
                phi = phi || 1.0;
                if (Math.abs (phi) > 1.0)
                    phi = 1.0;

                function fromPolar (magnitude, angle, degrees)
                {
                    var a;
                    if (degrees)
                        a = angle * 3.1415926 / 180.0;
                    else
                        a = angle
                    return ({ real: magnitude * Math.cos (a), imag: magnitude * Math.sin (a)});
                }

                var rotation = { real: phi, imag: Math.sqrt (1.0 - (phi * phi)) };

                function times (c1, c2)
                {
                    return ({ real: c1.real * c2.real - c1.imag * c2.imag, imag: c1.imag * c2.real + c1.real * c2.imag });
                }

                function rotate (line)
                {
                    var ret;

                    var parts = line.split (",");
                    if ((parts.length != 2) && (parts.length != 3))
                        ret = line;
                    else
                    {
                        var original;
                        if (parts.length == 2) // 2017-07-18 00:00:00 UTC,0.25<0d
                        {
                            var real_mag = parts[1].split ("<")
                            var degrees = false;
                            if (real_mag[1].endsWith ("d"))
                            {
                                real_mag[1] = real_mag[1].substring (0, real_mag[1].length - 1);
                                degrees = true;
                            }
                            original = fromPolar (real_mag[0], real_mag[1], degrees);
                        }
                        else // 2017-07-18 00:15:00 UTC,0.03,0.004
                            original = { real: parts[1], imag: parts[2]};
                        var transformed = times (original, rotation);
                        ret = parts[0] + "," + (transformed.real * 1000.0) + "," + (transformed.imag * 1000.0);
                    }

                    return (ret);
                }

                cimfiles.get (source,
                    function (data)
                    {
                        var lines = data.split ("\n");
                        var modified = lines.map (rotate).join ("\n");
                        modified = '# ' + target +  '\n' + modified;
                        cimfiles.put (target, modified, done);
                    },
                    done);
            }

            cimfiles.fetch (choice.directory,
                function (response)
                {
                    if (response.status == "FAIL")
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                    else
                    {
                        var root = response.result.root.substring (response.result.filesystem.length); // like hdfs://0eef240033b6:8020/data/ => /data/
                        choice.files = response.result.files.map (function (item) { return ({ house: item.path.substring (0, item.path.indexOf (".")), file: root + item.path }); });
                        cimquery.query (
                            choice.sql,
                            "",
                            "",
                            function (data)
                            {
                                function getRandomInt (min, max)
                                {
                                    min = Math.ceil (min);
                                    max = Math.floor (max);
                                    return (Math.floor (Math.random () * (max - min)) + min); //The maximum is exclusive and the minimum is inclusive
                                }
                                var players = data.map (
                                    function (row)
                                    {
                                        var house = row.name.substring (0, row.name.indexOf ("_"));
                                        var match = choice.files.filter (function (x) { return (x.house == house); });
                                        if (0 != match.length)
                                            row.file = match[0].file;
                                        else
                                        {
                                            row.fake = true;
                                            row.file = choice.files[getRandomInt (0, choice.files.length)].file;
                                        }
                                        return (row);
                                    }
                                );

                                // transform each file
                                var todo = players.length;
                                players.forEach (
                                    function (pl)
                                    {
                                        var name = "input_data/" + (pl.fake ? "fake_" : "") + pl.file.substring (pl.file.lastIndexOf ("/") + 1);
                                        var target = "/" + getStation () + "/" + getName () + "/" + name;
                                        generate_file (pl.file, target, choice.phi,
                                            function (response)
                                            {
                                                if (response.status == "OK")
                                                    pl.file = name;
                                                else
                                                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                                                todo = todo - 1;
                                                if (0 == todo)
                                                    callback (players);
                                            }
                                        );
                                    }
                                );
                            }
                        );
                    }
                }
           );
        }

        function makeplayers (choice, callback)
        {
            cimquery.query (
                choice.sql,
                "",
                "",
                function (players)
                {
                    var houses = players.length;
                    function done ()
                    {
                        houses = houses - 1;
                        if (0 == houses)
                            callback (players);
                    }
                    players.forEach (
                        function (row)
                        {
//    {
//        "name": "HAS2104_load",
//        "parent": "HAS2104_fuse_topo",
//        "property": "constant_power",
//        "unit": "Watt"
//    },
                            var house = row.name.substring (0, row.name.indexOf ("_"));
                            cimquery.query (
                                "select d.time, d.real, d.imag from data d where d.house = '" + house + "'", // could  order by time  here but it's not very fast
                                "",
                                "",
                                function (measurements)
                                {
                                    var name = "input_data/" + house + ".csv";
                                    row.file = name;
                                    var strings = measurements.sort (function (a, b) { return (a.time.localeCompare (b.time)); }).map (
                                        function (measurement)
                                        {
//    {
//        "time": "2017-07-18 00:15:00",
//        "real": 4037.5,
//        "imag": 1327.062075
//    },
                                            return (measurement.time + "," + measurement.real + "," + measurement.imag);
                                        }
                                    );
                                    if (0 == measurements.length)
                                    {
                                        row.error = "mo measurements";
                                        strings.push ("1970-01-01 00:00:00,0.0,0.0");
                                    }
                                    var target = "/" + getStation () + "/" + getName () + "/" + name;
                                    strings.unshift ("# " + target);
                                    var text = strings.join ("\n");
                                    cimfiles.put (target, text, done);
                                }
                            );
                        }
                    );
                }
            );

        }

        function outfile (choice, callback)
        {
            var dir = "/" + getStation () + "/" + getName () + "/" + choice.target_directory;
            cimfiles.put (dir, "",
                function (response)
                {
                    if (response.status == "OK")
                        cimquery.query (
                            choice.sql,
                            "",
                            "",
                            callback);
                    else
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }
            );
        }

        function query_players ()
        {
            // reset the list
            TheSimulation.player_choices = [];
            TheSimulation.players = [];
            document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";

            var queries = [];
            PlayerChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = PlayerChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            queries = queries.concat (selected);
                        else
                            TheSimulation.players.push (JSON.parse (item.value)); // assume raw JSON
                    }
                }
            );
            var items = queries.length;
            queries.forEach (
                function (item)
                {
                    TheSimulation.player_choices.push (item.title);
                    item.execute (item,
                        function (list)
                        {
                            TheSimulation.players = TheSimulation.players.concat (list);
                            items = items - 1;
                            if (0 == items)
                                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";

                        }
                    );
                }
            );
        }

        function query_recorders ()
        {
            // reset the list
            TheSimulation.recorder_choices = [];
            TheSimulation.recorders = [];
            document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";

            var queries = [];
            RecorderChooser.context.items.forEach (
                function (item)
                {
                    if ("" !== item.value)
                    {
                        // look it up in the pre-defined choices
                        var selected = RecorderChoices.filter (function (x) { return (x.title == item.value); });
                        if (0 != selected.length)
                            queries = queries.concat (selected);
                        else
                            TheSimulation.recorders.push (JSON.parse (item.value)); // assume raw JSON
                    }
                });
            var items = queries.length;
            queries.forEach (
                function (item)
                {
                    TheSimulation.recorder_choices.push (item.title);
                    item.execute (item,
                        function (list)
                        {
                            TheSimulation.recorders = TheSimulation.recorders.concat (list);
                            items = items - 1;
                            if (0 == items)
                                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>";
                        }
                    );
                }
            );
        }

        function do_refresh ()
        {
            TheSimulation.name = document.getElementById ("simulation_name").value;
            TheSimulation.description = document.getElementById ("simulation_description").value;
            query_players ();
            query_recorders ();
        }

        /**
         * Set up the simulation data
         * @param {string} name - the name of the simulation.
         * @param {string} island - the name of the Topological island for the simulation.
         * @param {string} station - the name of the station for the simulation.
         * @param {string} cim - the name of the CIM file for the simulation.
         */
        function setup (name, island, station, cim)
        {
            TheSimulation =
            {
                 name: name,
                 island: island,
                 station: station,
                 cim: cim
            };
            // update the name
            document.getElementById ("title").innerHTML = name;
            function callback (result)
            {
                // since the island is loaded, reset the select_island dropdown
                document.getElementById ("simulation_island").innerHTML = "<option value='" + station + "/" + island + "' selected>" + station + " (" + island + ")</option>";
            }
            do_load (cim, callback)
        }

        /**
         * @summary Call the export RESTful service.
         * @description Invokes the server side export function.
         * @param {string} island - the island name from the topology
         * @param {string} cim - the name of the cim file
         * @param {function} callback - the function to call back with signature function (data)
         * @param {function} error - the error function with signature function ({message: "xx"})
         * @function exportIsland
         * @memberOf module:cimsimulate
         */
        function exportIsland (island, cim, callback, error)
        {
            var url;
            var xmlhttp;
            url = util.home () + "cim/export" + cim;
            xmlhttp = util.createCORSRequest ("PUT", url);
            xmlhttp.onreadystatechange = function ()
            {
                if (4 == xmlhttp.readyState)
                    if (200 == xmlhttp.status || 201 == xmlhttp.status || 202 == xmlhttp.status)
                    {
                        resp = JSON.parse (xmlhttp.responseText);
                        if (resp.status != "OK")
                            error ({ status: "FAIL", message: resp.message });
                        else
                            cimfiles.get (cim, callback, error);
                    }
                    else if (null != error)
                        error ({ status: "FAIL", message: "xmlhttp.status is " + xmlhttp.status });
            };
            xmlhttp.send (island);
        }

        /**
         * @summary Event handler for island change.
         * @description Invokes the server side export function or gets the existing island RDF.
         * @param {object} event - the event that triggered the change <em>not used</em>.
         * @function select_island
         * @memberOf module:cimsimulate
         */
        function select_island (event)
        {
            var name;
            var selection;
            var island;
            var station;
            var directory;
            var rdf;
            var cim;

            selection = document.getElementById ("simulation_island").value;
            island = selection.substring (0, selection.indexOf ("/"));
            station = selection.substring (selection.indexOf ("/") + 1);
            name = document.getElementById ("simulation_name").value;
            if ("" == name)
                name = island;
            if (("undefined" != typeof (island)) && ("" != island))
            {
                // check if the rdf exists already
                directory = "/" + station + "/" + name + "/";
                rdf = island + ".rdf";
                cim = directory + rdf;

                function callback (data)
                {
                    TheRDF = data;
                    setup (name, island, station, cim);
                }

                function error (response)
                {
                    alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                }

                cimfiles.fetch (directory,
                    function (response)
                    {
                        if ((response.status == "FAIL") || (0 == response.result.files.filter (function (file) { return (file.path == rdf); })))
                            exportIsland (island, cim, callback, error);
                        else
                            cimfiles.get (cim, callback, error);
                    }
                );
            }
        }

        function render (data)
        {
            var simulate_template =
                "<div class='container'>\n" +
                "  <div class='row justify-content-center'>\n" +
                "    <div class='col-8'>\n" +
                "      <h1>Simulate using GridLAB-D</h1>\n" +
                "      <h2 id='title'>{{name}}</h2>\n" +
                "      <form>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_name'>Name</label>\n" +
                "          <input  id='simulation_name' type='text' class='form-control'aria-describedby='nameHelp' placeholder='Enter a name for the simulation' value='{{name}}'>\n" +
                "          <small id='nameHelp' class='form-text text-muted'>Enter a unique name for the simulation - used as a file name for the details.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_description'>Description</label>\n" +
                "          <input id='simulation_description' type='text' class='form-control'aria-describedby='descriptionHelp' placeholder='Enter a description for the simulation' value='{{description}}'>\n" +
                "          <small id='descriptionHelp' class='form-text text-muted'>Enter a user facing description for the simulation - used for drop down choice title.</small>\n" +
                "        </div>\n" +
                "        <div class='form-group'>\n" +
                "          <label for='simulation_island'>Island</label>\n" +
                "          <select id='simulation_island' class='form-control' name='island'>\n" +
                "{{#data}}\n" +
                "            <option value='{{island}}/{{station}}'{{is_selected}}>{{station}} ({{island}})</option>\n" +
                "{{/data}}\n" +
                "          </select>\n" +
                "          <small id='islandHelp' class='form-text text-muted'>Choose the topoogical island (Trafokreis) - usually associated with a transformer secondary terminal.</small>\n" +
                "        </div>\n" +
                "        <div id='players' class='form-group'>\n" +
                "        </div>\n" +
                "        <div id='recorders' class='form-group'>\n" +
                "        </div>\n" +
                "        <button id='do_refresh' name='do_refresh' type='button' class='btn btn-primary'>Refresh</button>\n" +
                "        <button id='do_save' name='do_save' type='button' class='btn btn-primary'>Save</button>\n" +
                "        <button id='do_simulate' name='do_simulate' type='button' class='btn btn-primary'>Simulate</button>\n" +
                "      </form>\n" +
                "      <div id='cim'>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n";

            var text = mustache.render
            (
                simulate_template,
                {
                    name: getName,
                    description: getDescription,
                    is_selected: function () { return ((this.mRID == getIsland ()) ? " selected" : ""); },
                    data: data
                }
            );
            document.getElementById ("main").innerHTML = text;
            document.getElementById ("simulation_island").onchange = select_island;
            document.getElementById ("do_refresh").onclick = do_refresh;
            document.getElementById ("do_save").onclick = do_save;
            document.getElementById ("do_simulate").onclick = do_simulate;
            if (null == PlayerChooser)
            {
                var help =
                    "<span id='players_help' class='help-block'>" +
                        "The queries to use to pick player (load) elements." +
                    "</span>";
                PlayerChooser = new chooser.Chooser ("players", "Players", "Player", PlayerChoices.map (function (x) { return (x.title); }), help);
            }
            if (null != data[0].player_choices)
                PlayerChooser.context.items = data[0].player_choices.map (function (item) { return ( { value: item }); });
            PlayerChooser.render ();
            if (null == RecorderChooser)
            {
                var help =
                    "<span id='recorders_help' class='help-block'>" +
                        "The queries to use to pick recorder elements." +
                    "</span>";
                RecorderChooser = new chooser.Chooser ("recorders", "Recorders", "Recorder", RecorderChoices.map (function (x) { return (x.title); }), help);
            }
            if (null != data[0].recorder_choices)
                RecorderChooser.context.items = data[0].recorder_choices.map (function (item) { return ( { value: item } ); });
            RecorderChooser.render ();
            if (null != TheSimulation)
                document.getElementById ("cim").innerHTML = "<pre>\n" +  jsonify (TheSimulation) + "\n</pre>"
        }

        function query_islands ()
        {
            // query for topological islands
            cimquery.query (
                // simple TopologicalIsland query
                // "select i.IdentifiedObject.mRID from TopologicalIsland i",
                // ToDo: this query assumes transformers are in a Bay which is directly in a Substation
                // "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Substation s, Bay b where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = b.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID and b.Substation = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID",
                // ToDo: this query assumes transformers are directly in a Substation
                "select i.IdentifiedObject.mRID island, s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from TopologicalIsland i, TopologicalNode n, Terminal t, PowerTransformer p, Substation s where n.TopologicalIsland = i.IdentifiedObject.mRID and t.TopologicalNode = n.IdentifiedObject.mRID and t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and p.ConductingEquipment.Equipment.EquipmentContainer = s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID",
                "",
                "",
                render
            );
        }

        function getStations (callback)
        {
            // get the list of stations
            cimquery.query (
                "select s.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.IdentifiedObject.mRID station from Substation s",
                "",
                "",
                function (data)
                {
                    var rdfstations = data.map (function (obj) { return (obj.station); });
                    // list files and try to match
                    var directory = "/"
                    cimfiles.fetch (directory,
                        function (response)
                        {
                            if (response.status == "FAIL")
                                alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                            else
                            {
                                var stations = response.result.files.filter (function (file) { return (file.is_directory && (-1 != rdfstations.indexOf (file.path))); }).map (function (file) { return (file.path); });
                                callback (stations);
                            }
                        }
                    );
                }
            );
        }

        function getSimulations (station, callback)
        {
            // choose the first one for now
            var directory = "/" + station + "/";
            cimfiles.fetch (directory,
                function (response)
                {
                    if (response.status == "FAIL")
                        alert ("message: " + (response.message ? response.message : "") + " error: " + (response.error ? response.error : ""));
                    else
                    {
                        var simulations = response.result.files.filter (function (file) { return (!file.is_directory && file.path.endsWith (".json")); } ).map (function (file) { return (file.path); });
                        callback (simulations);
                    }
                }
            );
        }

        /**
         * @summary Render the simulations page.
         * @description Uses mustache to create HTML DOM elements that display the simulation options.
         * @function initialize
         * @memberOf module:cimsimulate
         */
        function initialize ()
        {
            document.getElementById ("main").innerHTML = "";
            if (null == TheSimulation)
                getStations (function (stations) {
                    if (0 == stations.length)
                        query_islands ();
                    else
                        getSimulations (stations[0], function (simulations) {
                            // choose the first one for now
                            if (0 < simulations.length)
                            {
                                var simulation = "/" + stations[0] +  "/" + simulations[0];
                                // get the simulation json
                                cimfiles.get (simulation,
                                    function (data)
                                    {
                                        TheSimulation = JSON.parse (data);
                                        render ([ TheSimulation ]);
                                    },
                                    function (response) { alert (jsonify (response)); }
                                );
                            }
                            else
                                query_islands ();
                        });
                });
            else
                render ([ TheSimulation ]);
        }

        return (
            {
                initialize: initialize,
                getStations: getStations,
                getSimulations: getSimulations
            }
        );
    }
)
