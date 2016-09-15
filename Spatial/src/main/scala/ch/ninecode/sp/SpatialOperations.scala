package ch.ninecode.sp

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.reflect.runtime.universe
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

class SpatialOperations extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

    def nearest (sc: SparkContext, sqlContext: SQLContext, args: String): DataFrame =
    {
        val arguments = args.split (",").map (
            (s) =>
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap

        // get the name of the class of interest
        val clazz = arguments.getOrElse ("psr", "EnergyConsumer")

        // get longitude and latitude
        val lon = arguments.getOrElse ("lon", "7.281558").toDouble
        val lat = arguments.getOrElse ("lat", "47.124142").toDouble

        // get how many
        val n = arguments.getOrElse ("n", "5").toInt

//        // get all objects with a location attribute
//        val psrs = sc.getPersistentRDDs.values.filter ((x) => {"PowerSystemResource" == x.name}).head.asInstanceOf[RDD[PowerSystemResource]]
//        // get the rdd
//        val interesting = sc.getPersistentRDDs.values.filter ((x) => {clazz == x.name}).head.asInstanceOf[RDD[Element]]
//        // get the join
//        val dudu = psrs.keyBy (_.id).join (interesting.keyBy (_.id)).values

        // I can't figure out how to do this with a generic class
        val consumers = sc.getPersistentRDDs.values.filter ((x) => {"EnergyConsumer" == x.name}).head.asInstanceOf[RDD[EnergyConsumer]]
        val dudu = consumers.keyBy (_.sup.sup.sup.Location)

        // get the points
        val points = sc.getPersistentRDDs.values.filter ((x) => {"PositionPoint" == x.name}).head.asInstanceOf[RDD[PositionPoint]]
        val dada = points.keyBy (_.Location)

        val located_consumers = dudu.join (dada).values

        def ordering (item: Tuple2[EnergyConsumer, PositionPoint]) =
        {
            val dx = lon - item._2.xPosition.toDouble;
            val dy = lat - item._2.yPosition.toDouble;
            dx * dx + dy * dy;
        }

        return (sqlContext.createDataFrame (located_consumers.sortBy (ordering).map (_._1).take (n))) // ToDo: takeOrdered in one step?
    }
}

object SpatialOperations
{
    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain ().getCodeSource ().getLocation ().getPath ()
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        return (ret)
    }

    def main (args: Array[String])
    {
        val spatial = new SpatialOperations ()
        val filename = if (args.length > 0)
            args (0)
        else
            "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_sias_current_20160816_V8_Bruegg" + ".rdf"

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuit")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ()) // "/home/derrick/code/CIMScala/target/CIMScala-2.10-1.6.0-1.6.0.jar"
        val s2 = jarForObject (spatial) // "/home/derrick/code/CIMApplication/Spatial/target/Spatial-1.0-SNAPSHOT.jar"
        configuration.setJars (Array (s1, s2))

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

        val start = System.nanoTime ()
        val files = filename.split (",")
        val elements = _SqlContext.read.format ("ch.ninecode.cim").option ("StorageLevel", "MEMORY_AND_DISK_SER").load (files:_*)
        val count = elements.count

        val read = System.nanoTime ()

        spatial._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        val results = spatial.nearest (_Context, _SqlContext, "psr=EnergyConsumer,lon=7.281558,lat=47.124142,n=5")

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("execute: " + (System.nanoTime () - read) / 1e9 + " seconds")
        println ();
    }
}
