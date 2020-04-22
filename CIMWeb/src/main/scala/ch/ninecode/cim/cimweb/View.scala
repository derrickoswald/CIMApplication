package ch.ninecode.cim.cimweb

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ejb.Stateless
import javax.resource.ResourceException
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.DefaultValue
import javax.ws.rs.MatrixParam
import javax.ws.rs.PathParam
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("view/")
class View extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger (LOGGER_NAME)

    /**
     * Read CIM features from Spark.
     *
     * @param about md:FullModel rdf:about contents
     * @param zip if <code>true</code> returns the RDF file as a zip compressed file
     * @param xmin minimum longitude
     * @param ymin minimum latitude
     * @param xmax maximum longitude
     * @param ymax maximum latitude
     * @param reduceLines if <code>true</code> restricts the number of ACLineSegment to <code>maxLines</code>
     * @param maxLines the maximum number of ACLineSegments if <code>reduceLines</code> is <code>true</code>
     * @param dougPeuk if <code>true</code> applies the Ramer–Douglas–Peucker algorithm to reduce the number of PositionPoint verticies in ACLineSegment geometries
     * @param dougPeukFactor smoothing factor for the Ramer–Douglas–Peucker algorithm
     * @param resolution distance factor for the Ramer–Douglas–Peucker algorithm
     * (the epsilon parameter in the Ramer–Douglas–Peucker algorithm is epsilon = 5 * dougPeukFactor * resolution)
     * @return the result set as a CIM RDF XML file
     */
    @GET
    @Path ("{about:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_XML, "application/zip"))
    def Read (
        @PathParam ("about") about: String, // the about string if any
        @DefaultValue ("false") @MatrixParam ("zip") zip: String,
        @DefaultValue ("true") @MatrixParam ("all") all: String,
        @DefaultValue ("7.71") @MatrixParam ("xmin") xmin: String,
        @DefaultValue ("46.57") @MatrixParam ("ymin") ymin: String,
        @DefaultValue ("7.73") @MatrixParam ("xmax") xmax: String,
        @DefaultValue ("46.60") @MatrixParam ("ymax") ymax: String,
        @DefaultValue ("true") @MatrixParam ("reduceLines") reduceLines: String,
        @DefaultValue ("2000") @MatrixParam ("maxLines") maxLines: String,
        @DefaultValue ("true") @MatrixParam ("dougPeuk") dougPeuk: String,
        @DefaultValue ("2.0") @MatrixParam ("dougPeukFactor") dougPeukFactor: String,
        @DefaultValue ("1.0e-4") @MatrixParam ("resolution") resolution: String): Response =
    {
        val ret = new RESTfulJSONResult
        val response: Response = getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    val everything = try { all.toBoolean } catch { case _: Throwable => true }
                    val reduce = try { reduceLines.toBoolean } catch { case _: Throwable => false }
                    val doug = try { dougPeuk.toBoolean } catch { case _: Throwable => false }
                    _Logger.info ("View (\"%s\",all=%s [%g,%g],[%g,%g],reduce=%s,maxLines=%d,dougPeuk=%s,dougPeukFactor=%g,resolution=%g)".format (about, all, xmin.toDouble, ymin.toDouble, xmax.toDouble, ymax.toDouble, reduce, maxLines.toInt, doug, dougPeukFactor.toDouble, resolution.toDouble))
                    val function = ViewFunction (about, everything, xmin.toDouble, ymin.toDouble, xmax.toDouble, ymax.toDouble, reduce, maxLines.toInt, doug, dougPeukFactor.toDouble, resolution.toDouble)
                    val (spec, input) = getFunctionInput (function)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    if (null == output)
                    {
                        interaction.close ()
                        Response.serverError ().entity ("null is not a MappedRecord").build
                    }
                    else
                    {
                        val record = output.asInstanceOf[CIMMappedRecord]
                        val rdf = record.get (CIMFunction.RESULT).asInstanceOf[String]
                        interaction.close ()
                        if (try { zip.toBoolean } catch { case _: Throwable => false })
                        {
                            val bos = new ByteArrayOutputStream ()
                            val zos = new ZipOutputStream (bos)
                            zos.setLevel (9)
                            val name = "view.rdf"
                            zos.putNextEntry (new ZipEntry (name))
                            val data = rdf.getBytes (StandardCharsets.UTF_8)
                            zos.write (data, 0, data.length)
                            zos.finish ()
                            zos.close ()
                            val zip = "view.zip"
                            interaction.close ()
                            Response.ok (bos.toByteArray, "application/zip")
                                .header ("content-disposition", "attachment; filename=%s".format (zip))
                                .build
                        }
                        else
                            Response.ok (rdf, MediaType.APPLICATION_XML).build
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
                            Response.serverError ().entity (s"ResourceException on close\n${resourceexception.getMessage}").build
                    }
            case None =>
                Response.status (Response.Status.SERVICE_UNAVAILABLE).entity (s"could not get connection: ${ret.message}").build
        }

        response
    }
}
