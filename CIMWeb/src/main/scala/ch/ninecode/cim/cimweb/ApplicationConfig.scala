package ch.ninecode.cim.cimweb

import java.util

import javax.resource.ConnectionFactoryDefinition
import javax.resource.spi.TransactionSupport.TransactionSupportLevel
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@ConnectionFactoryDefinition (
    description = "Connection factory for Spark connection using CIMConnector",
    name = "java:comp/env/eis/SparkConnectionFactory",
    resourceAdapter = "#CIMConnector", // reference CIMConnector.rar in application.xml
    interfaceName = "ch.ninecode.cim.connector.CIMConnectionFactory",
    transactionSupport = TransactionSupportLevel.NoTransaction
)
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
            classOf[Estimation],
            classOf[FileOperations],
            classOf[Ingest],
            classOf[LoadFile],
            classOf[Ping],
            classOf[Pong],
            classOf[Query],
            classOf[ShortCircuitCalculation],
            classOf[Spatial],
            classOf[View]))
    }
}
