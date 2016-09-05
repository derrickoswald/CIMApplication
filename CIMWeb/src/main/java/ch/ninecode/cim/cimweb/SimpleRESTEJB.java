package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.naming.Context;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import javax.resource.ConnectionFactoryDefinition;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import java.util.Properties;
import javax.naming.InitialContext;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;

@ConnectionFactoryDefinition
(
    name = "java:comp/env/eis/SparkConnectionFactory",
    description = "Connection factory for Spark",
    interfaceName = "ch.ninecode.cim.connector.CIMConnectionFactory",
    resourceAdapter = "#CIMConnector", // reference CIMConnector.rar in application.xml
    minPoolSize = 2,
    transactionSupport = TransactionSupportLevel.NoTransaction
)

@Stateless
@Path("/ejb")
public class SimpleRESTEJB
{
    @Resource (lookup="java:comp/env/eis/SparkConnectionFactory")
    //@Resource (name = "java:comp/env/eis/SparkConnectionFactory", type = ConnectionFactory.class)
    CIMConnectionFactory factory;

    @SuppressWarnings ("unchecked")
    @GET
    public String ejb()
    {
        StringBuffer out = new StringBuffer ();
        Connection connection = getConnection (out);
        if (null != connection)
        {
            try
            {
                ConnectionMetaData meta = connection.getMetaData ();
                if (null != meta)
                {
                    out.append (" Product: ");
                    out.append (meta.getEISProductName ());
                    out.append (" Version: ");
                    out.append (meta.getEISProductVersion ());
                    out.append (" User: ");
                    out.append (meta.getUserName ());
                    out.append ("\n");
                }

                final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                spec.setFunctionName (CIMInteractionSpec.READ_FUNCTION);
                final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                input.setRecordShortDescription ("the parameters for this read operation");
                input.put ("filename", "hdfs://sandbox:9000/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
                final MappedRecord output = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                output.setRecordShortDescription ("the results of the read operation");
                final Interaction interaction = connection.createInteraction ();
                if (interaction.execute (spec, input, output))
                    if (!output.isEmpty ())
                        out.append ("read " + output.get ("count").toString () + " elements");
                    else
                        out.append ("interaction returned empty");
                else
                    out.append ("interaction execution failed");
                interaction.close ();
                connection.close ();
            }
            catch (ResourceException e)
            {
                out.append ("ResourceException on interaction");
                out.append ("\n");
                StringWriter string = new StringWriter ();
                PrintWriter writer = new PrintWriter (string);
                e.printStackTrace (writer);
                out.append (string.toString ());
                writer.close ();
            }
            finally
            {
                try
                {
                    connection.close ();
                }
                catch (ResourceException e)
                {
                    out.append ("ResourceException on close");
                    out.append ("\n");
                    StringWriter string = new StringWriter ();
                    PrintWriter writer = new PrintWriter (string);
                    e.printStackTrace (writer);
                    out.append (string.toString ());
                    writer.close ();
                }
            }
        }
        else
            out.append ("null");
        return ("ejb ok @ " + new Date().toString() + "\n" + out.toString () + "\n");
    }

    void print_context_r (StringBuffer out, Context context, String name, int depth)
    {
        try
        {
            NamingEnumeration<NameClassPair> values = context.list (name);
            while (values.hasMore ())
            {
                NameClassPair pair = values.next ();
                for (int i = 0; i < depth; i++)
                    out.append ("    ");
                out.append (pair.getName () + " : " + pair.getClassName ());
                out.append ("\n");
                if ("org.apache.openejb.core.ivm.naming.IvmContext" == pair.getClassName ())
                    print_context_r (out, context, name + "/" + pair.getName (), depth + 1);
            }
        }
        catch (NameNotFoundException nnfe)
        {
            out.append ("NameNotFoundException " + name);
            out.append ("\n");
        }
        catch (NamingException e)
        {
            out.append ("NamingException");
            out.append ("\n");
            StringWriter string = new StringWriter ();
            PrintWriter writer = new PrintWriter (string);
            e.printStackTrace (writer);
            out.append (string.toString ());
            writer.close ();
        }
    }

    void print_context (StringBuffer out, Context context, String name)
    {
        out.append (name);
        out.append ("\n");
        print_context_r (out, context, name, 1);
    }

    /**
     * Build a connection specification used by all the tests.
     * @return
     */
    CIMConnectionSpec remoteConfig ()
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();
        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", "1g");
        ret.getProperties ().put ("spark.executor.memory", "4g");

        return (ret);
    }

    public Connection getConnection (StringBuffer... buffer)
    {
        StringBuffer out = buffer.length > 0 ? buffer[0] : new StringBuffer ();
        Connection connection = null;
        try
        {
            if (null == factory)
            {
                final Properties properties = new Properties ();
//                properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");
//                properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*");

                try
                {
                    Context context = new InitialContext (properties);
                    print_context (out, context, "java:");
                    print_context (out, context, "openejb:");
                    //factory = (CIMConnectionFactory) context.lookup ("java:openejb/Resource/CIMConnector");
                    //factory = (CIMConnectionFactory) context.lookup ("java:comp/env/eis/SparkConnectionFactory");
                    //factory = (CIMConnectionFactory) context.lookup ("eis/SparkConnectionFactory");
                    //not a connection factory... factory = (CIMConnectionFactory) context.lookup ("openejb:Resource/CIMResourceAdapter");
                    factory = (CIMConnectionFactory) context.lookup ("openejb:Resource/CIMConnector.rar");
                }
                catch (NameNotFoundException nnfe)
                {
                    out.append ("NameNotFoundException");
                    out.append ("\n");
                    StringWriter string = new StringWriter ();
                    PrintWriter writer = new PrintWriter (string);
                    nnfe.printStackTrace (writer);
                    out.append (string.toString ());
                    writer.close ();
                }
                catch (NamingException e)
                {
                    out.append ("NamingException");
                    out.append ("\n");
                    StringWriter string = new StringWriter ();
                    PrintWriter writer = new PrintWriter (string);
                    e.printStackTrace (writer);
                    out.append (string.toString ());
                    writer.close ();
                }
            }
            if (null != factory)
                connection = factory.getConnection (remoteConfig ());
        }
        catch (ResourceException e)
        {
            out.append ("ResourceException");
            out.append ("\n");
            StringWriter string = new StringWriter ();
            PrintWriter writer = new PrintWriter (string);
            e.printStackTrace (writer);
            out.append (string.toString ());
            writer.close ();
        }

        return (connection);
    }
}
