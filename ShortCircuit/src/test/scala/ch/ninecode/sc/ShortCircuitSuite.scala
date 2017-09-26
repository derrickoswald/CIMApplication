package ch.ninecode.sc

import java.util.HashMap
import java.util.Map
import java.io.File

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture.FunSuite

import ch.ninecode.cim._
import ch.ninecode.model._

class ShortCircuitSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuitSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
        // register short circuit classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuitData], classOf[TData], classOf[ScNode]))
        // register short circuit inner classes
        configuration.registerKryoClasses (Array (classOf[ScEdge], classOf[HouseConnection]))

        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop // clean up the fixture
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val filename = FILE_DEPOT + "bkw_cim_export_sias_current_20161220_Haelig_no_EEA7355_or_EEA5287" + ".rdf"
            
        val start = System.nanoTime
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "false");
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        println(elements.count + " elements")
        val read = System.nanoTime
        println("read: " + (read - start) /  1e9 + " seconds")
        
        // identify topological nodes
        val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
        val ele = ntp.process (false)
        println(ele.count () + " elements")

        val topo = System.nanoTime ()
        println("topology: " + (topo - read) / 1e9 + " seconds")

        // short circuit calculations
        val sc_options = ShortCircuitOptions (csv_file = FILE_DEPOT + "KS_Leistungen.csv", trafos = FILE_DEPOT + "trafo.txt")
        val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)
        val house_connection = shortcircuit.run

        // write output to file and console
        val output = FILE_DEPOT + "/result"
        val string = house_connection.sortBy(h => shortcircuit.trafokreis_key(h.transformer)).map(h => {
           h.node + ";" + shortcircuit.trafokreis_key(h.transformer) + ";" + h.ik + ";" + h.ik3pol + ";" + h.ip + ";" + h.r + ";" + h.r0 + ";" + h.x + ";" + h.x0
        })
        
        val path = new File(output)
        FileUtils.deleteQuietly (path)
        string.saveAsTextFile(output)
        
        val results = string.collect        
        println ("results: " + results.length)
        println (s"""has;tra;ik;ik3pol;ip;r;r0;x;x0""")
        for (i <- 0 until results.length)
        {
            val h = results (i)
            println(h)
        }

    }

}
