package ch.ninecode.cim.cimweb

import java.util.Properties
import javax.naming.Context
import javax.naming.InitialContext
import javax.naming.NameClassPair
import javax.naming.NameNotFoundException
import javax.naming.NamingException
import javax.resource.ResourceException
import javax.resource.cci.Connection
import javax.resource.cci.MappedRecord

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMConnectionFactory
import ch.ninecode.cim.connector.CIMMappedRecord

class RESTful ()
{

    //import javax.annotation.Resource;
    //    @Resource
    //    (
    //        description = "Connection factory for Spark connection using CIMConnector",
    //        name = "CIMConnector.rar", // "java:app/eis/SparkConnectionFactory"
    //        authenticationType = Resource.AuthenticationType.APPLICATION
    //    )
    protected var _ConnectionFactory: CIMConnectionFactory = _

    try
        _ConnectionFactory = new InitialContext ().lookup ("java:openejb/Resource/CIMConnector.rar").asInstanceOf [CIMConnectionFactory]
    catch
    {
        case ne: NamingException ⇒
            System.out.println ("fuck this JNDI shit " + ne.getMessage)
    }

    /**
     * Debugging for JNDI.
     */
    def print_context_r (out: StringBuffer, context: Context, name: String, depth: Int): Unit =
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
            for (pair: NameClassPair ← context.list (name))
            {
                out.append (indent)
                out.append (pair.getName)
                out.append (" : ")
                out.append (pair.getClassName)
                out.append ("\n")
                print_context_r (out, context, name + "/" + pair.getName, depth + 1)
            }
        }
        catch
        {
            case _: NameNotFoundException ⇒
                out.append (indent)
                out.append ("NameNotFoundException ")
                out.append (name)
                out.append ("\n")
            case ne: NamingException ⇒
                if (!("Name is not bound to a Context" == ne.getMessage))
                {
                    out.append (indent)
                    out.append ("NamingException ")
                    out.append (ne.getMessage)
                    out.append ("\n")
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

    protected def getConnectionFactory: CIMConnectionFactory =
        _ConnectionFactory

    protected def getConnection (result: RESTfulJSONResult): Connection =
    {
        val out = new StringBuffer
        var connection: Connection = null
        try
        {
            if (null == _ConnectionFactory)
            {
                out.append ("resource injection failed\ntrying alternate lookup:\n")
                val properties = new Properties
                //                properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");
                //                properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*");
                try
                {
                    val context = new InitialContext (properties)
                    print_context (out, context, "java:")
                    print_context (out, context, "openejb:")
                    _ConnectionFactory = context.lookup ("openejb:Resource/CIMConnector.rar").asInstanceOf [CIMConnectionFactory]
                }
                catch
                {
                    case nnfe: NameNotFoundException ⇒
                        out.append ("NameNotFoundException: ")
                        out.append (nnfe.getMessage)
                        out.append ("\n")
                    case ne: NamingException ⇒
                        out.append ("NamingException: ")
                        out.append (ne.getMessage)
                        out.append ("\n")
                }
            }
            if (null != _ConnectionFactory)
            {
                val specification = _ConnectionFactory.getDefaultConnectionSpec
                connection = _ConnectionFactory.getConnection (specification)
            }
        }
        catch
        {
            case re: ResourceException ⇒
                out.append ("ResourceException: ")
                out.append (re.getMessage)
                out.append ("\n")
        }
        result.message = out.toString
        connection
    }

    @throws[ResourceException]
    protected def getInputRecord (description: String): MappedRecord =
    {
        val ret = getConnectionFactory.getRecordFactory.createMappedRecord (CIMMappedRecord.INPUT)
        ret.setRecordShortDescription (description)
        ret
    }
}
