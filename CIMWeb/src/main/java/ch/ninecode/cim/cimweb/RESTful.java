package ch.ninecode.cim.cimweb;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;

public class RESTful
{
    protected static JsonWriterFactory FACTORY_INSTANCE;

    protected static final String OK = "OK";
    protected static final String FAIL = "FAIL";

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
}
