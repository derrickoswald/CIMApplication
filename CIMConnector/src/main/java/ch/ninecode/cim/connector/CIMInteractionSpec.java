package ch.ninecode.cim.connector;

import javax.resource.cci.InteractionSpec;

public interface CIMInteractionSpec extends InteractionSpec
{
    String GET_DATAFRAME_FUNCTION = "getDataFrame";
    String EXECUTE_CIM_FUNCTION = "executeCIMFunction";

    String getFunctionName ();

    void setFunctionName (String functionName);
}