package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.MatrixParam;
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
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;
import ch.ninecode.sp.SpatialOperations;

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
@Path("Visualize/")
public class Visualize
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

    @SuppressWarnings ("unchecked")
    @GET
    @Path ("{method}")
    @Produces ({"text/plain", "application/json"})
    public String Operation
    (
        @PathParam ("method") String method, // "extract"
        @MatrixParam ("file") List<String> files,
        @DefaultValue ("7.28") @MatrixParam ("xmin") String xmin,
        @DefaultValue ("47.12") @MatrixParam ("ymin") String ymin,
        @DefaultValue ("7.29") @MatrixParam ("xmax") String xmax,
        @DefaultValue ("47.13") @MatrixParam ("ymax") String ymax,
        @DefaultValue ("true") @MatrixParam ("reduceLines") String reduceLines,
        @DefaultValue ("2000") @MatrixParam ("maxLines") String maxLines,
        @DefaultValue ("true") @MatrixParam ("dougPeuk") String dougPeuk,
        @DefaultValue ("2.0") @MatrixParam ("dougPeukFactor") String dougPeukFactor,
        @DefaultValue ("1.0e-4") @MatrixParam ("resolution") String resolution
    )
    {
        StringBuffer out = new StringBuffer ();
        if (null != factory)
        {
            Connection connection;
            try
            {
                connection = factory.getConnection (factory.getDefaultConnectionSpec ());
                if (null != connection)
                {
                    try
                    {
                        // allow for multiple file names like
                        // localhost:8080/cimweb/cim/Spatial/nearest;file=NIS_CIM_Export_b4_Bruegg;file=ISU_CIM_Export_20160505
                        StringBuilder sb = new StringBuilder ();
                        for (String file: files)
                        {
                            if (0 != sb.length ())
                                sb.append (",");
                            sb.append (factory.InputPath (file));
                        }

                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.EXECUTE_METHOD_FUNCTION);
                        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                        input.setRecordShortDescription ("record containing the file names and class and method to run");

                        // set up the method call details for the CIMConnector
                        SpatialOperations ops = new SpatialOperations ();
                        input.put ("method", method);
                        input.put ("class", ops.getClass ().getName ());
                        String jar = factory.JarPath (ops);
                        if (null != jar)
                            input.put ("jars", jar);
                        ops = null;

                        // set up the parameters
                        input.put ("filename", sb.toString ());
                        input.put ("xmin", xmin);
                        input.put ("ymin", ymin);
                        input.put ("xmax", xmax);
                        input.put ("ymax", ymax);
                        input.put ("reduceLines", reduceLines);
                        input.put ("maxLines", maxLines);
                        input.put ("dougPeuk", dougPeuk);
                        input.put ("dougPeukFactor", dougPeukFactor);
                        input.put ("resolution", resolution);
                        out.append (input.toString ());

                        final Interaction interaction = connection.createInteraction ();
                        final Record output = interaction.execute (spec, input);
                        if ((null == output) || !output.getClass ().isAssignableFrom (CIMResultSet.class))
                            throw new ResourceException ("object of class " + output.getClass ().toGenericString () + " is not a ResultSet");
                        else
                        {
                            CIMResultSet resultset = (CIMResultSet)output;
                            try
                            {
                                out.setLength (0);
                                out.append ("[");
                                while (resultset.next ())
                                {
                                    out.append ("{ ");
                                    out.append ("mRID: \"" + resultset.getString (1) + "\",");
                                    out.append ("name: \"" + resultset.getString (2) + "\",");
                                    out.append ("aliasName: \"" + resultset.getString (3) + "\",");
                                    out.append ("location: \"" + resultset.getString (4) + "\",");
                                    out.append ("baseVoltage: \"" + resultset.getString (5) + "\"");
                                    out.append ("},\n");
                                }
                                if (2 < out.length ())
                                    out.deleteCharAt (out.length () - 2); // get rid of trailing comma
                                out.append ("]\n");
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
