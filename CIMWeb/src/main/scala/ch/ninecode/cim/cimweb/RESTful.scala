package ch.ninecode.cim.cimweb

import java.net.URLClassLoader
import java.util
import java.util.Properties

import javax.annotation.Resource
import javax.json.Json
import javax.json.JsonArrayBuilder
import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NameNotFoundException
import javax.naming.NamingException
import javax.resource.ResourceException
import javax.resource.cci.MappedRecord
import scala.collection.JavaConversions.collectionAsScalaIterable

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMConnectionFactory
import ch.ninecode.cim.connector.CIMConnectionSpec
import ch.ninecode.cim.connector.CIMMappedRecord

class RESTful ()
{
    import RESTful._

    type map = java.util.Map[String,Object]

    def getClassLoaders: util.ArrayList[ClassLoader] =
    {
        val classLoaders = new util.ArrayList[ClassLoader]
        classLoaders.add (ClassLoader.getSystemClassLoader)
        if (!classLoaders.contains (Thread.currentThread.getContextClassLoader))
            classLoaders.add (Thread.currentThread.getContextClassLoader)
        try
        throw new Exception
        catch
        {
            case exception: Exception =>
                for (element: StackTraceElement <- exception.getStackTrace)
                    try
                    {
                        val classloader = Class.forName (element.getClassName).getClassLoader
                        if ((null != classloader) && !classLoaders.contains (classloader))
                            classLoaders.add (classloader)
                    }
                    catch
                    {
                        case _: ClassNotFoundException =>
                    }
        }
        classLoaders
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

    protected def getConnection (result: RESTfulJSONResult, debug: Boolean = false): CIMConnection =
    {
        val out = if (debug) new StringBuffer else null
        val factory = getConnectionFactory (out)
        if (debug)
            result.message = out.toString
        if (null != factory)
        {
            val specification: CIMConnectionSpec = factory.getDefaultConnectionSpec
            specification.getProperties.put ("spark.driver.memory", "1g")
            specification.getProperties.put ("spark.executor.memory", "2g")
            factory.getConnection (specification).asInstanceOf[CIMConnection]
        }
        else
        {
            result.status = RESTfulJSONResult.FAIL
            null
        }
    }

    @throws[ResourceException]
    protected def getInputRecord (description: String): MappedRecord =
    {
        val ret = getConnectionFactory ().getRecordFactory.createMappedRecord (CIMMappedRecord.INPUT)
        ret.setRecordShortDescription (description)
        ret
    }
}

object RESTful
{
    @Resource(
        name = "SparkConnectionFactory",
        description = "Connection factory for Spark connection using CIMConnector",
        authenticationType = Resource.AuthenticationType.APPLICATION,
        `type`=classOf[CIMConnectionFactory])
    var _ConnectionFactory: CIMConnectionFactory = _

    /**
     * Debugging for JNDI.
     */
    def print_context_r (out: StringBuffer, context: Context, name: String, depth: Int): Unit =
    {
        if (null != context && depth < 5)
        {
            val s = new StringBuilder
            var i = 0
            while (i < depth)
            {
                s.append ("    ")
                i += 1
            }
            val indent = s.toString
            try
            {
                val x = context.list (name)
                if (null != x)
                    while (x.hasMore)
                    {
                        val pair = x.next
                        if (null != pair)
                        {
                            out.append (indent)
                            out.append (pair.getName)
                            out.append (" : ")
                            out.append (pair.getClassName)
                            out.append ("\n")
                            print_context_r (out, context, name + "/" + pair.getName, depth + 1)
                        }
                    }
            }
            catch
            {
                case _: NameNotFoundException =>
                    out.append (indent)
                    out.append ("NameNotFoundException ")
                    out.append (name)
                    out.append ("\n")
                case ne: NamingException =>
                    if (!("Name is not bound to a Context" == ne.getMessage))
                    {
                        out.append (indent)
                        out.append ("NamingException ")
                        out.append (ne.getMessage)
                        out.append ("\n")
                    }
            }
        }
    }

    protected def print_context (out: StringBuffer, context: Context, name: String): StringBuffer =
    {
        val ret =
            if (null == out)
                new StringBuffer
            else
                out
        ret.append (name)
        ret.append ("\n")
        print_context_r (ret, context, name, 1)
        ret
    }

    def getConnectionFactory (debug_out: StringBuffer = null): CIMConnectionFactory =
    {
        val debug = null != debug_out
        try
        {
            if (null == _ConnectionFactory)
            {
                if (debug)
                    debug_out.append ("resource injection failed\ntrying alternate lookup:\n")
                val properties = new Properties
                try
                {
                    val context = new InitialContext (properties)
                    if (debug)
                        print_context (debug_out, context, "java:openejb")
                    try
                        _ConnectionFactory = context.lookup ("java:openejb/Resource/SparkConnectionFactory").asInstanceOf[CIMConnectionFactory]
                    catch
                    {
                        case ne: NamingException =>
                            debug_out.append ("NameNotFoundException: ")
                            debug_out.append (ne.getMessage)
                            debug_out.append ("\n")
                            if (debug)
                                print_context (debug_out, context, "java:comp")
                            try
                                _ConnectionFactory = context.lookup ("java:comp/env/eis/SparkConnectionFactory").asInstanceOf[CIMConnectionFactory]
                            catch
                            {
                                case ne2: NamingException =>
                                    debug_out.append ("NameNotFoundException: ")
                                    debug_out.append (ne2.getMessage)
                                    debug_out.append ("\n")
                                    System.out.println ("fuck this JNDI shit, I give up")
                            }
                    }
                }
                catch
                {
                    case nnfe: NameNotFoundException =>
                        if (debug)
                        {
                            debug_out.append ("NameNotFoundException: ")
                            debug_out.append (nnfe.getMessage)
                            debug_out.append ("\n")
                        }
                    case ne: NamingException =>
                        if (debug)
                        {
                            debug_out.append ("NamingException: ")
                            debug_out.append (ne.getMessage)
                            debug_out.append ("\n")
                        }
                }
            }
        }
        catch
        {
            case re: ResourceException =>
                if (debug)
                {
                    debug_out.append ("ResourceException: ")
                    debug_out.append (re.getMessage)
                    debug_out.append ("\n")
                }
        }
        _ConnectionFactory
    }
}