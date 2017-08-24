package ch.ninecode.cim.cimweb;

import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;

import ch.ninecode.cim.cimweb.RESTful.RESTfulResult;
import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;

@Stateless
@Path("/EnergyConsumer")
public class EnergyConsumer extends RESTful
{
    @SuppressWarnings ("unchecked")
    @GET
    @Produces({"text/plain", "application/json"})
    public String GetEnergyConsumers()
    {
        RESTfulResult ret = new RESTfulResult ();

        Connection connection = getConnection (ret);
        if (null != connection)
        {
            try
            {
                final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                spec.setFunctionName (CIMInteractionSpec.GET_DATAFRAME_FUNCTION);
                final MappedRecord input = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                input.setRecordShortDescription ("record containing the file name with key filename and sql query with key query");
                input.put ("filename", "hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
                input.put ("query", "select s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mRID, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName aliasName, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name name, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description description, p.xPosition, p.yPosition from EnergyConsumer s, PositionPoint p where s.ConductingEquipment.Equipment.PowerSystemResource.Location = p.Location and p.sequenceNumber = 0");
                final Interaction interaction = connection.createInteraction ();
                final Record output = interaction.execute (spec, input);
                if ((null == output) || !output.getClass ().isAssignableFrom (CIMResultSet.class))
                    throw new ResourceException ("object of class " + output.getClass ().toGenericString () + " is not a ResultSet");
                else
                {
                    CIMResultSet resultset = (CIMResultSet)output;
                    try
                    {
                        // form the response
                        JsonArrayBuilder features = Json.createArrayBuilder ();
                        while (resultset.next ())
                        {
                            JsonObjectBuilder feature = Json.createObjectBuilder ();
                            feature.add ("type", "Feature");
                            JsonObjectBuilder geometry = Json.createObjectBuilder ();
                            geometry.add ("type", "Point");
                            JsonArrayBuilder coordinates = Json.createArrayBuilder ();
                            coordinates.add (resultset.getDouble (5));
                            coordinates.add (resultset.getDouble (6));
                            geometry.add ("coordinates", coordinates);
                            feature.add ("geometry", geometry);
                            JsonObjectBuilder properties = Json.createObjectBuilder ();
                            properties.add ("mRID", resultset.getString (1));
                            properties.add ("aliasName", resultset.getString (2));
                            properties.add ("name", resultset.getString (3));
                            properties.add ("description", resultset.getString (4));
                            feature.add ("properties", properties);
                            features.add (feature);
                        }
                        resultset.close ();
                        JsonObjectBuilder response = Json.createObjectBuilder ();
                        response.add ("type", "FeatureCollection");
                        response.add ("features", features);
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
        else
        {
            ret._Status = FAIL;
            ret._Message += "getConnection failed";
        }

        return (ret.toString ());
    }
}
