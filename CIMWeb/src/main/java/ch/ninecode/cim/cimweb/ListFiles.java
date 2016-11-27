package ch.ninecode.cim.cimweb;

import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;

@Stateless
@Path("/list")
public class ListFiles extends RESTful
{
    static protected String LOGGER_NAME = ListFiles.class.getName ();
    static protected Logger _Logger = Logger.getLogger (LOGGER_NAME); // , String resourceBundleName)

    @GET
    @Produces ({"application/json"})
    public String listFiles ()
    {
        RESTfulResult ret = new RESTfulResult ();

        Connection connection = getConnection (ret);
        if (null != connection)
        {
            try
            {
                final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                spec.setFunctionName (CIMInteractionSpec.LIST_FILES);
                final MappedRecord output = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                output.setRecordShortDescription ("the results of the list operation");
                final Interaction interaction = connection.createInteraction ();
                if (interaction.execute (spec, null, output))
                {
                    if (!output.isEmpty ())
                        ret.setResult (output.get ("files").toString ());
                    else
                        ret._Message += "interaction returned empty";
                }
                else
                {
                    _Logger.severe ("interaction execution failed");
                    ret._Status = FAIL;
                    ret._Message += "interaction execution failed";
                }
                interaction.close ();
                connection.close ();
            }
            catch (ResourceException re)
            {
                _Logger.severe ("interaction resource exception");
                ret._Status = FAIL;
                ret._Message += re.getMessage ();
            }
            finally
            {
                try
                {
                    connection.close ();
                }
                catch (ResourceException re)
                {
                    _Logger.severe ("close resource exception");
                    ret._Status = FAIL;
                    ret._Message += re.getMessage ();
                }
            }
        }
        else
        {
            _Logger.severe ("_CIMConnectionFactory.getConnection() is null");
            ret._Status = FAIL;
            ret._Message += "getConnection failed";
        }

        return (ret.toString ());
    }
}
