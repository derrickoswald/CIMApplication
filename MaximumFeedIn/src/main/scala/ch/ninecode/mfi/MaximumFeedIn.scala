package ch.ninecode.mfi

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.util.CIMInitializer
import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Util

object MaximumFeedIn extends CIMInitializer[EinspeiseleistungOptions] with Main
{
    def run (options: EinspeiseleistungOptions): Unit =
    {
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                if (options.verbose)
                    LogManager.getLogger(getClass).setLevel(Level.INFO)
                val session: SparkSession = createSession(options)
                time("execution: %s seconds")
                {
                    val eins = Einspeiseleistung(session, options)
                    val _ = eins.run()
                }
            }
            else
                log.error("no CIM files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error(s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit(1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set(
                jarForObject(new DefaultSource()),
                jarForObject(EinspeiseleistungOptions())
            ).toArray

            // compose the classes to be registered with Kryo
            val kryo = Array.concat(
                // register CIMReader classes
                CIMClasses.list,
                // register Einspeiseleistung classes
                Einspeiseleistung.classes,
                // register GridLAB-D classes
                GridLABD.classes,
                // register Net classes
                Net.classes,
                // register Util classes
                Util.classes)

            // initialize the default options
            val temp = EinspeiseleistungOptions()
            val default = EinspeiseleistungOptions(
                main_options = MainOptions(application_name, application_version),
                spark_options = SparkOptions(jars = jars, kryo = kryo),
                cim_options = temp.cim_options.copy(options = temp.cim_options.toMap)
            )

            // parse the command line arguments
            new EinspeiseleistungOptionsParser(default).parse(args, default) match
            {
                case Some(options) =>
                    // execute the main program if everything checks out
                    run(options)
                    if (!options.main_options.unittest)
                        sys.exit(0)
                case None =>
                    sys.exit(1)
            }
        }
    }

}
