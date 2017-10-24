package ch.ninecode.cim.cimweb

import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonStructure
import javax.json.JsonWriterFactory
import javax.json.stream.JsonGenerator

case class RESTfulJSONResult (var status: String, var message: String, var result: JsonStructure)
{
    import RESTfulJSONResult._

    protected def getPrettyJsonWriterFactory: JsonWriterFactory =
    {
        if (null == FACTORY_INSTANCE)
        {
            val properties: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](1)
            properties.put (JsonGenerator.PRETTY_PRINTING, "true")
            FACTORY_INSTANCE = Json.createWriterFactory (properties)
        }
        FACTORY_INSTANCE
    }

    def this (status: String, message: String)
    {
        this (status, message, Json.createObjectBuilder.build)
    }

    def this (status: String)
    {
        this (status, "")
    }

    def this ()
    {
        this (RESTfulJSONResult.OK)
    }

    def setResult (structure: JsonStructure): Unit =
    {
        result = structure
    }

    def setResult (string: String): Unit =
    {
        try
            result = Json.createReader (new StringReader (string)).readObject
        catch
        {
            case je: JsonException ⇒
                status = FAIL
                message = je.getMessage
        }
    }

    def setResultException (e: Exception, msg: String): Unit =
    {
        val string = new StringWriter
        string.append (msg)
        string.append ("\n")
        val writer = new PrintWriter (string)
        e.printStackTrace (writer)
        writer.flush ()
        writer.close ()
        message = string.toString
        status = FAIL
    }

    def getJSON: JsonStructure =
    {
        Json.createObjectBuilder.add ("status", status).add ("message", message).add ("result", result).build
    }

    override def toString: String =
    {
        val string = new StringWriter
        val writer = getPrettyJsonWriterFactory.createWriter (string)
        val data = getJSON
        writer.write (data)
        writer.close ()
        string.toString
    }
}

object RESTfulJSONResult
{
    var FACTORY_INSTANCE: JsonWriterFactory = _
    val OK = "OK"
    val FAIL = "FAIL"
}