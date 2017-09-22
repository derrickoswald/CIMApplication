package ch.ninecode.cim.cimweb

import java.util
import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.resource.cci.ConnectionMetaData
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.Produces

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMConnectionMetaData

@Stateless
@Path ("/pong")
class Pong extends RESTful
{
    import Pong._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def ping (@DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
    {
        val date = new util.Date ().toString
        _Logger.info ("pong @ %s".format (date))
        val result = new RESTfulJSONResult ("OK", date)
        val connection = getConnection (result)
        if (null != connection)
        {
            if (try { debug.toBoolean } catch { case _: Throwable => false })
            {
                val environment = Json.createObjectBuilder
                for (pair <- System.getenv)
                    environment.add (pair._1, pair._2)
                val ret = Json.createObjectBuilder
                ret.add ("environment", environment)

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
                    metadata.add ("sparklibrary", meta.getSparkLibraryVersion)
                    metadata.add ("hadooplibrary", meta.getHadoopLibraryVersion)
                    metadata.add ("spark", meta.getSparkVersion)
                }
                ret.add ("metadata", metadata)

                result.setResult (ret.build)
            }
        }

        result.toString
    }
}

object Pong
{
    val LOGGER_NAME: String = Pong.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
