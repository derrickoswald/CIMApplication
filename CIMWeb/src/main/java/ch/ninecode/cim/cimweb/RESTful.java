package ch.ninecode.cim.cimweb;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.MappedRecord;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMMappedRecord;

public class RESTful
{
    protected static JsonWriterFactory FACTORY_INSTANCE;

    protected static final String OK = "OK";
    protected static final String FAIL = "FAIL";

//import javax.annotation.Resource;
//    @Resource
//    (
//        description = "Connection factory for Spark connection using CIMConnector",
//        name = "CIMConnector.rar", // "java:app/eis/SparkConnectionFactory"
//        authenticationType = Resource.AuthenticationType.APPLICATION
//    )
    protected CIMConnectionFactory _ConnectionFactory = null;

    public RESTful ()
    {
        try
        {
            _ConnectionFactory = (CIMConnectionFactory) new InitialContext ().lookup("java:openejb/Resource/CIMConnector.rar");
        }
        catch (NamingException ne)
        {
            System.out.println ("fuck this JNDI shit");
        }
    }

    public class RESTfulResult
    {
        public String _Status;
        public String _Message;
        public JsonStructure _Result;

        public RESTfulResult ()
        {
            this (OK);
        }
        public RESTfulResult (String status)
        {
            this (status, "");
        }
        public RESTfulResult (String status, String message)
        {
            this (status, message, Json.createObjectBuilder ().build ());
        }
        public RESTfulResult (String status, String message, JsonStructure result)
        {
            _Status = status;
            _Message = message;
            _Result = result;
        }

        public void setResult (JsonStructure structure)
        {
            _Result = structure;
        }

        public void setResult (String result)
        {
            try
            {
                _Result = Json.createReader (new StringReader (result)).readObject ();
            }
            catch (JsonException je)
            {
                _Status = FAIL;
                _Message = je.getMessage ();
            }
        }

        public void setResultException (Exception e, String message)
        {
            StringWriter string = new StringWriter ();
            string.append (message);
            string.append ("\n");
            PrintWriter writer = new PrintWriter (string);
            e.printStackTrace (writer);
            writer.flush ();
            writer.close ();
            _Message = string.toString ();
            _Status = FAIL;
        }

        public String toString ()
        {
            final StringWriter string = new StringWriter ();
            final JsonWriter writer = getPrettyJsonWriterFactory ().createWriter (string);
            final JsonObject data = Json.createObjectBuilder ()
                .add ("status", _Status)
                .add ("message", _Message)
                .add ("result", _Result)
            .build ();
            writer.write (data);
            writer.close();

            return (string.toString ());
        }
    }

    protected static JsonWriterFactory getPrettyJsonWriterFactory ()
    {
        if (null == FACTORY_INSTANCE)
        {
            final Map<String, Object> properties = new HashMap<> (1);
            properties.put (JsonGenerator.PRETTY_PRINTING, true);
            FACTORY_INSTANCE = Json.createWriterFactory (properties);
        }
        return FACTORY_INSTANCE;
    }

    /**
     * Debugging for JNDI.
     */

    void print_context_r (StringBuffer out, Context context, String name, int depth)
    {
        StringBuilder s = new StringBuilder ();
        for (int i = 0; i < depth; i++)
            s.append ("    ");
        String indent = s.toString ();
        try
        {
            NamingEnumeration<NameClassPair> values = context.list (name);
            if (null != values)
                while (values.hasMore ())
                {
                    NameClassPair pair = values.next ();
                    out.append (indent);
                    out.append (pair.getName ());
                    out.append (" : ");
                    out.append (pair.getClassName ());
                    out.append ("\n");
                    print_context_r (out, context, name + "/" + pair.getName (), depth + 1);
                }
        }
        catch (NameNotFoundException nnfe)
        {
            out.append (indent);
            out.append ("NameNotFoundException ");
            out.append (name);
            out.append ("\n");
        }
        catch (NamingException ne)
        {
            if (!"Name is not bound to a Context".equals (ne.getMessage ()))
            {
                out.append (indent);
                out.append ("NamingException ");
                out.append (ne.getMessage ());
                out.append ("\n");
            }
        }
    }

    protected StringBuffer print_context (StringBuffer out, Context context, String name)
    {
        StringBuffer ret;

        if (null == out)
            ret = new StringBuffer ();
        else
            ret = out;
        ret.append (name);
        ret.append ("\n");
        print_context_r (ret, context, name, 1);

        return (ret);
    }

    protected CIMConnectionFactory getConnectionFactory ()
    {
        return (_ConnectionFactory);
    }

    protected Connection getConnection (RESTfulResult result)
    {
        StringBuffer out = new StringBuffer ();
        Connection connection = null;
        try
        {
            if (null == _ConnectionFactory)
            {
                out.append ("resource injection failed\ntrying alternate lookup:\n");
                final Properties properties = new Properties ();
//                properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");
//                properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*");

                try
                {
                    Context context = new InitialContext (properties);
                    print_context (out, context, "java:");
                    print_context (out, context, "openejb:");
                    _ConnectionFactory = (CIMConnectionFactory) context.lookup ("openejb:Resource/CIMConnector.rar");
                }
                catch (NameNotFoundException nnfe)
                {
                    out.append ("NameNotFoundException: ");
                    out.append (nnfe.getMessage ());
                    out.append ("\n");
                }
                catch (NamingException ne)
                {
                    out.append ("NamingException: ");
                    out.append (ne.getMessage ());
                    out.append ("\n");
                }
            }
            if (null != _ConnectionFactory)
            {
                CIMConnectionSpec specification = _ConnectionFactory.getDefaultConnectionSpec ();
                connection = _ConnectionFactory.getConnection (specification);
            }
        }
        catch (ResourceException re)
        {
            out.append ("ResourceException: ");
            out.append (re.getMessage ());
            out.append ("\n");
        }

        result._Message = out.toString ();
        return (connection);
    }

    protected MappedRecord getInputRecord (String description) throws ResourceException
    {
        MappedRecord ret;

        ret = getConnectionFactory ().getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        ret.setRecordShortDescription (description);

        return (ret);
    }
}
