package ch.ninecode.cim.cimweb

import java.sql.SQLException

import javax.ejb.Stateless
import javax.json.Json
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.resource.ResourceException

import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMResultSet


@Stateless
@Path ("/EnergyConsumer") class EnergyConsumer extends RESTful
{
    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetEnergyConsumers: String =
    {
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.GET_DATAFRAME_FUNCTION)
                val input = getInputRecord ("record containing the sql query")
                input.asInstanceOf[map].put ("query", "select s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mRID, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName aliasName, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name name, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description description, p.xPosition, p.yPosition from EnergyConsumer s, PositionPoint p where s.ConductingEquipment.Equipment.PowerSystemResource.Location = p.Location and p.sequenceNumber = 0")
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a ResultSet")
                else
                    if (!output.getClass.isAssignableFrom (classOf [CIMResultSet]))
                        throw new ResourceException ("object of class " + output.getClass.toGenericString + " is not a ResultSet")
                    else
                    {
                        val resultset = output.asInstanceOf [CIMResultSet]
                        try
                        {
                            // form the response
                            val features = Json.createArrayBuilder
                            while (resultset.next)
                            {
                                val feature = Json.createObjectBuilder
                                feature.add ("type", "Feature")
                                val geometry = Json.createObjectBuilder
                                geometry.add ("type", "Point")
                                val coordinates = Json.createArrayBuilder
                                coordinates.add (resultset.getDouble (5))
                                coordinates.add (resultset.getDouble (6))
                                geometry.add ("coordinates", coordinates)
                                feature.add ("geometry", geometry)
                                val properties = Json.createObjectBuilder
                                properties.add ("mRID", resultset.getString (1))
                                properties.add ("aliasName", resultset.getString (2))
                                properties.add ("name", resultset.getString (3))
                                properties.add ("description", resultset.getString (4))
                                feature.add ("properties", properties)
                                features.add (feature)
                            }
                            resultset.close ()
                            val response = Json.createObjectBuilder
                            response.add ("type", "FeatureCollection")
                            response.add ("features", features)
                            ret.setResult (response.build)
                        }
                        catch
                        {
                            case sqlexception: SQLException ⇒
                                ret.setResultException (sqlexception, "SQLException on ResultSet")
                        }
                    }
                interaction.close ()
            }
            catch
            {
                case resourceexception: ResourceException =>
                    ret.setResultException (resourceexception, "ResourceException on interaction")
            }
            finally
                try
                    connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException (resourceexception, "ResourceException on close")
                }
        else
        {
            ret.status_$eq (RESTfulJSONResult.FAIL)
            ret.message_$eq (ret.message + "getConnection failed")
        }
        ret.toString
    }
}
