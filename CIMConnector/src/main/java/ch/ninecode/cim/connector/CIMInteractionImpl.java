package ch.ninecode.cim.connector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.InteractionSpec;
import javax.resource.cci.Record;
import javax.resource.cci.ResourceWarning;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.DataFrame;
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

    protected DataFrame readFile (SQLContext context, String filename) throws ResourceException
    {
        DataFrame element = context.read ().format ("ch.ninecode.cim").option ("StorageLevel", "MEMORY_AND_DISK_SER").load (filename);
        org.apache.spark.sql.execution.QueryExecution plan = element.queryExecution ();
        String test = plan.toString ();
//        res9: String =
//        == Parsed Logical Plan ==
//        Relation[sup#5101] CIMRelation
//
//        == Analyzed Logical Plan ==
//        sup: element
//        Relation[sup#5101] CIMRelation
//
//        == Optimized Logical Plan ==
//        Relation[sup#5101] CIMRelation
//
//        == Physical Plan ==
//        Scan CIMRelation[sup#5101] InputPaths: hdfs://sandbox:9000/data/NIS_CIM_Export_sias_current_20160816_V8_Bruegg.rdf
        if (!test.contains ("InputPaths"))
            throw new ResourceException ("input file not found: " + filename);

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
                                String filename = (String)((CIMMappedRecord) input).get ("filename");
                                String query = (String)((CIMMappedRecord) input).get ("query");
                                SQLContext sql = ((CIMConnection)getConnection ())._ManagedConnection._SqlContext;
                                readFile (sql, filename);
                                DataFrame result = sql.sql (query);
                                ret = new CIMResultSet (result.schema (), result.collect ());
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
                                                readFile (sql, filename);
                                                _method.setAccessible (true);
                                                Object o = _method.invoke (_obj, sc, sql, args);
                                                DataFrame result = (DataFrame)o;
                                                ret = new CIMResultSet (result.schema (), result.collect ());;
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