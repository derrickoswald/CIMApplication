package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;

import ch.ninecode.cim.connector.*;
import ch.ninecode.sp.SpatialOperationParameters;

@Stateless
@Path("Spatial/")
public class Spatial extends RESTful
{
    @SuppressWarnings ("unchecked")
    @GET
    @Path ("{method}")
    @Produces ({MediaType.APPLICATION_JSON})
    public String Operation
    (
        @PathParam ("method") String method, // "nearest"
        @DefaultValue ("EnergyConsumer") @MatrixParam ("psr") String psr,
        @DefaultValue ("7.281558") @MatrixParam ("lon") String lon,
        @DefaultValue ("47.124142") @MatrixParam ("lat") String lat,
        @DefaultValue ("5") @MatrixParam ("n") String n
    )
    {
        RESTfulResult ret = new RESTfulResult ();

        Connection connection = getConnection (ret);
        if (null != connection)
        {
            try
            {
                final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION);
                final MappedRecord input = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                input.setRecordShortDescription ("record containing the file names and class and method to run");
                if (method.equals ("nearest"))
                {
                    // set up the parameters
                    SpatialOperationParameters parameters = new SpatialOperationParameters (psr, Double.parseDouble (lon), Double.parseDouble (lat), Integer.parseInt (n));
                    SpatialNearestFunction near = new SpatialNearestFunction ();
                    near.setSpatialOperationParameters (parameters);
                    input.put ("function", near);
                }
                else
                    throw new ResourceException ("method " + method + " not recognized");
                final Interaction interaction = connection.createInteraction ();
                final Record output = interaction.execute (spec, input);
                if (null == output)
                    throw new ResourceException ("null is not a ResultSet");
                else if (!output.getClass ().isAssignableFrom (CIMResultSet.class))
                    throw new ResourceException ("object of class " + output.getClass ().toGenericString () + " is not a ResultSet");
                else
                {
                    CIMResultSet resultset = (CIMResultSet)output;
                    try
                    {
                        // form the response
                        JsonArrayBuilder houses = Json.createArrayBuilder ();
                        while (resultset.next ())
                        {
                            JsonObjectBuilder house = Json.createObjectBuilder ();
                            house.add ("mRID", resultset.getString (1));
                            house.add ("name", resultset.getString (2));
                            house.add ("aliasName", resultset.getString (3));
                            house.add ("xPosition", resultset.getString (4));
                            house.add ("yPosition", resultset.getString (5));
                            house.add ("PSRType", resultset.getString (6));
                            house.add ("BaseVoltage", resultset.getString (7));
                            house.add ("EquipmentContainer", resultset.getString (8));
                            house.add ("phaseConnection", resultset.getString (9));
                            house.add ("ao_name", resultset.getString (10));
                            house.add ("ao_aliasName", resultset.getString (11));
                            house.add ("ao_description", resultset.getString (12));
                            house.add ("ao_mainAddress", resultset.getString (13));
                            house.add ("ao_secondaryAddress", resultset.getString (14));
                            houses.add (house);
                        }
                        resultset.close ();
                        JsonObjectBuilder response = Json.createObjectBuilder ();
                        response.add ("psr", psr);
                        response.add ("lon", lon);
                        response.add ("lat", lat);
                        response.add ("n", n);

                        response.add ("houses", houses);
                        ret.setResult (response.build ());
                    }
                    catch (SQLException sqlexception)
                    {
                        StringWriter string = new StringWriter ();
                        string.append ("SQLException on ResultSet\n");
                        string.append ("SQLException on ResultSet");
                        PrintWriter writer = new PrintWriter (string);
                        sqlexception.printStackTrace (writer);
                        ret._Status = FAIL;
                        ret._Message = string.toString ();
                        writer.close ();
                    }

                }
                interaction.close ();
                connection.close ();
            }
            catch (ResourceException resourceexception)
            {
                StringWriter string = new StringWriter ();
                string.append ("ResourceException on interaction\n");
                PrintWriter writer = new PrintWriter (string);
                resourceexception.printStackTrace (writer);
                ret._Status = FAIL;
                ret._Message = string.toString ();
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
                    StringWriter string = new StringWriter ();
                    string.append ("ResourceException on close\n");
                    PrintWriter writer = new PrintWriter (string);
                    resourceexception.printStackTrace (writer);
                    ret._Status = FAIL;
                    ret._Message = string.toString ();
                    writer.close ();
                }
            }
        }

        return (ret.toString ());
    }
}
