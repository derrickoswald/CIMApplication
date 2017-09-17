package ch.ninecode.cim.cimweb

//import javax.annotation.sql.DataSourceDefinition;
//import javax.resource.ConnectionFactoryDefinition;
//import javax.resource.spi.TransactionSupport.TransactionSupportLevel;
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application
import java.util

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

//    description = "JDBC connection factory to time series database",
//    name = "java:app/jdbc/TimeSeries",
//    className = "org.sqlite.JDBC",
//    properties = { "JdbcUrl=jdbc:sqlite:/opt/apache-tomee-plus-7.0.1/database/timeseries.db" }


@ApplicationPath ("cim/")
class ApplicationConfig extends Application
{
    /**
     * Get the list of known handler classes.
     * This list should include all classes with @Path annotation.
     *
     * @return the set of URL handler classes
     */
    override def getClasses: util.Set[Class[_]] =
    {
        new util.HashSet[Class[_]](util.Arrays.asList (
            classOf[Ping],
            classOf[ListFiles],
            classOf[GetFile],
            classOf[LoadFile],
            classOf[SimpleRESTEJB],
            classOf[EnergyConsumer],
            classOf[GridLabExport],
            classOf[ShortCircuitCalculation],
            classOf[Spatial],
            classOf[TimeSeries],
            classOf[Visualize]))
    }
}

