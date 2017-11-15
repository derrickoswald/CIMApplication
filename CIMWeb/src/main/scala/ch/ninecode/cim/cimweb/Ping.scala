package ch.ninecode.cim.cimweb

import java.util
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.Produces

import scala.collection.JavaConversions._

@Stateless
@Path ("/ping")
class Ping extends RESTful
{
    import Ping._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def ping (@DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
    {
        val date = new util.Date ().toString
        _Logger.info ("ping @ %s".format (date))
        val result = new RESTfulJSONResult (RESTfulJSONResult.OK, date)
        if (try { debug.toBoolean } catch { case _: Throwable => false })
        {
            val environment = Json.createObjectBuilder
            for (pair <- System.getenv)
                environment.add (pair._1, pair._2)
            val ret = Json.createObjectBuilder
            ret.add ("environment", environment)
            val properties = Json.createObjectBuilder
            for (property ← System.getProperties)
                properties.add (property._1, property._2)
            ret.add ("properties", properties)
            result.setResult (ret.build)
        }

        result.toString
    }
}

object Ping
{
    val LOGGER_NAME: String = Ping.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}