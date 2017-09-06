package ch.ninecode.cim.cimweb

import javax.ejb._
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces

import java.util.Date
import java.util.logging.Logger

@Stateless
@Path ("/pong")
class Pong extends RESTful
{
    import Pong._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def pong (): String =
    {
        val date = new Date ().toString ()
        _Logger.info ("pong @ %s".format (date))
        return (new RESTfulResult ("OK", date).toString ())
    }
}

object Pong
{
    val LOGGER_NAME = Pong.getClass.getName ()
    val _Logger = Logger.getLogger (LOGGER_NAME)
}