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
//    protected String ServerName; // Name of the server for the EIS instance.
//    protected int PortNumber; // Port number for establishing a connection to an EIS instance.
//    protected String UserName; // Name of the user establishing a connection to an EIS instance.
//    protected String Password; // Password for the user establishing a connection.
    protected String _ConnectionURL; // URL for the EIS instance to which it connects.

    public CIMManagedConnectionFactory ()
    {
        super ();
        _PropertyChangeSupport = null;
        _PrintWriter = null;
        _ResourceAdapter = null;
        _ConnectionURL = null;
    }

    @Override
    public Object createConnectionFactory () throws ResourceException
    {
        return (new CIMConnectionFactory (this, null));
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
        final CIMConnectionRequestInfo _info;
        final CIMManagedConnection connection;

        if ((null == info) || (!info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
            _info = new CIMConnectionRequestInfo ();
        else
            _info = (CIMConnectionRequestInfo)info;
        _info.setMaster (getConnectionURL ());
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
        CIMManagedConnection ret = null;
        Iterator iterator = connections.iterator ();
        while (iterator.hasNext ())
        {
            CIMManagedConnection connection = (CIMManagedConnection)iterator.next ();
            if (null == connection._SparkContext) // always match a closed connection
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
        if ((null != ret) && (null == ret._SparkContext)) // if it was closed, reopen it
            ret.connect (subject, info);

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

    // requires a running standalone Spark instance with current user added to supergroup, for example:
    // docker run -it -p 8032:8032 -p 8088:8088 -p 8042:8042 -p 4040:4040 -p 9000:9000 -p 10000:10000 -p 10001:10001 -p 50010:50010 -p 7077:7077 -v /home/derrick/code/CIMScala/target:/opt/code -v /home/derrick/code/CIMScala/data:/opt/data --rm -h sandbox sequenceiq/spark:1.6.0 bash
    // sudo groupadd supergroup
    // sudo useradd derrick
    // sudo usermod --append --groups supergroup derrick
    // /usr/local/spark-1.6.0-bin-hadoop2.6/sbin/start-master.sh
    // /usr/local/spark-1.6.0-bin-hadoop2.6/sbin/start-slave.sh spark://sandbox:7077

    @ConfigProperty
    (
        type = String.class,
        description = "Spark stand-alone master URL.",
        defaultValue = "spark://sandbox:7077",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public String getConnectionURL ()
    {
        return (_ConnectionURL);
    }

    public void setConnectionURL (String url)
    {
        String old = _ConnectionURL;
        _ConnectionURL = url;
        firePropertyChange ("ConnectionURL", old, _ConnectionURL);
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
