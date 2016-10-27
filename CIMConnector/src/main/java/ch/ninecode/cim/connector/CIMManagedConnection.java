package ch.ninecode.cim.connector;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;

import ch.ninecode.cim.*;
import ch.ninecode.model.*;

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

    protected CIMResourceAdapter _Adapter;
    protected PrintWriter _PrintWriter;
    protected Vector<ConnectionEventListener> _Listeners;
    protected Subject _Subject;
    protected CIMConnectionRequestInfo _RequestInfo;
    protected CIMConnection _Connection;
    protected SparkContext _SparkContext;
    protected SQLContext _SqlContext;

    /**
     * Constructor for CIMManagedConnection
     */
    public CIMManagedConnection (CIMResourceAdapter adapter, PrintWriter writer)
    {
        super ();
        _Adapter = adapter;
        _PrintWriter = writer;
        _Listeners = new Vector<> ();
    }

    public void close ()
    {
        if (null != _SparkContext && !_SparkContext.isStopped ())
            _SparkContext.stop ();
        _SparkContext = null;
        _SqlContext = null;
        Enumeration<ConnectionEventListener> list = _Listeners.elements ();
        ConnectionEvent event = new ConnectionEvent (this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle (_Connection);
        while (list.hasMoreElements ())
            list.nextElement ().connectionClosed (event);
    }

    /**
     * Get the name of the CIMScala jar file.
     * @see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
     * @return the name of the jar file or <code>null</code> if the code isn't running from a jar
     */
    protected String CIMScalaJarPath ()
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
                logger.println ("CIMScala jar file could not be determined");
            ret = null;
        }
        else
            if (null != logger)
                logger.println ("CIMScala jar file: " + ret);
                // i.e.  /opt/apache-tomee-plus-1.7.4/apps/CIMApplication/CIMConnector/CIMScala-2.10-1.6.0-1.6.0.jar

        return (ret);
    }

    public void connect (Subject subject, ConnectionRequestInfo info)
        throws ResourceException
    {
        PrintWriter logger;

        logger = getLogWriter ();
        _Subject = subject;
        if ((null == info) || (!info.getClass ().isAssignableFrom (CIMConnectionRequestInfo.class)))
            _RequestInfo = new CIMConnectionRequestInfo ();
        else
            _RequestInfo = (CIMConnectionRequestInfo)info;
        if (null != logger)
            logger.println ("CIMConnectionRequestInfo = " + info.toString ());

        // create the configuration
        SparkConf configuration = new SparkConf (false);
        configuration.setAppName ("CIMConnector");
        configuration.set ("spark.driver.allowMultipleContexts", "false"); // default

        // set up the spark master
        if ("" != _RequestInfo.getMaster ())
            configuration.setMaster (_RequestInfo.getMaster ());
        else
            // run Spark locally with as many worker threads as logical cores on the machine
            configuration.setMaster ("local[*]");

        // add the other properties
        for (String key : _RequestInfo.getProperties ().keySet ())
            configuration.set (key, _RequestInfo.getProperties ().get (key));

        // set up the list of jars to send with the connection request
        String jar = CIMScalaJarPath ();
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
//      configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6"); // ("/usr/local/spark")
//      configuration.setExecutorEnv ("YARN_CONF_DIR", "/home/derrick/spark-1.6.0-bin-hadoop2.6/conf"); // ("YARN_CONF_DIR", "/usr/local/hadoop/etc/hadoop")

        // register low level classes
        Class<?>[] c1 = { Element.class, BasicElement.class, Unknown.class };
        configuration.registerKryoClasses (c1);

        // register CIM case classes
        // this is really Byzantine, all I need is the apply() method,
        // but all the rest are required for some reason,
        // ToDo: will have to fix this soon
        scala.Function1<CIMSubsetter<?>, BoxedUnit> fn = new scala.Function1<CIMSubsetter<?>, BoxedUnit> ()
        {
            public BoxedUnit apply (CIMSubsetter<?> sub)
            {
                Class<?>[] array = {sub.runtime_class ()};
                configuration.registerKryoClasses (array);
                return (BoxedUnit.UNIT);
            }

            @Override
            public <A> Function1<CIMSubsetter<?>, A> andThen (Function1<BoxedUnit, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcDD$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcDF$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcDI$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcDJ$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcFD$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcFF$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcFI$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcFJ$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcID$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcIF$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcII$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcIJ$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcJD$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcJF$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcJI$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcJJ$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcVD$sp (Function1<BoxedUnit, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcVF$sp (Function1<BoxedUnit, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcVI$sp (Function1<BoxedUnit, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcVJ$sp (Function1<BoxedUnit, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcZD$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcZF$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcZI$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<Object, A> andThen$mcZJ$sp (Function1<Object, A> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public double apply$mcDD$sp (double arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public double apply$mcDF$sp (float arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public double apply$mcDI$sp (int arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public double apply$mcDJ$sp (long arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public float apply$mcFD$sp (double arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public float apply$mcFF$sp (float arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public float apply$mcFI$sp (int arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public float apply$mcFJ$sp (long arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public int apply$mcID$sp (double arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public int apply$mcIF$sp (float arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public int apply$mcII$sp (int arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public int apply$mcIJ$sp (long arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public long apply$mcJD$sp (double arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public long apply$mcJF$sp (float arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public long apply$mcJI$sp (int arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public long apply$mcJJ$sp (long arg0)
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void apply$mcVD$sp (double arg0)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void apply$mcVF$sp (float arg0)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void apply$mcVI$sp (int arg0)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void apply$mcVJ$sp (long arg0)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public boolean apply$mcZD$sp (double arg0)
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean apply$mcZF$sp (float arg0)
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean apply$mcZI$sp (int arg0)
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean apply$mcZJ$sp (long arg0)
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public <A> Function1<A, BoxedUnit> compose (Function1<A, CIMSubsetter<?>> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcDD$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcDF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcDI$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcDJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcFD$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcFF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcFI$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcFJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcID$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcIF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcII$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcIJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcJD$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcJF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcJI$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcJJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, BoxedUnit> compose$mcVD$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, BoxedUnit> compose$mcVF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, BoxedUnit> compose$mcVI$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, BoxedUnit> compose$mcVJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcZD$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcZF$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcZI$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public <A> Function1<A, Object> compose$mcZJ$sp (Function1<A, Object> arg0)
            {
                // TODO Auto-generated method stub
                return null;
            }
        };
        CHIM.apply_to_all_classes (fn);

        // register edge related classes
        if (configuration.getBoolean ("ch.ninecode.cim.make_edges", false))
        {
            Class<?>[] classes = { PreEdge.class, Extremum.class, Edge.class };
            configuration.registerKryoClasses (classes);
        }

        // register topological classes
        if (configuration.getBoolean ("ch.ninecode.cim.do_topo", false))
        {
            Class<?>[] classes = { CuttingEdge.class, TopologicalData.class };
            configuration.registerKryoClasses (classes);
        }

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
        if (null != _Connection)
            _Connection.invalidate ();
        _Connection = null;
        _Listeners.clear ();
    }

    /**
     * @see ManagedConnection#cleanup()
     */
    public void cleanup () throws ResourceException
    {
        // no client state, right?
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