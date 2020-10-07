package ch.ninecode.util

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.Element

/**
 * Sample main program for testing purposes.
 */
trait OptionsSuiteMain extends CIMInitializer[OptionsSuiteOptions] with Main with CIMRDD
{
    def run (options: OptionsSuiteOptions): Unit =
    {
        if (options.verbose)
            LogManager.getLogger (getClass).setLevel (Level.INFO)
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                implicit val session: SparkSession = createSession (options)
                readCIM (session, options)
                time ("execution: %s seconds")
                {
                    // do something
                    val elements = getOrElse[Element]
                    val s = elements.take (10).map (_.toString)
                    s.foreach (log.info)

                    val size = session.sparkContext.getPersistentRDDs.size
                    log.info (s"$size RDDs processed")
                    for (_ <- 1 to 100)
                        Thread.sleep (1000)
                }
            }
            else
                log.error ("no CIM files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error (s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit (1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Array (
                jarForObject (com.datastax.spark.connector.mapper.ColumnMapper)
            )

            // initialize the default options
            // val topology_options = CIMTopologyOptions (identify_islands = true)
            // val rop = CIMReaderOptions (topology = true, topology_options = topology_options)
            val rop = CIMReaderOptions ()
            val default = OptionsSuiteOptions (
                main_options = MainOptions (application_name, application_version),
                spark_options = SparkOptions (jars = jars),
                cim_options = rop.copy (options = rop.toMap)
            )

            // parse the command line arguments
            new OptionsSuiteOptionsParser (default).parse (args, default) match
            {
                case Some (options) =>
                    // execute the main program if everything checks out
                    run (options)
                    if (!options.main_options.unittest)
                        sys.exit (0)
                case None =>
                    sys.exit (1)
            }
        }
    }
}
