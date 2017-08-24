package ch.ninecode.cim.connector;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.InteractionSpec;
import javax.resource.cci.Record;
import javax.resource.cci.ResourceWarning;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public class CIMInteractionImpl implements Interaction
{

    private static final String CLOSED_ERROR = "Connection closed";
    private static final String INVALID_FUNCTION_ERROR = "Invalid function";
    private static final String INVALID_INPUT_ERROR = "Invalid input record for function";
    private static final String INVALID_OUTPUT_ERROR = "Invalid output record for function";

    protected CIMConnection _Connection;
    protected boolean _Valid;

    /**
     * Constructor for CIMInteractionImpl
     */
    public CIMInteractionImpl (Connection connection) throws ResourceException
    {

        super ();
        if (null == connection)
            throw new ResourceException ("null cannot be used as a connection object");
        else if (!connection.getClass ().isAssignableFrom (CIMConnection.class))
            throw new ResourceException ("object of class " + connection.getClass ().toGenericString () + " cannot be used as a connection object");
        else
            _Connection = (CIMConnection)connection;
        _Valid = true;
    }

    /**
     * @see Interaction#close()
     */
    public void close () throws ResourceException
    {
        _Connection = null;
        _Valid = false;
    }

    /**
     * @see Interaction#getConnection()
     */
    public Connection getConnection ()
    {
        return (_Connection);
    }

    protected Dataset<Row> readFile (SparkSession session, String filename) throws ResourceException
    {
        String[] files = filename.split (",");
        HashMap<String,String> options = new HashMap<> ();
        options.put ("path", filename);
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");

        return (session.read ().format ("ch.ninecode.cim").options (options).load (files));
    }

    /**
     * @see Interaction#execute(InteractionSpec, Record, Record)
     */
    public boolean execute (InteractionSpec ispec, Record input, Record output)
        throws ResourceException
    {
        boolean ret;

        ret = false;
        if (_Valid)
        {
            if ((null == ispec) || (!ispec.getClass ().isAssignableFrom (CIMInteractionSpecImpl.class)))
                throw new ResourceException (INVALID_FUNCTION_ERROR);
            else
            {
                CIMInteractionSpecImpl _spec = (CIMInteractionSpecImpl) ispec;
                switch (_spec.getFunctionName ())
                {
                    case CIMInteractionSpec.LIST_FILES:
                        if (output.getRecordName ().equals (CIMMappedRecord.OUTPUT))
                        {
                            ((CIMMappedRecord) output).clear ();
                            String directory = "/";
                            if ((null != input) && (null != ((CIMMappedRecord)input).get ("path")))
                                directory = ((CIMMappedRecord)input).get ("path").toString ();
                            try
                            {
                                Object deb = (null != input) ? ((CIMMappedRecord)input).get ("debug") : "false";
                                boolean debug = (null == deb) ? false : Boolean.valueOf (deb.toString ());

                                // build a file system configuration, including core-site.xml
                                Configuration hdfs_configuration = new Configuration ();
                                if (null == hdfs_configuration.getResource ("core-site.xml"))
                                {
                                    String hadoop_conf = System.getenv ("HADOOP_CONF_DIR");
                                    if (null != hadoop_conf)
                                    {
                                        Path site = new Path (hadoop_conf, "core-site.xml");
                                        File f = new File (site.toString ());
                                        if (f.exists () && !f.isDirectory ())
                                            hdfs_configuration.addResource (site);
                                    }
                                }

                                // get the file system
                                URI uri = FileSystem.getDefaultUri (hdfs_configuration);
                                // or: URI uri = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY));
                                FileSystem hdfs = FileSystem.get (uri, hdfs_configuration);
                                Path root = new Path (hdfs.getUri ().toString (), directory);

                                // form the response
                                JsonObjectBuilder response = Json.createObjectBuilder ();
                                response.add ("filesystem", uri.toString ());
                                response.add ("root", root.toString ());

                                if (debug)
                                {
                                    JsonObjectBuilder configuration = Json.createObjectBuilder ();
                                    for (Entry<String, String> pair : hdfs_configuration)
                                    {
                                        String key = pair.getKey ();
                                        String value = pair.getValue ();
                                        configuration.add (key, value);
                                    }
                                    response.add ("configuration", configuration);

                                    JsonObjectBuilder environment = Json.createObjectBuilder ();
                                    Map<String, String> env = System.getenv ();
                                    for (String key : env.keySet ())
                                    {
                                        String value = env.get (key);
                                        environment.add (key, value);
                                    }
                                    response.add ("environment", environment);
                                }

                                // read the list of files
                                FileStatus[] statuses = hdfs.listStatus (root);
                                JsonArrayBuilder files = Json.createArrayBuilder ();
                                String prefix = root.toString ();
                                for (FileStatus fs: statuses)
                                {
                                    String path = fs.getPath ().toString ();
                                    if (path.startsWith (prefix))
                                        path = path.substring (prefix.length ());
                                    JsonObjectBuilder file = Json.createObjectBuilder ();
                                    file.add ("path", path);
                                    file.add ("length", fs.getLen ());
                                    file.add ("modification_time", fs.getModificationTime ());
                                    file.add ("access_time", fs.getAccessTime ());
                                    file.add ("is_directory", fs.isDirectory ());
                                    files.add (file);
                                }
                                response.add ("files", files);
                                ((CIMMappedRecord) output).put ("files", response.build ());
                                ret = true;
                            }
                            catch (Exception exception)
                            {
                                throw new ResourceException ("problem", exception);
                            }
                        }
                        else
                            throw new ResourceException (INVALID_OUTPUT_ERROR);
                        break;

                    case CIMInteractionSpec.READ_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            if (output.getRecordName ().equals (CIMMappedRecord.OUTPUT))
                            {
                                ((CIMMappedRecord) output).clear ();
                                try
                                {
                                    String filename = (String)((CIMMappedRecord) input).get ("filename");
                                    SparkSession session = ((CIMConnection)getConnection ())._ManagedConnection._SparkSession;
                                    long num = readFile (session, filename).count ();
                                    ((CIMMappedRecord) output).put ("count", num);
                                    ret = true;
                                }
                                catch (Exception exception)
                                {
                                    throw new ResourceException ("problem", exception);
                                }
                            }
                            else
                                throw new ResourceException (INVALID_OUTPUT_ERROR);
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                    case CIMInteractionSpec.GET_STRING_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            if (output.getRecordName ().equals (CIMMappedRecord.OUTPUT))
                            {
                                ((CIMMappedRecord) output).clear ();
                                try
                                {
                                    CIMMappedRecord record = (CIMMappedRecord)input;
                                    CIMConnection connection = (CIMConnection)getConnection ();
                                    String filename = record.get ("filename").toString ();
                                    String cls = record.get ("class").toString ();
                                    String method = record.get ("method").toString ();
                                    SparkSession session = connection._ManagedConnection._SparkSession;
                                    Object jars = record.get ("jars");
                                    if (null != jars)
                                        for (String jar: jars.toString ().split (","))
                                            if (!session.sparkContext ().jars().contains (jar))
                                                session.sparkContext ().addJar (jar);
                                    StringBuilder args = new StringBuilder();
                                    for (Object key : record.keySet ())
                                        if ((key != "filename") && (key != "class") && (key != "method") && (key != "jars"))
                                        {
                                            args.append ((0 == args.length ()) ? "" : ",");
                                            args.append (key.toString ());
                                            args.append ("=");
                                            args.append (record.get (key).toString ());
                                        }
                                    try
                                    {
                                        Class<?> c = Class.forName (cls);
                                        Object _obj = c.newInstance();

                                        Method[] allMethods = c.getDeclaredMethods();
                                        for (Method _method : allMethods)
                                        {
                                            String name = _method.getName();
                                            if (name.equals (method))
                                            {
                                                try
                                                {
                                                    long num = readFile (session, filename).count ();
                                                    System.out.println ("" + num + " elements");
                                                    _method.setAccessible (true);
                                                    Object o = _method.invoke (_obj, session, args.toString());
                                                    String result = (String)o;
                                                    ((CIMMappedRecord) output).put ("result", result);
                                                    ret = true;
                                                }
                                                catch (InvocationTargetException ite)
                                                {
                                                    throw new ResourceException ("problem", ite);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                    catch (ClassNotFoundException cnfe)
                                    {
                                        throw new ResourceException ("problem", cnfe);
                                    }
                                }
                                catch (Exception exception)
                                {
                                    throw new ResourceException ("problem", exception);
                                }
                            }
                            else
                                throw new ResourceException (INVALID_OUTPUT_ERROR);
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                    default:
                        throw new ResourceException (INVALID_FUNCTION_ERROR);
                }
            }
        }
        else
            throw new ResourceException (CLOSED_ERROR);

        return (ret);
    }

    /**
     * @see Interaction#execute(InteractionSpec, Record)
     */
    public Record execute (InteractionSpec ispec, Record input) throws ResourceException
    {
        CIMResultSet ret;

        ret = null;
        if (_Valid)
        {
            if ((null == ispec) || (!ispec.getClass ().isAssignableFrom (CIMInteractionSpecImpl.class)))
                throw new ResourceException (INVALID_FUNCTION_ERROR);
            else
            {
                CIMInteractionSpecImpl _spec = (CIMInteractionSpecImpl) ispec;
                switch (_spec.getFunctionName ())
                {
                    case CIMInteractionSpec.GET_DATAFRAME_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            try
                            {
                                CIMMappedRecord record = (CIMMappedRecord)input;
                                String filename = record.get ("filename").toString ();
                                String query = record.get ("query").toString ();
                                SparkSession session = ((CIMConnection)getConnection ())._ManagedConnection._SparkSession;
                                Dataset<Row> elements = readFile (session, filename);
                                elements.count ();
                                Dataset<Row> result = session.sqlContext ().sql (query);
                                ret = new CIMResultSet (result.schema (), result.collectAsList ());
                            }
                            catch (Exception exception)
                            {
                                throw new ResourceException ("problem", exception);
                            }
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                    case CIMInteractionSpec.EXECUTE_METHOD_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            try
                            {
                                CIMMappedRecord record = (CIMMappedRecord)input;
                                CIMConnection connection = (CIMConnection)getConnection ();
                                String filename = record.get ("filename").toString ();
                                String cls = record.get ("class").toString ();
                                String method = record.get ("method").toString ();
                                SparkSession session = connection._ManagedConnection._SparkSession;
                                Object jars = record.get ("jars");
                                if (null != jars)
                                    for (String jar: jars.toString ().split (","))
                                        if (!session.sparkContext ().jars().contains (jar))
                                            session.sparkContext ().addJar (jar);
//                                ToDo: don't know the mapping from Java world to Scala world
//                                HashMap<String,String> map = new HashMap<String,String> ();
//                                for (Object key: record.keySet ())
//                                    if ((key != "filename") && (key != "class") && (key != "method"))
//                                        map.put (key.toString (), (String)record.get (key));
                                StringBuilder args = new StringBuilder ();
                                for (Object key: record.keySet ())
                                    if ((key != "filename") && (key != "class") && (key != "method") && (key != "jars"))
                                    {
                                        args.append ((0 == args.length ()) ? "" : ",");
                                        args.append (key.toString ());
                                        args.append ("=");
                                        args.append (record.get (key).toString ());
                                    }
                                try
                                {
                                    Class<?> c = Class.forName (cls);
                                    Object _obj = c.newInstance();

                                    Method[] allMethods = c.getDeclaredMethods();
                                    for (Method _method : allMethods)
                                    {
                                        String name = _method.getName();
                                        if (name.equals (method))
                                        {
                                            try
                                            {
                                                System.out.println ("readFile " + filename);
                                                long num = readFile (session, filename).count ();
                                                System.out.println ("" + num + " elements");
                                                _method.setAccessible (true);
                                                System.out.println (method + " (sc, sql, \"" + args + "\")");
                                                Object o = _method.invoke (_obj, session, args.toString ());
                                                System.out.println ("got a result");
                                                @SuppressWarnings ("unchecked")
                                                Dataset<Row> result = (Dataset<Row>)o;
                                                System.out.println ("it's a DataFrame with " + result.count () + " rows");
                                                ret = new CIMResultSet (result.schema (), result.collectAsList ());
                                            }
                                            catch (InvocationTargetException ite)
                                            {
                                                throw new ResourceException ("problem", ite);
                                            }
                                            break;
                                        }
                                    }
                                }
                                catch (ClassNotFoundException cnfe)
                                {
                                    throw new ResourceException ("problem", cnfe);
                                }
                            }
                            catch (Exception exception)
                            {
                                throw new ResourceException ("problem", exception);
                            }
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                }
            }

        }

        return (ret);
    }

    /**
     * @see Interaction#getWarnings()
     */
    public ResourceWarning getWarnings () throws ResourceException
    {
        return (null);
    }

    /**
     * @see Interaction#clearWarnings()
     */
    public void clearWarnings () throws ResourceException
    {
    }
}