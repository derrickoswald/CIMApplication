package ch.ninecode.cim.cimweb;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// was this simple Application defintion:
//     @ApplicationPath ("/cim")
//     public class ApplicationConfig extends Application
//     {
//         public Set<Class<?>> getClasses ()
//         {
//             return new HashSet<> (Arrays.asList (SimpleRESTPojo.class, SimpleRESTEJB.class));
//         }
//     }
// but this leads to:
//     com.fasterxml.jackson.databind.JsonMappingException: Could not find creator property with name 'id' (in class org.apache.spark.rdd.RDDOperationScope)
// because of differing versions of jackson (I think): Glassfish uses jackson 2.5.1 and Spark uses jackson 2.4.4

@ApplicationPath ("/cim")
public class ApplicationConfig extends Application
{
    public Set<Class<?>> getClasses ()
    {
        return new HashSet<> (Arrays.asList (SimpleRESTPojo.class, SimpleRESTEJB.class, EnergyConsumer.class, ShortCircuitCalculation.class));
    }
}

