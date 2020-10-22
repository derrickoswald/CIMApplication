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

@Stateless
@Path("/ping")
class Ping extends PingPong
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger(LOGGER_NAME)

    @GET
    @Produces(Array(MediaType.APPLICATION_JSON))
    def ping (@DefaultValue("false") @MatrixParam("debug") debug: String): String =
    {
        val verbose = asBoolean(debug)
        _Logger.info("ping (debug=%s)".format(verbose))
        val result = new RESTfulJSONResult(RESTfulJSONResult.OK, new util.Date().toString)
        if (verbose)
        {
            val ret = Json.createObjectBuilder
                .add("environment", getEnvironment)
                .add("properties", getProperties)
                .add("classpath", getClassPaths)
            result.setResult(ret.build)
        }

        result.toString
    }
}
