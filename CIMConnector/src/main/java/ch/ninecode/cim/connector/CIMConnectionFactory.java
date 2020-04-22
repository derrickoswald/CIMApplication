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

    /**
     * An implementation class for ConnectionFactory must provide a default constructor.
     * @throws ResourceException <em>not used</em>
     */
    public CIMConnectionFactory () throws ResourceException
    {
    }

    public CIMConnectionFactory (ManagedConnectionFactory mcf, ConnectionManager cm)
        throws ResourceException
    {
        if (null == mcf)
            throw new ResourceException ("null cannot be used as a managed connection factory");
        else if (!mcf.getClass ().isAssignableFrom (CIMManagedConnectionFactory.class))
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
        if (null == _Reference)
            throw new NamingException ("Reference is null");
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
            _spec = getDefaultConnectionSpec ();
        else
            _spec = (CIMConnectionSpec)spec;
        CIMConnectionRequestInfo info = new CIMConnectionRequestInfo ();
        info.setMaster (_ManagedConnectionFactory.getConnectionURL ());
        info.setCassandra (_ManagedConnectionFactory.getServerName ());
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

    /**
     * Get the resource adapter.
     *
     * @return The CIMResourceAdapter object.
     */
    public CIMResourceAdapter getResourceAdapter ()
    {
        return ((CIMResourceAdapter)_ManagedConnectionFactory.getResourceAdapter ());
    }

    /**
     * Build a connection specification with the configured settings.
     * @return a connection specification that is probably good enough
     */
    public CIMConnectionSpec getDefaultConnectionSpec ()
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();

        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", _ManagedConnectionFactory._ResourceAdapter.getSparkDriverMemory ());
        ret.getProperties ().put ("spark.executor.memory", _ManagedConnectionFactory._ResourceAdapter.getSparkExecutorMemory ());

        return (ret);
    }
}
