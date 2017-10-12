package ch.ninecode.cim.connector;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;

import scala.Function1;
import scala.runtime.BoxedUnit;

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
import org.apache.spark.sql.SparkSession;

import ch.ninecode.cim.*;
import ch.ninecode.model.*;

/**
 * Connection to Apache Spark (http://spark.apache.org).
 * Assumes access to maven packages like:
 * org.apache.spark:spark-core_2.11-2.2.0
 * org.apache.spark:spark-sql_2.11-2.2.0
 * org.apache.spark:spark-hive-thriftserver_2.11-2.2.0
 * org.apache.spark:spark-graphx_2.11-2.2.0
 * org.apache.spark:spark-yarn_2.11-2.2.0
 *
 */
public class CIMManagedConnection implements ManagedConnection
{
    private static final String TRANSACTIONS_NOT_SUPPORTED_ERROR = "Transactions not supported";

    protected CIMResourceAdapter _Adapter;
    protected PrintWriter _PrintWriter;
    protected Vector<ConnectionEventListener> _Listeners;
    protected Subject _Subject;
    protected CIMConnectionRequestInfo _RequestInfo;
    protected ArrayList<CIMConnection> _Connections;
    protected SparkSession _SparkSession;

    /**
     * Constructor for CIMManagedConnection
     */
    public CIMManagedConnection (CIMResourceAdapter adapter, PrintWriter writer)
    {
        super ();
        _Adapter = adapter;
        _PrintWriter = writer;
        _Listeners = new Vector<> ();
        _Subject = null;
        _RequestInfo = null;
        _Connections = new ArrayList<> ();
        _SparkSession = null;
    }

    /**
     * Close a connection if it isn't already closed.
     * @param connection The connection to close.
     */
    public void close (CIMConnection connection)
    {
        if (null != connection)
            if (connection._Valid)
            {
                connection.invalidate ();
                Enumeration<ConnectionEventListener> list = _Listeners.elements ();
                ConnectionEvent event = new ConnectionEvent (this, ConnectionEvent.CONNECTION_CLOSED);
                event.setConnectionHandle (connection);
                while (list.hasMoreElements ())
                    list.nextElement ().connectionClosed (event);
            }
    }

    /**
     * Get the name of the CIMReader jar file.
     * See <a href="https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file">How to get the path of a running jar</a>
     * @return the name of the jar file or <code>null</code> if the code isn't running from a jar
     */
    protected String CIMReaderJarPath ()
        throws ResourceException
    {
        PrintWriter logger;
        String ret;

        logger = getLogWriter ();
        // arbitrarily pick a class to instantiate
        ret = (new DefaultSource ()).getClass ().getProtectionDomain ().getCodeSource ().getLocation ().getPath ();
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // good enough
        }

        if (!ret.endsWith (".jar"))
        {
            if (null != logger)
                logger.println ("CIMReader jar file could not be determined");
            ret = null;
        }
        else
            if (null != logger)
                logger.println ("CIMReader jar file: " + ret);

