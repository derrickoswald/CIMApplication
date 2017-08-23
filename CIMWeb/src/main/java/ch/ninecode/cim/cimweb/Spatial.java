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
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;
import ch.ninecode.sp.SpatialOperations;

@Stateless
@Path("Spatial/")
public class Spatial
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

    @SuppressWarnings ("unchecked")
    @GET
    @Path ("{method}")
    @Produces ({"text/plain", "application/json"})
    public String Operation
    (
        @PathParam ("method") String method, // "nearest"
        @MatrixParam ("file") List<String> files,
        @DefaultValue ("EnergyConsumer") @MatrixParam ("psr") String psr,
        @DefaultValue ("7.281558") @MatrixParam ("lon") String lon,
        @DefaultValue ("47.124142") @MatrixParam ("lat") String lat,
        @DefaultValue ("5") @MatrixParam ("n") String n
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
                            sb.append (file);
                        }

                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.EXECUTE_METHOD_FUNCTION);
                        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                        input.setRecordShortDescription ("record containing the file names and class and method to run");

                        // set up the method call details for the CIMConnector
                        SpatialOperations ops = new SpatialOperations ();
                        // this will never work
                        // ToDo: unified calling method
                        input.put ("method", method);
                        input.put ("class", ops.getClass ().getName ());
                        String jar = factory.JarPath (ops);
                        if (null != jar)
                            input.put ("jars", jar);
                        ops = null;

                        // set up the parameters
                        input.put ("filename", sb.toString ());
                        input.put ("psr", psr);
                        input.put ("lon", lon);
                        input.put ("lat", lat);
                        input.put ("n", n);
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
                                    out.append ("xPosition: \"" + resultset.getString (4) + "\",");
                                    out.append ("yPosition: \"" + resultset.getString (5) + "\",");
                                    out.append ("PSRType: \"" + resultset.getString (6) + "\",");
                                    out.append ("BaseVoltage: \"" + resultset.getString (7) + "\",");
                                    out.append ("EquipmentContainer: \"" + resultset.getString (8) + "\",");
                                    out.append ("phaseConnection: \"" + resultset.getString (9) + "\",");
                                    out.append ("ao_name: \"" + resultset.getString (10) + "\",");
                                    out.append ("ao_aliasName: \"" + resultset.getString (11) + "\",");
                                    out.append ("ao_description: \"" + resultset.getString (12) + "\",");
                                    out.append ("ao_mainAddress: \"" + resultset.getString (13) + "\",");
                                    out.append ("ao_secondaryAddress: \"" + resultset.getString (14) + "\"");
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
