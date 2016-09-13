package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.resource.ConnectionFactoryDefinition;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;

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
@Path("/Spatial/{file}")
public class Spatial
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

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

    @SuppressWarnings ("unchecked")
    @GET
    @Path("{p:/?}{item:((.*)?)}")
    @Produces ({"text/plain", "application/json"})
    public String Spatial (@PathParam("file") String filename, @PathParam("item") String item)
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
                        String full_file = "hdfs://sandbox:9000/data/" + filename + ".rdf";
                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.EXECUTE_METHOD_FUNCTION);
                        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                        input.setRecordShortDescription ("record containing the file name and class and method to run");
                        input.put ("filename", full_file);
                        input.put ("class", "ch.ninecode.spatial.SpatialOperations");
                        input.put ("method", "nearest");

                        input.put ("psr", "EnergyConsumer");
                        input.put ("lon", "7.281558");
                        input.put ("lat", "47.124142");
                        input.put ("n", "5");

                        final Interaction interaction = connection.createInteraction ();
                        final Record output = interaction.execute (spec, input);
                        if ((null == output) || !output.getClass ().isAssignableFrom (CIMResultSet.class))
                            throw new ResourceException ("object of class " + output.getClass ().toGenericString () + " is not a ResultSet");
                        else
                        {
                            CIMResultSet resultset = (CIMResultSet)output;
                            try
                            {
                                out.append ("[");
                                while (resultset.next ())
                                {
                                    out.append (resultset.getString (1) + "\",");
                                }
                                out.deleteCharAt (out.length () - 1); // get rid of trailing comma
                                out.append ("\n] }\n");
                                resultset.close ();
                            }
                            catch (SQLException sqlexception)
                            {
                                out.append ("SQLException on ResultSet");
                                out.append ("\n");
                                StringWriter string = new StringWriter ();
                                PrintWriter writer = new PrintWriter (string);
                                sqlexception.printStackTrace (writer);
                                out.append (string.toString ());
                                writer.close ();
                            }

                        }
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
