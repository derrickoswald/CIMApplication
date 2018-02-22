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
            statement.executeUpdate ("create table shortcircuit_run (id integer primary key autoincrement, description text, time text, default_supply_network_short_circuit_power double, default_supply_network_short_circuit_resistance double, default_supply_network_short_circuit_reactance double, base_temperature double, low_temperature double, high_temperature double, cmax double, cmin double, cosphi double)")
            statement.executeUpdate ("create index if not exists epoc on shortcircuit_run (time)")
        }
        val resultset2 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'shortcircuit'")
        val exists2 = resultset2.next ()
        resultset2.close ()
        if (!exists2)
        {
            statement.executeUpdate ("create table shortcircuit (id integer primary key autoincrement, run integer, node text, equipment text, terminal integer, trafo text, prev text, r double, x double, r0 double, x0 double, errors text, ik double, ik3pol double, ip double, sk double, motor_3ph_max_low double, motor_1ph_max_low double, motor_l_l_max_low double, motor_3ph_max_med double, motor_1ph_max_med double, motor_l_l_max_med double)")
            statement.executeUpdate ("create index if not exists equipment_index on shortcircuit (equipment)")
            statement.executeUpdate ("create index if not exists run_index on shortcircuit (run)")
        }
        val resultset3 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'nullungsbedingung'")
        val exists3 = resultset3.next ()
        resultset3.close ()
        if (!exists3)
        {
            statement.executeUpdate ("create table nullungsbedingung (id integer primary key autoincrement, run integer, node text, equipment text, terminal integer, trafo text, prev text, r double, x double, r0 double, x0 double, fuses text, fusemax double, fuseok boolean, errors text, ik double, ik3pol double, ip double, sk double)")
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
                val datainsert2 = connection.prepareStatement ("insert into shortcircuit (id, run, node, equipment, terminal, trafo, prev, r, x, r0, x0, fuses, fusemax, fuseok, errors, ik, ik3pol, ip, sk) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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