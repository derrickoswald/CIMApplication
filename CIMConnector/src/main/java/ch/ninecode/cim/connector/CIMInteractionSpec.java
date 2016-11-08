package ch.ninecode.cim.connector;

import javax.resource.cci.InteractionSpec;

public interface CIMInteractionSpec extends InteractionSpec
{
    public static final String LIST_FILES = "listFiles";
    public static final String READ_FUNCTION = "read";
    public static final String GET_DATAFRAME_FUNCTION = "getDataFrame";
    public static final String EXECUTE_METHOD_FUNCTION = "executeMethodReturningDataFrame";
    public static final String GET_STRING_FUNCTION = "getString";

    public String getFunctionName ();

    public void setFunctionName (String functionName);
}