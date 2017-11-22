package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.annotation.Resource
import javax.ejb.Stateless
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import java.io.PrintWriter
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.sql.SQLException
import javax.json.Json
import javax.json.JsonStructure
import javax.resource.ResourceException
import javax.resource.cci.Connection
import javax.resource.cci.Interaction
import javax.resource.cci.MappedRecord
import javax.resource.cci.Record
import javax.ws.rs.DefaultValue
import javax.ws.rs.MatrixParam
import javax.ws.rs.core.MediaType

import ch.ninecode.cim.connector.CIMConnectionFactory
import ch.ninecode.cim.connector.CIMConnectionSpec
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.cim.connector.CIMResultSet
import ch.ninecode.sc.ShortCircuitOptions


@Stateless
@Path ("/short_circuit/")
class ShortCircuitCalculation extends RESTful
{
    import ShortCircuitCalculation._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        @DefaultValue ("KS_Leistungen.csv") @MatrixParam ("spreadsheet") spreadsheet: String): String =
        GetShortCircuitData ("all", spreadsheet)


    @GET
    @Path ("{item:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        @PathParam ("item") item: String,
        @DefaultValue ("KS_Leistungen.csv") @MatrixParam ("spreadsheet") spreadsheet: String
        ): String =
    {
        val transformer = if (null != item && !(item == "")) if (item.startsWith ("/")) item.substring (1) else item else null
        _Logger.info ("shortcircuit transformer=%s spreadsheet=%s".format (transformer, spreadsheet))
        val ret = new RESTfulJSONResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val options = ShortCircuitOptions (false, spreadsheet, transformer, null)
                val query = ShortCircuitFunction (options)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, query)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                try
                {
                    if (null == output)
                        throw new ResourceException ("null is not a ResultSet")
                    else
                        if (!output.getClass.isAssignableFrom (classOf [CIMResultSet]))
                            throw new ResourceException ("object of class %s is not a ResultSet".format (output.getClass.toGenericString))
                        else
                        {
                            val resultset = output.asInstanceOf [CIMResultSet]
                            try
                            {
                                // form the response
                                val houses = Json.createArrayBuilder
                                while (resultset.next)
                                {
                                    //    case class HouseConnection(
                                    //          mRID: String,
                                    //          node: String,
                                    //          transformer: Array[TData],
                                    //          r: Double,
                                    //          x: Double,
                                    //          r0: Double,
                                    //          x0: Double,
                                    //          ik: Double = 0.0,
                                    //          ik3pol: Double = 0.0,
                                    //          ip: Double = 0.0)
                                    val house = Json.createObjectBuilder
                                    house.add ("mRID", resultset.getString (1))
                                    house.add ("node", resultset.getString (2))
                                    house.add ("transformer", resultset.getString (3))
                                    house.add ("r", resultset.getDouble (4))
                                    house.add ("x", resultset.getDouble (5))
                                    house.add ("r0", resultset.getDouble (6))
                                    house.add ("x0", resultset.getDouble (7))
    //                                house.add ("fuses", resultset.getString (8))
                                    house.add ("ik", resultset.getDouble (8))
                                    house.add ("ik3pol", resultset.getDouble (9))
                                    house.add ("ip", resultset.getDouble (10))
    //                                house.add ("wires_valid", resultset.getBoolean (12))
    //                                house.add ("trafo_valid", resultset.getBoolean (13))
    //                                house.add ("fuse_valid", resultset.getBoolean (14))
    //                                val coordinates = Json.createArrayBuilder
    //                                coordinates.add (resultset.getString (15))
    //                                coordinates.add (resultset.getString (16))
    //                                house.add ("coordinates", coordinates)
                                    houses.add (house)
                                }
                                resultset.close ()
                                ret.setResult (houses.build)
                            }
                            catch
                            {
                                case sqlexception: SQLException =>
                                    ret.setResultException (sqlexception, "SQLException on ResultSet")
                            }
                        }
                }
                finally
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
        ret.toString
    }
}

object ShortCircuitCalculation
{
    val LOGGER_NAME: String = ShortCircuitCalculation.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
