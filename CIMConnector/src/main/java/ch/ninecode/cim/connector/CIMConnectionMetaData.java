package ch.ninecode.cim.connector;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.resource.ResourceException;
import javax.resource.cci.ConnectionMetaData;

public class CIMConnectionMetaData implements ConnectionMetaData
{
    static Properties properties = getConnectorProperties ();
    static Properties getConnectorProperties ()
    {
        Properties ret = new Properties ();
        InputStream in = CIMConnectionMetaData.class.getResourceAsStream ("/cimconnector.properties");
        if (null != in)
            try
            {
                ret.load (in);
                in.close ();
            }
            catch (IOException e)
            {
                e.printStackTrace ();
            }
        return (ret);
    }

    protected CIMManagedConnection _ManagedConnection;

    /**
     * Constructor for CIMConnectionMetaData.
     */
    public CIMConnectionMetaData (CIMManagedConnection connection)
    {
        super ();
        _ManagedConnection = connection;
    }

    /**
     * @see ConnectionMetaData#getEISProductName()
     */
    public String getEISProductName () throws ResourceException
    {
        return (properties.getProperty ("artifactId"));
    }

    /**
     * @see ConnectionMetaData#getEISProductVersion()
     */
    public String getEISProductVersion () throws ResourceException
    {
        return (properties.getProperty ("version"));
    }

    /**
     * @see ConnectionMetaData#getUserName()
     */
    public String getUserName () throws ResourceException
    {
        return (_ManagedConnection._SparkSession.sparkContext ().sparkUser ());
    }

    /**
     * Get the maven group id.
     */
    public String getEISProductGroup () throws ResourceException
    {
        return (properties.getProperty ("groupId"));
    }

    /**
     * Get the Scala version the connector was compiled for.
     */
    public String getScalaVersion () throws ResourceException
    {
        return (properties.getProperty ("scala"));
    }

    /**
     * Get the Scala library version the connector was compiled for.
     */
    public String getScalaLibraryVersion () throws ResourceException
    {
        return (properties.getProperty ("scalalibrary"));
    }

    /**
     * Get the Spark library version the connector was compiled for.
     */
    public String getSparkLibraryVersion () throws ResourceException
    {
        return (properties.getProperty ("spark"));
    }

    /**
     * Get the Spark version the connector was compiled for.
     */
    public String getHadoopLibraryVersion () throws ResourceException
    {
        return (properties.getProperty ("hadoop"));
    }

    /**
     * Get the CIMReader version the connector was compiled for.
     */
    public String getCIMReaderVersion () throws ResourceException
    {
        return (properties.getProperty ("cimreader"));
    }

    /**
     * Get the executing spark version.
     */
    public String getSparkVersion () throws ResourceException
    {
        return (_ManagedConnection._SparkSession.version ());
    }
}