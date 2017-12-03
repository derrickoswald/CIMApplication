package ch.ninecode.cim.cimweb

import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.resource.cci.Connection
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.DefaultValue
import javax.ws.rs.MatrixParam
import javax.ws.rs.core.Response

import scala.collection.JavaConverters._

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("gridlab/")
class GridLAB extends RESTful
{
    import GridLAB._

    // get a righteous string for a glm filename
    def base_name (simulation: String): String =
    {
        val index = simulation.lastIndexOf ("/")
        val suffix = if (simulation.endsWith (".json")) simulation.length - 5 else simulation.length
        if (-1 == index) simulation.substring (0, suffix) else simulation.substring (index + 1, suffix)
    }

    // get a righteous string for a glm filename
    def glm_name (simulation: String): String =
    {
        base_name (simulation) + ".glm"
    }

    // get a righteous string for a zip filename
    def zip_name (simulation: String): String =
    {
        base_name (simulation) + ".zip"
    }

    def create (connection: Connection, simulation: String): String =
    {
        val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
        spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
        val input = getInputRecord ("input record containing the function to run")
        // set up the function with parameters
        val gridlab = GridLABExportFunction (simulation)
        input.asInstanceOf[map].put (CIMFunction.FUNCTION, gridlab)
        val interaction = connection.createInteraction
        val output = interaction.execute (spec, input)
        if (null == output)
            throw new ResourceException ("null is not a MappedRecord")
        else
            if (!output.getClass.isAssignableFrom (classOf [CIMMappedRecord]))
                throw new ResourceException ("object of class %s is not a MappedRecord".format (output.getClass.toGenericString))
            else
            {
                val record = output.asInstanceOf [CIMMappedRecord]
                record.get (CIMFunction.RESULT).asInstanceOf [String]
            }
    }

    def get (connection: Connection, file: String): String =
    {
        val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
        spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
        val input = getInputRecord ("input record containing the function to run")
        val function = GetFileFunction (file)
        input.asInstanceOf[map].put (CIMFunction.FUNCTION, function)
        val interaction = connection.createInteraction
        val output = interaction.execute (spec, input)
        val record = output.asInstanceOf [CIMMappedRecord]
        val xml = record.get (CIMFunction.RESULT).asInstanceOf [String]
        interaction.close ()
        if (xml.startsWith ("File does not exist:"))
            null
        else
            xml
    }

    case class GatherOptions (
        sim: Boolean, // include simulation
        cim: Boolean, // include CIM file
        in: Boolean,  // include input files
        out: Boolean, // include output files
        all: Boolean  // include everything
    )
    {
        def gather: Boolean = sim || cim || in || out || all
    }

    def gather (connection: Connection, simulation: String, options: GatherOptions): Array[Byte] =
    {
        // get the simulation json file
        val json = get (connection, simulation)
        if (null != json)
            // parse it
            try
                Json.createReader (new StringReader (json)).readObject match
                {
                    case details: JsonObject ⇒

                        // get the simulation root directory
                        val sim = if (-1 == simulation.lastIndexOf ("/")) simulation else simulation.substring (simulation.lastIndexOf ("/") + 1)
                        val sim_root = simulation.substring (0, simulation.length - sim.length)

                        // get the gridlab root directory
                        val glm_file = details.getString ("glm")
                        if (null == glm_file)
                            return Array ()
                        val last = glm_file.lastIndexOf ("/")
                        val (root, name) = if (-1 == last)
                            (simulation.substring (0, simulation.length - sim.length), glm_file) // assumed relative to simulation file
                        else
                        {
                            val name = glm_file.substring (last + 1)
                            val second_last = glm_file.lastIndexOf ("/", last - 1)
                            if (-1 == second_last)
                                ("", name)
                            else
                                (glm_file.substring (second_last + 1, glm_file.length - name.length), name)
                        }

                        // build the zip file
                        val bos = new ByteArrayOutputStream ()
                        val zos = new ZipOutputStream (bos)
                        zos.setLevel (9)

                        // store the glm
                        val glm = get (connection, sim_root + root + name)
                        if (null == glm)
                            return Array ()
                        else
                        {
                            zos.putNextEntry (new ZipEntry (root + name))
                            val data = glm.getBytes (StandardCharsets.UTF_8)
                            zos.write (data, 0, data.length)
                        }

                        // store the simulation
                        if (options.sim || options.all)
                        {
                            zos.putNextEntry (new ZipEntry (root + sim))
                            val data = json.getBytes (StandardCharsets.UTF_8)
                            zos.write (data, 0, data.length)
                        }

                        // store the CIM file
                        val cim = details.getString ("cim")
                        if ((null != cim ) && (options.cim || options.all))
                        {
                            val guts = get (connection, cim)
                            if (null != guts)
                            {
                                val name = if (-1 == cim.lastIndexOf ("/")) cim else cim.substring (cim.lastIndexOf ("/") + 1)
                                zos.putNextEntry (new ZipEntry (root + name))
                                val data = guts.getBytes (StandardCharsets.UTF_8)
                                zos.write (data, 0, data.length)
                            }
                        }

                        var dirs: Array[String] = Array()

                        // store the players
                        val players = details.getJsonArray ("players")
                        if (null != players)
                        {
                            for (element: JsonObject ← players.getValuesAs (classOf[JsonObject]).asScala) // ToDo: more robust checking
                            {
                                val player_file = element.getString ("file", "")
                                val player_name = if (-1 == player_file.lastIndexOf ("/")) player_file else player_file.substring (player_file.lastIndexOf ("/") + 1)
                                if (options.in || options.all)
                                {
                                    val player = get (connection, sim_root + root + player_file)
                                    if (null != player)
                                    {
                                        zos.putNextEntry (new ZipEntry (root + player_file))
                                        val data = player.getBytes (StandardCharsets.UTF_8)
                                        zos.write (data, 0, data.length)
                                    }
                                }
                                else
                                {
                                    val dir = player_file.substring (0, player_file.length - player_name.length)
                                    if (dir != "" && !dirs.contains (dir))
                                    {
                                        zos.putNextEntry (new ZipEntry (root + dir))
                                        dirs = dirs :+ dir
                                    }
                                }
                            }
                        }

                        // store the recorders
                        val recorders = details.getJsonArray ("recorders")
                        if (null != recorders)
                        {
                            for (element: JsonObject ← recorders.getValuesAs (classOf[JsonObject]).asScala) // ToDo: more robust checking
                            {
                                val recorder_file = element.getString ("file", "")
                                val recorder_name = if (-1 == recorder_file.lastIndexOf ("/")) recorder_file else recorder_file.substring (recorder_file.lastIndexOf ("/") + 1)
                                if (options.out || options.all)
                                {
                                    val recorder = get (connection, sim_root + root + recorder_file)
                                    if (null != recorder)
                                    {
                                        zos.putNextEntry (new ZipEntry (root + recorder_file))
                                        val data = recorder.getBytes (StandardCharsets.UTF_8)
                                        zos.write (data, 0, data.length)
                                    }
                                }
                                else
                                {
                                    val dir = recorder_file.substring (0, recorder_file.length - recorder_name.length)
                                    if (dir != "" && !dirs.contains (dir))
                                    {
                                        zos.putNextEntry (new ZipEntry (root + dir))
                                        dirs = dirs :+ dir
                                    }
                                }
                            }
                        }

                        // seal the zip file
                        zos.finish ()
                        zos.close ()
                        bos.toByteArray
                    case _ ⇒ Array()
                }
            catch
            {
                case je: JsonException ⇒ Array()
            }
        else
            Array()
    }

