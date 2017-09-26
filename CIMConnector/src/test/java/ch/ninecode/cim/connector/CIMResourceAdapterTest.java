package ch.ninecode.cim.connector;

import java.io.File;
import java.util.Properties;
import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionFactory;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;
import javax.resource.spi.ResourceAdapter;

import org.apache.spark.sql.SparkSession;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CIM Resource Adapter test suite
 */
@RunWith (Arquillian.class)
public class CIMResourceAdapterTest
{
    static boolean USE_LOCAL = true;
    static
    {
        if (USE_LOCAL)
            try
            {
                Enumeration entries;
                ZipFile zipFile;

                zipFile = new ZipFile ("src/test/data/NIS_CIM_Export_NS_INITIAL_FILL.zip");
                entries = zipFile.entries();

                while (entries.hasMoreElements())
                {
                    ZipEntry entry = (ZipEntry)entries.nextElement();
                    if (!entry.isDirectory())
                    {
                        InputStream in = zipFile.getInputStream (entry);
                        OutputStream out = new BufferedOutputStream (new FileOutputStream ("src/test/data/" + entry.getName ()));
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

    @SuppressWarnings ("unused")
    @Deployment
    public static ResourceAdapterArchive createDeployment ()
    {
        final ResourceAdapterArchive rar;

        // Note:
        // You can either build a simplified rar, or use the maven created rar.
        // The latter depends on a successful:
        //    mvn clean install
        // and a running spark master at spark://sandbox:7077 with existing hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf

        if (USE_LOCAL)
        {
            // build a simplified rar
            final JavaArchive jar = ShrinkWrap.create (JavaArchive.class, "CIMConnector.jar");
            jar.addPackage (java.lang.Package.getPackage ("ch.ninecode.cim.connector"));

            rar = ShrinkWrap.create (ResourceAdapterArchive.class, "CIMConnector.rar");
            rar.addAsLibrary (jar);
            rar.addAsManifestResource (new File ("src/test/resources/META-INF/ra.xml"), "ra.xml");
            // you can examine the contents of the simplified rar using the following line
            // System.out.println (rar.toString (true));
            rar.as (ZipExporter.class).exportTo (new File ("target/CIMConnector.rar"), true);
        }
        else
            // use the rar generated by maven
            rar = ShrinkWrap
                .create (ZipImporter.class, "CIMConnector.rar")
                .importFrom (new File("target/CIMConnector-2.3.1.rar"))
                .as (ResourceAdapterArchive.class);

        return rar;
    }

    /**
     * Get the initial context for the CIMConnector.
     *
     * @return The initial JNDI naming context.
     */
    protected InitialContext getInitialContext () throws Exception
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
    protected ConnectionFactory getConnectionFactory () throws Exception
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
    protected ConnectionFactory getConnectionFactory (InitialContext context) throws Exception
    {
        final ConnectionFactory factory = (ConnectionFactory) context.lookup ("java:openejb/Resource/CIMConnector"); // from id of connector element in ra.xml
        assertNotNull ("connectionFactory", factory);

        return (factory);
    }

    /**
     * Build a connection specification used by all the tests.
     *
     * @return A configured connection specification.
     */
    protected CIMConnectionSpec remoteConfig () throws Exception
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();
        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", "1g");
        ret.getProperties ().put ("spark.executor.memory", "4g");
        // the CIMReader jar is included as a maven reference if you don't have the CIMReader project open when testing
        // ret.getJars ().add ("../../CIMReader/target/CIMReader-2.11-2.2.0-2.2.0.jar"); // assumes CIMReader project is peer of CIMApplication

        return (ret);
    }

    /**
     * Get a connection to Spark.
     *
     * @return A connection to the Spark system.
     */
    protected Connection getConnection () throws Exception
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
    protected Connection getConnection (ConnectionFactory factory) throws Exception
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

    @SuppressWarnings ("unchecked")
    @Test
    public void testRead () throws Exception
    {
        // number of elements in the file
        // get number of lines at the top level with:
        // grep -P "^[\t]<cim" NIS_CIM_Export_NS_INITIAL_FILL.rdf | wc
        final long ELEMENTS = 679473L;
        final ConnectionFactory factory = getConnectionFactory ();
        final Connection connection = getConnection (factory);
        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
        spec.setFunctionName (CIMInteractionSpec.READ_FUNCTION);
        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        input.setRecordShortDescription ("record containing the file name with key filename");
        if (USE_LOCAL)
            input.put ("filename", "src/test/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        else
            input.put ("filename", "hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        final MappedRecord output = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
        output.setRecordShortDescription ("record that will return key count");
        final Interaction interaction = connection.createInteraction ();
        assertTrue ("interaction returned false", interaction.execute (spec, input, output));
        assertFalse ("interaction returned empty", output.isEmpty ());
        assertEquals ("interaction returned wrong value", ELEMENTS, output.get ("count"));
        interaction.close ();
        connection.close ();
    }

    @SuppressWarnings ("unchecked")
    @Test
    public void testReadEnergyConsumer () throws Exception
    {
        final ConnectionFactory factory = getConnectionFactory ();
        final Connection connection = getConnection (factory);
        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
        spec.setFunctionName (CIMInteractionSpec.GET_DATAFRAME_FUNCTION);
        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        input.setRecordShortDescription ("record containing the file name with key filename and sql query with key query");
        if (USE_LOCAL)
            input.put ("filename", "src/test/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        else
            input.put ("filename", "hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        input.put ("query", "select s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mRID, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName aliasName, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name name, s.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description description, p.xPosition, p.yPosition from EnergyConsumer s, PositionPoint p where s.ConductingEquipment.Equipment.PowerSystemResource.Location = p.Location and p.sequenceNumber = 0");
        final Interaction interaction = connection.createInteraction ();
        final Record output = interaction.execute (spec, input);
        assertNotNull ("output", output);
        assertTrue ("resultset", output.getClass ().isAssignableFrom (CIMResultSet.class));
        CIMResultSet resultset = (CIMResultSet)output;
        assertTrue ("resultset empty", resultset.next ());
        assertNotNull ("mRID", resultset.getString (1));
        assertTrue ("zero x coordinate", 0.0 != resultset.getDouble (5));
        assertTrue ("zero y coordinate", 0.0 != resultset.getDouble (6));
        resultset.close ();
        interaction.close ();
        connection.close ();
    }

    public String dummy (SparkSession session, String args)
    {
        System.out.println ("session.version = " + session.version ());
        System.out.println ("args = " + args);
        return ("OK");
    }

    @SuppressWarnings ("unchecked")
    @Test
    public void testGetString () throws Exception
    {
        final ConnectionFactory factory = getConnectionFactory ();
        final Connection connection = getConnection (factory);
        final CIMInteractionSpecImpl spec = new CIMInteractionSpecImpl ();
        spec.setFunctionName (CIMInteractionSpec.GET_STRING_FUNCTION);
        final MappedRecord input = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.INPUT);
        input.setRecordShortDescription ("record containing the file name and class and method to run");
        if (USE_LOCAL)
            input.put ("filename", "src/test/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        else
            input.put ("filename", "hdfs://sandbox:8020/data/NIS_CIM_Export_NS_INITIAL_FILL.rdf");
        input.put ("class", "ch.ninecode.cim.connector.CIMResourceAdapterTest");
        input.put ("method", "dummy");
        final MappedRecord output = factory.getRecordFactory ().createMappedRecord (CIMMappedRecord.OUTPUT);
        output.setRecordShortDescription ("the results of the read operation");

        final Interaction interaction = connection.createInteraction ();
        assertTrue (interaction.execute (spec, input, output));
        assertNotNull ("result", output.get ("result"));
        assertTrue ("result is empty", "" != output.get ("result"));
        String out = (output.get ("result")).toString ();
        assertTrue ("result is incorrect", out.equals ("OK"));
        interaction.close ();
        connection.close ();
    }

}
