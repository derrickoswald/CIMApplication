package ch.ninecode.cim

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
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

        val filename = "hdfs://sandbox:9000/data/" + "20160817-12_NIS_CIM_Export_b4_Brügg" + ".rdf";

        _SqlContext.sql ("create temporary table elements using ch.ninecode.cim options (path '" + filename + "')");
        val count = _SqlContext.sql ("select count(*) from elements");
        count.head ().getLong (0);

        val sc = new ShortCircuit ()
        sc.preparation (_Context, _SqlContext, "")
        val rdd = sc.stuff (_Context, _SqlContext, "transformer=all") // TRA5401
        val results = rdd.collect
        println (s"""
        id,Name,ik,ik3pol,ip,Transformer,r,x,r0,x0,fuses,wires_valid,trafo_valid,fuse_valid,x,y""")
        for (i <- 0 until results.length)
        {
            val h = results(i)
            println (h.getString(0) + "," + h.getString(1) + "," + h.getDouble(8) + "," + h.getDouble(9) + "," + h.getDouble(10) + "," + h.getString(2) + "," + h.getDouble(3) + "," + h.getDouble(4) + "," + h.getDouble(5) + "," + h.getDouble(6) + "," + h.getString(7) + "," + h.getBoolean(11) + "," + h.getBoolean(12) + "," + h.getBoolean(13) + "," + h.getString(14) + "," + h.getString(15))
        }
    }
}
