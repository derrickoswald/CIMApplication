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

import scala.collection.JavaConverters.asScalaSetConverter

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Get,Put,Delete the file from HDFS.
 */
@Stateless
@Path("file/")
class FileOperations extends RESTful
{
    lazy val _Logger: Logger = Logger.getLogger(getClass.getName)

    def shipFile (file: String, xml: String): Response =
    {
        val extension = file.substring(file.lastIndexOf(".") + 1)
        val media = extension match
        {
            case "xml" => MediaType.APPLICATION_XML
            case "rdf" => MediaType.APPLICATION_XML
            case "json" => MediaType.APPLICATION_JSON
            case "csv" => "text/csv"
            case "glm" => MediaType.TEXT_PLAIN
            case "zip" => "application/zip"
            case "txt" => MediaType.TEXT_PLAIN
            case "out" => MediaType.TEXT_PLAIN
            case _ => MediaType.APPLICATION_OCTET_STREAM
        }
        Response.ok(xml, media).build
    }

    def shipZip (file: String, xml: String): Response =
    {
        val bos = new ByteArrayOutputStream()
        val zos = new ZipOutputStream(bos)
        zos.setLevel(9)
        val name = if (-1 == file.lastIndexOf("/")) file else file.substring(file.lastIndexOf("/") + 1)
        zos.putNextEntry(new ZipEntry(name))
        val data = xml.getBytes(StandardCharsets.UTF_8)
        zos.write(data, 0, data.length)
        zos.finish()
        zos.close()
        val zip = if (-1 == name.lastIndexOf(".")) name else s"${name.substring(0, name.lastIndexOf("."))}.zip"
        Response.ok(bos.toByteArray, "application/zip")
            .header("content-disposition", s"attachment; filename=$zip")
            .build
    }

    def shipJSON (json: JsonObject, ret: RESTfulJSONResult): Unit =
    {
        ret.setResult(json)
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
    }

    def processMappedRecord (file: String, fetch: Boolean, zipped: Boolean, record: CIMMappedRecord): Response =
    {
        if (fetch)
        {
            record.get(CIMFunction.RESULT) match
            {
                case xml: String =>
                    if (xml.startsWith("File does not exist:")) // if not found use Response.Status.NOT_FOUND
                        Response.status(Response.Status.NOT_FOUND).build
                    else
                        if (zipped)
                            shipZip(file, xml)
                        else
                            shipFile(file, xml)
                case _ =>
                    Response.serverError().entity("GetFile interaction result is not a String").build
            }
        }
        else
        {
            record.get(CIMFunction.RESULT) match
            {
                case json: JsonObject =>
                    val ret = new RESTfulJSONResult
                    shipJSON(json, ret)
                    Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                case _ =>
                    Response.serverError().entity("ListFiles result is not a JsonObject").build
            }
        }
    }

    def processGET (file: String, fetch: Boolean, zipped: Boolean, debug: Boolean) (connection: CIMConnection): Response =
    {
        val function: CIMWebFunction =
            if (fetch)
                GetFileFunction(file) // get the file from HDFS
            else
                ListFilesFunction(file, debug) // list the files in HDFS

        try
        {
            val (spec, input) = getFunctionInput(function)
            val interaction = connection.createInteraction
            val output = interaction.execute(spec, input)
            interaction.close()
            output match
            {
                case record: CIMMappedRecord =>
                    processMappedRecord(file, fetch, zipped, record)
                case _ =>
                    Response.serverError().entity("interaction result is not a MappedRecord").build
            }
        }
        catch
        {
            case resourceexception: ResourceException =>
                Response.serverError().entity(s"ResourceException on interaction\n${resourceexception.getMessage}").build
        }
    }

    def processPUT (function: PutFileFunction) (connection: CIMConnection): Response =
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
                            val ret = new RESTfulJSONResult()
                            shipJSON(json, ret)
                            Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                        case _ =>
                            Response.serverError().entity("PutFile interaction result is not a JsonObject").build
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

    def processDELETE (function: DeleteFileFunction) (connection: CIMConnection): Response =
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
                            val ret = new RESTfulJSONResult
                            shipJSON(json, ret)
                            Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                        case _ =>
                            Response.serverError().entity("PutFile interaction result is not a JsonObject").build
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
    @Produces(Array(MediaType.APPLICATION_JSON))
    def getFile (
        @DefaultValue("false") @MatrixParam("debug") debug: String): Response =
        getFile("/", "false", debug)

    @GET
    @Path("{path:[^;]*}")
    @Produces(Array(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN, "text/csv", "application/zip"))
    def getFile (
        @PathParam("path") path: String,
        @DefaultValue("false") @MatrixParam("zip") zip: String,
        @DefaultValue("false") @MatrixParam("debug") debug: String): Response =
    {
        val dbg = asBoolean(debug)
        val zipped = asBoolean(zip)
        val file = if (path.startsWith("/")) path else s"/$path"
        val fetch = !file.endsWith("/")
        _Logger.info(s"file get (${if (fetch) "fetch" else "list"}) $file")
        withConnection (processGET(file, fetch, zipped, dbg))
    }

    @PUT
    @Path("{path:[^;]*}")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def putFile (
        @PathParam("path") path: String,
        @DefaultValue("false") @MatrixParam("unzip") unzip: String,
        data: Array[Byte]): Response =
    {
        val file = if (path.startsWith("/")) path else s"/$path"
        _Logger.info(s"file put $file")
        val function = PutFileFunction(file, data, asBoolean(unzip))
        withConnection (processPUT (function))
    }

    @DELETE
    @Path("{path:[^;]*}")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def deleteFile (@PathParam("path") path: String): Response =
    {
        val file = if (path.startsWith("/")) path else s"/$path"
        _Logger.info(s"file delete $file")
        val function = DeleteFileFunction(file)
        withConnection (processDELETE(function))
    }
}
