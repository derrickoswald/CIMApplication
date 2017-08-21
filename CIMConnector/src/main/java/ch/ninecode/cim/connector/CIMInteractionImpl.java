package ch.ninecode.cim.connector;

import java.util.HashMap;
import java.util.Iterator;
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
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.spark.SparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;

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
        if ((null == connection) || (!connection.getClass ().isAssignableFrom (CIMConnection.class)))
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

    protected Dataset<Row> readFile (SQLContext context, String filename) throws ResourceException
    {
        String[] files = filename.split (",");
        HashMap<String,String> options = new HashMap<> ();
        options.put ("path", filename);
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "true");
        options.put ("ch.ninecode.cim.do_join", "true");
        Dataset<Row> element = context.read ().format ("ch.ninecode.cim").options (options).load (files);

        return (element);
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
                            try
                            {
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
// debug:
                                JsonObjectBuilder configuration = Json.createObjectBuilder ();
                                Iterator<Entry<String, String>> i = hdfs_configuration.iterator ();
                                while (i.hasNext())
                                {
                                    Entry<String, String> pair = i.next ();
                                    String key = pair.getKey ();
                                    String value = pair.getValue ();
                                    configuration.add (key, value);
                                }
                                response.add ("configuration", configuration);

// debug:
                                JsonObjectBuilder environment = Json.createObjectBuilder ();
                                Map<String, String> env = System.getenv ();
                                for (String key: env.keySet ())
                                {
                                    String value = env.get (key);
                                    environment.add (key, value);
                                }
                                response.add ("environment", environment);

                                // read the list of files
                                RemoteIterator<LocatedFileStatus> iterator = hdfs.listFiles (root, false); // ToDo: handle recursive
                                JsonArrayBuilder files = Json.createArrayBuilder ();
                                String prefix = root.toString ();
                                while (iterator.hasNext())
                                {
                                    // "LocatedFileStatus{path=hdfs://sandbox:8020/data/KS_Leistungen.csv; isDirectory=false; length=403242; replication=1; blocksize=134217728; modification_time=1478602451352; access_time=1478607251538; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"
                                    // "LocatedFileStatus{path=hdfs://sandbox:8020/data/NIS_CIM_Export_sias_current_20160816_V9_Kiental.rdf; isDirectory=false; length=14360795; replication=1; blocksize=134217728; modification_time=1478607196243; access_time=1478607196018; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"

                                    LocatedFileStatus fs = iterator.next ();
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
                                    SQLContext sql = ((CIMConnection)getConnection ())._ManagedConnection._SqlContext;
                                    long num = readFile (sql, filename).count ();
                                    ((CIMMappedRecord) output).put ("count", new Long (num));
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
                                    SparkContext sc = connection._ManagedConnection._SparkContext;
                                    SQLContext sql = connection._ManagedConnection._SqlContext;
                                    Object jars = record.get ("jars");
                                    if (null != jars)
                                        for (String jar: jars.toString ().split (","))
                                            if (!sc.jars().contains (jar))
                                                sc.addJar (jar);
                                    String args = "";
                                    for (Object key: record.keySet ())
                                        if ((key != "filename") && (key != "class") && (key != "method"))
                                            args +=
                                                ((0 == args.length ()) ? "" : ",")
                                                + key.toString ()
                                                + "="
                                                + record.get (key).toString ();
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
                                                    readFile (sql, filename);
                                                    _method.setAccessible (true);
                                                    Object o = _method.invoke (_obj, sc, sql, args);
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
                                    catch (InstantiationException ie)
                                    {
                                        throw new ResourceException ("problem", ie);
                                    }
                                    catch (IllegalAccessException iae)
                                    {
                                        throw new ResourceException ("problem", iae);
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
                                SQLContext sql = ((CIMConnection)getConnection ())._ManagedConnection._SqlContext;
                                readFile (sql, filename);
                                Dataset<Row> result = sql.sql (query);
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
                                SparkContext sc = connection._ManagedConnection._SparkContext;
                                SQLContext sql = connection._ManagedConnection._SqlContext;
                                Object jars = record.get ("jars");
                                if (null != jars)
                                    for (String jar: jars.toString ().split (","))
                                        if (!sc.jars().contains (jar))
                                            sc.addJar (jar);
//                                ToDo: don't know the mapping from Java world to Scala world
//                                HashMap<String,String> map = new HashMap<String,String> ();
//                                for (Object key: record.keySet ())
//                                    if ((key != "filename") && (key != "class") && (key != "method"))
//                                        map.put (key.toString (), (String)record.get (key));
                                String args = "";
                                for (Object key: record.keySet ())
                                    if ((key != "filename") && (key != "class") && (key != "method"))
                                        args +=
                                            ((0 == args.length ()) ? "" : ",")
                                            + key.toString ()
                                            + "="
                                            + record.get (key).toString ();
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
                                                readFile (sql, filename);
                                                _method.setAccessible (true);
                                                System.out.println (method + " (sc, sql, \"" + args + "\")");
                                                Object o = _method.invoke (_obj, sc, sql, args);
                                                System.out.println ("got a result");
                                                @SuppressWarnings ("unchecked")
                                                Dataset<Row> result = (Dataset<Row>)o;
                                                System.out.println ("it's a DataFrame with " + result.count () + " rows");
                                                ret = new CIMResultSet (result.schema (), result.collectAsList ());;
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
                                catch (InstantiationException ie)
                                {
                                    throw new ResourceException ("problem", ie);
                                }
                                catch (IllegalAccessException iae)
                                {
                                    throw new ResourceException ("problem", iae);
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