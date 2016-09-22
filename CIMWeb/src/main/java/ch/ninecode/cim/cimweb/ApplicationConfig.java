package ch.ninecode.cim.cimweb;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
                SimpleRESTPojo.class,
                SimpleRESTEJB.class,
                EnergyConsumer.class,
                ShortCircuitCalculation.class,
                GridLabExport.class,
                Spatial.class
            )
        );
    }
}

