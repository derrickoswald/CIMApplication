package ch.ninecode.sc

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Database
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    def makeSchema (connection: Connection)
    {
        val statement = connection.createStatement ()
        val resultset1 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'shortcircuit_run'")
        val exists1 = resultset1.next ()
        resultset1.close ()
        if (!exists1)
        {
            statement.executeUpdate (
                """create table shortcircuit_run
                  |    -- table of parameters used for each program execution
                  |(
                  |    id integer primary key autoincrement,                   -- unique id for each program execution
                  |    description text,                                       -- arbitrary text value describing this program execution
                  |    time text,                                              -- the time of this program execution (number of miliseconds since 1970-01-01 00:00:00 UTC)
                  |    default_supply_network_short_circuit_power double,      -- available short circuit network power used (at transformer primary) where no equivalent injection was found (VA)
                  |    default_supply_network_short_circuit_resistance double, -- network short circuit resistance used where no equivalent injection was found (Ω)
                  |    default_supply_network_short_circuit_reactance double,  -- network short circuit reactance used where no equivalent injection was found (Ω)
                  |    base_temperature double,                                -- temperature of elements in the input CIM file (°C)
                  |    low_temperature double,                                 -- low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
                  |    high_temperature double,                                -- high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
                  |    cmax double,                                            -- voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless)
                  |    cmin double,                                            -- voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless)
                  |    cosphi double                                           -- power factor of (motor) load (dimensionless)
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists epoc on shortcircuit_run (time)")
        }
        val resultset2 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'shortcircuit'")
        val exists2 = resultset2.next ()
        resultset2.close ()
        if (!exists2)
        {
            statement.executeUpdate (
                """create table shortcircuit
                  |    -- table of maximum fault level values
                  |(
                  |    id integer primary key autoincrement, -- unique id for each maximum fault level result record
                  |    run integer,                          -- foreign key to corresponding shortcircuit_run table program execution
                  |    node text,                            -- CIM ConnectivityNode mRID
                  |    equipment text,                       -- CIM ConductingEquipment mRID
                  |    terminal integer,                     -- CIM Terminal mRID referring to the node and equipment
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    errors text,                          -- error and warning messages encountered in processing
                  |    ik double,                            -- one phase short bolted circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double,                            -- short-circuit power at the point of common coupling (VA)
                  |    motor_3ph_max_low double,             -- maximum motor power (3 phase) for repetition_rate<0.01/min (W)
                  |    motor_1ph_max_low double,             -- maximum motor power (1 phase, line to neutral) for repetition_rate<0.01/min (W)
                  |    motor_l_l_max_low double,             -- maximum motor power (line to line) for repetition_rate<0.01/min (W)
                  |    motor_3ph_max_med double,             -- maximum motor power (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (W)
                  |    motor_1ph_max_med double,             -- maximum motor power (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (W)
                  |    motor_l_l_max_med double              -- maximum motor power (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (W)
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists equipment_index on shortcircuit (equipment)")
            statement.executeUpdate ("create index if not exists run_index on shortcircuit (run)")
        }
        val resultset3 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'nullungsbedingung'")
        val exists3 = resultset3.next ()
        resultset3.close ()
        if (!exists3)
        {
            statement.executeUpdate (
                """create table nullungsbedingung
                  |    -- table of minimum fault level values
                  |(
                  |    id integer primary key autoincrement, -- unique id for each minimum fault level result record
                  |    run integer,                          -- foreign key to corresponding shortcircuit_run table program execution
                  |    node text,                            -- CIM ConnectivityNode mRID
                  |    equipment text,                       -- CIM ConductingEquipment mRID
                  |    terminal integer,                     -- CIM Terminal mRID referring to the node and equipment
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    fuses text,                           -- comma separated list of fuse values from the source (primary of feeding transformer) to this node (A)
                  |    fusemax double,                       -- maximum recommended fuse value for the calculated fault current (A)
                  |    fuseok boolean,                       -- evaluation of whether the first fuse is an appropriate value (true) or not (false)
                  |    errors text,                          -- error and warning messages encountered in processing
                  |    ik double,                            -- one phase short bolted circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double                             -- short-circuit power at the point of common coupling (VA)
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists equipment_index on nullungsbedingung (equipment)")
            statement.executeUpdate ("create index if not exists run_index on nullungsbedingung (run)")
        }
        statement.close ()
    }

    def store (description: String, options: ShortCircuitOptions) (records: Array[ScResult]): Int = synchronized
    {
        // make the directory
        val file = Paths.get ("results/dummy")
        Files.createDirectories (file.getParent)

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:results/shortcircuit.db")
            connection.setAutoCommit (false)

            // create schema
            makeSchema (connection)

            if (0 != records.length)
            {
                // insert the simulation
                val now = Calendar.getInstance ()
                val insert = connection.prepareStatement ("insert into shortcircuit_run (id, description, time, default_supply_network_short_circuit_power, default_supply_network_short_circuit_resistance, default_supply_network_short_circuit_reactance, base_temperature, low_temperature, high_temperature, cmax, cmin, cosphi) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                insert.setNull (1, Types.INTEGER)
                insert.setString (2, description)
                insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
                insert.setDouble (4, options.default_supply_network_short_circuit_power)
                insert.setDouble (5, options.default_supply_network_short_circuit_impedance.re)
                insert.setDouble (6, options.default_supply_network_short_circuit_impedance.im)
                insert.setDouble (7, options.base_temperature)
                insert.setDouble (8, options.low_temperature)
                insert.setDouble (9, options.high_temperature)
                insert.setDouble (10, options.cmax)
                insert.setDouble (11, options.cmin)
                insert.setDouble (12, options.cosphi)
                insert.executeUpdate ()
                val statement = connection.createStatement ()
                val resultset = statement.executeQuery ("select last_insert_rowid() id")
                resultset.next ()
                val id = resultset.getInt ("id")
                resultset.close ()
                statement.close ()

                // insert the results
                val datainsert1 = connection.prepareStatement ("insert into shortcircuit (id, run, node, equipment, terminal, trafo, prev, r, x, r0, x0, errors, ik, ik3pol, ip, sk, motor_3ph_max_low, motor_1ph_max_low, motor_l_l_max_low, motor_3ph_max_med, motor_1ph_max_med, motor_l_l_max_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                for (i <- records.indices)
                {
                    datainsert1.setNull (1, Types.INTEGER)
                    datainsert1.setInt (2, id)
                    datainsert1.setString (3, records(i).node)
                    datainsert1.setString (4, records(i).equipment)
                    datainsert1.setInt (5, records(i).terminal)
                    datainsert1.setString (6, records(i).tx)
                    datainsert1.setString (7, records(i).prev)
                    datainsert1.setDouble (8, records(i).low_r)
                    datainsert1.setDouble (9, records(i).low_x)
                    datainsert1.setDouble (10, records(i).low_r0)
                    datainsert1.setDouble (11, records(i).low_x0)
                    if (null == records(i).errors)
                        datainsert1.setNull (12, Types.VARCHAR)
                    else
                        datainsert1.setString (12, records(i).errors.mkString (","))
                    datainsert1.setDouble (13, records(i).low_ik)
                    datainsert1.setDouble (14, records(i).low_ik3pol)
                    datainsert1.setDouble (15, records(i).low_ip)
                    datainsert1.setDouble (16, records(i).low_sk)
                    datainsert1.setDouble (17, records(i).low_motor_3ph_max_low)
                    datainsert1.setDouble (18, records(i).low_motor_1ph_max_low)
                    datainsert1.setDouble (19, records(i).low_motor_l_l_max_low)
                    datainsert1.setDouble (20, records(i).low_motor_3ph_max_med)
                    datainsert1.setDouble (21, records(i).low_motor_1ph_max_med)
                    datainsert1.setDouble (22, records(i).low_motor_l_l_max_med)
                    datainsert1.executeUpdate ()
                }
                datainsert1.close ()
                val datainsert2 = connection.prepareStatement ("insert into nullungsbedingung (id, run, node, equipment, terminal, trafo, prev, r, x, r0, x0, fuses, fusemax, fuseok, errors, ik, ik3pol, ip, sk) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                for (i <- records.indices)
                {
                    datainsert2.setNull (1, Types.INTEGER)
                    datainsert2.setInt (2, id)
                    datainsert2.setString (3, records(i).node)
                    datainsert2.setString (4, records(i).equipment)
                    datainsert2.setInt (5, records(i).terminal)
                    datainsert2.setString (6, records(i).tx)
                    datainsert2.setString (7, records(i).prev)
                    datainsert2.setDouble (8, records(i).high_r)
                    datainsert2.setDouble (9, records(i).high_x)
                    datainsert2.setDouble (10, records(i).high_r0)
                    datainsert2.setDouble (11, records(i).high_x0)
                    if ((null == records(i).fuses) || records(i).fuses.isEmpty)
                    {
                        datainsert2.setNull (12, Types.VARCHAR)
                        datainsert2.setNull (13, Types.DOUBLE)
                        datainsert2.setNull (14, Types.BOOLEAN)
                    }
                    else
                    {
                        datainsert2.setString (12, records(i).fuses.mkString (","))
                        datainsert2.setDouble (13, FData.fuse (records(i).high_ik))
                        datainsert2.setBoolean (14, FData.fuseOK (records(i).high_ik, records(i).fuses))
                    }
                    if (null == records(i).errors)
                        datainsert2.setNull (15, Types.VARCHAR)
                    else
                        datainsert2.setString (15, records(i).errors.mkString (","))
                    datainsert2.setDouble (16, records(i).high_ik)
                    datainsert2.setDouble (17, records(i).high_ik3pol)
                    datainsert2.setDouble (18, records(i).high_ip)
                    datainsert2.setDouble (19, records(i).high_sk)
                    datainsert2.executeUpdate ()
                }
                datainsert2.close ()

                connection.commit ()

                id
            }
            else
                0
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ log.error ("exception caught: " + e)
            -1
        }
        finally
        {
            try
            {
                if (connection != null)
                    connection.close ()
            }
            catch
            {
                // connection close failed
                case e: SQLException ⇒ log.error ("exception caught: " + e)
            }
        }
    }
}