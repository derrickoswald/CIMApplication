package ch.ninecode.gl

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture.FunSuite

import javax.xml.bind.DatatypeConverter

import ch.ninecode.cim._
import ch.ninecode.model._

class GridLABDSuite extends FunSuite
{
    val FILE_DEPOT = "/home/derrick/Documents/9code/nis/cim/cim_export/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "4g")
        //configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))
        // register topological classes
        configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))
        // register GridLAB-D classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.gl.ShortCircuitData],
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.Transformer],
            classOf[ch.ninecode.gl.Solution],
            classOf[ch.ninecode.gl.ThreePhaseComplexVoltageDataElement]))

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    def readFile (session: SparkSession, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String] ()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "true")
        options.put ("ch.ninecode.cim.do_topo_islands", "true")
        val element = session.read.format ("ch.ninecode.cim").options (options).load (files:_*)

        return (element)
    }

    def store (equipment: String, description: String, t1: Calendar, results: RDD[Solution]): Int =
    {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:results.db")

            // create schema
            val statement = connection.createStatement ()
            statement.executeUpdate ("drop table if exists simulation")
            statement.executeUpdate ("create table simulation (id integer primary key autoincrement, equipment text, description text, time text)")
            statement.executeUpdate ("drop table if exists results")
            statement.executeUpdate ("create table results (id integer primary key autoincrement, simulation integer, element text, time text, real_a double, imag_a double, real_b double, imag_b double, real_c double, imag_c double, units text)")
            statement.close ()

            // insert the simulation
            val now = Calendar.getInstance ()
            val insert = connection.prepareStatement ("insert into simulation (id, equipment, description, time) values (?, ?, ?, ?)")
            insert.setNull (1, Types.INTEGER)
            insert.setString (2, equipment)
            insert.setString (3, description)
            insert.setTimestamp (4, new Timestamp (now.getTimeInMillis))
            insert.executeUpdate ()
            val resultset = statement.executeQuery ("select last_insert_rowid() id")
            resultset.next ()
            val id = resultset.getInt ("id")

            // insert the results
            val datainsert = connection.prepareStatement ("insert into results (id, simulation, element, time, real_a, imag_a, real_b, imag_b, real_c, imag_c, units) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            for (solution ← results.collect) {
                val a = Complex.fromPolar (solution.voltA_mag, solution.voltA_angle)
                val b = Complex.fromPolar (solution.voltB_mag, solution.voltB_angle)
                val c = Complex.fromPolar (solution.voltC_mag, solution.voltC_angle)
                datainsert.setNull (1, Types.INTEGER)
                datainsert.setInt (2, id)
                datainsert.setString (3, solution.node)
                datainsert.setTimestamp (4, new Timestamp (t1.getTimeInMillis))
                datainsert.setDouble (5, a.re)
                datainsert.setDouble (6, a.im)
                datainsert.setDouble (7, b.re)
                datainsert.setDouble (8, b.im)
                datainsert.setDouble (9, c.re)
                datainsert.setDouble (10, c.im)
                datainsert.setString (11, "Volts")
                datainsert.executeUpdate ()
            }
            return (id)
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ println ("exception caught: " + e); return (-1)
        }
        finally
        {
            try
            {
                if (connection != null)
                    connection.close()
            }
            catch {
                // connection close failed
                case e: SQLException ⇒ println ("exception caught: " + e);
            }
        }

    }

    def store_rdd (connection: Connection, id: Int, rdd: RDD[ThreePhaseComplexVoltageDataElement]): Unit =
    {
        // insert the results
        connection.setAutoCommit (false)
        val node = rdd.name.substring (0, rdd.name.length() - "_voltage.csv".length ())
        val datainsert = connection.prepareStatement ("insert into results (id, simulation, element, time, real_a, imag_a, real_b, imag_b, real_c, imag_c, units) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        for (dataitem ← rdd.collect)
        {
            datainsert.setNull (1, Types.INTEGER)
            datainsert.setInt (2, id)
            datainsert.setString (3, node)
            datainsert.setTimestamp (4, new Timestamp (dataitem.millis))
            datainsert.setDouble (5, dataitem.value_a.re)
            datainsert.setDouble (6, dataitem.value_a.im)
            datainsert.setDouble (7, dataitem.value_b.re)
            datainsert.setDouble (8, dataitem.value_b.im)
            datainsert.setDouble (9, dataitem.value_c.re)
            datainsert.setDouble (10, dataitem.value_c.im)
            datainsert.setString (11, "Volts")
            datainsert.executeUpdate ()
        }
        connection.commit ()
    }

    // ToDo: fix this duplication
    def store_rdd2 (connection: Connection, id: Int, rdd: RDD[ThreePhaseComplexCurrentDataElement]): Unit =
    {
        // insert the results
        connection.setAutoCommit (false)
        val node = rdd.name.substring (0, rdd.name.length() - "_current.csv".length ())
        val datainsert = connection.prepareStatement ("insert into results (id, simulation, element, time, real_a, imag_a, real_b, imag_b, real_c, imag_c, units) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        for (dataitem ← rdd.collect)
        {
            datainsert.setNull (1, Types.INTEGER)
            datainsert.setInt (2, id)
            datainsert.setString (3, node)
            datainsert.setTimestamp (4, new Timestamp (dataitem.millis))
            datainsert.setDouble (5, dataitem.value_a.re)
            datainsert.setDouble (6, dataitem.value_a.im)
            datainsert.setDouble (7, dataitem.value_b.re)
            datainsert.setDouble (8, dataitem.value_b.im)
            datainsert.setDouble (9, dataitem.value_c.re)
            datainsert.setDouble (10, dataitem.value_c.im)
            datainsert.setString (11, "Amps")
            datainsert.executeUpdate ()
        }
        connection.commit ()
    }

    def load_and_store (session: SparkSession, gridlabd: GridLABD, id: Int): List[String] =
    {
        var ret = List[String] ()

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:results.db")
            val outputs = gridlabd.list_files (gridlabd._TempFilePrefix)
            for (x <- outputs)
            {
                if (x.endsWith ("_voltage.csv"))
                {
                    val data = gridlabd.read_voltage_records (session, x)
                    data.setName (x.substring (x.lastIndexOf ("/") + 1))
                    ret = ret :+ data.name
                    data.persist (gridlabd._StorageLevel)
                    store_rdd (connection, id, data)
                }
                else if (x.endsWith ("_current.csv"))
                {
                    val data = gridlabd.read_current_records (session, x)
                    data.setName (x.substring (x.lastIndexOf ("/") + 1))
                    ret = ret :+ data.name
                    data.persist (gridlabd._StorageLevel)
                    store_rdd2 (connection, id, data)
                }
            }

        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ println ("exception caught: " + e)
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
                case e: SQLException ⇒ println ("exception caught: " + e);
            }
        }
        
        return (ret)
    }

    def analyse_voltages (session: SparkSession, simulation: Int, experiment: Experiment, nominal: Double, tolerance: Double): Unit =
    {
        val fmt = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        println ("analysing " + experiment.house
            + " for voltage in slot " + experiment.slot
            + " from " + fmt.format (experiment.t1.getTime ())
            + " to " + fmt.format (experiment.t2.getTime ()))

        val min = nominal - (nominal * tolerance / 100.0)
        val max = nominal + (nominal * tolerance / 100.0)

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:results.db")
            val query = connection.prepareStatement ("select * from results where simulation = ? and units = 'Volts' and abs(real_a) < 1000.0 and abs(imag_a) < 1000.0 and time between ? and ? order by time, element")
            query.setInt (1, simulation)
            query.setString (2, experiment.t1.getTimeInMillis ().toString ())
            query.setString (3, experiment.t2.getTimeInMillis ().toString ())
            val result = query.executeQuery ()
            var found = false
            while (result.next () && !found)
            {
                val element = result.getString ("element")
                val time = result.getString ("time")
                val a = Complex (result.getDouble ("real_a"), result.getDouble ("imag_a")).abs
                val b = Complex (result.getDouble ("real_b"), result.getDouble ("imag_b")).abs
                val c = Complex (result.getDouble ("real_c"), result.getDouble ("imag_c")).abs
                if ((a < min) || (a > max) || (b < min) || (b > max) || (c < min) || (c > max))
                {
                    val diff = (time.toLong - experiment.t1.getTimeInMillis ()) / 1000
                    val kw = experiment.from + experiment.step * (diff / experiment.interval)
                    println (experiment.house + " exceeds voltage tolerance with " + kw + "kW at " + element + " (" + a + "," + b + "," + c + ")")
                    found = true
                }
            }
            result.close ()
            query.close ()
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ println ("exception caught: " + e)
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
                case e: SQLException ⇒ println ("exception caught: " + e);
            }
        }
    }

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent (session: SparkSession): scala.collection.mutable.HashMap[String, Double] =
    {
        val ret = scala.collection.mutable.HashMap[String, Double] ()

        val wireinfos = session.sparkContext.getPersistentRDDs.filter(_._2.name == "WireInfo").head._2.asInstanceOf[RDD[WireInfo]]
        val lines = session.sparkContext.getPersistentRDDs.filter(_._2.name == "ACLineSegment").head._2.asInstanceOf[RDD[ACLineSegment]]
        val keyed = lines.keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
        val pairs = keyed.join (wireinfos.keyBy (_.id)).values.map (x => (x._1.id, x._2.ratedCurrent)).collect
        for (pair <- pairs)
            ret += pair
        return (ret)
    }

    def analyse_currents (session: SparkSession, simulation: Int, experiment: Experiment, cables: scala.collection.mutable.HashMap[String, Double]): Unit =
    {
        val fmt = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        println ("analysing " + experiment.house
            + " for current in slot " + experiment.slot
            + " from " + fmt.format (experiment.t1.getTime ())
            + " to " + fmt.format (experiment.t2.getTime ()))

        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:results.db")
            val query = connection.prepareStatement ("select * from results where simulation = ? and units = 'Amps' and time between ? and ? order by time, element")
            query.setInt (1, simulation)
            query.setString (2, experiment.t1.getTimeInMillis ().toString ())
            query.setString (3, experiment.t2.getTimeInMillis ().toString ())
            val result = query.executeQuery ()
            var found = false
            while (result.next () && !found)
            {
                val element = result.getString ("element")
                val max = if (cables.contains (element)) cables(element) else 1e6
                    
                val time = result.getString ("time")
                val a = Complex (result.getDouble ("real_a"), result.getDouble ("imag_a")).abs
                val b = Complex (result.getDouble ("real_b"), result.getDouble ("imag_b")).abs
                val c = Complex (result.getDouble ("real_c"), result.getDouble ("imag_c")).abs
                if (a + b + c > max)
                {
                    val diff = (time.toLong - experiment.t1.getTimeInMillis ()) / 1000
                    val kw = experiment.from + experiment.step * (diff / experiment.interval)
                    println (experiment.house + " exceeds current maximum with " + kw + "kW at " + element + " (" + a + "," + b + "," + c + ")")
                    found = true
                }
            }
            result.close ()
            query.close ()
        }
        catch
        {
            // if the error message is "out of memory",
            // it probably means no database file is found
            case e: SQLException ⇒ println ("exception caught: " + e)
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
                case e: SQLException ⇒ println ("exception caught: " + e);
            }
        }
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val start = System.nanoTime ()

        val root = if (true) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355"
        //val root = "NIS_CIM_Export_sias_current_20161220_Sample4"
        val filename =
            FILE_DEPOT + root + ".rdf"

        val elements = readFile (session, filename)
        println (elements.count () + " elements")
        val read = System.nanoTime ()

        // set up for execution
        val gridlabd = new GridLABD (session)
        gridlabd._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        gridlabd._TempFilePrefix = "./output/"
        gridlabd._CSV = FILE_DEPOT + "KS_Leistungen.csv"

        // clean up from any prior failed run
        FileUtils.deleteDirectory (new File (gridlabd._TempFilePrefix))

        // Hälig (STA7854)
        val equipment = "TRA5200"
        val swing = "ABG20106"

        // Häuselacker (STA2591)
