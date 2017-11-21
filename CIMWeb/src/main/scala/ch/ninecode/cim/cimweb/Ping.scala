package ch.ninecode.cim.cimweb

import java.net.URLClassLoader
import java.util
import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.Produces

import scala.collection.JavaConversions._

@Stateless
@Path ("/ping")
class Ping extends RESTful
{
    import Ping._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def ping (@DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
    {
        val verbose = try { debug.toBoolean } catch { case _: Throwable => false }
        _Logger.info ("ping (debug=%s)".format (verbose))
        val result = new RESTfulJSONResult (RESTfulJSONResult.OK, new util.Date ().toString)
        if (try { debug.toBoolean } catch { case _: Throwable => false })
        {
            val environment = Json.createObjectBuilder
            for (pair <- System.getenv)
                environment.add (pair._1, pair._2)
            val ret = Json.createObjectBuilder
            ret.add ("environment", environment)
            val properties = Json.createObjectBuilder
            for (property ← System.getProperties)
                properties.add (property._1, property._2)
            ret.add ("properties", properties)
            val classpath = Json.createArrayBuilder
            val classLoaders = new util.ArrayList[ClassLoader]
            classLoaders.add (ClassLoader.getSystemClassLoader)
            if (!classLoaders.contains (Thread.currentThread.getContextClassLoader))
                classLoaders.add (Thread.currentThread.getContextClassLoader)
            try
                throw new Exception
            catch
            {
                case exception: Exception ⇒
                    for (element: StackTraceElement <- exception.getStackTrace)
                        try
                        {
                            val classloader = Class.forName (element.getClassName).getClassLoader
                            if ((null != classloader) && !classLoaders.contains (classloader))
                                classLoaders.add (classloader)
                        }
                        catch
                        {
                            case oops: ClassNotFoundException ⇒
                        }
            }
            for (cl <- classLoaders)
                for (url <- cl.asInstanceOf[URLClassLoader].getURLs)
                    if ("file" == url.getProtocol)
                        classpath.add (url.getFile)
            ret.add ("classpath", classpath)
            result.setResult (ret.build)
        }

        result.toString
    }
}

object Ping
{
    val LOGGER_NAME: String = Ping.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}