package ch.ninecode.cim.connector;

import javax.json.JsonStructure;
import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.Interaction;
import javax.resource.cci.InteractionSpec;
import javax.resource.cci.Record;
import javax.resource.cci.ResourceWarning;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public class CIMInteractionImpl implements Interaction
{

    private static final String INVALID_CONNECTION = "Invalid (%1$s) Connection";
    private static final String CLOSED_ERROR = "Connection closed";
    private static final String INVALID_FUNCTION_ERROR = "Invalid function";
    private static final String FUNCTION_MISSING_ERROR = "CIMFunction not provided";
    private static final String INVALID_INPUT_ERROR = "Invalid input record for function";
    private static final String NOT_IMPLEMENTED = "Not implemented";

    protected CIMConnection _Connection;
    protected boolean _Valid;

    /**
     * Constructor for CIMInteractionImpl
     */
    public CIMInteractionImpl (Connection connection) throws ResourceException
    {

        super ();
        if (null == connection)
            throw new ResourceException (String.format (INVALID_CONNECTION, "null"));
        else if (!connection.getClass ().isAssignableFrom (CIMConnection.class))
            throw new ResourceException (String.format (INVALID_CONNECTION, connection.getClass ().toGenericString ()));
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

    /**
     * @see Interaction#execute(InteractionSpec, Record, Record)
     */
    public boolean execute (InteractionSpec ispec, Record input, Record output)
        throws ResourceException
    {
        throw new ResourceException (NOT_IMPLEMENTED);
    }

    /**
     * @see Interaction#execute(InteractionSpec, Record)
     */
    public Record execute (InteractionSpec ispec, Record input) throws ResourceException
    {
        Record ret;

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
                    case CIMInteractionSpec.EXECUTE_CIM_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            try
                            {
                                CIMMappedRecord record = (CIMMappedRecord)input;
                                Object obj = record.get (CIMFunction.FUNCTION);
                                if (!(obj instanceof CIMFunction))
                                    throw new ResourceException (FUNCTION_MISSING_ERROR);
                                CIMFunction function = (CIMFunction)obj;
                                // set up function call
                                SparkSession session = ((CIMConnection)getConnection ())._ManagedConnection._SparkSession;
                                for (String jar: function.getJars ())
                                    if (!session.sparkContext ().jars().contains (jar))
                                        session.sparkContext ().addJar (jar);
                                switch (function.getReturnType ())
                                {
                                    case Dataset:
                                    {
                                        Dataset<Row> result = function.executeResultSet (session);
                                        ret = new CIMResultSet (result.schema (), result.collectAsList ());
                                        break;
                                    }
                                    case String:
                                    {
                                        String result = function.executeString (session);
                                        ret = new CIMMappedRecord ();
                                        ((CIMMappedRecord)ret).put (CIMFunction.RESULT, result);
                                        break;
                                    }
                                    case JSON:
                                    {
                                        JsonStructure result = function.executeJSON (session);
                                        ret = new CIMMappedRecord ();
                                        ((CIMMappedRecord)ret).put (CIMFunction.RESULT, result);
                                        break;
                                    }
                                }
                            }
                            catch (Exception exception)
                            {
                                throw new ResourceException (exception.getLocalizedMessage (), exception);
                            }
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                    case CIMInteractionSpec.GET_DATAFRAME_FUNCTION:
                        if (input.getRecordName ().equals (CIMMappedRecord.INPUT))
                            try
                            {
                                CIMMappedRecord record = (CIMMappedRecord)input;
                                String query = record.get (CIMFunction.QUERY).toString ();
                                SparkSession session = ((CIMConnection)getConnection ())._ManagedConnection._SparkSession;
                                Dataset<Row> result = session.sqlContext ().sql (query);
                                ret = new CIMResultSet (result.schema (), result.collectAsList ());
                            }
                            catch (Exception exception)
                            {
                                throw new ResourceException (exception.getLocalizedMessage (), exception);
                            }
                        else
                            throw new ResourceException (INVALID_INPUT_ERROR);
                        break;
                }
            }

        }
        else
            throw new ResourceException (CLOSED_ERROR);

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