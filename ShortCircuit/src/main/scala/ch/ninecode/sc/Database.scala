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
            statement.executeUpdate ("create table shortcircuit_run (id integer primary key autoincrement, description text, time text, default_supply_network_short_circuit_power double, default_supply_network_short_circuit_angle double, cmax double, cmin double, cosphi double, starting_ratio double)")
            statement.executeUpdate ("create index if not exists epoc on shortcircuit_run (time)")
        }
        val resultset2 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'shortcircuit'")
        val exists2 = resultset2.next ()
        resultset2.close ()
        if (!exists2)
        {
            statement.executeUpdate ("create table shortcircuit (id integer primary key autoincrement, run integer, node text, equipment text, trafo text, r double, x double, r0 double, x0 double, fuses text, fusemax double, fuseok boolean, errors text, ik double, ik3pol double, ip double, sk double, motor_3ph_max_low double, motor_1ph_max_low double, motor_l_l_max_low double, motor_3ph_max_med double, motor_1ph_max_med double, motor_l_l_max_med double)")
            statement.executeUpdate ("create index if not exists equipment_index on shortcircuit (equipment)")
            statement.executeUpdate ("create index if not exists run_index on shortcircuit (run)")
        }
        statement.close ()
    }

    def store (description: String, options: ShortCircuitOptions) (records: Array[HouseConnection]): Int = synchronized
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
                val insert = connection.prepareStatement ("insert into shortcircuit_run (id, description, time, default_supply_network_short_circuit_power, default_supply_network_short_circuit_angle, cmax, cmin, cosphi, starting_ratio) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                insert.setNull (1, Types.INTEGER)
                insert.setString (2, description)
                insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
                insert.setDouble (4, options.default_supply_network_short_circuit_power)
                insert.setDouble (5, options.default_supply_network_short_circuit_angle)
                insert.setDouble (6, options.cmax)
                insert.setDouble (7, options.cmin)
                insert.setDouble (8, options.cosphi)
                insert.setDouble (9, options.starting_ratio)
                insert.executeUpdate ()
                val statement = connection.createStatement ()
                val resultset = statement.executeQuery ("select last_insert_rowid() id")
                resultset.next ()
                val id = resultset.getInt ("id")
                resultset.close ()
                statement.close ()

                // insert the results
                val datainsert = connection.prepareStatement ("insert into shortcircuit (id, run, node, equipment, trafo, r, x, r0, x0, fuses, fusemax, fuseok, errors, ik, ik3pol, ip, sk, motor_3ph_max_low, motor_1ph_max_low, motor_l_l_max_low, motor_3ph_max_med, motor_1ph_max_med, motor_l_l_max_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                for (i <- records.indices)
                {
                    datainsert.setNull (1, Types.INTEGER)
                    datainsert.setInt (2, id)
                    datainsert.setString (3, records(i).node)
                    datainsert.setString (4, records(i).equipment)
                    datainsert.setString (5, records(i).tx)
                    datainsert.setDouble (6, records(i).r)
                    datainsert.setDouble (7, records(i).x)
                    datainsert.setDouble (8, records(i).r0)
                    datainsert.setDouble (9, records(i).x0)
                    datainsert.setString (10, records(i).fuses.mkString (","))
                    if (records(i).fuses.isEmpty)
                    {
                        datainsert.setNull (11, Types.DOUBLE)
                        datainsert.setNull (12, Types.BOOLEAN)
                    }
                    else
                    {
                        datainsert.setDouble (11, FData.fuse (records(i).ik))
                        datainsert.setBoolean (12, FData.fuseOK (records(i).ik, records(i).fuses))
                    }
                    if (null == records(i).errors)
                        datainsert.setNull (13, Types.VARCHAR)
                    else
                        datainsert.setString (13, records(i).errors.mkString (","))
                    datainsert.setDouble (14, records(i).ik)
                    datainsert.setDouble (15, records(i).ik3pol)
                    datainsert.setDouble (16, records(i).ip)
                    datainsert.setDouble (17, records(i).sk)
                    datainsert.setDouble (18, records(i).motor_3ph_max_low)
                    datainsert.setDouble (19, records(i).motor_1ph_max_low)
                    datainsert.setDouble (20, records(i).motor_l_l_max_low)
                    datainsert.setDouble (21, records(i).motor_3ph_max_med)
                    datainsert.setDouble (22, records(i).motor_1ph_max_med)
                    datainsert.setDouble (23, records(i).motor_l_l_max_med)
                    datainsert.executeUpdate ()
                }
                datainsert.close ()
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