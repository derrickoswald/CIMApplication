package ch.ninecode.cim.connector;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;

import javax.resource.ResourceException;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.ConnectionDefinition;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;

@ConnectionDefinition
(
    connectionFactory = javax.resource.cci.ConnectionFactory.class,
    connectionFactoryImpl = ch.ninecode.cim.connector.CIMConnectionFactory.class,
    connection = javax.resource.cci.Connection.class,
    connectionImpl = ch.ninecode.cim.connector.CIMConnection.class
)
public class CIMManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation
{
    private static final long serialVersionUID = 1L;
    protected transient PropertyChangeSupport _PropertyChangeSupport;
    protected PrintWriter _PrintWriter;
    protected CIMResourceAdapter _ResourceAdapter;
//    protected int PortNumber; // Port number for establishing a connection to an EIS instance.
//    protected String UserName; // Name of the user establishing a connection to an EIS instance.
//    protected String Password; // Password for the user establishing a connection.
    protected String _ConnectionURL; // Spark master URL
    protected String _ServerName; // Cassandra connection host

    public CIMManagedConnectionFactory ()
    {
        super ();
        _PropertyChangeSupport = null;
        _PrintWriter = new PrintWriter (System.out);
        _ResourceAdapter = null;
        _ConnectionURL = null;
        _ServerName = null;    }

    @Override
    public Object createConnectionFactory () throws ResourceException
    {
        return (createConnectionFactory (null));
    }

    @Override
    public Object createConnectionFactory (ConnectionManager manager) throws ResourceException
    {
        return (new CIMConnectionFactory (this, manager));
    }

    @Override
    public int hashCode ()
    {
        int ret;

//      The equals and hashCode method implementation should be based on a complete set of
//      configuration properties that make a ManagedConnectionFactory instance unique and specific to an EIS instance.
        ret = (null == _ConnectionURL) ? 846348742 : _ConnectionURL.hashCode ();

        return (ret);
    }

    @Override
    public boolean equals (Object object)
    {
        boolean ret = false;
        if (object instanceof CIMManagedConnectionFactory)
        {
            CIMManagedConnectionFactory that = (CIMManagedConnectionFactory)object;
            ret = _ConnectionURL.equals (that._ConnectionURL);
        }

        return (ret);
    }

