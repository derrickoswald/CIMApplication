package ch.ninecode.cim.cimweb

import java.sql.SQLException

import scala.util.Failure
import scala.util.Success
import scala.util.Try

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

import ch.ninecode.cim.connector.CIMResultSet
import ch.ninecode.sp.SpatialOperationParameters

@Stateless
@Path("spatial/")
class Spatial extends RESTful
{
    @GET
    @Path("{method}")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def Operation (
        @PathParam("method") method: String, // "nearest"
        @DefaultValue("EnergyConsumer") @MatrixParam("psr") psr: String,
        @DefaultValue("7.281558") @MatrixParam("lon") lon: String,
        @DefaultValue("47.124142") @MatrixParam("lat") lat: String,
        @DefaultValue("5") @MatrixParam("n") n: String): String =
    {
        val ret = new RESTfulJSONResult()
        getConnection(ret) match
        {
            case Some(connection) =>
                try
                {
                    // set up the function with parameters
                    def function: Try[SpatialNearestFunction] =
                    {
                        if (method == "nearest")
                            try
                            {
                                Success(SpatialNearestFunction(SpatialOperationParameters(psr, lon.toDouble, lat.toDouble, n.toInt)))
                            }
                            catch
                            {
                                case nfe: NumberFormatException =>
                                    ret.setResultException(nfe, "Parameter format problem")
                                    Failure(nfe)
                            }
                        else
                            Failure(new ResourceException(s"""method $method not recognized"""))
                    }

                    function match
                    {
                        case Success(near) =>
                            val (spec, input) = getFunctionInput(near)
                            val interaction = connection.createInteraction
                            val output = interaction.execute(spec, input)
                            output match
                            {
                                case resultset: CIMResultSet =>
                                    try
                                    {
                                        // form the response
                                        val houses = Json.createArrayBuilder
                                        while (resultset.next)
                                        {
                                            val house = Json.createObjectBuilder
                                                .add("mRID", resultset.getString(1))
                                                .add("name", resultset.getString(2))
                                                .add("aliasName", resultset.getString(3))
                                                .add("xPosition", resultset.getString(4))
                                                .add("yPosition", resultset.getString(5))
                                                .add("PSRType", resultset.getString(6))
                                                .add("BaseVoltage", resultset.getString(7))
                                                .add("EquipmentContainer", resultset.getString(8))
                                                .add("phaseConnection", resultset.getString(9))
                                                .add("ao_name", resultset.getString(10))
                                                .add("ao_aliasName", resultset.getString(11))
                                                .add("ao_description", resultset.getString(12))
                                                .add("ao_mainAddress", resultset.getString(13))
                                                .add("ao_secondaryAddress", resultset.getString(14))
                                            houses.add(house)
                                        }
                                        resultset.close()
                                        val response = Json.createObjectBuilder
                                            .add("psr", psr)
                                            .add("lon", lon)
                                            .add("lat", lat)
                                            .add("n", n)
                                            .add("houses", houses)
                                        ret.setResult(response.build)
                                    } catch
                                    {
                                        case sqlexception: SQLException =>
                                            ret.setResultException(sqlexception, "SQLException on ResultSet")
                                    }
                                case _ =>
                                    ret.setResultException(new ResourceException(s"SpatialFunction interaction result is not a ResultSet"), "unhandled interaction result")
                            }
                            interaction.close()
                            connection.close()
                        case Failure(_) =>
                            ret.setResultException(new ResourceException(s"SpatialFunction interaction failed"), "interaction")
                    }
                }
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException(resourceexception, "ResourceException on interaction")
                }
                finally
                    try
                    connection.close()
                    catch
                    {
                        case resourceexception: ResourceException =>
                            ret.setResultException(resourceexception, "ResourceException on close")
                    }
            case None =>
                ret.setResultException(new ResourceException("no Spark connection"), "could not get Connection")
        }
        ret.toString
    }
}
