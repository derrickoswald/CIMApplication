package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.resource.ConnectionFactoryDefinition;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.gl.GridLABD;

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
@Path("/GridLabExport/{file}")
public class GridLabExport
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
    public Response GetGridLABExport (@PathParam("file") String filename, @PathParam("item") String item)
    {
        String transformer = (null != item && !item.equals ("")) ? item : null;
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
                        spec.setFunctionName (CIMInteractionSpec.GET_STRING_FUNCTION);
                        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                        input.setRecordShortDescription ("record containing the file name and class and method to run");
                        input.put ("filename", full_file);

                        // set up the method call details for the CIMConnector
                        GridLABD gl = new GridLABD ();
                        input.put ("class", gl.getClass ().getName ());
                        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
                        String path = gl.getClass ().getProtectionDomain ().getCodeSource ().getLocation ().getPath ();
                        String decodedPath;
                        try
                        {
                            decodedPath = URLDecoder.decode (path, "UTF-8");
                        }
                        catch (UnsupportedEncodingException e)
                        {
                            decodedPath = path;
                        }
                        if (decodedPath.endsWith (".jar"))
                            input.put ("jars", decodedPath);
                        if (null == transformer)
                            input.put ("method", "preparation");
                        else
                            input.put ("method", "stuff");
                        if (null != transformer)
                            input.put ("transformer", transformer);
                        final MappedRecord output = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                        output.setRecordShortDescription ("the results of the read operation");
                        final Interaction interaction = connection.createInteraction ();
                        if (interaction.execute (spec, input, output))
                        {
                            String result = output.get ("result").toString ();
                            out.append (result);
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
        return (Response.ok (out.toString (), MediaType.APPLICATION_OCTET_STREAM)
            .header ("content-disposition", "attachment; filename =" + "gridlabd.glm")
            .build ());
    }
}
