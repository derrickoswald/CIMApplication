package ch.ninecode.gl

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar
import java.util.HashMap

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim._
import ch.ninecode.model._

import javax.xml.bind.DatatypeConverter

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
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.Transformer],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))

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

    def store (equipment: String, description: String, t1: Calendar, results: RDD[MaxEinspeiseleistung]): Int =
    {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")

        var connection: Connection = null
        try
        {
            // create a database connection
            connection = DriverManager.getConnection ("jdbc:sqlite:" + equipment + "/results.db")
            connection.setAutoCommit (false)

            // create schema
            val statement = connection.createStatement ()
            statement.executeUpdate ("drop table if exists simulation")
            statement.executeUpdate ("create table simulation (id integer primary key autoincrement, equipment text, description text, time text)")
            statement.executeUpdate ("drop table if exists results")
            statement.executeUpdate ("create table results (id integer primary key autoincrement, simulation integer, house text, maximum double)")
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
            val records = results.collect ()
            var datainsert = connection.prepareStatement ("insert into results (id, simulation, house, maximum) values (?, ?, ?, ?)")
            for (i <- 0 until records.length)
            {
                datainsert.setNull (1, Types.INTEGER)
                datainsert.setInt (2, id)
                datainsert.setString (3, records(i).house)
                records(i).max match
                {
                    case None =>
                        datainsert.setNull (4, Types.DOUBLE)
                    case Some (kw) =>
                        datainsert.setDouble (4, kw)
                }
                datainsert.executeUpdate ()
            }
            connection.commit ()

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
                    connection.close ()
            }
            catch {
                // connection close failed
                case e: SQLException ⇒ println ("exception caught: " + e);
            }
        }

    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val start = System.nanoTime ()

        val root = if (true) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355" // Hälig
        //val root = "NIS_CIM_Export_sias_current_20161220_Sample4" // Häuselacker
        val filename =
            FILE_DEPOT + root + ".rdf"

        val elements = readFile (session, filename)
        println (elements.count () + " elements")
        val read = System.nanoTime ()
        println ("read : " + (read - start) / 1e9 + " seconds")

        // set up for execution
        val gridlabd = new GridLABD (session)
        gridlabd.HDFS_URI = "" // local
        gridlabd._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

        // Hälig (STA7854)
        val equipment = "TRA5200"
        val swing = "ABG20106"

        // Häuselacker (STA2591)
//        val equipment = "TRA3967"
//        val swing = "SIG47079"

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

        val experiments = gridlabd.export (session,
            "equipment=" + equipment +
            ",topologicalnodes=true" +
            ",start=" + DatatypeConverter.printDateTime (t0) +
            ",finish=" + DatatypeConverter.printDateTime (t1) +
            ",swing=" + swing)
        val export = System.nanoTime ()
        println ("export: " + (export - read) / 1e9 + " seconds")

        val data = gridlabd.solve (session, equipment)
        val solve = System.nanoTime ()
        println ("solve: " + (solve - export) / 1e9 + " seconds")

        val results = gridlabd.analyse (session, experiments, data)
        val analyse = System.nanoTime ()
        println ("analyse: " + (analyse - solve) / 1e9 + " seconds")

        val id = store (equipment, "Einspeiseleistung", Calendar.getInstance (), results)
        val save = System.nanoTime ()
        println ("save: " + (save - analyse) / 1e9 + " seconds")

        println ()

        // clean up this run
        // FileUtils.deleteDirectory (new File (gridlabd._TempFilePrefix))
    }

}
