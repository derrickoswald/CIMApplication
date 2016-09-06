package ch.ninecode.cim.cimweb;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
    public Response GetGridLABExport (@PathParam("file") String filename, @PathParam("item") String item)
    {
        String transformer = (null != item && !item.equals ("")) ? item : null;
        StringBuffer out = new StringBuffer ();
        String test =
"        module tape;\n" +
"        module powerflow\n" +
"        {\n" +
"            solver_method NR;\n" +
"            default_maximum_voltage_error 10e-6;\n" +
"            NR_iteration_limit 5000;\n" +
"            NR_superLU_procs 16;\n" +
"            nominal_frequency 50;\n" +
"        };\n" +
"\n" +
"        clock\n" +
"        {\n" +
"            timezone GMT0+1;\n" +
"            starttime '2013-12-10 12:00:00';\n" +
"            stoptime '2013-12-10 15:00:00';\n" +
"        };\n" +
"\n" +
"        class player\n" +
"        {\n" +
"            complex value;\n" +
"        };\n" +
"\n" +
"        object voltdump\n" +
"        {\n" +
"            filename voltdump.csv;\n" +
"            mode polar;\n" +
"            runtime '2014-08-19 13:00:00';\n" +
"        };\n" +
"\n" +
"        object line_configuration\n" +
"        {\n" +
"            name \"line_3x25Cu/25\";\n" +
"            z11 0.727+0.08j Ohm/km;\n" +
"            z12 0.0+0.0j Ohm/km;\n" +
"            z13 0.0+0.0j Ohm/km;\n" +
"            z21 0.0+0.0j Ohm/km;\n" +
"            z22 0.727+0.08j Ohm/km;\n" +
"            z23 0.0+0.0j Ohm/km;\n" +
"            z31 0.0+0.0j Ohm/km;\n" +
"            z32 0.0+0.0j Ohm/km;\n" +
"            z33 0.727+0.08j Ohm/km;\n" +
"        };\n" +
"\n" +
"        object line_configuration\n" +
"        {\n" +
"            name \"line_3x95Cu/95\";\n" +
"            z11 0.193+0.07j Ohm/km;\n" +
"            z12 0.0+0.0j Ohm/km;\n" +
"            z13 0.0+0.0j Ohm/km;\n" +
"            z21 0.0+0.0j Ohm/km;\n" +
"            z22 0.193+0.07j Ohm/km;\n" +
"            z23 0.0+0.0j Ohm/km;\n" +
"            z31 0.0+0.0j Ohm/km;\n" +
"            z32 0.0+0.0j Ohm/km;\n" +
"            z33 0.193+0.07j Ohm/km;\n" +
"        };\n" +
"\n" +
"        object transformer_configuration\n" +
"        {\n" +
"            name transformer;\n" +
"            connect_type WYE_WYE;\n" +
"            install_type PADMOUNT;\n" +
"            power_rating 500;\n" +
"            primary_voltage 4800;\n" +
"            secondary_voltage 400;\n" +
"            resistance 0.011;\n" +
"            reactance 0.02;\n" +
"        };\n" +
"\n" +
"        object node\n" +
"        {\n" +
"            name \"HAS42130\";\n" +
"            phases \"ABCN\";\n" +
"            bustype PQ;\n" +
"            nominal_voltage 400V;\n" +
"        }\n" +
"\n" +
"        object meter\n" +
"        {\n" +
"            name \"HAS42130_0\";\n" +
"            phases \"ABCN\";\n" +
"            bustype PQ;\n" +
"            nominal_voltage 400V;\n" +
"        };\n" +
"\n" +
"        object underground_line\n" +
"        {\n" +
"            name \"HAS42130_0_stub\";\n" +
"            phases \"ABCN\";\n" +
"            from \"HAS42130\";\n" +
"            to \"HAS42130_0\";\n" +
"            length 25m;\n" +
"            configuration \"line_3x25Cu/25\";\n" +
"        };";

        out.append (test);

        return (Response.ok (out.toString (), MediaType.APPLICATION_OCTET_STREAM)
            .header ("content-disposition", "attachment; filename =" + "gridlabd.data")
            .build ());
    }
}
