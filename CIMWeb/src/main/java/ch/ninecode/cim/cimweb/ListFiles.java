package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.resource.ConnectionFactoryDefinition;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;

import java.io.PrintWriter;
import java.io.StringWriter;

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
@Path("/list")
public class ListFiles
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

    /**
     * Build a connection specification.
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

    @GET
    @Produces ({"application/json"})
    public String listFiles ()
    {
        StringBuffer out = new StringBuffer ();
        if (null != factory)
        {
            Connection connection;
            try
            {
                connection = factory.getConnection (remoteConfig ());
                if (null != connection)
                {
                    try
                    {
                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.LIST_FILES);
                        final MappedRecord output = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                        output.setRecordShortDescription ("the results of the list operation");
                        final Interaction interaction = connection.createInteraction ();
                        if (interaction.execute (spec, null, output))
                            if (!output.isEmpty ())
                                out.append (output.get ("files").toString ());
                            else
                                out.append ("interaction returned empty");
                        else
                            out.append ("interaction execution failed");
                        interaction.close ();
                        connection.close ();
                    }
                    catch (ResourceException resourceexception)
                    {
                        out.append ("ResourceException on interaction");
                        out.append ("\n");
                        StringWriter string = new StringWriter ();
                        PrintWriter writer = new PrintWriter (string);
                        resourceexception.printStackTrace (writer);
                        out.append (string.toString ());
                        writer.close ();
                    }
                    finally
                    {
                        try
                        {
                            connection.close ();
                        }
                        catch (ResourceException resourceexception)
                        {
                            out.append ("ResourceException on close");
                            out.append ("\n");
                            StringWriter string = new StringWriter ();
                            PrintWriter writer = new PrintWriter (string);
                            resourceexception.printStackTrace (writer);
                            out.append (string.toString ());
                            writer.close ();
                        }
                    }
                }
            }
            catch (ResourceException exception)
            {
                out.append ("ResourceException");
                out.append ("\n");
                StringWriter string = new StringWriter ();
                PrintWriter writer = new PrintWriter (string);
                exception.printStackTrace (writer);
                out.append (string.toString ());
                writer.close ();
            }
        }

        return (out.toString ());
    }
}
