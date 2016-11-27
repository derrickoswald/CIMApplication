package ch.ninecode.cim.cimweb;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Resource;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;

public class RESTful
{
    protected static JsonWriterFactory FACTORY_INSTANCE;

    protected static final String OK = "OK";
    protected static final String FAIL = "FAIL";

//    @Resource
//    (
//        description = "Connection factory for Spark connection using CIMConnector",
//        lookup = "java:app/eis/SparkConnectionFactory", // "openejb:Resource/CIMConnector.rar"
//        authenticationType = Resource.AuthenticationType.APPLICATION
//    )
    protected CIMConnectionFactory _ConnectionFactory = null;

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

        public void setResult (String result)
        {
            try
            {
                _Result = Json.createReader (new StringReader (result)).readObject ();
            }
            catch (JsonParsingException jpe)
            {
                _Status = FAIL;
                _Message = jpe.getMessage ();
            }
            catch (JsonException je)
            {
                _Status = FAIL;
                _Message = je.getMessage ();
            }
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

    public static String asString (final JsonStructure data)
    {
        final StringWriter string = new StringWriter ();
        final JsonWriter writer = getPrettyJsonWriterFactory ().createWriter (string);
        writer.write (data);
        writer.close ();

        return (string.toString());
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
        String indent = "";
        for (int i = 0; i < depth; i++)
            indent += "    ";
        try
        {
            NamingEnumeration<NameClassPair> values = context.list (name);
            if (null != values)
                while (values.hasMore ())
                {
                    NameClassPair pair = values.next ();
                    out.append (indent);
                    out.append (pair.getName () + " : " + pair.getClassName ());
                    out.append ("\n");
                    print_context_r (out, context, name + "/" + pair.getName (), depth + 1);
                }
        }
        catch (NameNotFoundException nnfe)
        {
            out.append (indent);
            out.append ("NameNotFoundException " + name);
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
                    _ConnectionFactory = (CIMConnectionFactory) context.lookup ("java:/app/eis/SparkConnectionFactory"); // "openejb:Resource/CIMConnector.rar");
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
}