    /**
     * @see ManagedConnectionFactory#createManagedConnection(Subject, ConnectionRequestInfo)
     */
    @Override
    public ManagedConnection createManagedConnection (Subject subject, ConnectionRequestInfo info) throws ResourceException
    {
        PrintWriter logger;
        final CIMConnectionRequestInfo _info;
        final CIMManagedConnection connection;

        logger = getLogWriter ();
        if ((null == info) || (!info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
            _info = new CIMConnectionRequestInfo ();
        else
            _info = (CIMConnectionRequestInfo)info;
        _info.setMaster (getConnectionURL ());
        _info.setCassandra (getServerName ());
        if (null != logger)
            logger.println ("allocating new CIMManagedConnection");
        connection = new CIMManagedConnection ((CIMResourceAdapter)getResourceAdapter (), getLogWriter ());
        connection.connect (subject, _info);

        return (connection);
    }

    @Override
    public PrintWriter getLogWriter () throws ResourceException
    {
        return (_PrintWriter);
    }

    @SuppressWarnings ("rawtypes")
    @Override
    public ManagedConnection matchManagedConnections (Set connections, Subject subject, ConnectionRequestInfo info) throws ResourceException
    {
        PrintWriter logger;
        Iterator iterator;
        CIMManagedConnection ret;

        ret = null;
        logger = getLogWriter ();
        iterator = connections.iterator ();
        while (iterator.hasNext ())
        {
            CIMManagedConnection connection = (CIMManagedConnection)iterator.next ();
            if (null == connection._SparkSession) // always match a closed connection
            {
                ret = connection;
                break;
            }
            else
                if (((null == connection._RequestInfo) && (null == info)) || ((null != connection._RequestInfo) && connection._RequestInfo.equals (info)))
                    if (((null == connection._Subject) && (null == subject)) || ((null != connection._Subject) && connection._Subject.equals (subject)))
                    {
                        ret = connection;
                        break;
                    }
        }
        if (null != ret)
        {
            if (null != logger)
                logger.println ("matched CIMManagedConnection");
            if (null == ret._SparkSession) // if it was closed, reopen it
            {
                if (null != logger)
                    logger.println ("reopening Spark connection");
                ret.connect (subject, info);
            }
        }
        else if (null != logger)
            logger.println ("CIMManagedConnection not matched");



        return (ret);
    }

    @Override
    public void setLogWriter (PrintWriter print_writer) throws ResourceException
    {
        PrintWriter old = _PrintWriter;
        _PrintWriter = print_writer;
        firePropertyChange ("LogWriter", old, _PrintWriter);
    }

    /**
     * Provides the association between the ResourceAdapter and the ManagedConnectionfactory.
     * @return the resource adapter if available, <code>null</code> otherwise
     */
    @Override
    public ResourceAdapter getResourceAdapter ()
    {
        return (_ResourceAdapter);
    }

    @Override
    public void setResourceAdapter (ResourceAdapter adapter) throws ResourceException
    {
        if (null == _ResourceAdapter)
            if (adapter.getClass ().isAssignableFrom (CIMResourceAdapter.class))
            {
                _ResourceAdapter = (CIMResourceAdapter)adapter;
                firePropertyChange ("ResourceAdapter", null, _ResourceAdapter);
            }
            else
                throw new ResourceException ("the ResourceAdapter is not a CIMResourceAdapter");
        else
            throw new ResourceException ("the ResourceAdapter association must not change during the lifetime of a ManagedConnectionFactory JavaBean");
    }

    /**
     * Get the Spark master URL.
     *
     * For example <code>spark://sandbox:7077</code>.
     *
     * @implNote For TomEE+, this value may also be set via a system property,
     * either on the command line via environment variable:
     * <code>CATALINA_OPTS=-DSparkConnectionFactory.ConnectionURL=spark://sandbox:7077</code>
     * or adding it to &lt;tomee installation directory&gt;/conf/system.properties:
     * <code>SparkConnectionFactory.ConnectionURL=spark://sandbox:7077</code>
     *
     * @return The current setting for Spark master.
     */
    public String getConnectionURL () { return (_ConnectionURL); }

    /**
     * Set the Spark master URL.
     *
     * @implNote Setting this to something other than the default <code>local[*]</code> requires a running
     * "standalone" Spark instance with the current user added to supergroup.
     * An example for creating such an instance, using Docker "compose", can be found in the
     * <a href="https://github.com/derrickoswald/CIMSpark/tree/master/CIMReader/src/test/resources/sandbox.yaml" target="_blank">
     * <code>spark_master</code> container</a>.
     *
     * @param url The new Spark master URL.
     */
    @ConfigProperty
    (
        type = String.class,
        description = "Spark stand-alone master URL.",
        defaultValue = "local[*]",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setConnectionURL (String url)
    {
        String old = _ConnectionURL;
        _ConnectionURL = url;
        firePropertyChange ("ConnectionURL", old, _ConnectionURL);
    }

    /**
     * Get the Cassandra connection host.
     *
     * @return The current setting for Cassandra connection.host.
     */
    public String getServerName () { return (_ServerName); }

    /**
     * Set the Cassandra connection host.
     *
     * @param server The new Cassandra connection host.
     */
    @ConfigProperty
    (
        type = String.class,
        description = "Cassandra connection.host.",
        defaultValue = "localhost",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setServerName (String server)
    {
        String old = _ServerName;
        _ServerName = server;
        firePropertyChange ("ServerName", old, _ServerName);
    }

    /**
     * The addPropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void addPropertyChangeListener (PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().addPropertyChangeListener (listener);
    }

    /**
     * The addPropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void addPropertyChangeListener (String name, PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().addPropertyChangeListener (name, listener);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (PropertyChangeEvent event)
    {

        getPropertyChangeSupport ().firePropertyChange (event);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, int oldValue, int newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, Object oldValue, Object newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * The firePropertyChange method was generated to support the propertyChange field.
     */
    public void firePropertyChange (String name, boolean oldValue, boolean newValue)
    {
        getPropertyChangeSupport ().firePropertyChange (name, oldValue, newValue);
    }

    /**
     * Accessor for the propertyChange field.
     */
    protected PropertyChangeSupport getPropertyChangeSupport ()
    {
        if (_PropertyChangeSupport == null)
            _PropertyChangeSupport = new PropertyChangeSupport (this);
        return (_PropertyChangeSupport);
    }

    /**
     * The hasListeners method was generated to support the propertyChange field.
     */
    public synchronized boolean hasListeners (String name)
    {
        return (getPropertyChangeSupport ().hasListeners (name));
    }

    /**
     * The removePropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void removePropertyChangeListener (PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().removePropertyChangeListener (listener);
    }

    /**
     * The removePropertyChangeListener method was generated to support the propertyChange field.
     */
    public synchronized void removePropertyChangeListener (String name, PropertyChangeListener listener)
    {
        getPropertyChangeSupport ().removePropertyChangeListener (name, listener);
    }
}
