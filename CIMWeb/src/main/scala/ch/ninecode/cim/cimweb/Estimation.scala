package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.POST
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.DefaultValue
import javax.ws.rs.MatrixParam

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.sim.SimulationOptions

@Stateless
@Path ("estimation")
class Estimation extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger (LOGGER_NAME)

    @POST
    @Produces (Array (MediaType.APPLICATION_JSON))
    def estimate (
        @DefaultValue ("false") @MatrixParam ("verbose") _verbose: String,
        @DefaultValue ("false") @MatrixParam ("keep") _keep: String,
        data: Array[Byte]): String =
    {
        val verbose = asBoolean (_verbose)
        val keep = asBoolean (_keep)
        val json = new String (data, "UTF-8")
        _Logger.info ("""estimation verbose=%s, keep=%s, json=%s""".format (verbose, keep, json))
        var ret = new RESTfulJSONResult
        getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    // set up the function with parameters
                    // Note:
                    // to determine the Cassandra host we need to look in
                    // the SparkContext configuration for spark.cassandra.connection.host, i.e.	"sandbox",
                    // so we do that in the EstimationFunction when we get a SparkSession,
                    // otherwise it defaults to localhost
                    val options = SimulationOptions (verbose = verbose, keep = keep, simulation = Seq (json))
                    val estimator = EstimationFunction (options)
                    val (spec, input) = getFunctionInput (estimator)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get (CIMFunction.RESULT) match
                            {
                                case struct: JsonObject =>
                                    ret = RESTfulJSONResult (struct.getString ("status"), struct.getString ("message"), struct.getJsonObject ("result"))
                                case _ =>
                                    ret.setResultException (new ResourceException ("EstimationFunction result is not a JsonObject"), "unhandled result type")
                            }
                        case _ =>
                            ret.setResultException (new ResourceException ("EstimationFunction interaction result is not a MappedRecord"), "unhandled interaction result")
                    }
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
            case None =>
                ret.setResultException (new ResourceException ("no Spark connection"), "could not get Connection")
        }

        ret.toString
    }

}
