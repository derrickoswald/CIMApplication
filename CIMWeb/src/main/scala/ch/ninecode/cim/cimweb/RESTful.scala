package ch.ninecode.cim.cimweb

import java.util.logging.Level
import java.util.logging.Logger

import javax.annotation.Resource
import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NameNotFoundException
import javax.naming.NamingException
import javax.resource.ResourceException
import javax.resource.cci.MappedRecord
import javax.ws.rs.core.Response

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMConnectionFactory
import ch.ninecode.cim.connector.CIMConnectionSpec
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

class RESTful ()
{

    import RESTful._

    type map = java.util.Map[String, Object]

    def withConnection (fn: CIMConnection => Response): Response =
    {
        val ret = new RESTfulJSONResult
        getConnection(ret) match
        {
            case Some(connection) =>
                fn (connection)
            case None =>
                Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(s"could not get connection: ${ret.message}").build
        }
    }

    def asBoolean (string: String): Boolean =
    {
        string match
        {
            case "true" => true
            case "false" => false
            case _ => false
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    protected def getConnection (result: RESTfulJSONResult, debug: Boolean = false): Option[CIMConnection] =
    {
        val out = if (debug) Some(new StringBuffer) else None
        val factory = getConnectionFactory(out)
        if (debug)
            result.message = out.toString
        if (null != factory)
        {
            val specification: CIMConnectionSpec = factory.getDefaultConnectionSpec
            val properties = specification.getProperties
            properties.putAll(Map(
                "spark.driver.memory" -> "1g",
                "spark.executor.memory" -> "2g").asJava)
            Some(factory.getConnection(specification).asInstanceOf[CIMConnection])
        }
        else
        {
            result.status = RESTfulJSONResult.FAIL
            None
        }
    }

    @throws[ResourceException]
    protected def getInputRecord (description: String): MappedRecord =
    {
        val ret = getConnectionFactory().getRecordFactory.createMappedRecord(CIMMappedRecord.INPUT)
        ret.setRecordShortDescription(description)
        ret
    }

    // set up the function with parameters
    @throws[ResourceException]
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    protected def getFunctionInput (function: CIMWebFunction): (CIMInteractionSpec, MappedRecord) =
    {
        val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
        spec.setFunctionName(CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
        val input = getInputRecord("input record containing the function to run")
        val _ = input.asInstanceOf[map].put(CIMFunction.FUNCTION, function)
        (spec, input)
    }
}

object RESTful
{
    @Resource(
        name = "SparkConnectionFactory",
        lookup = "java:comp/env/eis/Spark",
        description = "Connection factory for Spark connection using CIMConnector")
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    var _ConnectionFactory: CIMConnectionFactory = _

    lazy val log: Logger = Logger.getLogger(getClass.getName)

    /**
     * Debugging for JNDI.
     */
    def print_context_r (out: StringBuffer, context: Context, name: String, depth: Int): Unit =
    {
        def append (string: String): Unit =
        {
            val _ = out.append(string)
        }

        if (null != context && depth < 5)
        {
            val indent = List.fill(depth)("    ").mkString
            try
            {
                val x = context.list(name)
                if (null != x)
                    while (x.hasMore)
                    {
                        val pair = x.next
                        if (null != pair)
                        {
                            append(s"${indent}${pair.getName} : ${pair.getClassName}\n")
                            print_context_r(out, context, s"$name/${pair.getName}", depth + 1)
                        }
                    }
            }
            catch
            {
                case _: NameNotFoundException =>
                    append(s"${indent}NameNotFoundException $name\n")
                case ne: NamingException =>
                    if (!("Name is not bound to a Context" == ne.getMessage))
                        append(s"${indent}NamingException ${ne.getMessage}\n")
            }
        }
    }

    protected def print_context (out: Option[StringBuffer], context: Context, name: String): Unit =
    {
        val ret = out match
        {
            case Some(stringbuffer) => stringbuffer
            case _ => new StringBuffer
        }
        print_context_r(ret.append(s"$name\n"), context, name, 1)
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def lookupFactory (context: InitialContext, name: String): Try[CIMConnectionFactory] =
    {
        try
        {
            val factory = context.lookup(name)
            Success(factory.asInstanceOf[CIMConnectionFactory])
        }
        catch
        {
            case ne: NamingException =>
                Logger.getLogger(getClass.getName).log(Level.SEVERE, s"NameNotFoundException: ${ne.getMessage}")
                Failure(ne)
        }
    }

    def append (message: String, out: Option[StringBuffer]): Unit =
    {
        out.foreach(_.append(message))
    }

    def getConnectionFactory (debug_out: Option[StringBuffer] = None): CIMConnectionFactory =
    {
        try
        {
            if (null == _ConnectionFactory)
            {
                log.severe("CDI injection for ConnectionFactory failed, trying alternate lookup")
                append("resource injection failed\ntrying alternate lookup:\n", debug_out)
                try
                {
                    val context: InitialContext = new InitialContext()
                    print_context(debug_out, context, "java:openejb")
                    lookupFactory(context, "java:openejb/Resource/SparkConnectionFactory") match
                    {
                        case Success(connection) => _ConnectionFactory = connection
                        case Failure(error) =>
                            append(error.getLocalizedMessage, debug_out)
                            print_context(debug_out, context, "eis")
                            lookupFactory(context, "eis/SparkConnectionFactory") match
                            {
                                case Success(connection) => _ConnectionFactory = connection
                                case Failure(error) =>
                                    append(error.getLocalizedMessage, debug_out)
                            }
                    }
                }
                catch
                {
                    case nnfe: NameNotFoundException =>
                        append(s"NameNotFoundException: ${nnfe.getLocalizedMessage}\n", debug_out)
                    case ne: NamingException =>
                        append(s"NamingException: ${ne.getLocalizedMessage}\n", debug_out)
                }
            }
        }
        catch
        {
            case re: ResourceException =>
                append(s"ResourceException: ${re.getLocalizedMessage}\n", debug_out)
        }
        _ConnectionFactory
    }
}