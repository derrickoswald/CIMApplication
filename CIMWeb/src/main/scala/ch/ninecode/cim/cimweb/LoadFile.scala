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
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMConnection
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

    def load (function: CIMWebFunction) (connection: CIMConnection): Response =
    {
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
                            val error = "error"
                            if (json.containsKey(error))
                            {
                                val result = Json.createObjectBuilder
                                for (key <- json.keySet.asScala)
                                    if (key != error)
                                        result.add(key, json.get(key))
                                val ret = RESTfulJSONResult(
                                    RESTfulJSONResult.FAIL,
                                    json.getString(error),
                                    result.build)
                                Response.serverError().entity (ret.toString).build
                            }
                            else
                            {
                                val ret = new RESTfulJSONResult
                                ret.setResult (json)
                                Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                            }
                        case _ =>
                            Response.serverError().entity (s"load function result is not a JsonObject").build
                    }
                case _ =>
                    Response.serverError().entity("interaction result is not a MappedRecord").build
            }
        }
        catch
        {
            case resourceexception: ResourceException =>
                Response.serverError().entity(s"ResourceException on interaction: ${resourceexception.getMessage}").build
        }
    }

    @GET
    @Path("load/{path:.*}")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def getFile (
        @PathParam("path") path: PathSegment,
    ): Response =
    {
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
                        case "id" =>           param._2.asScala.map (x => (                    param._1  , x))
                        case "StorageLevel" => param._2.asScala.map (x => (                    param._1  , x))
                        case _ =>              param._2.asScala.map (x => (s"ch.ninecode.cim.${param._1}", x))
                    }
                }
            ).toMap
            _Logger.info(s"load CIM ${files.mkString(",")} ${options.toString}")
            withConnection(load(LoadCIMFileFunction(files, Some(options))))
        }
        else
            if (files(0).endsWith(".csv"))
            {
                // see https://spark.apache.org/docs/2.4.5/api/scala/index.html#org.apache.spark.sql.DataFrameReader
                val options: Map[String, String] = path.getMatrixParameters.asScala.flatMap (
                    param => param._2.asScala.map (x => (param._1, x))).toMap
                _Logger.info(s"load CSV ${files.mkString(",")} ${options.toString}")
                withConnection(load (LoadCSVFileFunction(files, options)))
            }
            else
                Response.serverError().entity(s"unrecognized file format (${files(0)})").build

    }
}
