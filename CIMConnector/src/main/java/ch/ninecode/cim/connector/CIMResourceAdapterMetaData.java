package ch.ninecode.cim.connector;

import javax.resource.cci.ResourceAdapterMetaData;

public class CIMResourceAdapterMetaData implements ResourceAdapterMetaData
{
    private static final String ADAPTER_VERSION = "1.0";
    private static final String ADAPTER_VENDOR_NAME = "9code GmbH";
    private static final String ADAPTER_NAME = "CIM Spark Resource Adapter";
    private static final String ADAPTER_DESCRIPTION =
        "A resource adapter linking Spark and the CIMReader with the J2EE world.";
    private static final String SPEC_VERSION = "1.0";
    private static final String[] INTERACTION_SPECS_SUPPORTED =
        { "ch.ninecode.cim.connector.CIMInteractionSpecImpl" };

    public CIMResourceAdapterMetaData ()
    {
        super ();
    }

    @Override
    public String getAdapterName ()
    {
        return (ADAPTER_NAME);
    }

    @Override
    public String getAdapterShortDescription ()
    {
        return (ADAPTER_DESCRIPTION);
    }

    @Override
    public String getAdapterVendorName ()
    {
        return (ADAPTER_VENDOR_NAME);
    }

    @Override
    public String getAdapterVersion ()
    {
        return (ADAPTER_VERSION);
    }

    @Override
    public String[] getInteractionSpecsSupported ()
    {
        return (INTERACTION_SPECS_SUPPORTED);
    }

    @Override
    public String getSpecVersion ()
    {
        return (SPEC_VERSION);
    }

    @Override
    public boolean supportsExecuteWithInputAndOutputRecord ()
    {
        return (true);
    }

    @Override
    public boolean supportsExecuteWithInputRecordOnly ()
    {
        return (false);
    }

    @Override
    public boolean supportsLocalTransactionDemarcation ()
    {
        return (false);
    }
}
