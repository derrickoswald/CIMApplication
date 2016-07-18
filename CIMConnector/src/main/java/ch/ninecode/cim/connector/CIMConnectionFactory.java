package ch.ninecode.cim.connector;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionFactory;
import javax.resource.cci.ConnectionSpec;
import javax.resource.cci.RecordFactory;
import javax.resource.cci.ResourceAdapterMetaData;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;

public class CIMConnectionFactory implements ConnectionFactory
{
    private static final long serialVersionUID = 1L;
    protected CIMManagedConnectionFactory _ManagedConnectionFactory;
    protected ConnectionManager _ConnectionManager;
    protected Reference _Reference;

    public CIMConnectionFactory (ManagedConnectionFactory mcf, ConnectionManager cm)
        throws ResourceException
    {
        super ();
        if ((null == mcf) || (!mcf.getClass ().isAssignableFrom (CIMManagedConnectionFactory.class)))
            throw new ResourceException ("object of class " + mcf.getClass ().toGenericString () + " cannot be used as a managed connection factory");
        else
            _ManagedConnectionFactory = (CIMManagedConnectionFactory)mcf;
        _ConnectionManager = cm;
    }

    @Override
    public void setReference (Reference reference)
    {
        _Reference = reference;
    }

    @Override
    public Reference getReference () throws NamingException
    {
        return (_Reference);
    }

    @Override
    public Connection getConnection () throws ResourceException
    {
        return (getConnection (null));
    }

    @Override
    public Connection getConnection (ConnectionSpec spec) throws ResourceException
    {
        CIMConnectionSpec _spec;
        if ((null == spec) || (!spec.getClass ().isAssignableFrom (CIMConnectionSpec.class)))
            _spec = new CIMConnectionSpec ();
        else
            _spec = (CIMConnectionSpec)spec;
        CIMConnectionRequestInfo info = new CIMConnectionRequestInfo ();
        info.setMaster (_ManagedConnectionFactory.getConnectionURL ());
        for (String key : _spec.getProperties ().keySet ())
            info.getProperties ().put (key, _spec.getProperties ().get (key));
        for (String jar : _spec.getJars ())
            info.getJars ().add (jar);

        return ((Connection)_ConnectionManager.allocateConnection (_ManagedConnectionFactory, info));
    }

    @Override
    public ResourceAdapterMetaData getMetaData () throws ResourceException
    {
        return (new CIMResourceAdapterMetaData ());
    }

    @Override
    public RecordFactory getRecordFactory () throws ResourceException
    {
        return (new CIMRecordFactory ());
    }

}
