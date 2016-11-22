package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;

import java.util.Properties;
import java.util.logging.Logger;

@Stateless
@Path("/list")
public class ListFiles extends RESTful
{
    static protected String LOGGER_NAME = ListFiles.class.getName ();
    static protected Logger _Logger = Logger.getLogger (LOGGER_NAME); // , String resourceBundleName)

    @Resource
    (
        description = "Connection factory for Spark connection using CIMConnector",
        lookup = "java:app/eis/SparkConnectionFactory",
        authenticationType = Resource.AuthenticationType.APPLICATION
    )
    CIMConnectionFactory _CIMConnectionFactory;

    /**
     * Build a connection specification.
     * @return
     */
    CIMConnectionSpec remoteConfig ()
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();
        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", "1g");
        ret.getProperties ().put ("spark.executor.memory", "4g");

        return (ret);
    }

    @GET
    @Produces ({"application/json"})
    public String listFiles ()
    {
        RESTfulResult ret = new RESTfulResult ();
        if (null == _CIMConnectionFactory)
        {
            _Logger.severe ("injection of java:app/eis/SparkConnectionFactory failed");
            try
            {
                Context context = new InitialContext (new Properties ());
                _CIMConnectionFactory = (CIMConnectionFactory) context.lookup ("openejb:Resource/CIMConnector.rar");
            }
            catch (NamingException ne)
            {
                _Logger.severe ("lookup of openejb:Resource/CIMConnector.rar failed");
                ret._Status = FAIL;
                ret._Message = ne.getMessage ();
            }
        }

        if (null != _CIMConnectionFactory)
        {
            Connection connection;
            try
            {
                connection = _CIMConnectionFactory.getConnection (remoteConfig ());
                if (null != connection)
                {
                    try
                    {
                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.LIST_FILES);
                        final MappedRecord output = _CIMConnectionFactory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
                        output.setRecordShortDescription ("the results of the list operation");
                        final Interaction interaction = connection.createInteraction ();
                        if (interaction.execute (spec, null, output))
                        {
                            if (!output.isEmpty ())
                                ret.setResult (output.get ("files").toString ());
                            else
                                ret._Message = "interaction returned empty";
                        }
                        else
                        {
                            _Logger.severe ("interaction execution failed");
                            ret._Status = FAIL;
                            ret._Message = "interaction execution failed";
                        }
                        interaction.close ();
                        connection.close ();
                    }
                    catch (ResourceException re)
                    {
                        _Logger.severe ("interaction resource exception");
                        ret._Status = FAIL;
                        ret._Message = re.getMessage ();
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
                            ret._Message = re.getMessage ();
                        }
                    }
                }
                else
                {
                    _Logger.severe ("_CIMConnectionFactory.getConnection() is null");
                    ret._Status = FAIL;
                    ret._Message = "getConnection failed";
                }

            }
            catch (ResourceException re)
            {
                _Logger.severe ("resource exception");
                ret._Status = FAIL;
                ret._Message = re.getMessage ();
            }
        }
        else
        {
            _Logger.severe ("_CIMConnectionFactory for CIMConnector is null");
            ret._Status = FAIL;
            ret._Message = "CIMConnector is null";
        }

        return (ret.toString ());
    }
}
