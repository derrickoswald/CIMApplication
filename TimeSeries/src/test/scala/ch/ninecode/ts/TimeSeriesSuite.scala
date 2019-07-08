package ch.ninecode.ts

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util
import java.util.zip.ZipInputStream

import scala.collection.JavaConverters._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FunSuite

import ch.ninecode.ts.Main.main

class TimeSeriesSuite extends FunSuite with BeforeAndAfterAll
{
    val FILE_DEPOT = "data/"
    val MAPPING_FILE = "mapping.csv"
    val LPEX_FILE1 = "lpex1.txt"
    val LPEX_FILE2 = "lpex2.txt"

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

    def setHadoopConfigurationDirectory (path: String): Unit =
    {
        if (null == System.getenv ("HADOOP_CONF_DIR"))
        {
            val newenv = new java.util.HashMap[String, String]()
            newenv.put ("HADOOP_CONF_DIR", path)
            setEnv (newenv)
        }
    }

    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    override def beforeAll (): Unit =
    {
        setHadoopConfigurationDirectory ("/home/derrick/spark/hadoop-2.7.6/etc/hadoop")
    }

    test ("Help")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("TimeSeries")
    {
        main (Array ("--unittest", "--verbose",
            "--master", "local[*]",
            "--host", "beach"))
    }
}
