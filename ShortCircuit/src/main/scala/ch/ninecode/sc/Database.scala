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
                  |    id integer primary key autoincrement,    -- unique id for each program execution
                  |    description text,                        -- arbitrary text value describing this program execution
                  |    time text,                               -- the time of this program execution (number of miliseconds since 1970-01-01 00:00:00 UTC)
                  |    default_short_circuit_power double,      -- available short circuit network power used (at transformer primary) where no equivalent injection was found (VA)
                  |    default_short_circuit_resistance double, -- network short circuit resistance used where no equivalent injection was found (Ω)
                  |    default_short_circuit_reactance double,  -- network short circuit reactance used where no equivalent injection was found (Ω)
                  |    base_temperature double,                 -- temperature of elements in the input CIM file (°C)
                  |    low_temperature double,                  -- low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
                  |    high_temperature double,                 -- high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
                  |    cmax double,                             -- voltage factor for maximum fault level (used for rating equipment) (dimensionless)
                  |    cmin double,                             -- voltage factor for minimum fault level (used for protections settings) (dimensionless)
                  |    worstcasepf boolean,                     -- worst case motor power factor assumed (cos term = 1.0, cosphi ignored)
                  |    cosphi double                            -- power factor of (motor) load (dimensionless)
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
                  |    -- table of maximum fault level values (impedances at low_temperature)
                  |(
                  |    id integer primary key autoincrement, -- unique id for each maximum fault level result record
                  |    run integer,                          -- foreign key to corresponding shortcircuit_run table program execution
                  |    node text,                            -- CIM ConnectivityNode mRID
                  |    equipment text,                       -- CIM ConductingEquipment mRID
                  |    terminal integer,                     -- CIM Terminal mRID referring to the node and equipment
                  |    errors text,                          -- comma separated list of error and warning messages encountered in processing
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    ik double,                            -- one phase short bolted circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double,                            -- short-circuit power at the point of common coupling (VA)
                  |    imax_3ph_low double,                  -- maximum inrush current (3 phase) for repetition_rate<0.01/min (A)
                  |    imax_1ph_low double,                  -- maximum inrush current (1 phase, line to neutral) for repetition_rate<0.01/min (A)
                  |    imax_2ph_low double,                  -- maximum inrush current (line to line) for repetition_rate<0.01/min (A)
                  |    imax_3ph_med double,                  -- maximum inrush current (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (A)
                  |    imax_1ph_med double,                  -- maximum inrush current (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (A)
                  |    imax_2ph_med double                   -- maximum inrush current (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (A)
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
                  |    -- table of minimum fault level values (impedances at high_temperature)
                  |(
                  |    id integer primary key autoincrement, -- unique id for each minimum fault level result record
                  |    run integer,                          -- foreign key to corresponding shortcircuit_run table program execution
                  |    node text,                            -- CIM ConnectivityNode mRID
                  |    equipment text,                       -- CIM ConductingEquipment mRID
                  |    terminal integer,                     -- CIM Terminal mRID referring to the node and equipment
                  |    errors text,                          -- comma separated list of error and warning messages encountered in processing
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    ik double,                            -- one phase short bolted circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double,                            -- short-circuit power at the point of common coupling (VA)
                  |    fuses text,                           -- comma separated list of fuse values from the source (primary of feeding transformer) to this node (A)
                  |    fusemax double,                       -- maximum recommended fuse value for the calculated fault current (A)
                  |    fuseok boolean                        -- evaluation of whether the first fuse is an appropriate value (true) or not (false)
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
                val insert = connection.prepareStatement ("insert into shortcircuit_run (id, description, time, default_short_circuit_power, default_short_circuit_resistance, default_short_circuit_reactance, base_temperature, low_temperature, high_temperature, cmax, cmin, worstcasepf, cosphi) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                insert.setNull (1, Types.INTEGER)
                insert.setString (2, description)
                insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
                insert.setDouble (4, options.default_short_circuit_power)
                insert.setDouble (5, options.default_short_circuit_impedance.re)
                insert.setDouble (6, options.default_short_circuit_impedance.im)
                insert.setDouble (7, options.base_temperature)
                insert.setDouble (8, options.low_temperature)
                insert.setDouble (9, options.high_temperature)
                insert.setDouble (10, options.cmax)
                insert.setDouble (11, options.cmin)
                insert.setBoolean (12, options.worstcasepf)
                insert.setDouble (13, options.cosphi)
                insert.executeUpdate ()
                val statement = connection.createStatement ()
                val resultset = statement.executeQuery ("select last_insert_rowid() id")
                resultset.next ()
                val id = resultset.getInt ("id")
                resultset.close ()
                statement.close ()

                // insert the results
                val datainsert1 = connection.prepareStatement ("insert into shortcircuit (id, run, node, equipment, terminal, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, imax_3ph_low, imax_1ph_low, imax_2ph_low, imax_3ph_med, imax_1ph_med, imax_2ph_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                for (i <- records.indices)
                {
                    datainsert1.setNull (1, Types.INTEGER)
                    datainsert1.setInt (2, id)
                    datainsert1.setString (3, records(i).node)
                    datainsert1.setString (4, records(i).equipment)
                    datainsert1.setInt (5, records(i).terminal)
                    if (null == records(i).errors)
                        datainsert1.setNull (6, Types.VARCHAR)
                    else
                        datainsert1.setString (6, records(i).errors.mkString (","))
                    datainsert1.setString (7, records(i).tx)
                    datainsert1.setString (8, records(i).prev)
                    datainsert1.setDouble (9, records(i).low_r)
                    datainsert1.setDouble (10, records(i).low_x)
                    datainsert1.setDouble (11, records(i).low_r0)
                    datainsert1.setDouble (12, records(i).low_x0)
                    datainsert1.setDouble (13, records(i).low_ik)
                    datainsert1.setDouble (14, records(i).low_ik3pol)
                    datainsert1.setDouble (15, records(i).low_ip)
                    datainsert1.setDouble (16, records(i).low_sk)
                    datainsert1.setDouble (17, records(i).imax_3ph_low)
                    datainsert1.setDouble (18, records(i).imax_1ph_low)
                    datainsert1.setDouble (19, records(i).imax_2ph_low)
                    datainsert1.setDouble (20, records(i).imax_3ph_med)
                    datainsert1.setDouble (21, records(i).imax_1ph_med)
                    datainsert1.setDouble (22, records(i).imax_2ph_med)
                    datainsert1.executeUpdate ()
                }
                datainsert1.close ()
                val datainsert2 = connection.prepareStatement ("insert into nullungsbedingung (id, run, node, equipment, terminal, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, fuses, fusemax, fuseok) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                for (i <- records.indices)
                {
                    datainsert2.setNull (1, Types.INTEGER)
                    datainsert2.setInt (2, id)
                    datainsert2.setString (3, records(i).node)
                    datainsert2.setString (4, records(i).equipment)
                    datainsert2.setInt (5, records(i).terminal)
                    if (null == records(i).errors)
                        datainsert2.setNull (6, Types.VARCHAR)
                    else
                        datainsert2.setString (6, records(i).errors.mkString (","))
                    datainsert2.setString (7, records(i).tx)
                    datainsert2.setString (8, records(i).prev)
                    datainsert2.setDouble (9, records(i).high_r)
                    datainsert2.setDouble (10, records(i).high_x)
                    datainsert2.setDouble (11, records(i).high_r0)
                    datainsert2.setDouble (12, records(i).high_x0)
                    datainsert2.setDouble (13, records(i).high_ik)
                    datainsert2.setDouble (14, records(i).high_ik3pol)
                    datainsert2.setDouble (15, records(i).high_ip)
                    datainsert2.setDouble (16, records(i).high_sk)
                    if ((null == records(i).fuses) || records(i).fuses.isEmpty)
                    {
                        datainsert2.setNull (17, Types.VARCHAR)
                        datainsert2.setNull (18, Types.DOUBLE)
                        datainsert2.setNull (19, Types.BOOLEAN)
                    }
                    else
                    {
                        datainsert2.setString (17, records(i).fuses.mkString (","))
                        datainsert2.setDouble (18, FData.fuse (records(i).high_ik))
                        datainsert2.setBoolean (19, FData.fuseOK (records(i).high_ik, records(i).fuses))
                    }
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