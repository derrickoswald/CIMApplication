package ch.ninecode.gl

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.HashMap
import java.util.Map

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture

import ch.ninecode.cim._
import ch.ninecode.model._

class GridLABDSuite extends fixture.FunSuite
{
    case class ContextPair (_SparkContext: SparkContext, _SQLContext: SQLContext)

    val FILE_DEPOT = "/home/derrick/Documents/9code/nis/cim/cim_export/"

    type FixtureParam = ContextPair

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))
        // register topological classes
        configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))
        // register GridLAB-D classes
        configuration.registerKryoClasses (Array (classOf[ch.ninecode.gl.PreNode], classOf[ch.ninecode.gl.PreEdge]))

        val context = new SparkContext (configuration)
        context.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val sql_context = new SQLContext (context)

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (ContextPair (context, sql_context))) // "loan" the fixture to the test
        }
        finally context.stop () // clean up the fixture
    }

    def readFile (context: SQLContext, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "false");
        options.put ("ch.ninecode.cim.do_join", "true");
        options.put ("ch.ninecode.cim.do_topo", "true");
        options.put ("ch.ninecode.cim.do_topo_islands", "false");
        val element = context.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val plan = element.queryExecution
        val test = plan.toString ()
        if (!test.contains ("InputPaths"))
            throw new Exception ("input file not found: " + filename + "\n" + test)

        return (element)
    }

    test ("Basic")
    {
        a: ContextPair ⇒

        val start = System.nanoTime ()

        val context: SparkContext = a._SparkContext
        val sql_context: SQLContext = a._SQLContext

        val filename =
        FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_V9_Guemligen" + ".rdf"
//        FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_V9_Bubenei" + ".rdf"
//        "," +
//        FILE_DEPOT + "ISU_CIM_Export_20160505" + ".rdf"
        val elements = readFile (sql_context, filename)

        val read = System.nanoTime ()

        // set up for execution
        val gridlabd = new GridLABD ()
        gridlabd._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        gridlabd._TempFilePrefix = "./output/"
        gridlabd._CSV = FILE_DEPOT + "KS_Leistungen.csv"

        // clean up from any prior failed run
        FileUtils.deleteDirectory (new File (gridlabd._TempFilePrefix))

        val has = "HAS10002" // my house: "HAS10002" smaller network: "HAS82225" another example: "HAS67883"
        val result = gridlabd.export (context, sql_context, "equipment=" + has + ",topologicalnodes=true")

        val process = System.nanoTime ()

        Files.write (Paths.get (has + ".glm"), result.getBytes (StandardCharsets.UTF_8))

        val write = System.nanoTime ()

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("process: " + (process - read) / 1e9 + " seconds")
        println ("write: " + (write - process) / 1e9 + " seconds")
        println ()

        // clean up this run
        FileUtils.deleteDirectory (new File (gridlabd._TempFilePrefix))
    }

}
