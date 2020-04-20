package ch.ninecode.cim.connector;

import java.io.File;
import java.util.Properties;
import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.json.JsonStructure;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionFactory;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;
import javax.resource.spi.ResourceAdapter;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * CIM Resource Adapter test suite
 */
@RunWith (Arquillian.class)
public class CIMResourceAdapterTest
{
    static
    {
        try
        {
            Enumeration<? extends ZipEntry> entries;
            ZipFile zipFile;

            zipFile = new ZipFile ("data/DemoData.zip");
            entries = zipFile.entries();

            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory())
                {
                    InputStream in = zipFile.getInputStream (entry);
                    OutputStream out = new BufferedOutputStream (new FileOutputStream ("data/" + entry.getName ()));
                    {
                        byte[] buffer = new byte[1024];
                        int len;

                        while((len = in.read(buffer)) >= 0)
                            out.write(buffer, 0, len);

                        in.close();
                        out.close();
                    }
                }
            }

            zipFile.close();

        }
        catch (IOException ioe)
        {
            System.err.println ("Unhandled exception: ");
            ioe.printStackTrace ();
        }
    }

    @Deployment
    public static ResourceAdapterArchive createDeployment ()
    {
        final ResourceAdapterArchive rar;

        // build a simplified rar
        final JavaArchive jar = ShrinkWrap.create (JavaArchive.class, "CIMConnector.jar");
        jar.addPackage (java.lang.Package.getPackage ("ch.ninecode.cim.connector"));

        rar = ShrinkWrap.create (ResourceAdapterArchive.class, "CIMConnector.rar");
        rar.addAsLibrary (jar);
        rar.addAsManifestResource (new File ("src/test/resources/META-INF/ra.xml"), "ra.xml");
        // you can examine the contents of the simplified rar using the following line
        // System.out.println (rar.toString (true));
        rar.as (ZipExporter.class).exportTo (new File ("target/CIMConnector.rar"), true);

        return (rar);
    }

    /**
     * Get the initial context for the CIMConnector.
     *
     * @return The initial JNDI naming context.
     */
    InitialContext getInitialContext () throws Exception
    {
        final Properties properties = new Properties ();
        properties.setProperty (Context.INITIAL_CONTEXT_FACTORY, "org.apache.openejb.client.LocalInitialContextFactory");
        properties.setProperty ("openejb.deployments.classpath.include", ".*resource-injection.*");
        final InitialContext context = new InitialContext (properties);
        assertNotNull ("context", context);

        return (context);
    }

    /**
     * Get the connection factory.
     *
     * @return The connection factory for the CIMConnector.
     */
    ConnectionFactory getConnectionFactory () throws Exception
    {
        final InitialContext context = getInitialContext ();

        return (getConnectionFactory (context));
    }

    /**
     * Get the connection factory.
     *
     * @param context The initial JNDI naming context.
     * @return The connection factory for the CIMConnector.
     */
    ConnectionFactory getConnectionFactory (InitialContext context) throws Exception
    {
        final ConnectionFactory factory = (ConnectionFactory) context.lookup ("java:openejb/Resource/SparkConnectionFactory"); // from id of connection-definition element in ra.xml
        assertNotNull ("connectionFactory", factory);

        return (factory);
    }

    /**
     * Build a connection specification used by all the tests.
     *
     * @return A configured connection specification.
     */
    CIMConnectionSpec remoteConfig () throws Exception
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();
        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", "1g");
        ret.getProperties ().put ("spark.executor.memory", "4g");

        return (ret);
    }

    /**
     * Get a connection to Spark.
     *
     * @return A connection to the Spark system.
     */
    Connection getConnection () throws Exception
    {
        final InitialContext context = getInitialContext ();
        final ConnectionFactory factory = getConnectionFactory (context);

        return (getConnection (factory));
    }

    /**
     * Get a connection to Spark.
     *
     * @param factory The connection factory for the CIMConnector.
     * @return A connection to the Spark system.
     */
    Connection getConnection (ConnectionFactory factory) throws Exception
    {
        final Connection connection = factory.getConnection (remoteConfig ());
        assertNotNull ("connection", connection);

        return (connection);
    }

    @Test
    public void testResourceAdapter () throws Exception
    {
        final Connection connection = getConnection ();
        connection.close ();
        final ResourceAdapter resourceAdapter = (ResourceAdapter) getInitialContext ().lookup ("java:openejb/Resource/CIMResourceAdapter"); // from id of resourceadapter element in ra.xml
        assertNotNull ("resourceAdapter", resourceAdapter);
        assertNotNull ("YarnConfigurationPath", ((CIMResourceAdapter) resourceAdapter).getYarnConfigurationPath ());
    }

    @Test
    public void testMetadata () throws Exception
    {
        final Connection connection = getConnection ();
        ConnectionMetaData meta = connection.getMetaData ();
        assertNotNull ("meta data", meta);
        assertEquals ("CIMConnector", meta.getEISProductName ());
        assertNotNull ("product version", meta.getEISProductVersion ()); // assertEquals ("1.6.0", meta.getEISProductVersion ());
        assertNotNull ("user name", meta.getUserName ()); // assertEquals ("derrick", meta.getUserName ());
        connection.close ();
    }

    protected class Loader implements CIMFunction
    {
        String _File;

        Loader (String file)
        {
            _File = file;
        }

        @Override
        public Return getReturnType () { return (Return.String); }

        @Override
        public String[] getJars () { return (new String[0]); }

        /**
         * Execute against this class returning a dataset.
         *
         * @param spark The Spark session to use.
         * @return The resultant data set, which will be turned into a ResultSet.
         */
        @Override
        public Dataset<Row> executeResultSet (SparkSession spark)
        {
            return null;
        }

        /**
         * Execute against this class returning a string.
         *
         * @param spark The Spark session to use.
         * @return The resultantsString.
         */
        @Override
        public String executeString (SparkSession spark)
        {
            Dataset<Row> elements = spark.read ().format ("ch.ninecode.cim").load (_File);
            return ("OK " + elements.count ());
        }

        /**
         * Execute against this class returning a JSON structure (JSONObject or JSONArray).
         *
         * @param spark The Spark session to use.
         * @return The resultant JSON.
         */
        @Override
        public JsonStructure executeJSON (SparkSession spark)
        {
            return null;
        }
    }

    @SuppressWarnings ("unchecked")
    @Test
    public void testReadEnergyConsumer () throws Exception
    {
        final ConnectionFactory factory = getConnectionFactory ();
        final Connection connection = getConnection (factory);

        // load a file
        final CIMInteractionSpecImpl spec1 = new CIMInteractionSpecImpl ();
        spec1.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION);
        final MappedRecord input1 = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        input1.setRecordShortDescription ("record containing the function to read a file");
        input1.put (CIMFunction.FUNCTION, new Loader ("data/DemoData.rdf"));
        final Interaction interaction1 = connection.createInteraction ();
        final Record output1 = interaction1.execute (spec1, input1);
        assertTrue ("CIMMappedRecord", output1.getClass ().isAssignableFrom (CIMMappedRecord.class));
        assertTrue ("OK", ((CIMMappedRecord)output1).get (CIMFunction.RESULT).toString ().startsWith ("OK"));
        interaction1.close ();

        // query for EnergyConsumer
        final CIMInteractionSpecImpl spec2 = new CIMInteractionSpecImpl ();
        spec2.setFunctionName (CIMInteractionSpec.GET_DATAFRAME_FUNCTION);
        final MappedRecord input2 = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        input2.setRecordShortDescription ("record containing the sql query with key query");
        input2.put (CIMFunction.QUERY, "select s.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mRID, s.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName aliasName, s.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name name, s.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description description, p.xPosition, p.yPosition from EnergyConsumer s, PositionPoint p where s.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.Location = p.Location and p.sequenceNumber = 1");
        final Interaction interaction2 = connection.createInteraction ();
        final Record output2 = interaction2.execute (spec2, input2);
        assertNotNull ("output", output2);
        assertTrue ("resultset", output2.getClass ().isAssignableFrom (CIMResultSet.class));
        CIMResultSet resultset = (CIMResultSet)output2;
        assertTrue ("resultset empty", resultset.next ());
        assertNotNull ("mRID", resultset.getString (1));
        assertNotSame ("zero x coordinate", "0.0", resultset.getString (5));
        assertNotSame ("zero y coordinate", "0.0", resultset.getString (6));
        resultset.close ();
        interaction2.close ();
        connection.close ();
    }
}
