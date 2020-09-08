package ch.ninecode.ts

import org.apache.spark.sql.SparkSession

import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkInitializer
import ch.ninecode.util.SparkOptions

// import com.intel.analytics.bigdl.utils.Engine

object TimeSeries extends SparkInitializer[TimeSeriesOptions] with Main
{
    def run (options: TimeSeriesOptions): Unit =
    {
        if (options.main_options.valid)
        {
            org.apache.log4j.LogManager.getLogger (getClass).setLevel (org.apache.log4j.Level.INFO)
            val session: SparkSession = createSession (options)
            //Engine.init
            time ("execution: %s seconds")
            {
                options.operation match
                {
                    case Operations.Statistics =>
                        val ts = TimeSeriesStats (session, options)
                        ts.run ()
                    case Operations.Meta =>
                        val meta = TimeSeriesMeta (session, options)
                        meta.run ()
                    case Operations.Model =>
                        val model = TimeSeriesModel (session, options)
                        model.makeDecisionTreeRegressorModel ()
                    case Operations.MetaModel =>
                        val model = TimeSeriesModel (session, options)
                        model.makeMetaDecisionTreeRegressorModel ()
                    case Operations.SimpleMetaModel =>
                        val model = TimeSeriesModel (session, options)
                        model.makeSingleMetaDecisionTreeRegressorModel ()
                    case Operations.Synthesize =>
                        val model = TimeSeriesModel (session, options)
                        if (options.classes.isEmpty)
                            model.generateSimpleTimeSeries (options.synthesis, options.start, options.end, options.period, options.yearly_kWh)
                        else
                            model.generateMetaTimeSeries (options.synthesis, options.start, options.end, options.period, options.yearly_kWh, options.classes)
                }
            }
        }
    }

    def main (args: Array[String])
    {
        val have = scala.util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error (s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit (1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set (
                jarForObject (com.datastax.spark.connector.mapper.ColumnMapper),
                jarForObject (TimeSeriesOptions ())
            ).toArray

            // initialize the default options
            val default = TimeSeriesOptions (
                main_options = MainOptions (application_name, application_version),
                spark_options = SparkOptions (jars = jars),
            )

            // parse the command line arguments
            new TimeSeriesOptionsParser (default).parse (args, default) match
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
