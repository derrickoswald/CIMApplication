package ch.ninecode.cim.connector;

import javax.resource.cci.InteractionSpec;

public interface CIMInteractionSpec extends InteractionSpec
{
    public static final String SAY_HELLO_FUNCTION = "sayHello";
    public static final String READ_FUNCTION = "read";

    public String getFunctionName ();

    public void setFunctionName (String functionName);
}