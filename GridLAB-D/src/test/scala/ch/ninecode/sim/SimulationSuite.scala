package ch.ninecode.sim

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util

import scala.collection.JavaConverters._

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterAll

import ch.ninecode.sim.Main.main

class SimulationSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "data/"

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
                    val map = obj.asInstanceOf[java.util.Map[String, String]]
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
                        val newenv = new java.util.HashMap[String, String] ()
                        newenv.put ("SPARK_LOCAL_IP", ip.getHostAddress)
                        setEnv (newenv)
                    }
                }
            }
        }
    }

    override def beforeAll ()
    {
        setLocalIP ()
    }

    test ("Basic")
    {
        main (Array ("--verbose", "loser.json", "stupid.json"))
    }

    ignore ("Help")
    {
        main (Array ("--help", "loser.json", "stupid.json"))
    }
}
