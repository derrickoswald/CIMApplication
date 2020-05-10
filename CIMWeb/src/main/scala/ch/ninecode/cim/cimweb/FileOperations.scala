package ch.ninecode.cim.cimweb

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

import scala.collection.JavaConversions.iterableAsScalaIterable

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Get,Put,Delete the (XML) file from HDFS.
 */
@Stateless
@Path ("file/")
class FileOperations extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger (LOGGER_NAME)

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def getFile (
        @DefaultValue ("false") @MatrixParam ("debug") debug: String): Response =
        getFile ("/", "false", debug)

    @GET
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, "text/plain", "text/csv", "application/zip"))
    def getFile (
        @PathParam ("path") path: String,
        @DefaultValue ("false") @MatrixParam ("zip") zip: String,
        @DefaultValue ("false") @MatrixParam ("debug") debug: String): Response =
    {
        val file = if (path.startsWith ("/")) path else s"/$path"
        _Logger.info ("file get %s".format (file))
        val fetch = !file.endsWith ("/")
        val function: CIMWebFunction =
            if (fetch)
                GetFileFunction (file) // get the (XML) file from HDFS.
            else
                ListFilesFunction (file, asBoolean (debug)) // list the files in HDFS.
        val ret = new RESTfulJSONResult
        val response: Response = getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    val (spec, input) = getFunctionInput (function)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            // if not found use Response.Status.NOT_FOUND
                            if (fetch)
                            {
                                record.get (CIMFunction.RESULT) match
                                {
                                    case xml: String =>
                                        interaction.close ()
                                        if (xml.startsWith ("File does not exist:"))
                                            Response.status (Response.Status.NOT_FOUND).build
                                        else
                                            if (asBoolean (zip))
                                            {
                                                val bos = new ByteArrayOutputStream ()
                                                val zos = new ZipOutputStream (bos)
                                                zos.setLevel (9)
                                                val name = if (-1 == file.lastIndexOf ("/")) file else file.substring (file.lastIndexOf ("/") + 1)
                                                zos.putNextEntry (new ZipEntry (name))
                                                val data = xml.getBytes (StandardCharsets.UTF_8)
                                                zos.write (data, 0, data.length)
                                                zos.finish ()
                                                zos.close ()
                                                val zip = if (-1 == name.lastIndexOf (".")) name else s"${name.substring (0, name.lastIndexOf ("."))}.zip"
                                                Response.ok (bos.toByteArray, "application/zip")
                                                    .header ("content-disposition", "attachment; filename=%s".format (zip))
                                                    .build
                                            }
                                            else
                                            {
                                                val extension = file.substring (file.lastIndexOf (".") + 1)
                                                val media = extension match
                                                {
                                                    case "xml" => MediaType.APPLICATION_XML
                                                    case "rdf" => MediaType.APPLICATION_XML
                                                    case "json" => MediaType.APPLICATION_JSON
                                                    case "csv" => "text/csv"
                                                    case "glm" => "text/plain"
                                                    case "zip" => "application/zip"
                                                    case "txt" => "text/plain"
                                                    case "out" => "text/plain"
                                                    case _ => MediaType.APPLICATION_OCTET_STREAM
                                                }
                                                Response.ok (xml, media).build
                                            }
                                    case _ =>
                                        interaction.close ()
                                        Response.serverError ().entity ("GetFile interaction result is not a String").build
                                }
                            }
                            else
                            {
                                record.get (CIMFunction.RESULT) match
                                {
                                    case json: JsonObject =>
                                        interaction.close ()
                                        ret.setResult (json)
                                        if (json.containsKey ("error"))
                                        {
                                            ret.status = RESTfulJSONResult.FAIL
                                            ret.message = json.getString ("error")
                                            val result = Json.createObjectBuilder
                                            for (key <- json.keySet)
                                                if (key != "error")
                                                    result.add (key, json.get (key))
                                            ret.setResult (result.build)
                                        }
                                        Response.ok (ret.toString, MediaType.APPLICATION_JSON).build
                                    case _ =>
                                        interaction.close ()
                                        Response.serverError ().entity ("ListFiles result is not a JsonObject").build
                                }
                            }
                        case _ =>
                            interaction.close ()
                            Response.serverError ().entity ("interaction result is not a MappedRecord").build
                    }
                }
                catch
                {
                    case resourceexception: ResourceException =>
                        Response.serverError ().entity (s"ResourceException on interaction\n${resourceexception.getMessage}").build
                }
                finally
                    try
                        connection.close ()
                    catch
                    {
                        case resourceexception: ResourceException =>
                            val _ = Response.serverError ().entity (s"ResourceException on close\n${resourceexception.getMessage}").build
                    }
            case None =>
                Response.status (Response.Status.SERVICE_UNAVAILABLE).entity (s"could not get connection: ${ret.message}").build
        }
        response
    }

    @PUT
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def putFile (
        @PathParam ("path") path: String,
        @DefaultValue ("false") @MatrixParam ("unzip") unzip: String,
        data: Array[Byte]): String =
    {
        val file = if (path.startsWith ("/")) path else s"/$path"
        _Logger.info ("file put %s".format (file))
        val function = PutFileFunction (file, data, asBoolean (unzip))
        val ret = new RESTfulJSONResult
        getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    val (spec, input) = getFunctionInput (function)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get (CIMFunction.RESULT) match
                            {
                                case json: JsonObject =>
                                    ret.setResult (json)
                                    if (json.containsKey ("error"))
                                    {
                                        ret.status = RESTfulJSONResult.FAIL
                                        ret.message = json.getString ("error")
                                        val result = Json.createObjectBuilder
                                        for (key <- json.keySet)
                                            if (key != "error")
                                                result.add (key, json.get (key))
                                        ret.setResult (result.build)
                                    }
                                case _ =>
                                    ret.setResultException (new ResourceException ("PutFile interaction result is not a JsonObject"), "unhandled result")
                            }
                        case _ =>
                            ret.setResultException (new ResourceException ("interaction result is not a MappedRecord"), "unhandled result")
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
            case _ =>
                ret.setResultException (new ResourceException ("could not get a CIMConnection"), "configuration error")
        }

        ret.toString
    }

    @DELETE
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def deleteFile (@PathParam ("path") path: String): String =
    {
        val file = if (path.startsWith ("/")) path else s"/$path"
        _Logger.info ("file delete %s".format (file))
        val function = DeleteFileFunction (file)
        val ret = new RESTfulJSONResult
        getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    val (spec, input) = getFunctionInput (function)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get (CIMFunction.RESULT) match
                            {
                                case json: JsonObject =>
                                    ret.setResult (json)
                                    if (json.containsKey ("error"))
                                    {
                                        ret.status = RESTfulJSONResult.FAIL
                                        ret.message = json.getString ("error")
                                        val result = Json.createObjectBuilder
                                        for (key <- json.keySet)
                                            if (key != "error")
                                                result.add (key, json.get (key))
                                        ret.setResult (result.build)
                                    }
                                case _ =>
                                    ret.setResultException (new ResourceException ("DeleteFile interaction result is not a JsonObject"), "unhandled result")
                            }
                        case _ =>
                            ret.setResultException (new ResourceException ("interaction result is not a MappedRecord"), "unhandled result")
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
            case _ =>
                ret.setResultException (new ResourceException ("could not get a CIMConnection"), "configuration error")
        }

        ret.toString
    }
}
