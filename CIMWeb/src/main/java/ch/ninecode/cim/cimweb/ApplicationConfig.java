package ch.ninecode.cim.cimweb;

//import javax.annotation.sql.DataSourceDefinition;
//import javax.resource.ConnectionFactoryDefinition;
//import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// This is only available in J2EE7:

//@ConnectionFactoryDefinition
//(
//    description = "Connection factory for Spark connection using CIMConnector",
//    name = "java:app/eis/SparkConnectionFactory",
//    resourceAdapter = "CIMConnector", // reference CIMConnector.rar in application.xml
//    interfaceName = "ch.ninecode.cim.connector.CIMConnectionFactory",
//    transactionSupport = TransactionSupportLevel.NoTransaction
//)
//
//@DataSourceDefinition
//(
//    description = "JDBC connection factory to time series database",
//    name = "java:app/jdbc/TimeSeries",
//    className = "org.sqlite.JDBC",
//    properties = { "JdbcUrl=jdbc:sqlite:/opt/apache-tomee-plus-7.0.1/database/timeseries.db" }
//)

@ApplicationPath ("cim/")
public class ApplicationConfig extends Application
{
    /**
     * Get the list of known handler classes.
     * This list should inlude all classes with @Path annotation.
     * @return the set of url handler classes
     */
    public Set<Class<?>> getClasses ()
    {
        return new HashSet<>
        (
            Arrays.asList
            (
                Ping.class,
                ListFiles.class,
                SimpleRESTEJB.class,
                EnergyConsumer.class,
                GridLabExport.class,
                ShortCircuitCalculation.class,
                Spatial.class,
                TimeSeries.class,
                Visualize.class
            )
        );
    }
}

