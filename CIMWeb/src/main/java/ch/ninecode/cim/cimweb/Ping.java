package ch.ninecode.cim.cimweb;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.util.Date;
import java.util.logging.Logger;

@Path ("/ping")
public class Ping extends RESTful
{
    static protected String LOGGER_NAME = Ping.class.getName ();
    static protected Logger _Logger = Logger.getLogger (LOGGER_NAME); // , String resourceBundleName)

    @GET
    @Produces ({"application/json"})
    public String ping ()
    {
        final String date = new Date ().toString ();
        _Logger.info ("ping @ " + date);
        return (new RESTfulResult ("OK", date).toString ());
    }
}
