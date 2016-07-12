package ch.ninecode.cim.connector;

import javax.resource.ResourceException;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.spi.ManagedConnectionMetaData;

public class CIMManagedConnectionMetaData implements ManagedConnectionMetaData
{

    private static final int MAX_CONNECTIONS = 1;

    private ConnectionMetaData _ConnectionMetaData;

    /**
     * Constructor for CIMManagedConnectionMetaData
     */
    public CIMManagedConnectionMetaData (ConnectionMetaData metadata)
    {
        super ();
        _ConnectionMetaData = metadata;
    }

    /**
     * @see ManagedConnectionMetaData#getEISProductName()
     */
    public String getEISProductName () throws ResourceException
    {
        return (_ConnectionMetaData.getEISProductName ());
    }

    /**
     * @see ManagedConnectionMetaData#getEISProductVersion()
     */
    public String getEISProductVersion () throws ResourceException
    {
        return (_ConnectionMetaData.getEISProductVersion ());
    }

    /**
     * @see ManagedConnectionMetaData#getMaxConnections()
     */
    public int getMaxConnections () throws ResourceException
    {
        return (MAX_CONNECTIONS);
    }

    /**
     * @see ManagedConnectionMetaData#getUserName()
     */
    public String getUserName () throws ResourceException
    {
        return (_ConnectionMetaData.getUserName ());
    }
}