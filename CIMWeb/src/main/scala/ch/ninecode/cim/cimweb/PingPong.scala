package ch.ninecode.cim.cimweb

import java.net.URLClassLoader

import javax.json.Json
import javax.json.JsonArrayBuilder
import javax.json.JsonObjectBuilder

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.JavaConverters.propertiesAsScalaMapConverter

abstract class PingPong extends RESTful
{
    def getEnvironment: JsonObjectBuilder = System.getenv.asScala.foldLeft (Json.createObjectBuilder)((b, p) => b.add (p._1, p._2))

    def getProperties: JsonObjectBuilder = System.getProperties.asScala.foldLeft (Json.createObjectBuilder)((b, p) => b.add (p._1, p._2))

    def classLoaderFrom (element: StackTraceElement): Option[ClassLoader] =
    {
        try
        Option (Class.forName (element.getClassName).getClassLoader)
        catch
        {
            case _: ClassNotFoundException =>
                None
        }
    }

    @SuppressWarnings (Array ("org.wartremover.warts.Throw"))
    def getClassLoaders: Set[ClassLoader] =
    {
        val system = Set [ClassLoader](
            ClassLoader.getSystemClassLoader,
            Thread.currentThread.getContextClassLoader)
        val trace =
            try
            throw new Exception
            catch
            {
                case exception: Exception =>
                    exception.getStackTrace.flatMap (classLoaderFrom).toSet
            }
        system.union (trace)
    }

    def getClassPaths: JsonArrayBuilder =
    {
        val classpath = Json.createArrayBuilder

        for (cl <- getClassLoaders)
            cl match
            {
                case url_loader: URLClassLoader =>
                    for (url <- url_loader.getURLs)
                        if ("file" == url.getProtocol)
                            classpath.add (url.getFile)
                case _ =>
            }
        classpath
    }

}
