package ch.ninecode.cim.connector;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;

@Connector
(
    description = "Interface to CIM data in Apache Spark.",
    displayName = "CIMConnector",
    smallIcon = "/images/CIMConnector16.jpg",
    largeIcon = "/images/CIMConnector32.jpg",
    vendorName = "9code GmbH",
    eisType = "CIM Spark Connector",
    version = "0.1",
    licenseDescription =
    {
        "Copyright (c) 2016 9code GmbH\n" +
        "\n" +
        "Permission is hereby granted, free of charge, to any person\n" +
        "obtaining a copy of this software and associated documentation\n" +
        "files (the \"Software\"), to deal in the Software without\n" +
        "restriction, including without limitation the rights to use,\n" +
        "copy, modify, merge, publish, distribute, sublicense, and/or\n" +
        "sell copies of the Software, and to permit persons to whom the\n" +
        "Software is furnished to do so, subject to the following conditions:\n" +
        "\n" +
        "The above copyright notice and this permission notice shall be\n" +
        "included in all copies or substantial portions of the Software.\n" +
        "\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND,\n" +
        "EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF\n" +
        "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.\n" +
        "IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY\n" +
        "CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,\n" +
        "TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE\n" +
        "SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."
    },
    licenseRequired = false
//    AuthenticationMechanism[] authMechanisms() default {};
//    boolean reauthenticationSupport() default false;
//    SecurityPermission[] securityPermissions() default {};
//    TransactionSupport.TransactionSupportLevel transactionSupport() default TransactionSupport.TransactionSupportLevel.NoTransaction;
//    Class<? extends WorkContext>[] requiredWorkContexts() default {};
)
public class CIMResourceAdapter implements ResourceAdapter
{
    protected String _YarnConfigurationPath = "/home/derrick/spark-1.6.0-bin-hadoop2.6/conf";
    protected String _SparkDriverMemory = "1g";
    protected String _SparkExecutorMemory = "4g";
    protected String _InputFilePrefix = "hdfs://sandbox:8020/data/";
    protected String _InputFileSuffix = ".rdf";

    @Override
    public void endpointActivation (MessageEndpointFactory arg0, ActivationSpec arg1) throws ResourceException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void endpointDeactivation (MessageEndpointFactory arg0, ActivationSpec arg1)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public XAResource[] getXAResources (ActivationSpec[] arg0) throws ResourceException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void start (BootstrapContext arg0) throws ResourceAdapterInternalException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop ()
    {
        // TODO Auto-generated method stub

    }

    /**
     * Override equals to ensure singleton behaviour of the ResourceAdapter.
     * @param object the object to compare this to
     * @return <code>true</code> if this object is equal to that object.
     */
    @Override
    public boolean equals (Object object)
    {
        boolean ret = false;
        if (object instanceof CIMResourceAdapter)
        {
            CIMResourceAdapter that = (CIMResourceAdapter)object;
            ret = super.equals (that);
        }

        return (ret);
    }

    /**
     * Override hashCode to ensure singleton behaviour of the ResourceAdapter.
     * @return a constant hash value
     */
    @Override
    public int hashCode ()
    {
        return (42424242);
    }

    @ConfigProperty
    (
        type = String.class,
        description = "Path to Yarn configuration files such as core-site.xml and yarn-site.xml.",
        defaultValue = "/home/derrick/spark-1.6.0-bin-hadoop2.6/conf",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setYarnConfigurationPath (String path)
    {
        _YarnConfigurationPath = path;
    }

    public String getYarnConfigurationPath ()
    {
        return (_YarnConfigurationPath);
    }

    @ConfigProperty
    (
        type = String.class,
        description = "Setting for spark.driver.memory value.",
        defaultValue = "1g",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setSparkDriverMemory (String memory)
    {
        _SparkDriverMemory = memory;
    }

    public String getSparkDriverMemory ()
    {
        return (_SparkDriverMemory);
    }

    @ConfigProperty
    (
        type = String.class,
        description = "Setting for spark.executor.memory value.",
        defaultValue = "4g",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setSparkExecutorMemory (String memory)
    {
        _SparkExecutorMemory = memory;
    }

    public String getSparkExecutorMemory ()
    {
        return (_SparkExecutorMemory);
    }

    @ConfigProperty
    (
        type = String.class,
        description = "Input file prefix - where machines on the cluster should look for input files.",
        defaultValue = "hdfs://sandbox:8020/data/",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setInputFilePrefix (String prefix)
    {
        _InputFilePrefix = prefix;
    }

    public String getInputFilePrefix ()
    {
        return (_InputFilePrefix);
    }

    @ConfigProperty
    (
        type = String.class,
        description = "Input file suffix - input file type or extension.",
        defaultValue = ".rdf",
        ignore = false,
        supportsDynamicUpdates = false,
        confidential = false
    )
    public void setInputFileSuffix (String suffix)
    {
        _InputFileSuffix = suffix;
    }

    public String getInputFileSuffix ()
    {
        return (_InputFileSuffix);
    }

}
