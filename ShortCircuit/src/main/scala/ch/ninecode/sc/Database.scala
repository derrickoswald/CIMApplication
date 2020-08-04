package ch.ninecode.sc

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar

import org.apache.spark.rdd.RDD
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Database (options: ShortCircuitOptions, filename: String = "shortcircuit.db") extends Serializable {
    val log: Logger = LoggerFactory.getLogger(getClass)

    def store (records: RDD[ScResult]): Int = synchronized {
        if (records.isEmpty()) {
            log.error("no results to store in database")
            0
        } else {
            // make the directory
            val file = Paths.get("results/dummy")
            Files.createDirectories(file.getParent)

            // load the sqlite-JDBC driver using the current class loader
            Class.forName("org.sqlite.JDBC")

            var connection: Connection = null
            try {
                // create a database connection
                connection = DriverManager.getConnection(s"jdbc:sqlite:results/$filename")
                connection.setAutoCommit(false)

                makeSchema(connection)
                val id = storeData(connection, records)
                connection.commit()
                id
            } catch {
                // if the error message is "out of memory",
                // it probably means no database file is found
                case e: SQLException ⇒ log.error("exception caught: " + e)
                    -1
            } finally {
                try {
                    if (connection != null)
                        connection.close()
                }
                catch {
                    // connection close failed
                    case e: SQLException ⇒ log.error("exception caught: " + e)
                }
            }
        }
    }

    private def makeSchema (connection: Connection) {
        val statement = connection.createStatement()
        createTableShortcircuitrun(statement)
        createTableShortcircuit(statement)
        createTableNullungsbedingung(statement)
        createTableFusesummary(statement)
        statement.close()
    }

    private def createTableShortcircuitrun(statement: Statement): Unit = {
        val resultset1 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'shortcircuit_run'")
        val exists1 = resultset1.next()
        resultset1.close()
        if (!exists1) {
            statement.executeUpdate(
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
            statement.executeUpdate("create index if not exists epoc on shortcircuit_run (time)")
        }
    }

    private def createTableShortcircuit(statement: Statement): Unit = {
        val resultset2 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'shortcircuit'")
        val exists2 = resultset2.next()
        resultset2.close()
        if (!exists2) {
            statement.executeUpdate(
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
            statement.executeUpdate("create index if not exists sc_equipment_index on shortcircuit (equipment)")
            statement.executeUpdate("create index if not exists sc_run_index on shortcircuit (run)")
        }
    }

    private def createTableNullungsbedingung(statement: Statement): Unit = {
        val resultset3 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'nullungsbedingung'")
        val exists3 = resultset3.next()
        resultset3.close()
        if (!exists3) {
            statement.executeUpdate(
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
                  |    fuses text,                           -- fuse values from the source (primary of feeding transformer) to this node (A)
                  |    last_fuses text,                      -- fuse(s) connected directly to the node (A)
                  |    last_fuses_id text,                   -- mRID of fuse(s) connected directly to the node (A)
                  |    iksplit text,                         -- short circuit current(s) (A)
                  |    fusemax text,                         -- maximum recommended fuse value(s) for the calculated fault current(s) (A)
                  |    fuseok boolean                        -- evaluation of whether the fuse(s) has(have) appropriate value(s) (true) or not (false)
                  |)""".stripMargin)
            statement.executeUpdate("create index if not exists nu_equipment_index on nullungsbedingung (equipment)")
            statement.executeUpdate("create index if not exists nu_run_index on nullungsbedingung (run)")
        }
    }

    private def createTableFusesummary(statement: Statement): Unit = {
        val resultset4 = statement.executeQuery("select name from sqlite_master where type = 'table' and name = 'fusesummary'")
        val exists4 = resultset4.next()
        resultset4.close()
        if (!exists4) {
            statement.executeUpdate(
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
            statement.executeUpdate("create index if not exists fs_container_index on fusesummary (container)")
            statement.executeUpdate("create index if not exists fs_run_index on nullungsbedingung (run)")
        }
    }

    private def storeData(connection: Connection, records: RDD[ScResult]): Int = {
        val id: Int = storeShortcircuitrunTable(connection)

        // insert the results
        val sqlShortcircuit = "insert into shortcircuit (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, costerm, imax_3ph_low, imax_1ph_low, imax_2ph_low, imax_3ph_med, imax_1ph_med, imax_2ph_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val insertShortcircuit = connection.prepareStatement(sqlShortcircuit)
        val sqlNullungsbedingung = "insert into nullungsbedingung (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, fuses, last_fuses, last_fuses_id, iksplit, fusemax, fuseok) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val insertNullungsbedingung = connection.prepareStatement(sqlNullungsbedingung)
        val zipped: RDD[(ScResult, Long)] = records.zipWithIndex
        var index = 0L
        var done = false
        do {
            val batch = zipped.filter(x ⇒ x._2 >= index && x._2 < index + options.batchsize).map(_._1).collect
            for (i <- batch.indices) {
                val result = batch(i)
                storeShortcircuitTable(insertShortcircuit, id, result)
                storeNullungsbedingungTable(insertNullungsbedingung, id, result)
            }
            if (0 == batch.length || batch.length < options.batchsize)
                done = true
            else
                index = index + options.batchsize
        }
        while (!done)

        insertShortcircuit.close()
        insertNullungsbedingung.close()

        storeFusesummaryTable(connection, id)
        id
    }

    private def storeFusesummaryTable(connection: Connection, id: Int): Unit = {
        // add fuse summary
        val sql =
            """insert into fusesummary (id, run, container, allok, ok, bad, unknown)
              |select NULL, ?, container, cast (0 = (total (not fuseOK) + total (fuseOK is NULL)) as boolean), cast (total (fuseOK) as integer), cast (total (not fuseOK) as integer), cast (total (fuseOK is NULL) as integer)
              |from nullungsbedingung where run = ? and container is not null group by container""".stripMargin
        val insert = connection.prepareStatement(sql)
        insert.setInt(1, id)
        insert.setInt(2, id)
        insert.executeUpdate()
        insert.close()
    }

    private def storeNullungsbedingungTable(insert: PreparedStatement, id: Int, result: ScResult): Int = {
        // "insert into
        // nullungsbedingung (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, fuses, last_fuses, last_fuses_id, iksplit, fusemax, fuseok)
        // values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        insert.setNull(1, Types.INTEGER)
        insert.setInt(2, id)
        insert.setString(3, result.node)
        insert.setString(4, result.equipment)
        insert.setInt(5, result.terminal)
        if ((null == result.container) || ("" == result.container))
            insert.setNull(6, Types.VARCHAR)
        else
            insert.setString(6, result.container)
        if (null == result.errors)
            insert.setNull(7, Types.VARCHAR)
        else
            insert.setString(7, result.errors.mkString(","))
        insert.setString(8, result.tx)
        insert.setString(9, result.prev)
        insert.setDouble(10, result.high_r)
        insert.setDouble(11, result.high_x)
        insert.setDouble(12, result.high_r0)
        insert.setDouble(13, result.high_x0)
        insert.setDouble(14, result.high_ik)
        insert.setDouble(15, result.high_ik3pol)
        insert.setDouble(16, result.high_ip)
        insert.setDouble(17, result.high_sk)
        if (null == result.branches) {
            insert.setNull(18, Types.VARCHAR)
            insert.setNull(19, Types.VARCHAR)
            insert.setNull(20, Types.VARCHAR)
            insert.setNull(21, Types.VARCHAR)
            insert.setNull(22, Types.VARCHAR)
            insert.setNull(23, Types.BOOLEAN)
        }
        else {
            insert.setString(18, result.fuseString)
            insert.setString(19, result.lastFusesString)
            insert.setString(20, result.lastFusesId)
            insert.setString(21, result.iksplitString)
            insert.setString(22, result.fuseMax)
            if (result.lastFuseHasMissingValues)
                insert.setNull(23, Types.BOOLEAN)
            else
                insert.setBoolean(23, result.fuseOK(options.cmin))
        }
        insert.executeUpdate()
    }

    private def storeShortcircuitTable(insert: PreparedStatement, id: Int, result: ScResult): Int = {
        // "insert into shortcircuit (id, run, node, equipment, terminal, container, errors, trafo, prev, r, x, r0, x0, ik, ik3pol, ip, sk, costerm, imax_3ph_low, imax_1ph_low, imax_2ph_low, imax_3ph_med, imax_1ph_med, imax_2ph_med) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        insert.setNull(1, Types.INTEGER)
        insert.setInt(2, id)
        insert.setString(3, result.node)
        insert.setString(4, result.equipment)
        insert.setInt(5, result.terminal)
        if ((null == result.container) || ("" == result.container))
            insert.setNull(6, Types.VARCHAR)
        else
            insert.setString(6, result.container)
        if (null == result.errors)
            insert.setNull(7, Types.VARCHAR)
        else
            insert.setString(7, result.errors.mkString(","))
        insert.setString(8, result.tx)
        insert.setString(9, result.prev)
        insert.setDouble(10, result.low_r)
        insert.setDouble(11, result.low_x)
        insert.setDouble(12, result.low_r0)
        insert.setDouble(13, result.low_x0)
        insert.setDouble(14, result.low_ik)
        insert.setDouble(15, result.low_ik3pol)
        insert.setDouble(16, result.low_ip)
        insert.setDouble(17, result.low_sk)
        insert.setDouble(18, result.costerm)
        insert.setDouble(19, result.imax_3ph_low)
        insert.setDouble(20, result.imax_1ph_low)
        insert.setDouble(21, result.imax_2ph_low)
        insert.setDouble(22, result.imax_3ph_med)
        insert.setDouble(23, result.imax_1ph_med)
        insert.setDouble(24, result.imax_2ph_med)
        insert.executeUpdate()
    }

    private def storeShortcircuitrunTable(connection: Connection): Int = {
        val now = Calendar.getInstance()
        val sql =
            """insert into
              |shortcircuit_run (id,
              |                  description,
              |                  time,
              |                  max_default_short_circuit_power,
              |                  max_default_short_circuit_resistance,
              |                  max_default_short_circuit_reactance,
              |                  min_default_short_circuit_power,
              |                  min_default_short_circuit_resistance,
              |                  min_default_short_circuit_reactance,
              |                  base_temperature,
              |                  low_temperature,
              |                  high_temperature,
              |                  cmax,
              |                  cmin,
              |                  worstcasepf,
              |                  cosphi)
              |values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
        val insert = connection.prepareStatement(sql)
        insert.setNull(1, Types.INTEGER)
        insert.setString(2, options.description)
        insert.setTimestamp(3, new Timestamp(now.getTimeInMillis))
        insert.setDouble(4, options.default_short_circuit_power_max)
        insert.setDouble(5, options.default_short_circuit_impedance_max.re)
        insert.setDouble(6, options.default_short_circuit_impedance_max.im)
        insert.setDouble(7, options.default_short_circuit_power_min)
        insert.setDouble(8, options.default_short_circuit_impedance_min.re)
        insert.setDouble(9, options.default_short_circuit_impedance_min.im)
        insert.setDouble(10, options.base_temperature)
        insert.setDouble(11, options.low_temperature)
        insert.setDouble(12, options.high_temperature)
        insert.setDouble(13, options.cmax)
        insert.setDouble(14, options.cmin)
        insert.setBoolean(15, options.worstcasepf)
        insert.setDouble(16, options.cosphi)
        insert.executeUpdate()

        val statement = connection.createStatement()
        val resultset = statement.executeQuery("select last_insert_rowid() id")
        resultset.next()
        val id = resultset.getInt("id")
        resultset.close()
        statement.close()
        id
    }
}