package ch.ninecode.cim.connector;

import java.util.HashMap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

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
                            try
                            {
                                CIMConnection connection = (CIMConnection)getConnection ();
                                Configuration hdfs_configuration = new Configuration ();
                                hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
                                hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
                                String prefix = connection._ManagedConnection._Adapter.getInputFilePrefix ();
                                String suffix = connection._ManagedConnection._Adapter.getInputFileSuffix ();
                                FileSystem hdfs = FileSystem.get (URI.create (prefix), hdfs_configuration);
                                Path root = new Path (prefix);
                                RemoteIterator<LocatedFileStatus> iterator = hdfs.listFiles (root, false); // ToDo: recursive
                                StringBuilder sb = new StringBuilder ();
                                sb.append (
                                    "{\n" +
                                    "    \"files\":\n" +
                                    "    [\n");
                                boolean have = false;
                                while (iterator.hasNext())
                                {
                                    // "LocatedFileStatus{path=hdfs://sandbox:8020/data/KS_Leistungen.csv; isDirectory=false; length=403242; replication=1; blocksize=134217728; modification_time=1478602451352; access_time=1478607251538; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"
                                    // "LocatedFileStatus{path=hdfs://sandbox:8020/data/NIS_CIM_Export_sias_current_20160816_V9_Kiental.rdf; isDirectory=false; length=14360795; replication=1; blocksize=134217728; modification_time=1478607196243; access_time=1478607196018; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"

                                    LocatedFileStatus fs = iterator.next ();
                                    String path = fs.getPath ().toString ();
                                    if (path.startsWith (prefix))
                                        path = path.substring (prefix.length ());
                                    if (path.endsWith (suffix))
                                        path = path.substring (0, path.length () - suffix.length ());
                                    if (have)
                                        sb.append (",\n");
                                    sb.append ("        {\n");
                                    sb.append ("            \"path\": ");
                                    sb.append ("\"");
                                    sb.append (path);
                                    sb.append ("\",\n");
                                    sb.append ("            \"length\": ");
                                    sb.append (fs.getLen ());
                                    sb.append (",\n");
                                    sb.append ("            \"modification_time\": ");
                                    sb.append (fs.getModificationTime ());
                                    sb.append (",\n");
                                    sb.append ("            \"access_time\": ");
                                    sb.append (fs.getAccessTime ());
                                    sb.append ("\n");
                                    sb.append ("        }");
                                    have = true;
                                }
                                sb.append (
                                    "\n" +
                                    "    ]\n" +
                                    "}");
                                ((CIMMappedRecord) output).put ("files", sb.toString ());
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
                    case CIMInteractionSpec.GET_DATASET_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            try
                            {
                                CIMMappedRecord record = (CIMMappedRecord)input;
                                String filename = record.get ("filename").toString ();
                                String query = record.get ("query").toString ();
                                SQLContext sql = ((CIMConnection)getConnection ())._ManagedConnection._SqlContext;
                                readFile (sql, filename);
                                Dataset<Row> result = sql.sql (query);
                                ret = new CIMResultSet (result.schema (), (Row[])result.collect ());
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
                                                Dataset<Row> result = (Dataset<Row>)o;
                                                System.out.println ("it's a Dataset<Row> with " + result.count () + " rows");
                                                ret = new CIMResultSet (result.schema (), (Row[])result.collect ());;
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