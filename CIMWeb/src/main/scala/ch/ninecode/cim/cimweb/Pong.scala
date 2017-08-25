package ch.ninecode.cim.cimweb

import javax.ejb._
import javax.persistence._
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces

import java.util.Date

@Stateless
@Path ("/pong")
class Pong extends RESTful
{
    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def pong (): String =
    {
        val date = new Date ().toString ()
        return (new RESTfulResult ("OK", date).toString ())
    }
}