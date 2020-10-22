package ch.ninecode.cim.cimweb

import java.util
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonStructure
import javax.json.JsonObjectBuilder
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.Produces

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter

import ch.ninecode.cim.connector.CIMConnectionMetaData
import ch.ninecode.cim.connector.CIMConnectionSpec
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.cim.connector.CIMResourceAdapter
import ch.ninecode.cim.connector.CIMResourceAdapterMetaData

@Stateless
@Path("/pong")
class Pong extends PingPong
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger(LOGGER_NAME)

    def getMetaData (meta: CIMResourceAdapterMetaData): JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("name", meta.getAdapterName)
            .add("description", meta.getAdapterShortDescription)
            .add("vendor", meta.getAdapterVendorName)
            .add("version", meta.getAdapterVersion)
            .add("specification_version", meta.getSpecVersion)
            .add("execute_with_input_and_output_records", meta.supportsExecuteWithInputAndOutputRecord)
            .add("execute_with_input_record_only", meta.supportsExecuteWithInputRecordOnly)
            .add("supports_local_transaction_demarcation", meta.supportsLocalTransactionDemarcation)
            .add("interaction_specifications_supported", meta.getInteractionSpecsSupported.mkString(","))
    }

    def getResourceAdapterProperties (resource_adapter: CIMResourceAdapter): JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("YarnConfigurationPath", resource_adapter.getYarnConfigurationPath)
            .add("SparkDriverMemory", resource_adapter.getSparkDriverMemory)
            .add("SparkExecutorMemory", resource_adapter.getSparkExecutorMemory)
    }

    def getConnectionmetaData (meta: CIMConnectionMetaData): JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("product", meta.getEISProductName)
            .add("version", meta.getEISProductVersion)
            .add("group", meta.getEISProductGroup)
            .add("user", meta.getUserName)
            .add("scala", meta.getScalaVersion)
            .add("scalalibrary", meta.getScalaLibraryVersion)
            .add("spark", meta.getSparkVersion)
            .add("sparklibrary", meta.getSparkLibraryVersion)
            .add("hadooplibrary", meta.getHadoopLibraryVersion)
            .add("cimreader", meta.getCIMReaderVersion)
    }

    @GET
    @Produces(Array(MediaType.APPLICATION_JSON))
    def pong (@DefaultValue("false") @MatrixParam("debug") debug: String): String =
    {
        val verbose = asBoolean(debug)
        _Logger.info("pong (debug=%s)".format(verbose))

        val result = new RESTfulJSONResult(RESTfulJSONResult.OK, new util.Date().toString)
        val ret = Json.createObjectBuilder

        if (verbose)
        {
            val _ = ret.add("environment", getEnvironment)
                .add("properties", getProperties)
                .add("classpath", getClassPaths)
        }

        val out = if (verbose) Some(new StringBuffer) else None
        val factory = RESTful.getConnectionFactory(out) // ToDo: solve CDI (Contexts and Dependency Injection) problem and add debug output
        if (null != factory)
        {
            val _ = if (verbose)
            {
                // add the Resource Adapter metadata
                factory.getMetaData match
                {
                    case meta: CIMResourceAdapterMetaData =>
                        val _ = ret.add("resource_adapter_metadata", getMetaData(meta))
                }

                // add the Resource Adapter properties
                factory.getResourceAdapter match
                {
                    case resource_adapter: CIMResourceAdapter =>
                        val _ = ret.add("resource_adapter_properties", getResourceAdapterProperties(resource_adapter))
                }

                factory.getDefaultConnectionSpec match
                {
                    case connection_spec: CIMConnectionSpec =>
                        val properties = connection_spec.getProperties.asScala.foldLeft(Json.createObjectBuilder)((b, p) => b.add(p._1, p._2))
                        val jars = connection_spec.getJars.asScala.foldLeft(Json.createArrayBuilder)((b, j) => b.add(j))
                        val default_connection_spec = Json.createObjectBuilder
                            .add("username", connection_spec.getUserName)
                            .add("password", connection_spec.getPassword)
                            .add("properties", properties)
                            .add("jars", jars)
                        val _ = ret.add("default_connection_spec", default_connection_spec)
                }
            }

            getConnection(result) match // ToDo: solve CDI (Contexts and Dependency Injection) problem and add debug output
            {
                case Some(connection) =>
                    if (verbose)
                    {
                        // add the Connection metadata
                        connection.getMetaData match
                        {
                            case meta: CIMConnectionMetaData =>
                                val _ = ret.add("connection_metadata", getConnectionmetaData(meta))
                        }
                    }

                    val (spec, input) = getFunctionInput(PongFunction())
                    val interaction = connection.createInteraction
                    val output = interaction.execute(spec, input)
                    val _ = output match
                    {
                        case record: CIMMappedRecord =>
                            record.get(CIMFunction.RESULT) match
                            {
                                case json: JsonStructure =>
                                    ret.add("spark_instance", json)
                                case _ =>
                                    result.setResultException(new ResourceException("Pong result is not a JsonStructure"), "unhandled result")
                            }
                        case _ =>
                            result.setResultException(new ResourceException("Pong interaction result is not a MappedRecord"), "unhandled interaction result")
                    }
                    interaction.close()
                case None =>
                    result.status = RESTfulJSONResult.FAIL
                    val _ = ret.add("error", "could not get CIMConnection")
            }
        }
        else
        {
            result.status = RESTfulJSONResult.FAIL
            val r = ret.add("error", "could not get CIMConnectionFactory")
            val _ = out.map(sb => r.add("tried", sb.toString))
        }

        result.setResult(ret.build)
        result.toString
    }
}
