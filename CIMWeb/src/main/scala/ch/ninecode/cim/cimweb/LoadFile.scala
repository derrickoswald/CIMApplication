package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter

import javax.ws.rs.core.PathSegment

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Load the (RDF or CSV) file from HDFS into Spark.
 */
@Stateless
@Path("/")
class LoadFile extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger(LOGGER_NAME)

    def load (ret: RESTfulJSONResult, function: CIMWebFunction): Unit =
    {
        getConnection(ret) match
        {
            case Some(connection) =>
                try
                {
                    val (spec, input) = getFunctionInput(function)
                    val interaction = connection.createInteraction
                    val output = interaction.execute(spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get(CIMFunction.RESULT) match
                            {
                                case json: JsonObject =>
                                    ret.setResult(json)
                                    // if not found use Response.Status.NOT_FOUND
                                    if (json.containsKey("error"))
                                    {
                                        ret.status = RESTfulJSONResult.FAIL
                                        ret.message = json.getString("error")
                                        val result = Json.createObjectBuilder
                                        for (key <- json.keySet.asScala)
                                            if (key != "error")
                                                result.add(key, json.get(key))
                                        ret.setResult(result.build)
                                    }
                                    else
                                        ret.message = ""
                                case _ =>
                                    ret.setResultException(new ResourceException("LoadFileFunction result is not a JsonObject"), "unhandled result")
                            }
                        case _ =>
                            ret.setResultException(new ResourceException("LoadFileFunction interaction result is not a MappedRecord"), "unhandled interaction result")
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
    }

    @GET
    @Path("load/{path:.*}")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def getFile (
        @PathParam("path") path: PathSegment,
    ): String =
    {
        val ret = new RESTfulJSONResult
        val files = path.getPath.split(',').map(f => if (f.startsWith("/")) f else s"/$f")
        // based on the type execute load
        if (files(0).endsWith(".rdf") || files(0).endsWith(".xml"))
        {
            // see https://github.com/derrickoswald/CIMSpark/tree/master/CIMReader#reader-api
            val options: Map[String, String] = path.getMatrixParameters.asScala.flatMap (
                param =>
                {
                    param._1 match
                    {
                        case "StorageLevel" => param._2.asScala.map (x => (                    param._1  , x))
                        case _ =>              param._2.asScala.map (x => (s"ch.ninecode.cim.${param._1}", x))
                    }
                }
            ).toMap
            _Logger.info(s"load CIM ${files.mkString(",")} ${options.toString}")
            load(ret, LoadCIMFileFunction(files, Some(options)))
        }
        else
            if (files(0).endsWith(".csv"))
            {
                // see https://spark.apache.org/docs/2.4.5/api/scala/index.html#org.apache.spark.sql.DataFrameReader
                val options: Map[String, String] = path.getMatrixParameters.asScala.flatMap (
                    param => param._2.asScala.map (x => (param._1, x))).toMap
                _Logger.info(s"load CSV ${files.mkString(",")} ${options.toString}")
                load(ret, LoadCSVFileFunction(files, options))
            }
            else
            {
                ret.setResultException(new ResourceException(s"unrecognized file format (${files(0)})"), "ResourceException on input")
            }

        ret.toString
    }
}
