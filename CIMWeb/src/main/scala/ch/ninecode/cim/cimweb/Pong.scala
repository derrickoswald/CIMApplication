package ch.ninecode.cim.cimweb

import java.net.URLClassLoader
import java.util
import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonStructure
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.Produces

import scala.collection.JavaConversions._
import ch.ninecode.cim.connector.CIMConnectionMetaData
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.cim.connector.CIMResourceAdapterMetaData

@Stateless
@Path ("/pong")
class Pong extends RESTful
{
    import Pong._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def pong (@DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
    {
        val verbose = try { debug.toBoolean } catch { case _: Throwable => false }
        _Logger.info ("pong (debug=%s)".format (verbose))

        val result = new RESTfulJSONResult (RESTfulJSONResult.OK, new util.Date ().toString)
        val ret = Json.createObjectBuilder

        if (verbose)
        {
            val environment = Json.createObjectBuilder
            for (pair ← System.getenv)
                environment.add (pair._1, pair._2)
            ret.add ("environment", environment)
            val properties = Json.createObjectBuilder
            for (property ← System.getProperties)
                properties.add (property._1, property._2)
            ret.add ("properties", properties)
            val classpath = Json.createArrayBuilder
            val classLoaders = new util.ArrayList[ClassLoader]
            classLoaders.add (ClassLoader.getSystemClassLoader)
            if (!classLoaders.contains (Thread.currentThread.getContextClassLoader))
                classLoaders.add (Thread.currentThread.getContextClassLoader)
            try
                throw new Exception
            catch
            {
                case exception: Exception ⇒
                    for (element: StackTraceElement <- exception.getStackTrace)
                        try
                        {
                            val classloader = Class.forName (element.getClassName).getClassLoader
                            if ((null != classloader) && !classLoaders.contains (classloader))
                                classLoaders.add (classloader)
                        }
                        catch
                        {
                            case oops: ClassNotFoundException ⇒
                        }
            }
            for (cl <- classLoaders)
                for (url <- cl.asInstanceOf[URLClassLoader].getURLs)
                    if ("file" == url.getProtocol)
                        classpath.add (url.getFile)
            ret.add ("classpath", classpath)
        }

        val factory = RESTful.getConnectionFactory () // ToDo: solve CDI (Contexts and Dependency Injection) problem and add debug output
        if (null != factory)
        {
            if (verbose)
            {
                // add the Resource Adapter metadata
                val metadata = Json.createObjectBuilder
                val meta: CIMResourceAdapterMetaData = factory.getMetaData.asInstanceOf[CIMResourceAdapterMetaData]
                if (null != meta)
                {
                    metadata.add ("name", meta.getAdapterName)
                    metadata.add ("description", meta.getAdapterShortDescription)
                    metadata.add ("vendor", meta.getAdapterVendorName)
                    metadata.add ("version", meta.getAdapterVersion)
                    metadata.add ("specification_version", meta.getSpecVersion)
                    metadata.add ("execute_with_input_and_output_records", meta.supportsExecuteWithInputAndOutputRecord)
                    metadata.add ("execute_with_input_record_only", meta.supportsExecuteWithInputRecordOnly)
                    metadata.add ("supports_local_transaction_demarcation", meta.supportsLocalTransactionDemarcation)
                    metadata.add ("interaction_specifications_supported", meta.getInteractionSpecsSupported.mkString (","))
                }
                ret.add ("resource_adapter_metadata", metadata)

                // add the Resource Adapter properties
                val resource_adapter_properties = Json.createObjectBuilder
                val resource_adapter = factory.getResourceAdapter
                if (null != resource_adapter)
                {
                    resource_adapter_properties.add ("YarnConfigurationPath", resource_adapter.getYarnConfigurationPath)
                    resource_adapter_properties.add ("SparkDriverMemory", resource_adapter.getSparkDriverMemory)
                    resource_adapter_properties.add ("SparkExecutorMemory", resource_adapter.getSparkExecutorMemory)
                }
                ret.add ("resource_adapter_properties", resource_adapter_properties)

                val default_connection_spec = Json.createObjectBuilder
                val connection_spec = factory.getDefaultConnectionSpec
                if (null != connection_spec)
                {
                    default_connection_spec.add ("username", connection_spec.getUserName)
                    default_connection_spec.add ("password", connection_spec.getPassword)
                    val properties = Json.createObjectBuilder
                    for (property <- connection_spec.getProperties)
                        properties.add (property._1, property._2)
                    default_connection_spec.add ("properties", properties)
                    val jars = Json.createArrayBuilder
                    for (jar <- connection_spec.getJars)
                        jars.add (jar)
                    default_connection_spec.add ("jars", jars)
                }
                ret.add ("default_connection_spec", default_connection_spec)
            }

            val connection = getConnection (result)  // ToDo: solve CDI (Contexts and Dependency Injection) problem and add debug output
            if (null != connection)
            {
                if (verbose)
                {
                    // add the Connection metadata
                    val metadata = Json.createObjectBuilder
                    val meta: CIMConnectionMetaData = connection.getMetaData.asInstanceOf[CIMConnectionMetaData]
                    if (null != meta)
                    {
                        metadata.add ("product", meta.getEISProductName)
                        metadata.add ("version", meta.getEISProductVersion)
                        metadata.add ("group", meta.getEISProductGroup)
                        metadata.add ("user", meta.getUserName)
                        metadata.add ("scala", meta.getScalaVersion)
                        metadata.add ("scalalibrary", meta.getScalaLibraryVersion)
                        metadata.add ("spark", meta.getSparkVersion)
                        metadata.add ("sparklibrary", meta.getSparkLibraryVersion)
                        metadata.add ("hadooplibrary", meta.getHadoopLibraryVersion)
                        metadata.add ("cimreader", meta.getCIMReaderVersion)
                    }
                    ret.add ("connection_metadata", metadata)
                }

                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val pong = PongFunction ()
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, pong)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.add ("spark_instance", record.get (CIMFunction.RESULT).asInstanceOf [JsonStructure])
                }
                interaction.close ()
            }
            else
            {
                result.status = RESTfulJSONResult.FAIL
                ret.add ("error", "could not get CIMConnection")
            }
        }
        else
        {
            result.status = RESTfulJSONResult.FAIL
            ret.add ("error", "could not get CIMConnectionFactory")
        }

        result.setResult (ret.build)
        result.toString
    }
}

object Pong
{
    val LOGGER_NAME: String = Pong.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
