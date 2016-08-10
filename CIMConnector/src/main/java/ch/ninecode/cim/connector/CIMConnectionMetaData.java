package ch.ninecode.cim.connector;

import javax.resource.ResourceException;
import javax.resource.cci.ConnectionMetaData;

public class CIMConnectionMetaData implements ConnectionMetaData
{

    private static final String PRODUCT_NAME = "Spark";

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
        return (PRODUCT_NAME);
    }

    /**
     * @see ConnectionMetaData#getEISProductVersion()
     */
    public String getEISProductVersion () throws ResourceException
    {
        return (_ManagedConnection._SparkContext.version ());
    }

    /**
     * @see ConnectionMetaData#getUserName()
     */
    public String getUserName () throws ResourceException
    {
        return (_ManagedConnection._SparkContext.sparkUser ());
    }

}