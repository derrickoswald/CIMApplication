package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import javax.resource.ConnectionFactoryDefinition;
import javax.resource.spi.TransactionSupport.TransactionSupportLevel;

import ch.ninecode.cim.connector.CIMConnectionFactory;
import ch.ninecode.cim.connector.CIMConnectionSpec;

@ConnectionFactoryDefinition
(
    name = "java:comp/env/eis/SparkConnectionFactory",
    description = "Connection factory for Spark",
    interfaceName = "ch.ninecode.cim.connector.CIMConnectionFactory",
    resourceAdapter = "#CIMConnector", // reference CIMConnector.rar in application.xml
    minPoolSize = 2,
    transactionSupport = TransactionSupportLevel.NoTransaction
)

@Stateless
@Path("/GridLabExport/{file}")
public class GridLabExport
{
    @Resource (lookup="openejb:Resource/CIMConnector.rar")
    CIMConnectionFactory factory;

    /**
     * Build a connection specification used by all the tests.
     * @return
     */
    CIMConnectionSpec remoteConfig ()
    {
        CIMConnectionSpec ret;

        ret = new CIMConnectionSpec ();
        ret.setUserName ("derrick"); // not currently used
        ret.setPassword ("secret"); // not currently used
        ret.getProperties ().put ("spark.driver.memory", "1g");
        ret.getProperties ().put ("spark.executor.memory", "4g");
        ret.getJars ().add ("/opt/apache-tomee-plus-1.7.4/apps/CIMApplication/lib/GridLAB-D-1.0-SNAPSHOT.jar");

        return (ret);
    }

    @GET
    @Path("{p:/?}{item:((.*)?)}")
    @Produces ({"text/plain", "application/json"})
    public String GetGridLABExport (@PathParam("file") String filename, @PathParam("item") String item)
    {
        String transformer = (null != item && !item.equals ("")) ? item : null;
        StringBuffer out = new StringBuffer ();
        out.append ("{\n" +
            "\"test\": true,\n" +
            "\"transformer\": \"" + transformer + "\"\n" +
        "}");
        return (out.toString ());
    }
}
