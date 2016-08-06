package ch.ninecode.cim

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.SQLContext

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

object App
{

    def main (args: Array[String])
    {
        // create the configuration
        val configuration = new SparkConf (false);
        configuration.setAppName ("ShortCircuit");
        configuration.setMaster ("spark://sandbox:7077");
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/");
        configuration.set ("spark.driver.memory", "1g");
        configuration.set ("spark.executor.memory", "4g");
        configuration.setJars (Array ("/home/derrick/code/CIMScala/target/CIMScala-1.6.0-SNAPSHOT.jar",
            "/home/derrick/code/CIMApplication/ShortCircuit/target/ShortCircuit-1.0-SNAPSHOT.jar"));

        // make a Spark context and SQL context
        val _Context = new SparkContext (configuration);
        _Context.setLogLevel ("INFO"); // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val _SqlContext = new SQLContext (_Context);

        val sc = new ShortCircuit ()
        val rdd = sc.stuff (_Context, _SqlContext)
        val results = rdd.collect
        println (s"""
        id,Name,ik,ik3pol,ip,Transformer,r,x,r0,x0,fuses,x,y""")
//        for (h <- results)
//            println (h.mRID + "," + h.node + "," + h.ik + "," + h.ik3pol + "," + h.ip + "," + h.transformer + "," + h.r + "," + h.x + "," + h.r0 + "," + h.x0 + "," + h.fuses + "," + h.location_x + "," + h.location_y)
        for (i <- 0 until results.length)
        {
            val h = results(i)
            println (h.getString(0) + "," + h.getString(1) + "," + h.getDouble(8) + "," + h.getDouble(9) + "," + h.getDouble(10) + "," + h.getString(2) + "," + h.getDouble(3) + "," + h.getDouble(4) + "," + h.getDouble(5) + "," + h.getDouble(6) + "," + h.getString(7) + "," + h.getString(11) + "," + h.getString(12))
        }
    }

}
