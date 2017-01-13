package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpec;
import ch.ninecode.cim.connector.CIMInteractionSpecImpl;
import ch.ninecode.cim.connector.CIMMappedRecord;
import ch.ninecode.cim.connector.CIMResultSet;

@Stateless
@Path("/EnergyConsumer")
public class EnergyConsumer
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

    /**
     * Build a connection specification used by all the tests.
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

    @SuppressWarnings ("unchecked")
    @GET
    @Produces({"text/plain", "application/json"})
    public String GetEnergyConsumers()
    {
        StringBuffer out = new StringBuffer ();
        if (null != factory)
        {
            Connection connection;
            try
            {
                connection = factory.getConnection (remoteConfig ());
                if (null != connection)
                {
                    try
                    {
                        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
                        spec.setFunctionName (CIMInteractionSpec.GET_DATAFRAME_FUNCTION);
                        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
                        input.setRecordShortDescription ("record containing the file name with key filename and sql query with key query");
                        input.put ("filename", "hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
                        input.put ("query", "select s.sup.sup.sup.sup.mRID mRID, s.sup.sup.sup.sup.aliasName aliasName, s.sup.sup.sup.sup.name name, s.sup.sup.sup.sup.description description, p.xPosition, p.yPosition from EnergyConsumer s, PositionPoint p where s.sup.sup.sup.Location = p.Location and p.sequenceNumber = 0");
                        final Interaction interaction = connection.createInteraction ();
                        final Record output = interaction.execute (spec, input);
                        if ((null == output) || !output.getClass ().isAssignableFrom (CIMResultSet.class))
                            throw new ResourceException ("object of class " + output.getClass ().toGenericString () + " is not a ResultSet");
                        else
                        {
                            CIMResultSet resultset = (CIMResultSet)output;
                            try
                            {
                                out.append ("{ \"type\": \"FeatureCollection\",\n\"features\": [");
                                while (resultset.next ())
                                {
                                    out.append ("\n{ \"type\": \"Feature\",\n" +
                                        "\"geometry\": {\"type\": \"Point\", \"coordinates\": [" + resultset.getDouble (5) + ", " + resultset.getDouble (6) + "]},\n" +
                                        "\"properties\": {" +
                                        "\"mRID\": \"" + resultset.getString (1) + "\", " +
                                        "\"aliasName\": \"" + resultset.getString (2) + "\", " +
                                        "\"name\": \"" + resultset.getString (3) + "\", " +
                                        "\"description\": \"" + resultset.getString (4) + "\"" +
                                            "}\n" +
                                        "},");
                                }
                                out.deleteCharAt (out.length () - 1); // get rid of trailing comma
                                out.append ("\n] }\n");
                                resultset.close ();
                            }
                            catch (SQLException sqlexception)
                            {
                                out.append ("SQLException on ResultSet");
                                out.append ("\n");
                                StringWriter string = new StringWriter ();
                                PrintWriter writer = new PrintWriter (string);
                                sqlexception.printStackTrace (writer);
                                out.append (string.toString ());
                                writer.close ();
                            }
                        }
                        interaction.close ();
                        connection.close ();
                    }
                    catch (ResourceException resourceexception)
                    {
                        out.append ("ResourceException on interaction");
                        out.append ("\n");
                        StringWriter string = new StringWriter ();
                        PrintWriter writer = new PrintWriter (string);
                        resourceexception.printStackTrace (writer);
                        out.append (string.toString ());
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
                            out.append ("ResourceException on close");
                            out.append ("\n");
                            StringWriter string = new StringWriter ();
                            PrintWriter writer = new PrintWriter (string);
                            resourceexception.printStackTrace (writer);
                            out.append (string.toString ());
                            writer.close ();
                        }
                    }
                }
            }
            catch (ResourceException exception)
            {
                out.append ("ResourceException");
                out.append ("\n");
                StringWriter string = new StringWriter ();
                PrintWriter writer = new PrintWriter (string);
                exception.printStackTrace (writer);
                out.append (string.toString ());
                writer.close ();
            }
        }
        return (out.toString ());
    }
}
