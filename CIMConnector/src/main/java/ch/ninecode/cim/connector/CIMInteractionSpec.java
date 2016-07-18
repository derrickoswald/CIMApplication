package ch.ninecode.cim.connector;

import javax.resource.cci.InteractionSpec;

public interface CIMInteractionSpec extends InteractionSpec
{
    public static final String READ_FUNCTION = "read";
    public static final String GET_DATAFRAME_FUNCTION = "getDataFrame";

    public String getFunctionName ();

    public void setFunctionName (String functionName);
}