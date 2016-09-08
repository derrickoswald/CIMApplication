package ch.ninecode

import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

object GridLABDApplication
{
    def main (args: Array[String])
    {
        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLAB-D")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        configuration.setJars (Array ("/home/derrick/code/CIMScala/target/CIMScala-2.10-1.6.0-1.6.0.jar"
            , "/home/derrick/code/CIMApplication/GridLAB-D/target/GridLAB-D-1.0-SNAPSHOT.jar"
            ))
        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[ch.ninecode.cim.Edge]))

        // make a Spark context and SQL context
        val _Context = new SparkContext (configuration)
        _Context.setLogLevel ("INFO") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val _SqlContext = new SQLContext (_Context)

        val filename = "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_sias_current_20160816_V8_Bruegg" + ".rdf"

        val start = System.nanoTime ()

        val elements = _SqlContext.read.format ("ch.ninecode.cim").option ("StorageLevel", "MEMORY_AND_DISK_SER").load (filename)
        val count = elements.count

        val read = System.nanoTime ()

        val gridlab = new GridLABD ()
        gridlab._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

        val prep = System.nanoTime ()

        val result = gridlab.preparation (_Context, _SqlContext, "transformer=all") // TRA5401

        val graph = System.nanoTime ()

        Files.write (Paths.get ("gridlabd.glm"), result.getBytes (StandardCharsets.UTF_8))

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("prep : " + (prep - read) / 1e9 + " seconds")
        println ("graph: " + (graph - prep) / 1e9 + " seconds")
        println ("write: " + (System.nanoTime () - graph) / 1e9 + " seconds")
        println ();
    }
}
