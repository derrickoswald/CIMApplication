package ch.ninecode.mfi

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.rdd.RDD
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Database
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    def mkdirs (path: Path): Unit =
    {
        val parent = path.getParent
        if (null != parent)
        {
            val _ = Files.createDirectories(parent)
        }
    }

    def loadDriver (className: String): Unit =
    {
        val _ = Class.forName(className)
    }

    def using[T <: AutoCloseable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block(resource)
        }
        finally
        {
            try
            {
                resource.close()
            }
            catch
            {
                // connection close failed?
                case e: Exception =>
                    log.error(s"exception caught: ${e.toString}")
            }
        }
    }

    def executeUpdate (statement: Statement, sql: String): Unit =
    {
        val _ = statement.executeUpdate(sql)
    }

    def executeUpdate (statement: PreparedStatement): Unit =
    {
        val _ = statement.executeUpdate()
    }

    def makeSchema (connection: Connection)
    {
        val statement = connection.createStatement()
        val resultset1 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'simulation'")
        val exists1 = resultset1.next()
        resultset1.close()
        if (!exists1)
        {
            executeUpdate(statement,
                """create table simulation
                  |    -- table of simulation executions
                  |(
                  |    id integer primary key autoincrement, -- unique id for each simulation program execution
                  |    description text,                     -- textual description of the simulation run
                  |    time text                             -- the date and time at which the simulation was run
                  |)""".stripMargin)
            executeUpdate(statement, "create index if not exists epoch on simulation (time)")
        }
        val resultset2 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'results'")
        val exists2 = resultset2.next()
        resultset2.close()
        if (!exists2)
        {
            executeUpdate(statement,
                """create table results
                  |    -- table of calculated maximum feed-in values
                  |(
                  |    id integer primary key autoincrement, -- unique id for each simulation result
                  |    simulation integer,                   -- foreign key to corresponding simulation table program execution
                  |    trafo text,                           -- mRID of the PowerTransformer (or ganged transformers) supplying the house
                  |    feeder text,                          -- mRID of the Connector supplying the house from the substation
                  |    house text,                           -- mRID of the EnergyConsumer at this feed-in node
                  |    maximum double,                       -- maximum feed-in power (W)
                  |    eea integer,                          -- the number of PV installations at this feed-in node
                  |    reason text,                          -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
                  |    details text                          -- details regarding the limiting criteria
                  |)""".stripMargin)
            executeUpdate(statement, "create index if not exists house on results (house)")
            executeUpdate(statement, "create index if not exists sim on results (simulation)")
        }
        val resultset3 = statement.executeQuery("select name from sqlite_master where type = 'view' and name = 'feedin'")
        val exists3 = resultset3.next()
        resultset3.close()
        if (!exists3)
        {
            executeUpdate(statement,
                """create view if not exists intermediate as
                  |    -- staging view for unified (precalculated & GridLAB-D simulated) feed-in values
                  |    select
                  |        0 Priority,             -- low priority for precalculated values
                  |        s.description Analysis, -- 'Threshold Precalculation'
                  |        datetime(s.time/1000, 'unixepoch', 'localtime') Date, -- textual program execution time
                  |        s.time When_Epoch,      -- numeric program execution time
                  |        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the house
                  |        r.feeder Feeder,        -- mRID of the Connector supplying the house from the substation
                  |        r.house House,          -- mRID of the EnergyConsumer at this feed-in node
                  |        r.maximum Maximum,      -- maximum feed-in power (W)
                  |        r.reason Reason,        -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
                  |        r.details Details       -- details regarding the limiting criteria
                  |    from
                  |        simulation s,           -- program executions
                  |        results r               -- result values
                  |    where
                  |        s.description = 'Threshold Precalculation' and -- select only precalculated values
                  |        s.id = r.simulation     -- join the program execution with the result value
                  |union
                  |    select
                  |        1 Priority,             -- high priority for load-flow values
                  |        s.description Analysis, -- 'Einspeiseleistung'
                  |        datetime(s.time/1000, 'unixepoch', 'localtime') Date, -- textual program execution time
                  |        s.time When_Epoch,      -- numeric program execution time
                  |        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the house
                  |        r.feeder Feeder,        -- mRID of the Connector supplying the house from the substation
                  |        r.house House,          -- mRID of the EnergyConsumer at this feed-in node
                  |        r.maximum Maximum,      -- maximum feed-in power (W)
                  |        r.reason Reason,        -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
                  |        r.details Details       -- details regarding the limiting criteria
                  |    from
                  |        simulation s,           -- program executions
                  |        results r               -- result values
                  |    where
                  |        s.description = 'Einspeiseleistung' and -- select only load-flow values
                  |        s.id = r.simulation     -- join the program execution with the result value""".stripMargin)
            executeUpdate(statement,
                """create view if not exists feedin as
                  |    -- view of the most recent best estimated value of maximum feed-in power
                  |    select
                  |        i.Analysis,             -- type of analysis, prefer 'Einspeiseleistung'
                  |        i.Transformer,          -- mRID of the PowerTransformer (or ganged transformers) supplying the house
                  |        i.Feeder,               -- mRID of the Connector supplying the house from the substation
                  |        i.House,                -- mRID of the EnergyConsumer at this feed-in node
                  |        i.Maximum,              -- maximum feed-in power (W)
                  |        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
                  |        i.Details,              -- details regarding the limiting criteria
                  |        max(i.When_Epoch) When_Epoch -- select only the most recent value
                  |    from
                  |        intermediate i          -- staging view
                  |    where
                  |        Priority = 1            -- select only the load-flow values (if any)
                  |    group by
                  |        House                   -- for each unique EnergyConsumer mRID
                  |union
                  |    select
                  |        i.Analysis,             -- type of analysis, fall back to 'Threshold Precalculation'
                  |        i.Transformer,          -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
                  |        i.Feeder,               -- mRID of the Connector supplying the house from the substation
                  |        i.House,                -- mRID of the EnergyConsumer at this feed-in node
                  |        i.Maximum,              -- maximum feed-in power (W)
                  |        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
                  |        i.Details,              -- details regarding the limiting criteria
                  |        max(i.When_Epoch) When_Epoch -- select only the most recent value
                  |    from
                  |        intermediate i          -- staging view
                  |    where
                  |        House not in (select House from intermediate where Priority = 1 group By House) -- select precalculated values if no load-flow value is present
                  |    group by
                  |        House                   -- for each unique EnergyConsumer mRID""".stripMargin)
        }
        statement.close()
    }

    def store (description: String, outputfile: String)(records: Array[MaxEinspeiseleistung]): Int = synchronized
    {
        // make the directory
        val file = Paths.get(outputfile)
        mkdirs(file)

        // load the sqlite-JDBC driver using the current class loader
        loadDriver("org.sqlite.JDBC")

        // create a database connection
        try
        {
            // create a database connection
            using(DriverManager.getConnection(s"jdbc:sqlite:${outputfile}"))
            {
                connection =>
                    connection.setAutoCommit(false)

                    // create schema
                    makeSchema(connection)

                    if (0 != records.length)
                    {
                        // insert the simulation
                        val now = Calendar.getInstance()
                        val insert = connection.prepareStatement("insert into simulation (id, description, time) values (?, ?, ?)")
                        insert.setNull(1, Types.INTEGER)
                        insert.setString(2, description)
                        insert.setTimestamp(3, new Timestamp(now.getTimeInMillis))
                        executeUpdate(insert)
                        val statement = connection.createStatement()
                        val resultset = statement.executeQuery("select last_insert_rowid() id")
                        val id = if (resultset.next()) resultset.getInt("id") else -1
                        resultset.close()
                        statement.close()

                        // insert the results
                        val datainsert = connection.prepareStatement("insert into results (id, simulation, trafo, feeder, house, maximum, reason, details) values (?, ?, ?, ?, ?, ?, ?, ?)")
                        for (i <- records.indices)
                        {
                            datainsert.setNull(1, Types.INTEGER)
                            datainsert.setInt(2, id)
                            datainsert.setString(3, records(i).trafo)
                            datainsert.setString(4, records(i).feeder)
                            datainsert.setString(5, records(i).house)
                            records(i).reason match
                            {
                                case "voltage limit" | "current limit" | "transformer limit" =>
                                    records(i).max match
                                    {
                                        case Some(kw) => datainsert.setDouble(6, kw)
                                        case None => datainsert.setNull(6, Types.DOUBLE)
                                    }
                                    datainsert.setString(7, records(i).reason)
                                    datainsert.setString(8, records(i).details)
                                case _ =>
                                    datainsert.setNull(6, Types.DOUBLE)
                                    datainsert.setString(7, "no results")
                                    datainsert.setString(8, records(i).reason)
                            }
                            datainsert.executeUpdate()
                        }
                        datainsert.close()
                        connection.commit()

                        id
                    }
                    else
                        0
            }
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException =>
                log.error(s"exception caught: ${e.toString}")
                -1
        }
    }

    def store_precalculation (description: String, outputfile: String)(results: RDD[MaxPowerFeedingNodeEEA]): Int = synchronized
    {
        // make the directory
        val file = Paths.get(outputfile)
        mkdirs(file)

        // load the sqlite-JDBC driver using the current class loader
        loadDriver("org.sqlite.JDBC")

        try
        {
            // create a database connection
            using(DriverManager.getConnection(s"jdbc:sqlite:${outputfile}"))
            {
                connection =>
                    connection.setAutoCommit(false)

                    // create schema
                    makeSchema(connection)

                    // insert the simulation
                    val now = Calendar.getInstance()
                    val insert = connection.prepareStatement("insert into simulation (id, description, time) values (?, ?, ?)")
                    insert.setNull(1, Types.INTEGER)
                    insert.setString(2, description)
                    insert.setTimestamp(3, new Timestamp(now.getTimeInMillis))
                    executeUpdate(insert)
                    val statement = connection.createStatement()
                    val resultset = statement.executeQuery("select last_insert_rowid() id")
                    val id = if (resultset.next()) resultset.getInt("id") else -1
                    resultset.close()
                    statement.close()

                    // insert the results
                    val records = results.collect()

                    val datainsert = connection.prepareStatement("insert into results (id, simulation, trafo, feeder, house, maximum, eea, reason, details) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    for (i <- records.indices)
                    {
                        datainsert.setNull(1, Types.INTEGER)
                        datainsert.setInt(2, id)
                        datainsert.setString(3, records(i).source_obj)
                        datainsert.setString(4, records(i).feeder)
                        datainsert.setString(5, records(i).mrid)
                        datainsert.setInt(7, if (records(i).eea != null) records(i).eea.size else 0)
                        records(i).reason match
                        {
                            case "voltage limit" | "current limit" | "transformer limit" =>
                                datainsert.setDouble(6, records(i).max_power_feeding)
                                datainsert.setString(8, records(i).reason)
                                datainsert.setString(9, records(i).details)
                            case _ =>
                                datainsert.setNull(6, Types.DECIMAL) // also set the maximum to null
                                datainsert.setString(8, "no results")
                                datainsert.setString(9, records(i).reason)
                        }
                        datainsert.executeUpdate()
                    }
                    datainsert.close()
                    connection.commit()

                    id
            }
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException =>
                log.error(s"exception caught: ${e.toString}")
                -1
        }
    }

    def fetchTransformersWithEEA (simulation: Int, outputfile: String): Array[String] =
    {
        val ret = new ArrayBuffer[String]()

        // check if the directory exists
        val file = Paths.get(s"${outputfile}")
        if (!Files.exists(file))
            log.error(s"database file ${file} does not exist")
        else
        {
            // load the sqlite-JDBC driver using the current class loader
            loadDriver("org.sqlite.JDBC")

            try
            {
                // create a database connection
                using(DriverManager.getConnection(s"jdbc:sqlite:${outputfile}"))
                {
                    connection =>
                        val statement = connection.prepareStatement("select distinct(trafo) from results where eea > 0 and trafo not in (select trafo from results where simulation in (select id from simulation where id > ? and description = 'Einspeiseleistung'))")
                        statement.setInt(1, simulation)
                        val resultset = statement.executeQuery()
                        while (resultset.next())
                            ret.append(resultset.getString(1))
                        resultset.close()
                }
            }
            catch
            {
                // if the error message is "out of memory",
                // it probably means no database file is found
                case e: SQLException =>
                    log.error(s"exception caught: ${e.toString}")
            }
        }
        ret.toArray
    }

    /**
     * Determine the set of EnergyConsumers for which a re-calculation is necessary.
     *
     * @param simulation The current (most recent) simulation.
     * @param reference  The reference (historical) simulation.
     * @param delta      The difference in the amount of power that will trigger a recalculation.
     * @param outputfile The name of the output database file.
     * @return The list of mRID for EnergyConsumer objects needing to be recalculated.
     */
    def fetchHousesWithDifferentEEA (simulation: String, reference: Int, delta: Double, outputfile: String): Array[String] =
    {
        val ret = new ArrayBuffer[String]()

        // check if the directory exists
        val file = Paths.get(s"${outputfile}")
        if (!Files.exists(file))
            log.error(s"database file ${file} does not exist")
        else
        {
            // load the sqlite-JDBC driver using the current class loader
            loadDriver("org.sqlite.JDBC")
            try
            {
                // create a database connection
                using(DriverManager.getConnection(s"jdbc:sqlite:${outputfile}"))
                {
                    connection =>
                        val statement = connection.prepareStatement("select distinct(current.house) from (select * from results where simulation = ?) current, (select * from results where simulation = ?) reference where current.house = reference.house and ((current.eea != reference.eea) or (abs(current.maximum - reference.maximum) > ?))")
                        statement.setInt(1, simulation.toInt)
                        statement.setInt(2, reference)
                        statement.setDouble(3, delta)
                        val resultset = statement.executeQuery()
                        while (resultset.next())
                            ret.append(resultset.getString(1))
                        resultset.close()
                }
            }
            catch
            {
                // if the error message is "out of memory",
                // it probably means no database file is found
                case e: SQLException =>
                    log.error(s"exception caught: ${e.toString}")
            }
        }
        ret.toArray
    }
}