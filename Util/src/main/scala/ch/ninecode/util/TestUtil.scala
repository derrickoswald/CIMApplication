package ch.ninecode.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.Outcome
import org.scalatest.fixture

import scala.collection.JavaConverters._


trait TestUtil extends fixture.FunSuite with SQLite with Unzip
{
    val classesToRegister: Array[Array[Class[_]]]
    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // set the env to add the real local address
        setLocalIP ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName (this.getClass.getSimpleName)
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

        // register relevant classes
        registerDependency(configuration)

        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    def registerDependency(configuration: SparkConf): Unit = {
        classesToRegister.foreach((classToRegister) => {
            configuration.registerKryoClasses(classToRegister)
        })
    }

    def getElementsFromSession (session: SparkSession, filename: String) : DataFrame =
    {
        val files = filename.split (",")
        val options = Map [String, String](
            "path" -> filename,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo" -> "true",
            "ch.ninecode.cim.force_retain_switches" -> "Unforced",
            "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
            "ch.ninecode.cim.debug" -> "true",
            "ch.ninecode.cim.do_deduplication" -> "true"
        )
        session.sqlContext.read.format ("ch.ninecode.cim")
            .options (options)
            .load (files: _*)
            .persist (StorageLevel.MEMORY_AND_DISK_SER)
    }

    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3, message: String = null): Unit =
    {
        val diff = number - reference
        assert (Math.abs (diff) <= epsilon,
            if (null == message)
                s"""$number vs. reference $reference differs by more than $epsilon ($diff)"""
            else
                message)
    }

    /**
     * Add to the process environment.
     *
     * @see https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
     * @param newenv The list of key value pairs to add.
     */
    protected def setEnv (newenv: java.util.HashMap[String, String]): Unit =
    {
        try
        {
            val env: util.Map[String, String] = System.getenv
            for (cl <- Class.forName ("java.util.Collections").getDeclaredClasses)
            {
                if ("java.util.Collections$UnmodifiableMap" == cl.getName)
                {
                    val field = cl.getDeclaredField ("m")
                    field.setAccessible (true)
                    val obj = field.get (env)
                    val map = obj.asInstanceOf [java.util.Map[String, String]]
                    map.putAll (newenv)
                }
            }
        }
        catch
        {
            case e: Exception =>
                e.printStackTrace ()
        }
    }


    /**
     * Set SPARK_LOCAL_IP to the IP address in dotted-quad format (e.g. 1.2.3.4) if it isn't set.
     *
     * Avoids "Set SPARK_LOCAL_IP if you need to bind to another address" warning message.
     *
     * @see findLocalInetAddress: https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/util/Utils.scala
     */
    def setLocalIP (): Unit =
    {
        if (null == System.getenv ("SPARK_LOCAL_IP"))
        {
            val address = InetAddress.getLocalHost
            if (address.isLoopbackAddress)
            {
                // Address resolves to something like 127.0.1.1, which happens on Debian; try to find
                // a better address using the local network interfaces
                // getNetworkInterfaces returns ifs in reverse order compared to ifconfig output order
                // on unix-like system. On windows, it returns in index order.
                // It's more proper to pick ip address following system output order.
                val interfaces = NetworkInterface.getNetworkInterfaces.asScala.toSeq
                val windows = System.getProperty ("os.name").startsWith ("Windows") // underlying operating system is Windows

                for (ni <- if (windows) interfaces else interfaces.reverse)
                {
                    val addresses = ni.getInetAddresses.asScala.filterNot (addr => addr.isLinkLocalAddress || addr.isLoopbackAddress).toSeq
                    if (addresses.nonEmpty)
                    {
                        val addr = addresses.find (_.isInstanceOf [Inet4Address]).getOrElse (addresses.head)
                        // because of Inet6Address.toHostName may add interface at the end if it knows about it
                        val ip = InetAddress.getByAddress (addr.getAddress)
                        // We've found an address that looks reasonable!
                        val newenv = new java.util.HashMap[String, String]()
                        newenv.put ("SPARK_LOCAL_IP", ip.getHostAddress)
                        newenv.put ("SPARK_HOME", "/home/derrick/spark/spark-2.3.2-bin-hadoop2.7")
                        setEnv (newenv)
                    }
                }
            }
        }
    }





}



