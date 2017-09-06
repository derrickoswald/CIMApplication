package ch.ninecode.cim.connector;

import javax.resource.cci.InteractionSpec;

public interface CIMInteractionSpec extends InteractionSpec
{
    static final String LIST_FILES = "listFiles";
    static final String READ_FUNCTION = "read";
    static final String GET_DATAFRAME_FUNCTION = "getDataFrame";
    static final String EXECUTE_METHOD_FUNCTION = "executeMethodReturningDataFrame";
    static final String GET_STRING_FUNCTION = "getString";
    static final String EXECUTE_CIM_FUNCTION = "executeCIMFunction";

    String getFunctionName ();

    void setFunctionName (String functionName);
}