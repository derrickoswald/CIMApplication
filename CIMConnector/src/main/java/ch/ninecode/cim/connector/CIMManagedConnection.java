package ch.ninecode.cim.connector;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Vector;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;

/**
 * Connection to Apache Spark (http://spark.apache.org).
 * Assumes access to maven packages like:
 * org.apache.spark:spark-core_2.10:1.6.0
 * org.apache.spark:spark-sql_2.10:1.6.0
 * org.apache.spark:spark-hive-thriftserver_2.10:1.6.0
 * org.apache.spark:spark-graphx_2.10:1.6.0
 * org.apache.spark:spark-yarn_2.10:1.6.0
 *
 */
public class CIMManagedConnection implements ManagedConnection
{
    private static final String TRANSACTIONS_NOT_SUPPORTED_ERROR = "Transactions not supported";

    protected Subject _Subject;
    protected CIMConnectionRequestInfo _RequestInfo;
    protected CIMConnection _Connection;
    protected Vector<ConnectionEventListener> _Listeners;
    protected PrintWriter _PrintWriter;
    protected SparkContext _SparkContext;
    protected SQLContext _SqlContext;

    /**
     * Constructor for CIMManagedConnection
     */
    public CIMManagedConnection (Subject subject, ConnectionRequestInfo info)
    {
        super ();
        _Listeners = new Vector<> ();
        _PrintWriter = null;
        _Subject = subject;
        if ((null != info) && (info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
            _RequestInfo = (CIMConnectionRequestInfo)info;
    }

    public void close ()
    {
        if (null != _SparkContext)
            _SparkContext.stop ();
        _SparkContext = null;
        _SqlContext = null;
        Enumeration<ConnectionEventListener> list = _Listeners.elements ();
        ConnectionEvent event = new ConnectionEvent (this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle (_Connection);
        while (list.hasMoreElements ())
            list.nextElement ().connectionClosed (event);
    }

    public void connect (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        PrintWriter logger;
        CIMConnectionRequestInfo _info;

        logger = getLogWriter ();
        if ((null == info) || (!info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
            _info = new CIMConnectionRequestInfo ();
        else
            _info = (CIMConnectionRequestInfo)info;
        if (null != logger)
            logger.println ("CIMConnectionRequestInfo = " + info.toString ());

        // create the configuration
        SparkConf configuration = new SparkConf (false);
        configuration.setAppName ("CIMConnector");
        if (_info.getMaster () != "")
            configuration.setMaster (_info.getMaster ());
        else
            configuration.setMaster ("local[*]"); // run Spark locally with as many worker threads as logical cores on the machine
        for (String key : _info.getProperties ().keySet ())
            configuration.set (key, _info.getProperties ().get (key));
        String[] jars = new String[_info.getJars ().size ()];
        configuration.setJars (_info.getJars ().toArray (jars));
        if (null != logger)
            logger.println ("SparkConf = " + configuration.toDebugString ());
        configuration.set ("spark.driver.allowMultipleContexts", "false"); // default

        // so far, it only works for Spark standalone (as above with master set to spark://sandbox:7077
        // here are some options I tried for Yarn access master set to "yarn-client" that didn't work
//      configuration.setMaster ("yarn-client"); // assumes a resource manager is specified in yarn-site.xml, e.g. sandbox:8032
//      configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6"); // ("/usr/local/spark")
//      configuration.setExecutorEnv ("YARN_CONF_DIR", "/home/derrick/spark-1.6.0-bin-hadoop2.6/conf"); // ("YARN_CONF_DIR", "/usr/local/hadoop/etc/hadoop")

        // make a Spark context and SQL context
        _SparkContext = SparkContext.getOrCreate (configuration);
        _SparkContext.setLogLevel ("INFO"); // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        if (null != logger)
            logger.println ("SparkContext = " + _SparkContext.toString ());
        _SqlContext = SQLContext.getOrCreate (_SparkContext);
        if (null != logger)
            logger.println ("SQLContext = " + _SqlContext.toString ());
    }

    /**
     * @see ManagedConnection#getConnection(Subject, ConnectionRequestInfo)
     */
    public Object getConnection (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        _Connection = new CIMConnection (this);
        return (_Connection);
    }

    /**
     * @see ManagedConnection#destroy()
     */
    public void destroy () throws ResourceException
    {
        close ();
        _Connection.invalidate ();
        _Connection = null;
        _Listeners = null;
    }

    /**
     * @see ManagedConnection#cleanup()
     */
    public void cleanup () throws ResourceException
    {
        _Connection.invalidate ();
    }

    /**
     * @see ManagedConnection#associateConnection(Object)
     */
    public void associateConnection (Object connection) throws ResourceException
    {
        if ((null == connection) || (connection.getClass ().isAssignableFrom (CIMConnection.class)))
            _Connection = (CIMConnection)connection;
        else
            throw new ResourceException ("object of class " + connection.getClass ().toGenericString () + " cannot be associated as a connection object");
    }

    /**
     * @see ManagedConnection#addConnectionEventListener(ConnectionEventListener)
     */
    public void addConnectionEventListener (ConnectionEventListener listener)
    {
        _Listeners.add (listener);
    }

    /**
     * @see ManagedConnection#removeConnectionEventListener(ConnectionEventListener)
     */
    public void removeConnectionEventListener (ConnectionEventListener listener)
    {
        _Listeners.remove (listener);
    }

    /**
     * @see ManagedConnection#getXAResource()
     */
    public XAResource getXAResource () throws ResourceException
    {
        throw new NotSupportedException (TRANSACTIONS_NOT_SUPPORTED_ERROR);
    }

    /**
     * @see ManagedConnection#getLocalTransaction()
     */
    public LocalTransaction getLocalTransaction () throws ResourceException
    {
        throw new NotSupportedException (TRANSACTIONS_NOT_SUPPORTED_ERROR);
    }

    /**
     * @see ManagedConnection#getMetaData()
     */
    public ManagedConnectionMetaData getMetaData () throws ResourceException
    {
        return (new CIMManagedConnectionMetaData (_Connection.getMetaData ()));
    }

    /**
     * @see ManagedConnection#setLogWriter(PrintWriter)
     */
    public void setLogWriter (PrintWriter out) throws ResourceException
    {
        _PrintWriter = out;
    }

    /**
     * @see ManagedConnection#getLogWriter()
     */
    public PrintWriter getLogWriter () throws ResourceException
    {
        return (_PrintWriter);
    }
}