        return (ret);
    }

    /**
     * Check and save the subject and connection request information.
     * @param subject The principal under which to connect.
     * @param info The connection request information.
     * @see CIMConnectionRequestInfo
     * @throws ResourceException if the subject or request info are not the same as this managed connection has already.
     */
    public void check (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        PrintWriter logger;

        logger = getLogWriter ();
        if (null == _Subject)
            _Subject = subject;
        else
            if ((null != subject) && !_Subject.equals (subject))
                throw new ResourceException ("subject " + subject.toString () + " not equal to current subject " + _Subject.toString ());
        if ((null != logger) && (null != _Subject))
            logger.println ("Subject = " + _Subject.toString ());
        if (null == _RequestInfo)
            if ((null == info) || (!info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
                _RequestInfo = new CIMConnectionRequestInfo ();
            else
                _RequestInfo = (CIMConnectionRequestInfo)info;
        else
            if ((null != info) && !_RequestInfo.equals (info))
                throw new ResourceException ("connection request info " + info.toString () + " not equal to current info " + _RequestInfo.toString ());
        if (null != logger)
            logger.println ("CIMConnectionRequestInfo = " + _RequestInfo.toString ());
    }

    /**
     * Connect to Spark.
     * @param subject The principal under which to connect.
     * @param info The connection request information.
     * @see CIMConnectionRequestInfo
     * @throws ResourceException
     */
    public void connect (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        PrintWriter logger;

        if (null == _SparkSession)
        {
            logger = getLogWriter ();
            check (subject, info);

            // create the configuration
            SparkConf configuration = new SparkConf (false);
            configuration.setAppName ("CIMConnector");
            configuration.set ("spark.driver.userClassPathFirst", "false"); // default
            configuration.set ("spark.executor.userClassPathFirst", "false"); // default
            configuration.set ("spark.driver.allowMultipleContexts", "false"); // default

            // set up the spark master
            if (!_RequestInfo.getMaster ().equals (""))
                configuration.setMaster (_RequestInfo.getMaster ());
            else
                // run Spark locally with as many worker threads as logical cores on the machine
                configuration.setMaster ("local[*]");

            // add the other properties
            for (String key : _RequestInfo.getProperties ().keySet ())
                configuration.set (key, _RequestInfo.getProperties ().get (key));

            // set up the list of jars to send with the connection request
            String jar = CIMReaderJarPath ();
            String[] jars = new String[_RequestInfo.getJars ().size () + (null == jar ? 0 : 1)];
            jars = _RequestInfo.getJars ().toArray (jars);
            if (null != jar)
                jars[jars.length - 1] = jar;
            configuration.setJars (jars);

            if (null != logger)
                logger.println ("SparkConf = " + configuration.toDebugString ());

            // so far, it only works for Spark standalone (as above with master set to spark://sandbox:7077
            // here are some options I tried for Yarn access master set to "yarn-client" that didn't work
    //      configuration.setMaster ("yarn-client"); // assumes a resource manager is specified in yarn-site.xml, e.g. sandbox:8032
    //      configuration.setSparkHome ("/home/derrick/spark/spark-2.2.0-bin-hadoop2.7/"); // ("/usr/local/spark")
    //      configuration.setExecutorEnv ("YARN_CONF_DIR", "/home/derrick/spark/spark-2.2.0-bin-hadoop2.7/conf"); // ("YARN_CONF_DIR", "/usr/local/hadoop/etc/hadoop")

            // register CIMReader classes
            configuration.registerKryoClasses (CIMClasses.list ());

            // make a Spark session
            _SparkSession = SparkSession.builder ().config (configuration).getOrCreate ();
            _SparkSession.sparkContext ().setLogLevel ("INFO"); // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
            if (null != logger)
                logger.println ("SparkSession = " + _SparkSession.toString ());
        }
    }

    /**
     * @see ManagedConnection#getConnection(Subject, ConnectionRequestInfo)
     */
    public Object getConnection (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        check (subject, info);
        CIMConnection connection = new CIMConnection (this);
        _Connections.add (connection);
        return (connection);
    }

    /**
     * @see ManagedConnection#destroy()
     */
    public void destroy () throws ResourceException
    {
        cleanup ();
        // Note:
        //
        // Since there can be only one SparkSession per JVM,
        // and we wish the state of the cluster to persist across stateless ejb calls,
        // and the Geronimo ConnectionManager used by TomEE handles each request independently
        // (https://geronimo.apache.org/GMOxDOC30/connectors-and-transaction-management.html)
        // which means this destroy method is called a lot (when connection count exceeds 4 I think),
        // to reclaim connections, we cannot shut down the SparkContext here.
        //
        // There may be a way to use a persistent local transaction in the entire application
        // to cause the ConnectionManager to use only one ManagedConnection
        // according to the local transaction contract that specifies all ejbs can see the same
        // resource adapter state
        // (http://download.oracle.com/otn-pub/jcp/connector_architecture-1.6-fr-oth-JSpec/connector-1_6-final-spec.pdf),
        // but it needs implementation at the CIMReasourceAdapter level
        // (TransactionSupport.TransactionSupportLevel transactionSupport() default TransactionSupport.TransactionSupportLevel.LocalTransaction;)
        // and a way for the javax.ws.rs.core.Application to specify the ConnectionFactoryDefinition with
        // transactionSupport = TransactionSupportLevel.LocalTransaction
        // and open a LocalTransaction at application start,
        // and commit/rollback at application shutdown.
        // So, for now we comment this out:
//        if (null != _SparkSession)
//            _SparkSession.stop ();
        _SparkSession = null;
    }

    /**
     * @see ManagedConnection#cleanup()
     */
    public void cleanup () throws ResourceException
    {
        for (CIMConnection connection : _Connections)
            close (connection);
    }

    public void disssociateConnection (Object connection) throws ResourceException
    {
        if (null != connection)
            _Connections.remove (connection);
        else
            throw new ResourceException ("null cannot be dissociated as a connection object");
    }

    /**
     * @see ManagedConnection#associateConnection(Object)
     */
    public void associateConnection (Object connection) throws ResourceException
    {
        if (null != connection)
        {
            if (connection.getClass ().isAssignableFrom (CIMConnection.class))
            {
                CIMConnection c = (CIMConnection)connection;
                c._ManagedConnection.disssociateConnection (c);
                if (!_Connections.contains (c))
                    _Connections.add (c);
            }
            else
                throw new ResourceException ("object of class " + connection.getClass ().toGenericString () + " cannot be associated as a connection object");
        }
        else
            throw new ResourceException ("null cannot be associated as a connection object");
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
        return (new CIMManagedConnectionMetaData ((new CIMConnection (this)).getMetaData ()));
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