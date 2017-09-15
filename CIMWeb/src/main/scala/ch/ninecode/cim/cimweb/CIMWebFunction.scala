package ch.ninecode.cim.cimweb

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.tools.nsc.io.Jar
import scala.util.Random
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMFunction.Return
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

abstract class CIMWebFunction extends CIMFunction
{
    var jars: Array[String] = new Array[String] (0)

    def setJars (newjars: Array[String]): Unit = jars = newjars

    override def getJars: Array[String] = jars

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
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

        ret
    }

    override def execute (spark: SparkSession): Dataset[Row] =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def execute (spark: SparkSession, mime_type: String): String =
        throw new UnsupportedOperationException ("execute called on wrong method signature")

    override def toString: String =
    {
        val sb: StringBuilder = new StringBuilder
        sb.append (getReturnType.toString)
        sb.append (" execute (session")
        sb.append (getReturnType match { case Return.Dataset => "" case Return.String => ", " + getMimeType })
        sb.append (") [")
        sb.append (jars.mkString (","))
        sb.append ("]")
        sb.toString
    }
}