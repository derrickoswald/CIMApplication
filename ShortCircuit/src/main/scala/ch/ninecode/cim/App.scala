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
        val final_result = sc.stuff (_Context, _SqlContext)
        val f_results = final_result.collect
        println (s"""%table
        id\tName\tik\tik3pol\tip\tTransformer\tr\tx\tr0\tx0\tfuses""")
        for (h <- f_results)
            println (h.mRID + "\t" + h.node + "\t" + h.ik + "\t" + h.ik3pol + "\t" + h.ip + "\t" + h.transformer.transformer.id + "\t" + h.r + "\t" + h.x + "\t" + h.r0 + "\t" + h.x0 + "\t" + h.fuses)
    }

}