//        val equipment = "TRA3967"
//        val swing = "ABG20106"

        // val t0 = Calendar.getInstance ()
        // or
        // val t1 = t0.clone ().asInstanceOf[Calendar]
        // t1.add (Calendar.MINUTE, 1)
        // parse ISO8601 format date
        // 2015-11-18 12:00:00
//        val t0 = javax.xml.bind.DatatypeConverter.parseDateTime ("2015-11-18 12:00:00".replace (" ", "T"))
//        val t1 = javax.xml.bind.DatatypeConverter.parseDateTime ("2015-11-19 12:00:00".replace (" ", "T"))
        val t0 = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-01-24 12:00:00".replace (" ", "T"))
        // ToDo: how to get the times right in the header
        val t1 = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-01-24 14:00:00".replace (" ", "T"))

        val result = gridlabd.export (session,
            "equipment=" + equipment +
            ",topologicalnodes=true" +
            ",start=" + DatatypeConverter.printDateTime (t0) +
            ",finish=" + DatatypeConverter.printDateTime (t1) +
            ",swing=" + swing +
            ",feeder=false")

        val export = System.nanoTime ()

        val file = Paths.get (equipment + ".glm")
        Files.write (file, result._1.getBytes (StandardCharsets.UTF_8))
        val results = gridlabd.solve (session, equipment)

        val solve = System.nanoTime ()

        val id = store (equipment, "Einspeiseleistung", t1, results)
        val list = load_and_store (session, gridlabd, id)

        val save = System.nanoTime ()

        for (experiment <- result._2)
            analyse_voltages (session, id, experiment, 400.0, 3.0)
        val cables = getCableMaxCurrent (session)
        for (experiment <- result._2)
            analyse_currents (session, id, experiment, cables)

        val analyse = System.nanoTime ()

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("export: " + (export - read) / 1e9 + " seconds")
        println ("solve: " + (solve - export) / 1e9 + " seconds")
        println ("save: " + (save - solve) / 1e9 + " seconds")
        println ("analyse: " + (analyse - save) / 1e9 + " seconds")
        println ()

        // clean up this run
        // FileUtils.deleteDirectory (new File (gridlabd._TempFilePrefix))
    }

}