    @GET
    @Path ("{simulation:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON, "application/zip"))
    def export (
        @PathParam ("simulation") simulation: String, // the name of the JSON simulation file on HDFS
        @DefaultValue ("false") @MatrixParam ("sim") sim: String,
        @DefaultValue ("false") @MatrixParam ("cim") cim: String,
        @DefaultValue ("false") @MatrixParam ("in") in: String,
        @DefaultValue ("false") @MatrixParam ("out") out: String,
        @DefaultValue ("false") @MatrixParam ("all") all: String
    ): Response =
    {
        _Logger.info ("gridlab export %s;all=%s".format (simulation, all))
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        val response: Response = if (null != connection)
            try
            {
                val sim = if (simulation.startsWith ("/")) simulation else "/" + simulation
                val options = GatherOptions (
                    sim = try { sim.toBoolean } catch { case _: Throwable => false },
                    cim = try { cim.toBoolean } catch { case _: Throwable => false },
                    in = try { in.toBoolean } catch { case _: Throwable => false },
                    out = try { out.toBoolean } catch { case _: Throwable => false },
                    all = try { all.toBoolean } catch { case _: Throwable => false })
                if (options.gather)
                {
                    val zip = gather (connection, sim, options)
                    if (zip.length > 0)
                        Response.ok (zip, "application/zip")
                            .header ("content-disposition", "attachment; filename=%s".format (zip_name (simulation)))
                            .build
                    else
                        Response.serverError ().entity ("zip construction failed").build
                }
                else
                {
                    val glm = create (connection, sim)
                    Response.ok (glm, MediaType.APPLICATION_OCTET_STREAM)
                        .header ("content-disposition", "attachment; filename=%s".format (glm_name (simulation)))
                        .build
                }
            }
            catch
            {
                case resourceexception: ResourceException =>
                    ret.setResultException (resourceexception, "ResourceException on interaction")
                    Response.serverError ().entity (ret.message).build
            }
            finally
                try
                    connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException (resourceexception, "ResourceException on close")
                        Response.serverError ().entity (ret.message).build
                }
        else
            Response.status (Response.Status.SERVICE_UNAVAILABLE).entity ("could not get connection").build

        response
    }

    @POST
    @Path ("{simulation:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def simulate (
        @PathParam ("simulation") simulation: String // the name of the JSON simulation file on HDFS
    ): String =
    {
        _Logger.info ("gridlab simulate %s".format (simulation))
        var ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                val simfile = if (simulation.startsWith ("/")) simulation else "/" + simulation
                val gridlab = GridLABSimulateFunction (simfile)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, gridlab)
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

object GridLAB
{
    val LOGGER_NAME: String = GridLAB.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
