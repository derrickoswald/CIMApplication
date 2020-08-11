package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.DefaultValue
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces

import scala.collection.JavaConverters.asScalaSetConverter

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Load the (RDF or CSV) file from HDFS into Spark.
 */
@Stateless
@Path ("load/")
class LoadFile extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger (LOGGER_NAME)

    def load (ret: RESTfulJSONResult, function: CIMWebFunction): Unit =
    {
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
                                    // if not found use Response.Status.NOT_FOUND
                                    if (json.containsKey ("error"))
                                    {
                                        ret.status = RESTfulJSONResult.FAIL
                                        ret.message = json.getString ("error")
                                        val result = Json.createObjectBuilder
                                        for (key <- json.keySet.asScala)
                                            if (key != "error")
                                                result.add (key, json.get (key))
                                        ret.setResult (result.build)
                                    }
                                    else
                                        ret.message = ""
                                case _ =>
                                    ret.setResultException (new ResourceException ("LoadFileFunction result is not a JsonObject"), "unhandled result")
                            }
                        case _ =>
                            ret.setResultException (new ResourceException ("LoadFileFunction interaction result is not a MappedRecord"), "unhandled interaction result")
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
    }

    @GET
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def getFile (
        @PathParam ("path") path: String,
        @DefaultValue ("MEMORY_AND_DISK_SER") @MatrixParam ("StorageLevel") storage: String,
        @DefaultValue ("false") @MatrixParam ("do_about") do_about: String,
        @DefaultValue ("false") @MatrixParam ("do_normalize") do_normalize: String,
        @DefaultValue ("false") @MatrixParam ("do_deduplication") do_deduplication: String,
        @DefaultValue ("false") @MatrixParam ("make_edges") make_edges: String,
        @DefaultValue ("false") @MatrixParam ("do_join") do_join: String,
        @DefaultValue ("false") @MatrixParam ("do_topo_islands") do_topo_islands: String,
        @DefaultValue ("false") @MatrixParam ("do_topo") do_topo: String,
        @DefaultValue ("Unforced") @MatrixParam ("force_retain_switches") force_retain_switches: String,
        @DefaultValue ("Unforced") @MatrixParam ("force_retain_fuses") force_retain_fuses: String,
        @DefaultValue ("Unforced") @MatrixParam ("force_switch_separate_islands") force_switch_separate_islands: String,
        @DefaultValue ("Unforced") @MatrixParam ("force_fuse_separate_islands") force_fuse_separate_islands: String,
        @DefaultValue ("false") @MatrixParam ("default_switch_open_state") default_switch_open_state: String,
        @DefaultValue ("false") @MatrixParam ("debug") debug: String,
        @DefaultValue ("") @MatrixParam ("cache") cache: String,
        @DefaultValue ("67108864") @MatrixParam ("split_maxsize") split_maxsize: String,
        @DefaultValue ("false") @MatrixParam ("header") header: String,
        @DefaultValue ("false") @MatrixParam ("ignoreLeadingWhiteSpace") ignoreLeadingWhiteSpace: String,
        @DefaultValue ("false") @MatrixParam ("ignoreTrailingWhiteSpace") ignoreTrailingWhiteSpace: String,
        @DefaultValue (",") @MatrixParam ("sep") sep: String,
        @DefaultValue ("\"") @MatrixParam ("quote") quote: String,
        @DefaultValue ("\\") @MatrixParam ("escape") escape: String,
        @DefaultValue ("UTF-8") @MatrixParam ("encoding") encoding: String,
        @DefaultValue ("#") @MatrixParam ("comment") comment: String,
        @DefaultValue ("") @MatrixParam ("nullValue") nullValue: String,
        @DefaultValue ("NaN") @MatrixParam ("nanValue") nanValue: String,
        @DefaultValue ("Inf") @MatrixParam ("positiveInf") positiveInf: String,
        @DefaultValue ("-Inf") @MatrixParam ("negativeInf") negativeInf: String,
        @DefaultValue ("yyyy-MM-dd") @MatrixParam ("dateFormat") dateFormat: String,
        @DefaultValue ("yyyy-MM-dd'T'HH:mm:ss.SSSXXX") @MatrixParam ("timestampFormat") timestampFormat: String,
        @DefaultValue ("PERMISSIVE") @MatrixParam ("mode") mode: String,
        @DefaultValue ("false") @MatrixParam ("inferSchema") inferSchema: String
    ): String =
    {
        val ret = new RESTfulJSONResult
        val files = path.split (',').map (f => if (f.startsWith ("/")) f else s"/$f")
        // based on the type execute load
        if (files (0).endsWith (".rdf") || files (0).endsWith (".xml"))
        {
            // see https://github.com/derrickoswald/CIMReader#reader-api
            val options = Map [String, String](
                "StorageLevel" -> storage,
                "ch.ninecode.cim.do_about" -> do_about,
                "ch.ninecode.cim.do_normalize" -> do_normalize,
                "ch.ninecode.cim.do_deduplication" -> do_deduplication,
                "ch.ninecode.cim.make_edges" -> make_edges,
                "ch.ninecode.cim.do_join" -> do_join,
                "ch.ninecode.cim.do_topo_islands" -> do_topo_islands,
                "ch.ninecode.cim.do_topo" -> do_topo,
                "ch.ninecode.cim.force_retain_switches" -> force_retain_switches,
                "ch.ninecode.cim.force_retain_fuses" -> force_retain_fuses,
                "ch.ninecode.cim.force_switch_separate_islands" -> force_switch_separate_islands,
                "ch.ninecode.cim.force_fuse_separate_islands" -> force_fuse_separate_islands,
                "ch.ninecode.cim.default_switch_open_state" -> default_switch_open_state,
                "ch.ninecode.cim.debug" -> debug,
                "ch.ninecode.cim.split_maxsize" -> split_maxsize,
                "ch.ninecode.cim.cache" -> cache
            )
            _Logger.info (s"load CIM ${files.mkString (",")} ${options.toString}")
            load (ret, LoadCIMFileFunction (files, Some (options)))
        }
        else
            if (files (0).endsWith (".csv"))
            {
                // see https://spark.apache.org/docs/2.4.5/api/scala/index.html#org.apache.spark.sql.DataFrameReader
                val options = Map [String, String](
                    "header" -> header,
                    "ignoreLeadingWhiteSpace" -> ignoreLeadingWhiteSpace,
                    "ignoreTrailingWhiteSpace" -> ignoreTrailingWhiteSpace,
                    "sep" -> sep,
                    "quote" -> quote,
                    "escape" -> escape,
                    "encoding" -> encoding,
                    "comment" -> comment,
                    "nullValue" -> nullValue,
                    "nanValue" -> nanValue,
                    "positiveInf" -> positiveInf,
                    "negativeInf" -> negativeInf,
                    "dateFormat" -> dateFormat,
                    "timestampFormat" -> timestampFormat,
                    "mode" -> mode,
                    "inferSchema" -> inferSchema
                )
                _Logger.info (s"load CSV ${files.mkString (",")} ${options.toString}")
                load (ret, LoadCSVFileFunction (files, options))
            }
            else
            {
                ret.setResultException (new ResourceException ("unrecognized file format (%s)".format (files (0))), "ResourceException on input")
            }

        ret.toString
    }
}
