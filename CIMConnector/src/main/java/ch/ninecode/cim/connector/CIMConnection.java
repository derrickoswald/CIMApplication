package ch.ninecode.cim.connector;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.LocalTransaction;
import javax.resource.cci.ResultSetInfo;
import javax.resource.spi.ManagedConnection;

public class CIMConnection implements Connection
{

    private static final String CLOSED_ERROR = "Connection closed";
    private static final String TRANSACTIONS_NOT_SUPPORTED = "Local transactions not supported";

    protected boolean _Valid;
    protected CIMManagedConnection _ManagedConnection;

    /**
     * Constructor for CIMConnection.
     */
    public CIMConnection (ManagedConnection mc)
    {
        super ();
        _ManagedConnection = (CIMManagedConnection)mc;
        _Valid = true;
    }

    void invalidate ()
    {
        _ManagedConnection = null;
        _Valid = false;
    }

    /**
     * @see Connection#createInteraction()
     */
    public Interaction createInteraction () throws ResourceException
    {
        if (_Valid)
            return new CIMInteractionImpl (this);
        else
            throw new ResourceException (CLOSED_ERROR);
    }

    /**
     * @see Connection#getLocalTransaction()
     */
    public LocalTransaction getLocalTransaction () throws ResourceException
    {
        throw new NotSupportedException (TRANSACTIONS_NOT_SUPPORTED);
    }

    /**
     * @see Connection#getMetaData()
     */
    public ConnectionMetaData getMetaData () throws ResourceException
    {
        if (_Valid)
            return new CIMConnectionMetaData (_ManagedConnection);
        else
            throw new ResourceException (CLOSED_ERROR);
    }

    /**
     * @see Connection#getResultSetInfo()
     */
    public ResultSetInfo getResultSetInfo () throws ResourceException
    {
        return (new CIMResultSetInfo ());
    }

    /**
     * @see Connection#close()
     */
    public void close () throws ResourceException
    {
        if (_Valid)
            _ManagedConnection.close (this);
        else
            throw new ResourceException (CLOSED_ERROR);
    }
}