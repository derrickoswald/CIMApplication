/**
 * Cassandra common queries for CIM Application
 */
"use strict";

define
(
    ["cimquery"],
    /**
     * @summary Cassandra access.
     * @description Common Cassandra queries for CIM application.
     * @exports cimcassandra
     * @version 1.0
     */
    function (cimquery)
    {
        /**
         * Retrieve simulation details from the given simulation.
         * @param simulation the simulation record from Cassandra
         * @returns {Promise<[T, T]>}
         */
        function getSimulationDetails (simulation)
        {
            let sim = simulation;
            let keyspace = sim.output_keyspace;
            let id = sim.id;
            return (
                Promise.all (
                    [
                        cimquery.queryPromise ({ sql: `select * from ${keyspace}.simulation_player where simulation = '${id}'`, cassandra: true })
                            .then (players => sim.players = players, reason => { alert (reason); sim.players = []; }),
                        cimquery.queryPromise ({ sql: `select * from ${keyspace}.simulation_recorder where simulation = '${id}'`, cassandra: true })
                            .then (recorders => sim.recorders = recorders, reason => { alert (reason); sim.recorders = []; })
                    ]
                )
                .then (() => sim)
            );
        }

        /**
         * Retrieve the simulations from the given keyspace with players and recorders.
         * @param keyspace the keyspace to query
         * @returns {Promise<[T]>}
         */
        function getSimulationsWithDetails (keyspace)
        {
            return (
                cimquery.queryPromise ({ sql: `select * from ${keyspace}.simulation`, cassandra: true })
                    .then (
                        simulations =>
                        {
                            const sims = simulations;
                            return (
                                Promise.all (
                                    sims.map (simulation => getSimulationDetails (simulation))
                                )
                                .then (() => sims)
                            );
                        }
                    )
            );
        }

        /**
         * Retrieve the simulations from the given keyspace.
         * @param keyspace the keyspace to query
         * @returns {Promise<[T]>}
         */
        function getSimulations (keyspace)
        {
            return (cimquery.queryPromise ({ sql: `select * from ${keyspace}.simulation`, cassandra: true }));
        }

        /**
         * Get the keyspaces with simulation data and their simulations.
         */
        function getAllSimulations ()
        {
            return (
                cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering", cassandra: true })
                    .then (
                        keyspaces =>
                        {
                            return (
                                Promise.all (keyspaces.map (keyspace => getSimulations (keyspace.keyspace_name)))
                                .then (simulations => simulations.flatMap (x => x))
                            );
                        }
                    )
            );
        }

        /**
         * Get the keyspaces with simulation data and their simulations plus the players and recorders.
         */
        function getAllSimulationsWithDetails ()
        {
            return (
                cimquery.queryPromise ({ sql: "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering", cassandra: true })
                    .then (
                        keyspaces =>
                        {
                            return (
                                Promise.all (keyspaces.map (keyspace => getSimulationsWithDetails (keyspace.keyspace_name)))
                                    .then (simulations => simulations.flatMap (x => x))
                            );
                        }
                    )
            );
        }

        /**
         * Get the available keyspace names.
         *
         * @returns {Promise<String[]>}
         */
        function getKeyspaces ()
        {
            return (
                    new Promise (
                            (resolve, reject) =>
                            {
                                cimquery.queryPromise (
                                        {
                                            cassandra: true,
                                            sql: "select * from system_schema.keyspaces"
                                        }
                                ).then (
                                        (result) =>
                                        {
                                            resolve (result.map (x => x.keyspace_name).filter (
                                                    x =>
                                                    {
                                                        switch (x)
                                                        {
                                                            case "system_auth":
                                                            case "system_schema":
                                                            case "system_distributed":
                                                            case "system":
                                                            case "system_traces":
                                                                return (false);
                                                            default:
                                                                return (true);
                                                        }
                                                    }
                                                    )
                                            );
                                        }
                                )
                            }
                    )
            );
        }

        return (
            {
                getSimulationDetails: getSimulationDetails,
                getSimulationsWithDetails: getSimulationsWithDetails,
                getSimulations: getSimulations,
                getAllSimulations: getAllSimulations,
                getAllSimulationsWithDetails: getAllSimulationsWithDetails,
                getKeyspaces: getKeyspaces
            }
        );
    }
);