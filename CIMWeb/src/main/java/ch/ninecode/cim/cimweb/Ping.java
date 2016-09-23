package ch.ninecode.cim.cimweb;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.Date;

@Path("/ping")
public class Ping
{
    @GET
    public String ping ()
    {
        return "ping ok @ " + new Date ().toString ();
    }
}
