package ch.ninecode.sim

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.util.Util

object Main
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val APPLICATION_NAME: String = properties.getProperty ("artifactId")
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    def jarForClass (cls: Class[_]): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = cls.getProtectionDomain.getCodeSource.getLocation.getPath
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
            val name = s"/tmp/${Random.nextInt (99999999)}.jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    /**
     * Build jar with dependencies (creates target/program_name_and_version-jar-with-dependencies.jar):
     * mvn package
     * Invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/program_name_and_version-jar-with-dependencies.jar --verbose --host beach demodata.json
     * or on AWS:
     * /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar --host sandbox demodata.json
     * Note: At the moment the "cim" property in the json file is file-system dependent, e.g. "cim": "data/DemoData.rdf", or "cim": "hdfs://sandbox:8020/data/DemoData.rdf".
     */
    def main (args: Array[String])
    {
        val optionparser = new SimulationOptionsParser (APPLICATION_NAME, APPLICATION_VERSION)

        optionparser.parse (args, SimulationOptions ()) match
        {
            case Some (options) =>
                if (options.valid)
                {
                    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)

                    if (options.simulation.nonEmpty)
                    {
                        val begin = System.nanoTime ()

                        // get the necessary jar files to send to the cluster
                        val sim = jarForClass (SimulationOptions ().getClass)
                        val glm = jarForClass (Class.forName ("ch.ninecode.gl.GLMEdge"))
                        val reader = jarForClass (Class.forName ("ch.ninecode.cim.DefaultSource"))
                        val util = jarForClass (Class.forName ("ch.ninecode.util.Complex"))
                        val json = jarForClass (Class.forName ("javax.json.JsonStructure"))
                        val json_impl = jarForClass (Class.forName ("org.glassfish.json.JsonProviderImpl"))
                        val datastax = jarForClass (Class.forName ("com.datastax.oss.driver.api.core.CqlSession"))

                        // create the configuration
                        val configuration = new SparkConf (false)
                            .setAppName (APPLICATION_NAME)
                            .set ("spark.cassandra.connection.host", options.host)
                            .set ("spark.cassandra.connection.port", options.port.toString)
                            .set ("spark.sql.catalog.casscatalog", "com.datastax.spark.connector.datasource.CassandraCatalog")
                            .setJars (Set (sim, glm, reader, util, json, json_impl, datastax).toArray)

                            // register CIMReader classes
                            .registerKryoClasses (CIMClasses.list)
                            // use the custom registrator
                            .set ("spark.kryo.registrator", "ch.ninecode.cim.CIMRegistrator")
                            // register GridLAB-D classes
                            .registerKryoClasses (GridLABD.classes)
                            // register Net classes
                            .registerKryoClasses (Net.classes)
                            // register Simulation analysis classes
                            .registerKryoClasses (Simulation.classes)
                            // register Util classes
                            .registerKryoClasses (Util.classes)

                        if ("" != options.master)
                        {
                            val _ = configuration.setMaster (options.master)
                        }
                        options.options.foreach ((pair: (String, String)) => configuration.set (pair._1, pair._2))

                        // make a Spark session
                        val session = SparkSession.builder ().config (configuration).getOrCreate ()
                        session.sparkContext.setLogLevel (options.log_level.toString)
                        if ("" != options.checkpoint)
                            session.sparkContext.setCheckpointDir (options.checkpoint)
                        val version = session.version
                        log.info (s"Spark $version session established")
                        if (version.take (SPARK.length) != SPARK.take (version.length))
                            log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")

                        val setup = System.nanoTime ()
                        log.info (s"setup: ${(setup - begin) / 1e9} seconds")

                        if (options.simulation.nonEmpty)
                        {
                            val ids = Simulation (session, options).run ()
                            log.info (s"simulated ${ids.mkString (",")}")
                        }
                        else
                            log.error ("no simulation JSON files specified")

                        val calculate = System.nanoTime ()
                        log.info (s"execution: ${(calculate - setup) / 1e9} seconds")
                    }
                }
                if (!options.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
