package ch.ninecode.cim.cimweb

import java.sql.SQLException
import javax.ejb.Stateless
import javax.json.Json
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.resource.ResourceException

import ch.ninecode.cim.connector._
import ch.ninecode.sp.SpatialOperationParameters

@Stateless
@Path ("Spatial/")
class Spatial extends RESTful
{
    @GET
    @Path ("{method}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def Operation (
        @PathParam ("method") method: String, // "nearest"
        @DefaultValue ("EnergyConsumer") @MatrixParam ("psr") psr: String,
        @DefaultValue ("7.281558") @MatrixParam ("lon") lon: String,
        @DefaultValue ("47.124142") @MatrixParam ("lat") lat: String,
        @DefaultValue ("5") @MatrixParam ("n") n: String): String =
    {
        val ret = new RESTfulResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                if (method == "nearest")
                    try
                    {
                        // set up the function with parameters
                        val near = SpatialNearestFunction (SpatialOperationParameters (psr, lon.toDouble, lat.toDouble, n.toInt))
                        type map = java.util.Map[String,Object]
                        input.asInstanceOf[map].put ("function", near)
                    }
                    catch
                    {
                        case nfe: NumberFormatException => ret.setResultException (nfe, "Parameter format problem")
                    }
                else
                    throw new ResourceException ("method \"%s\" not recognized".format (method))
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a ResultSet")
                else
                    if (!output.getClass.isAssignableFrom (classOf [CIMResultSet]))
                        throw new ResourceException ("object of class %s is not a ResultSet".format (output.getClass.toGenericString))
                    else
                    {
                        val resultset = output.asInstanceOf [CIMResultSet]
                        try
                        { // form the response
                            val houses = Json.createArrayBuilder
                            while (resultset.next)
                            {
                                val house = Json.createObjectBuilder
                                house.add ("mRID", resultset.getString (1))
                                house.add ("name", resultset.getString (2))
                                house.add ("aliasName", resultset.getString (3))
                                house.add ("xPosition", resultset.getString (4))
                                house.add ("yPosition", resultset.getString (5))
                                house.add ("PSRType", resultset.getString (6))
                                house.add ("BaseVoltage", resultset.getString (7))
                                house.add ("EquipmentContainer", resultset.getString (8))
                                house.add ("phaseConnection", resultset.getString (9))
                                house.add ("ao_name", resultset.getString (10))
                                house.add ("ao_aliasName", resultset.getString (11))
                                house.add ("ao_description", resultset.getString (12))
                                house.add ("ao_mainAddress", resultset.getString (13))
                                house.add ("ao_secondaryAddress", resultset.getString (14))
                                houses.add (house)
                            }
                            resultset.close ()
                            val response = Json.createObjectBuilder
                            response.add ("psr", psr)
                            response.add ("lon", lon)
                            response.add ("lat", lat)
                            response.add ("n", n)
                            response.add ("houses", houses)
                            ret.setResult (response.build)
                        } catch
                        {
                            case sqlexception: SQLException =>
                                ret.setResultException (sqlexception, "SQLException on ResultSet")
                        }
                    }
                interaction.close ()
                connection.close ()
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
        ret.toString
    }
}
