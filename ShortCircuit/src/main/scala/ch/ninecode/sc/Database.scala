package ch.ninecode.sc

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar

import org.apache.spark.rdd.RDD
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
                  |    id integer primary key autoincrement,        -- unique id for each program execution
                  |    description text,                            -- arbitrary text value describing this program execution
                  |    time text,                                   -- the time of this program execution (number of miliseconds since 1970-01-01 00:00:00 UTC)
                  |    max_default_short_circuit_power double,      -- maximum available short circuit network power used (at transformer primary) where no equivalent injection was found (VA)
                  |    max_default_short_circuit_resistance double, -- maximum network short circuit resistance used where no equivalent injection was found (Ω)
                  |    max_default_short_circuit_reactance double,  -- maximum network short circuit reactance used where no equivalent injection was found (Ω)
                  |    min_default_short_circuit_power double,      -- minimum available short circuit network power used (at transformer primary) where no equivalent injection was found (VA)
                  |    min_default_short_circuit_resistance double, -- minimum network short circuit resistance used where no equivalent injection was found (Ω)
                  |    min_default_short_circuit_reactance double,  -- minimum network short circuit reactance used where no equivalent injection was found (Ω)
                  |    base_temperature double,                     -- temperature of elements in the input CIM file (°C)
                  |    low_temperature double,                      -- low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
                  |    high_temperature double,                     -- high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
                  |    cmax double,                                 -- voltage factor for maximum fault level (used for rating equipment) (dimensionless)
                  |    cmin double,                                 -- voltage factor for minimum fault level (used for protections settings) (dimensionless)
                  |    worstcasepf boolean,                         -- worst case motor power factor assumed (cos term = 1.0, cosphi ignored)
                  |    cosphi double                                -- power factor of (motor) load (dimensionless)
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
                  |    terminal integer,                     -- CIM Terminal sequence number referring to the node and equipment
                  |    container text,                       -- CIM EquipmentContainer mRID the equipment resides in
                  |    errors text,                          -- comma separated list of error and warning messages encountered in processing
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    ik double,                            -- one phase bolted short circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double,                            -- short-circuit power at the point of common coupling (VA)
                  |    costerm,                              -- cos(Ψ-φ) value used in calculating imax values (dimensionless)
                  |    imax_3ph_low double,                  -- maximum inrush current (3 phase) for repetition_rate<0.01/min (A)
                  |    imax_1ph_low double,                  -- maximum inrush current (1 phase, line to neutral) for repetition_rate<0.01/min (A)
                  |    imax_2ph_low double,                  -- maximum inrush current (line to line) for repetition_rate<0.01/min (A)
                  |    imax_3ph_med double,                  -- maximum inrush current (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (A)
                  |    imax_1ph_med double,                  -- maximum inrush current (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (A)
                  |    imax_2ph_med double                   -- maximum inrush current (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (A)
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists sc_equipment_index on shortcircuit (equipment)")
            statement.executeUpdate ("create index if not exists sc_run_index on shortcircuit (run)")
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
                  |    terminal integer,                     -- CIM Terminal sequence number referring to the node and equipment
                  |    container text,                       -- CIM EquipmentContainer mRID the equipment resides in
                  |    errors text,                          -- comma separated list of error and warning messages encountered in processing
                  |    trafo text,                           -- CIM PowerTransformer supplying the node
                  |    prev text,                            -- previous (on path from trafo to node) CIM ConnectivityNode mRID
                  |    r double,                             -- aggregate positive sequence resistance from the trafo (primary) to this node (Ω)
                  |    x double,                             -- aggregate positive sequence reactance from the trafo (primary) to this node (Ω)
                  |    r0 double,                            -- aggregate zero sequence resistance from the trafo (primary) to this node (Ω)
                  |    x0 double,                            -- aggregate zero sequence reactance from the trafo (primary) to this node (Ω)
                  |    ik double,                            -- one phase bolted short circuit current (A)
                  |    ik3pol double,                        -- three phase bolted short circuit current (A)
                  |    ip double,                            -- maximum aperiodic short-circuit current according to IEC 60909-0 (A)
                  |    sk double,                            -- short-circuit power at the point of common coupling (VA)
                  |    fuses text,                           -- comma separated list of fuse values from the source (primary of feeding transformer) to this node (A)
                  |    fusemax double,                       -- maximum recommended fuse value for the calculated fault current (A)
                  |    fuseok boolean                        -- evaluation of whether the first fuse is an appropriate value (true) or not (false)
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists nu_equipment_index on nullungsbedingung (equipment)")
            statement.executeUpdate ("create index if not exists nu_run_index on nullungsbedingung (run)")
        }
        val resultset4 = statement.executeQuery ("select name from sqlite_master where type = 'table' and name = 'fusesummary'")
        val exists4 = resultset4.next ()
        resultset4.close ()
        if (!exists4)
        {
            statement.executeUpdate (
                """create table fusesummary
                  |    -- table summarizing fuse results
                  |(
                  |    id integer primary key autoincrement, -- unique id for each summary record
                  |    run integer,                          -- foreign key to corresponding shortcircuit_run table program execution
                  |    container text,                       -- CIM EquipmentContainer mRID the equipment resides in
                  |    allok boolean,                        -- evaluation of whether all fuses in the container are ok (true) or not (false)
                  |    ok integer,                           -- number of appropriate fuse values in the container
                  |    bad integer,                          -- number of inappropriate fuse values in the container
                  |    unknown integer                       -- number of unknown fuse status values in the container
                  |)""".stripMargin)
            statement.executeUpdate ("create index if not exists fs_container_index on fusesummary (container)")
            statement.executeUpdate ("create index if not exists fs_run_index on nullungsbedingung (run)")
        }
        statement.close ()
    }

    def store (options: ShortCircuitOptions) (records: RDD[ScResult]): Int = synchronized
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

            if (!records.isEmpty)
            {
                // insert the simulation
                val now = Calendar.getInstance ()
                val insert = connection.prepareStatement ("insert into shortcircuit_run (id, description, time, max_default_short_circuit_power, max_default_short_circuit_resistance, max_default_short_circuit_reactance, min_default_short_circuit_power, min_default_short_circuit_resistance, min_default_short_circuit_reactance, base_temperature, low_temperature, high_temperature, cmax, cmin, worstcasepf, cosphi) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                insert.setNull (1, Types.INTEGER)
                insert.setString (2, options.description)
                insert.setTimestamp (3, new Timestamp (now.getTimeInMillis))
                insert.setDouble (4, options.default_short_circuit_power_max)
                insert.setDouble (5, options.default_short_circuit_impedance_max.re)
                insert.setDouble (6, options.default_short_circuit_impedance_max.im)
                insert.setDouble (7, options.default_short_circuit_power_min)
                insert.setDouble (8, options.default_short_circuit_impedance_min.re)
                insert.setDouble (9, options.default_short_circuit_impedance_min.im)
                insert.setDouble (10, options.base_temperature)
                insert.setDouble (11, options.low_temperature)
                insert.setDouble (12, options.high_temperature)
                insert.setDouble (13, options.cmax)
                insert.setDouble (14, options.cmin)
                insert.setBoolean (15, options.worstcasepf)
                insert.setDouble (16, options.cosphi)
                insert.executeUpdate ()
                val statement = connection.createStatement ()
                val resultset = statement.executeQuery ("select last_insert_rowid() id")
                resultset.next ()
                val id = resultset.getInt ("id")
                resultset.close ()
                statement.close ()

                // insert the results
                val datainsert1 = connection.prepareStatement ("insert into shortcircuit (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, costerm, imax_3ph_low, imax_1ph_low, imax_2ph_low, imax_3ph_med, imax_1ph_med, imax_2ph_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                val datainsert2 = connection.prepareStatement ("insert into nullungsbedingung (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, fuses, fusemax, fuseok) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                val zipped = records.zipWithIndex
                var index = 0L
                var done = false
                do
                {
                    val batch = zipped.filter (x ⇒ x._2 >= index && x._2 < index + options.batchsize).map (_._1).collect
                    for (i <- batch.indices)
                    {
                        datainsert1.setNull (1, Types.INTEGER)
                        datainsert1.setInt (2, id)
                        datainsert1.setString (3, batch(i).node)
                        datainsert1.setString (4, batch(i).equipment)
                        datainsert1.setInt (5, batch(i).terminal)
                        if ((null == batch(i).container) || ("" == batch(i).container))
                            datainsert1.setNull (6, Types.VARCHAR)
                        else
                            datainsert1.setString (6, batch(i).container)
                        if (null == batch(i).errors)
                            datainsert1.setNull (7, Types.VARCHAR)
                        else
                            datainsert1.setString (7, batch(i).errors.mkString (","))
                        datainsert1.setString (8, batch(i).tx)
                        datainsert1.setString (9, batch(i).prev)
                        datainsert1.setDouble (10, batch(i).low_r)
                        datainsert1.setDouble (11, batch(i).low_x)
                        datainsert1.setDouble (12, batch(i).low_r0)
                        datainsert1.setDouble (13, batch(i).low_x0)
                        datainsert1.setDouble (14, batch(i).low_ik)
                        datainsert1.setDouble (15, batch(i).low_ik3pol)
                        datainsert1.setDouble (16, batch(i).low_ip)
                        datainsert1.setDouble (17, batch(i).low_sk)
                        datainsert1.setDouble (18, batch(i).costerm)
                        datainsert1.setDouble (19, batch(i).imax_3ph_low)
                        datainsert1.setDouble (20, batch(i).imax_1ph_low)
                        datainsert1.setDouble (21, batch(i).imax_2ph_low)
                        datainsert1.setDouble (22, batch(i).imax_3ph_med)
                        datainsert1.setDouble (23, batch(i).imax_1ph_med)
                        datainsert1.setDouble (24, batch(i).imax_2ph_med)
                        datainsert1.executeUpdate ()
                    }
                    for (i <- batch.indices)
                    {
                        datainsert2.setNull (1, Types.INTEGER)
                        datainsert2.setInt (2, id)
                        datainsert2.setString (3, batch(i).node)
                        datainsert2.setString (4, batch(i).equipment)
                        datainsert2.setInt (5, batch(i).terminal)
                        if ((null == batch(i).container) || ("" == batch(i).container))
                            datainsert2.setNull (6, Types.VARCHAR)
                        else
                            datainsert2.setString (6, batch(i).container)
                        if (null == batch(i).errors)
                            datainsert2.setNull (7, Types.VARCHAR)
                        else
                            datainsert2.setString (7, batch(i).errors.mkString (","))
                        datainsert2.setString (8, batch(i).tx)
                        datainsert2.setString (9, batch(i).prev)
                        datainsert2.setDouble (10, batch(i).high_r)
                        datainsert2.setDouble (11, batch(i).high_x)
                        datainsert2.setDouble (12, batch(i).high_r0)
                        datainsert2.setDouble (13, batch(i).high_x0)
                        datainsert2.setDouble (14, batch(i).high_ik)
                        datainsert2.setDouble (15, batch(i).high_ik3pol)
                        datainsert2.setDouble (16, batch(i).high_ip)
                        datainsert2.setDouble (17, batch(i).high_sk)
                        if ((null == batch(i).fuses) || batch(i).fuses.isEmpty)
                        {
                            datainsert2.setNull (18, Types.VARCHAR)
                            datainsert2.setNull (19, Types.DOUBLE)
                            datainsert2.setNull (20, Types.BOOLEAN)
                        }
                        else
                        {
                            datainsert2.setString (18, batch(i).fuseString)
                            datainsert2.setDouble (19, FData.fuse (batch(i).high_ik))
                            datainsert2.setBoolean (20, FData.fuseOK (batch(i).high_ik, batch(i).fuses))
                        }
                        datainsert2.executeUpdate ()
                    }
                    if (0 == batch.length || batch.length < options.batchsize)
                        done = true
                    else
                        index = index + options.batchsize
                }
                while (!done)
                datainsert1.close ()
                datainsert2.close ()

                // add fuse summary
                val datainsert3 = connection.prepareStatement ("insert into fusesummary (id, run, container, allok, ok, bad, unknown) select NULL, ?, container, cast (0 = (total (not fuseOK) + total (fuseOK is NULL)) as boolean), cast (total (fuseOK) as integer), cast (total (not fuseOK) as integer), cast (total (fuseOK is NULL) as integer) from nullungsbedingung where run = ? and container is not null group by container")
                datainsert3.setInt (1, id)
                datainsert3.setInt (2, id)
                datainsert3.executeUpdate ()
                datainsert3.close ()

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