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
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.sim.SimulationOptions

@Stateless
@Path ("estimation")
class Estimation extends RESTful
{
    import Estimation._

    @POST
    @Produces (Array (MediaType.APPLICATION_JSON))
    def estimate (
         @DefaultValue ("false") @MatrixParam ("verbose") _verbose: String,
         @DefaultValue ("false") @MatrixParam ("keep") _keep: String,
         @DefaultValue ("false") @MatrixParam ("summarize") _summarize: String,
         @DefaultValue ("false") @MatrixParam ("events") _events: String,
         data: Array[Byte]): String =
    {
        val verbose = try { _verbose.toBoolean } catch { case _: Throwable => false }
        val keep = try { _keep.toBoolean } catch { case _: Throwable => false }
        val summarize = try { _summarize.toBoolean } catch { case _: Throwable => false }
        val events = try { _events.toBoolean } catch { case _: Throwable => false }
        val json = new String (data, "UTF-8")
        _Logger.info ("""estimation verbose=%s, keep=%s, events=%s, summarize=%s, json=%s""".format (verbose, keep, events, summarize,json))
        var ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                // Note:
                // to determine the Cassandra host we need to look in
                // the SparkContext configuration for spark.cassandra.connection.host, i.e.	"sandbox",
                // so we do that in the EstimationFunction when we get a SparkSession,
                // otherwise it defaults to localhost
                val options = SimulationOptions (verbose = verbose, keep = keep, simulation = Seq (json))
                val estimator = EstimationFunction (options)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, estimator)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    val struct = record.get (CIMFunction.RESULT).asInstanceOf [JsonObject]
                    ret = RESTfulJSONResult (struct.getString ("status"), struct.getString ("message"), struct.getJsonObject ("result"))
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

        ret.toString
    }

}

object Estimation
{
    val LOGGER_NAME: String = Estimation.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
