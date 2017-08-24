package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.json.JsonStructure;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.ws.rs.*;

import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;

/**
 * List the files in HDFS.
 */
@Stateless
@Path("list")
public class ListFiles extends RESTful
{
    static protected String LOGGER_NAME = ListFiles.class.getName ();
    static protected Logger _Logger = Logger.getLogger (LOGGER_NAME); // , String resourceBundleName)

    @GET
    @Produces ({"application/json"})
    public String listFiles (@DefaultValue("false") @MatrixParam ("debug") String debug)
    {
        return (listFiles ("", debug));
    }

    @SuppressWarnings ("unchecked")
    @GET @Path("{path}")
    @Produces ({"application/json"})
    public String listFiles (@PathParam("path") String path, @DefaultValue("false") @MatrixParam ("debug") String debug)
    {
        RESTfulResult ret = new RESTfulResult ();

        Connection connection = getConnection (ret);
        if (null != connection)
        {
            try
            {
                final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                spec.setFunctionName (CIMInteractionSpec.LIST_FILES);
                final MappedRecord input = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                input.setRecordShortDescription ("the directory path to list");
                input.put ("path", path.startsWith ("/") ? path : "/" + path);
                if (!debug.equals ("false"))
                    input.put ("debug", debug);
                final MappedRecord output = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                output.setRecordShortDescription ("the results of the list operation");
                final Interaction interaction = connection.createInteraction ();
                if (interaction.execute (spec, input, output))
                {
                    if (!output.isEmpty ())
                    {
                        Object result = output.get ("files");
                        if (result instanceof JsonStructure)
                            ret.setResult ((JsonStructure)result);
                        else
                            ret.setResult (result.toString ());
                    }
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
            catch (ResourceException resourceexception)
            {
                _Logger.severe ("interaction resource exception");
                StringWriter string = new StringWriter ();
                string.append ("ResourceException on interaction\n");
                PrintWriter writer = new PrintWriter (string);
                resourceexception.printStackTrace (writer);
                ret._Status = FAIL;
                ret._Message = string.toString ();
                writer.close ();
            }
            finally
            {
                try
                {
                    connection.close ();
                }
                catch (ResourceException resourceexception)
                {
                    _Logger.severe ("close resource exception");
                    StringWriter string = new StringWriter ();
                    string.append ("ResourceException on close\n");
                    PrintWriter writer = new PrintWriter (string);
                    resourceexception.printStackTrace (writer);
                    ret._Status = FAIL;
                    ret._Message = string.toString ();
                    writer.close ();
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
