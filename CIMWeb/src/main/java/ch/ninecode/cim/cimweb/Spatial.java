package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
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

import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;
import ch.ninecode.sp.SpatialOperations;

@Stateless
@Path("Spatial/")
public class Spatial extends RESTful
{
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
        RESTfulResult ret = new RESTfulResult ();

        Connection connection = getConnection (ret);
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
                final MappedRecord input = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                input.setRecordShortDescription ("record containing the file names and class and method to run");

                // set up the method call details for the CIMConnector
                SpatialOperations ops = new SpatialOperations ();
                input.put ("method", method);
                input.put ("class", ops.getClass ().getName ());
                String jar = getConnectionFactory ().JarPath (ops);
                if (null != jar)
                    input.put ("jars", jar);
                ops = null;

                // set up the parameters
                input.put ("filename", sb.toString ());
                input.put ("psr", psr);
                input.put ("lon", lon);
                input.put ("lat", lat);
                input.put ("n", n);

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
                        response.add ("filename", sb.toString ());
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
