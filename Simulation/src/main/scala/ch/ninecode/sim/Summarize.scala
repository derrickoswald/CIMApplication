package ch.ninecode.sim

import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import com.datastax.driver.core.ResultSet

import scala.reflect.runtime.universe.TypeTag
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DateType
import org.slf4j.Logger
import org.slf4j.LoggerFactory



/**
 * Summarize the simulation.
 *
 * Comupte various quality factors for the network after running a simulation.
 *
 * @param spark   The Spark session
 * @param options The simulation options. Note: Currently only the verbose option is used.
 */
case class Summarize (spark: SparkSession, options: SimulationOptions)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    def logInfo (msg: => String): Unit = if (log.isInfoEnabled && options.unittest) log.info (msg)

    def show (dataframe: DataFrame, records: Int = 5): Unit = if (options.unittest) dataframe.show (records)

    /**
     * Utilization
     *
     * Utilization is the value of an element divided by the maximum rated value.
     * For cables, this is the current divided by the ratedCurrent (A).
     * For transformers, this would be the power output divided by the ratedS (VA).
     */
    def utilization (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Utilization")
        val simulated_current_values = access.raw_values
            .filter ("type = 'current'")
            .cache
        logInfo ("""Utilization: %d simulated current values to process""".format (simulated_current_values.count))
        show (simulated_current_values)

        val lines = access.geojson_lines

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        def maxCurrent[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse ("ratedCurrent", "1.0").toDouble)

        val cables = simulated_current_values
            .withColumn ("current", magnitude [Double, Double].apply (simulated_current_values ("real_a"), simulated_current_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                lines,
                Seq ("simulation", "mrid"))
        val ratedCables = cables
            .withColumn ("ratedCurrent", maxCurrent [Map[String, String]].apply (cables ("properties")))
            .drop ("properties")
        val utilization = ratedCables
            .withColumn ("utilization", round (ratedCables ("current") / ratedCables ("ratedCurrent") * 100.0 * 100.0) / 100.0)
        logInfo ("""Utilization: %d cable records to process""".format (utilization.count))
        show (utilization)

        { // open a scope to avoid variable name clashes
            val mrid = utilization.schema.fieldIndex ("mrid")
            val typ = utilization.schema.fieldIndex ("type")
            val period = utilization.schema.fieldIndex ("period")
            val time = utilization.schema.fieldIndex ("time")
            val current = utilization.schema.fieldIndex ("current")
            val ratedCurrent = utilization.schema.fieldIndex ("ratedCurrent")
            val percent = utilization.schema.fieldIndex ("utilization")
            val simulation = utilization.schema.fieldIndex ("simulation")
            val transformer = utilization.schema.fieldIndex ("transformer")

            val work = utilization.rdd.map (row ⇒ (row.getString (mrid), row.getString (typ), row.getInt (period), row.getTimestamp (time), row.getDouble (current), row.getDouble (ratedCurrent), row.getDouble (percent), "A÷A×100", row.getString (transformer), row.getString (simulation))).cache

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "utilization",
                SomeColumns ("mrid", "type", "period", "time", "value", "reference", "utilization", "units", "transformer", "simulation"))
            log.info ("""Utilization: utilization records saved to %s.utilization""".format (access.output_keyspace))
        }

        val summary = utilization
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .withColumn ("date", utilization ("time").cast (DateType))
            .drop ("time")
            .groupBy ("mrid", "type", "date", "transformer", "simulation")
            .agg (
                "utilization" → "min",
                "utilization" → "avg",
                "utilization" → "max"
            )
            .withColumnRenamed ("min(utilization)", "min_utilization")
            .withColumnRenamed ("avg(utilization)", "avg_utilization")
            .withColumnRenamed ("max(utilization)", "max_utilization")
        logInfo ("""Utilization: %d daily summaries calculated""".format (summary.count))
        show (summary)

        {
            val mrid = summary.schema.fieldIndex ("mrid")
            val typ = summary.schema.fieldIndex ("type")
            val date = summary.schema.fieldIndex ("date")
            val min_utilization = summary.schema.fieldIndex ("min_utilization")
            val avg_utilization = summary.schema.fieldIndex ("avg_utilization")
            val max_utilization = summary.schema.fieldIndex ("max_utilization")
            val simulation = summary.schema.fieldIndex ("simulation")
            val transformer = summary.schema.fieldIndex ("transformer")

            val work = summary.rdd.map (row ⇒ (row.getString (mrid), row.getString (typ), row.getDate (date), row.getDouble (min_utilization), row.getDouble (avg_utilization), row.getDouble (max_utilization), "A÷A×100", row.getString (transformer), row.getString (simulation))).cache

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "utilization_summary_by_day",
                SomeColumns ("mrid", "type", "date", "min_utilization", "avg_utilization", "max_utilization", "units", "transformer", "simulation"))
            log.info ("""Utilization: utilization summary records saved to %s.utilization_summary_by_day""".format (access.output_keyspace))
        }

        val trafokreise = utilization
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("period")
            .withColumn ("date", utilization ("time").cast (DateType))
            .drop ("time")
            .groupBy ("type", "date", "transformer", "simulation")
            .agg (
                "utilization" → "min",
                "utilization" → "avg",
                "utilization" → "max"
            )
            .withColumnRenamed ("min(utilization)", "min_utilization")
            .withColumnRenamed ("avg(utilization)", "avg_utilization")
            .withColumnRenamed ("max(utilization)", "max_utilization")
        logInfo ("""Utilization: %d transformer area daily summaries calculated""".format (trafokreise.count))
        show (trafokreise)

        {
            val typ = trafokreise.schema.fieldIndex ("type")
            val date = trafokreise.schema.fieldIndex ("date")
            val min_utilization = trafokreise.schema.fieldIndex ("min_utilization")
            val avg_utilization = trafokreise.schema.fieldIndex ("avg_utilization")
            val max_utilization = trafokreise.schema.fieldIndex ("max_utilization")
            val simulation = trafokreise.schema.fieldIndex ("simulation")
            val transformer = trafokreise.schema.fieldIndex ("transformer")

            val work = trafokreise.rdd.map (row ⇒ (row.getString (transformer), row.getString (typ), row.getDate (date), row.getDouble (min_utilization), row.getDouble (avg_utilization), row.getDouble (max_utilization), "A÷A×100", row.getString (simulation))).cache

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "utilization_summary_by_day_by_transformer",
                SomeColumns ("mrid", "type", "date", "min_utilization", "avg_utilization", "max_utilization", "units", "simulation"))
            log.info ("""Utilization: transformer area utilization summary records saved to %s.utilization_summary_by_day_by_transformer""".format (access.output_keyspace))
        }

        // do daily 1 month, 3 month, 6 month and 12 month historical series
        val timeseries = utilization
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("period")
            .withColumn ("date", utilization ("time").cast (DateType))
            .drop ("time")
            .groupBy ("mrid", "type", "date", "transformer", "simulation")
            .agg (
                "utilization" → "min",
                "utilization" → "avg",
                "utilization" → "max"
            )
            .withColumnRenamed ("min(utilization)", "min_utilization")
            .withColumnRenamed ("avg(utilization)", "avg_utilization")
            .withColumnRenamed ("max(utilization)", "max_utilization")
            .union (trafokreise.withColumn ("mrid", trafokreise ("transformer"))
                // reorder the columns because union is brain-dead
                .select ("mrid", "type", "date", "transformer", "simulation", "min_utilization", "avg_utilization", "max_utilization"))
            .orderBy ("simulation", "type", "transformer", "mrid", "date")
            .cache
        logInfo ("""Utilization: %d time series values""".format (timeseries.count))
        show (timeseries)

        {
            val mrid = timeseries.schema.fieldIndex ("mrid")
            val typ = timeseries.schema.fieldIndex ("type")
            val date = timeseries.schema.fieldIndex ("date")
            val min_utilization = timeseries.schema.fieldIndex ("min_utilization")
            val avg_utilization = timeseries.schema.fieldIndex ("avg_utilization")
            val max_utilization = timeseries.schema.fieldIndex ("max_utilization")
            val simulation = timeseries.schema.fieldIndex ("simulation")
            val transformer = timeseries.schema.fieldIndex ("transformer")

            //case class Record (mrid: String, `type`: String, period: Long, date: Date, min_utilization: Double, avg_utilization: Double, max_utilization: Double, simulation: String, transformer: String)
            type Record = (String, String, Long, Date, Double, Double, Double, String, String)
            trait History
            {
                val size: Int
                val minvalues: Array[Double]
                val avgvalues: Array[Double]
                val maxvalues: Array[Double]
                var level: Int = 0
                var simulation: String = ""
                var `type`: String = ""
                var transformer: String = ""
                var mrid: String = ""
                var date: Date = _
            }
            case class History30
            (
                size: Int = 30,
                minvalues: Array[Double] = Array.ofDim [Double](30),
                avgvalues: Array[Double] = Array.ofDim [Double](30),
                maxvalues: Array[Double] = Array.ofDim [Double](30))
                extends History
            case class History90
            (
                size: Int = 90,
                minvalues: Array[Double] = Array.ofDim [Double](90),
                avgvalues: Array[Double] = Array.ofDim [Double](90),
                maxvalues: Array[Double] = Array.ofDim [Double](90))
                extends History
            case class History180
            (
                size: Int = 180,
                minvalues: Array[Double] = Array.ofDim [Double](180),
                avgvalues: Array[Double] = Array.ofDim [Double](180),
                maxvalues: Array[Double] = Array.ofDim [Double](180))
                extends History
            case class History365
            (
                size: Int = 365,
                minvalues: Array[Double] = Array.ofDim [Double](365),
                avgvalues: Array[Double] = Array.ofDim [Double](365),
                maxvalues: Array[Double] = Array.ofDim [Double](365))
                extends History

            def emit (history: History): List[Record] =
            {
                val min = history.minvalues.slice (0, history.level).reduce ((a: Double, b: Double) ⇒ if (a < b) a else b)
                val avg = history.avgvalues.slice (0, history.level).sum / history.size
                val max = history.maxvalues.slice (0, history.level).reduce ((a: Double, b: Double) ⇒ if (a > b) a else b)
                List (/*Record*/ (history.mrid, history.`type`, history.size * 24L * 60L * 60L * 1000L, history.date, min, avg, max, history.simulation, history.transformer))
            }

            def flush (row: Row, history: History): List[Record] =
            {
                val ret = if (history.simulation == "") // initial call?
                    List ()
                else
                    emit (history)
                history.level = 0
                for (i ← 0 until history.size)
                {
                    history.minvalues (i) = 0.0
                    history.avgvalues (i) = 0.0
                    history.maxvalues (i) = 0.0
                }
                history.simulation = row.getString (simulation)
                history.transformer = row.getString (transformer)
                history.`type` = row.getString (typ)
                history.mrid = row.getString (mrid)
                ret
            }

            def update (row: Row, history: History)
            {
                val index = history.level % history.size // where to store the current value
                history.minvalues (index) = row.getDouble (min_utilization) // push the current values into the arrays
                history.avgvalues (index) = row.getDouble (avg_utilization)
                history.maxvalues (index) = row.getDouble (max_utilization)
                history.date = row.getDate (date) // update the date to the latest value
                history.level = history.level + 1 // the number of values we've seen
            }

            def historical (history: History)(row: Row): List[Record] =
            {
                if (history.mrid != row.getString (mrid)) // switch to another cable?
                {
                    val ret = flush (row, history)
                    update (row, history)
                    ret
                }
                else
                {
                    update (row, history)
                    if (history.level >= history.size) // have we got enough values to start outputting
                        emit (history)
                    else
                        List ()
                }
            }

            def make_history (histories: Array[History])(row: Row): List[ /*Record*/ (String, String, Long, Date, Double, Double, Double, String, String)] =
            {
                histories.flatMap (h ⇒ historical (h)(row)).toList
            }
            import spark.implicits._
            val periods: Array[History] =
                Array (
                    History30 (),
                    History90 (),
                    History180 (),
                    History365 ()
                )
            val history = timeseries
                .repartition (timeseries ("mrid"))
                .flatMap (make_history (periods))
            logInfo ("""Utilization: %d historical values calculated""".format (history.count))
            // println (history.take(5).mkString ("\n"))

            // save to Cassandra
            history.rdd.saveToCassandra (access.output_keyspace, "utilization_historical",
                SomeColumns ("mrid", "type", "period", "date", "min_utilization", "avg_utilization", "max_utilization", "simulation", "transformer"))
            log.info ("""Utilization: historical values saved to %s.utilization_historical""".format (access.output_keyspace))
        }

        // do the 'all time' historical values
        val alltime = timeseries
            .groupBy ("mrid", "type", "transformer", "simulation")
            .agg (
                "date" → "max",
                "min_utilization" → "min",
                "avg_utilization" → "avg",
                "max_utilization" → "max"
            )
            .withColumnRenamed ("max(date)", "date")
            .withColumnRenamed ("min(min_utilization)", "min_utilization")
            .withColumnRenamed ("avg(avg_utilization)", "avg_utilization")
            .withColumnRenamed ("max(max_utilization)", "max_utilization")
        logInfo ("""Utilization: %d all time historical values calculated""".format (alltime.count))
        show (alltime)

        {
            val mrid = alltime.schema.fieldIndex ("mrid")
            val typ = alltime.schema.fieldIndex ("type")
            val date = alltime.schema.fieldIndex ("date")
            val min_utilization = alltime.schema.fieldIndex ("min_utilization")
            val avg_utilization = alltime.schema.fieldIndex ("avg_utilization")
            val max_utilization = alltime.schema.fieldIndex ("max_utilization")
            val simulation = alltime.schema.fieldIndex ("simulation")
            val transformer = alltime.schema.fieldIndex ("transformer")

            val work = alltime.rdd.map (row ⇒ (row.getString (mrid), row.getString (typ), 0L, row.getDate (date), row.getDouble (min_utilization), row.getDouble (avg_utilization), row.getDouble (max_utilization), row.getString (simulation), row.getString (transformer))).cache

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "utilization_historical",
                SomeColumns ("mrid", "type", "period", "date", "min_utilization", "avg_utilization", "max_utilization", "simulation", "transformer"))
            log.info ("""Utilization: all time historical values saved to %s.utilization_historical""".format (access.output_keyspace))
        }
    }

    /**
     * Load factor
     *
     * Load factor is average load of a system divided by its peak load.
     * The higher the load factor is, the smoother the load profile is,
     * and the more the infrastructure is being utilized.
     * The highest possible load factor is 1, which indicates a flat load profile.
     * For this calculation we use the peak value per day.
     */
    def load_factor (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Load Factor")
        val simulated__power_values = access.raw_values
            .filter ("type = 'power'") // ToDo: how to pick the transformer power values if another recorder asks for power
            .cache
        logInfo ("""Load Factor: %d simulation values to process""".format (simulated__power_values.count))
        show (simulated__power_values)

        val trafos = access.geojson_polygons.drop ("properties").cache

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated__power_values
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("period")
            .withColumn ("date", simulated__power_values ("time").cast (DateType))
            .drop ("time")
            .withColumn ("power", magnitude [Double, Double].apply (simulated__power_values ("real_a"), simulated__power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        logInfo ("""Load Factor: %d power values to process""".format (simulated_value_trafos.count))
        show (simulated_value_trafos)

        val aggregates = simulated_value_trafos
            .groupBy ("mrid", "type", "date", "simulation") // sum over time for each day
            .agg ("power" → "avg", "power" → "max")
            .withColumnRenamed ("avg(power)", "avg_power")
            .withColumnRenamed ("max(power)", "peak_power")
        val loadfactors = aggregates
            .withColumn ("load_factor", aggregates ("avg_power") / aggregates ("peak_power"))
        logInfo ("""Load Factor: %d transformer average and peak power values""".format (loadfactors.count))
        show (loadfactors)

        val mrid = loadfactors.schema.fieldIndex ("mrid")
        val typ = loadfactors.schema.fieldIndex ("type")
        val date = loadfactors.schema.fieldIndex ("date")
        val avg_power = loadfactors.schema.fieldIndex ("avg_power")
        val peak_power = loadfactors.schema.fieldIndex ("peak_power")
        val load_factor = loadfactors.schema.fieldIndex ("load_factor")
        val simulation = loadfactors.schema.fieldIndex ("simulation")

        val work = loadfactors.rdd.map (
            row ⇒ (row.getString (mrid), row.getString (typ), row.getDate (date), row.getDouble (avg_power), row.getDouble (peak_power), row.getDouble (load_factor), "VA÷VA", row.getString (simulation)))

        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "load_factor_by_day",
            SomeColumns ("mrid", "type", "date", "avg_power", "peak_power", "load_factor", "units", "simulation"))
        log.info ("""Load Factor: load factor records saved to %s.load_factor_by_day""".format (access.output_keyspace))
    }

    /**
     * Coincidence factor
     *
     * Coincidence factor is the peak of a system divided by the sum of peak loads of its individual components.
     * It tells how likely the individual components are peaking at the same time.
     * The highest possible coincidence factor is 1, when all of the individual components are peaking at the same time.
     * For this calculation we use the peak values per day.
     */
    def coincidence_factor (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Coincidence Factor")

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_power_values = access.raw_values
            .filter ("type = 'power'")
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("period")
            .cache
        logInfo ("""Coincidence Factor: %d simulation values to process""".format (simulated_power_values.count))
        show (simulated_power_values)

        val trafos = access.geojson_polygons.drop ("properties").cache

        val simulated_value_trafos = simulated_power_values
            .withColumn ("power", magnitude [Double, Double].apply (simulated_power_values ("real_a"), simulated_power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", simulated_power_values ("time").cast (DateType))
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        logInfo ("""Coincidence Factor: %d simulation values with transformers to process""".format (simulated_value_trafos.count))
        show (simulated_value_trafos)

        val peaks_trafos = simulated_value_trafos
            .groupBy ("simulation", "mrid", "date", "type")
            .agg ("power" → "max")
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("max(power)", "peak_power")
        logInfo ("""Coincidence Factor: %d peaks found""".format (peaks_trafos.count))
        show (peaks_trafos)
        // now do the peaks for the energy consumers

        val _measured_value = access.raw_meter_values
        val houses = access.geojson_points.drop ("properties").cache

        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf [Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))

        val measured_value = _measured_value
            .withColumn ("power", power [Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", _measured_value ("time").cast (DateType))
            .cache
        logInfo ("""Coincidence Factor: %d measured values to process""".format (measured_value.count))
        show (measured_value)

        val peaks_houses = measured_value
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "power")
        logInfo ("Coincidence Factor: %d peaks found".format (peaks_houses.count))
        show (peaks_houses)

        val measured_value_houses = peaks_houses
            .join (
                houses,
                Seq ("mrid"))
        logInfo ("""Coincidence Factor: %d measured peaks with transformers to process""".format (measured_value_houses.count))
        show (measured_value_houses)

        // sum up the individual peaks for each transformer and date combination
        val sums_houses = measured_value_houses
            .groupBy ("transformer", "date")
            .agg ("power" → "sum")
            .withColumnRenamed ("sum(power)", "sum_power")
        logInfo ("""Coincidence Factor: %d summed peaks with transformers to process""".format (sums_houses.count))
        show (sums_houses)

        val _coincidences = peaks_trafos
            .join (sums_houses, Seq ("transformer", "date"))
        val coincidences = _coincidences
            .withColumn ("coincidence", _coincidences ("peak_power") / _coincidences ("sum_power"))
        logInfo ("""Coincidence Factor: %d coincidence factors calculated""".format (coincidences.count))
        show (coincidences)

        val transformer = coincidences.schema.fieldIndex ("transformer")
        val date = coincidences.schema.fieldIndex ("date")
        val typ = coincidences.schema.fieldIndex ("type")
        val peak_power = coincidences.schema.fieldIndex ("peak_power")
        val sum_power = coincidences.schema.fieldIndex ("sum_power")
        val coincidence = coincidences.schema.fieldIndex ("coincidence")
        val simulation = coincidences.schema.fieldIndex ("simulation")

        val work = coincidences.rdd.map (
            row ⇒
            {
                val factor = if (row.isNullAt (coincidence)) 0.0 else row.getDouble (coincidence)
                (row.getString (transformer), row.getString (typ), row.getDate (date), row.getDouble (peak_power), row.getDouble (sum_power), factor, "VA÷VA", row.getString (simulation))
            }
        )

        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "coincidence_factor_by_day",
            SomeColumns ("mrid", "type", "date", "peak_power", "sum_power", "coincidence_factor", "units", "simulation"))
        log.info ("""Coincidence Factor: coincidence factor records saved to %s.coincidence_factor_by_day""".format (access.output_keyspace))
    }

    /**
     * Responsibility factor
     *
     * Responsibility factor is the load of an individual component at the time of system peak divided by
     * the peak load of this individual component.
     * Responsibility factor tells how much of the component is contributing to the system peak.
     * When a component peaks at the same time as the system, its responsibility factor is 100%.
     * For this calculation we use the transformer peak value per day.
     */
    def responsibility_factor (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Responsibility Factor")
        val simulated_power_values = access.raw_values
            .filter ("type = 'power'")
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("type", "period")
            .cache
        logInfo ("""Responsibility Factor: %d simulation values to process""".format (simulated_power_values.count))
        show (simulated_power_values)

        val trafos = access.geojson_polygons.drop ("properties").cache

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated_power_values
            .withColumn ("magnitude", magnitude [Double, Double].apply (simulated_power_values ("real_a"), simulated_power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", simulated_power_values ("time").cast (DateType))
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        logInfo ("""Responsibility Factor: %d simulation values with transformers to process""".format (simulated_value_trafos.count))
        show (simulated_value_trafos)

        val peaks = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "magnitude")
        logInfo ("""Responsibility Factor: %d peaks found""".format (peaks.count))
        show (peaks)

        val info = peaks.join (simulated_value_trafos, Seq ("mrid", "date", "magnitude"))
            .withColumnRenamed ("mrid", "transformer")
        logInfo ("""Responsibility Factor: %d peaks joined with simulation values""".format (info.count))
        show (info)

        val _measured_value = access.raw_meter_values

        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf [Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))

        val measured_value = _measured_value
            .withColumn ("power", power [Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a", "imag_a", "period")
            .withColumn ("date", _measured_value ("time").cast (DateType))
            .cache
        logInfo ("""Responsibility Factor: %d measured values to process""".format (measured_value.count))
        show (measured_value)

        val houses = access.geojson_points.drop ("properties").cache

        val measured_value_and_trafo = measured_value
            .join (
                houses,
                Seq ("mrid"))
        logInfo ("""Responsibility Factor: %d measurements with energy consumers to process""".format (measured_value_and_trafo.count))
        show (measured_value_and_trafo)

        val maximums = measured_value_and_trafo
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "peak")
        logInfo ("""Responsibility Factor: %d maximums found""".format (maximums.count))
        show (maximums)

        val peak_times = measured_value_and_trafo
            .join (info, Seq ("date", "time", "transformer", "simulation"))
            .drop ("magnitude")
        logInfo ("""Responsibility Factor: %d peak times found""".format (peak_times.count))
        show (peak_times)

        val responsibilities = peak_times.join (maximums, Seq ("mrid", "date"))
            .withColumn ("responsibility", round (measured_value_and_trafo ("power") / maximums ("peak") * 100.0 * 100.0) / 100.0)
        logInfo ("""Responsibility Factor: %d responsibility factors evaluated""".format (responsibilities.count))
        show (responsibilities)

        val mrid = responsibilities.schema.fieldIndex ("mrid")
        val date = responsibilities.schema.fieldIndex ("date")
        val time = responsibilities.schema.fieldIndex ("time")
        val transformer = responsibilities.schema.fieldIndex ("transformer")
        val p = responsibilities.schema.fieldIndex ("power")
        val peak = responsibilities.schema.fieldIndex ("peak")
        val responsibility = responsibilities.schema.fieldIndex ("responsibility")
        val simulation = responsibilities.schema.fieldIndex ("simulation")

        val work = responsibilities.rdd.map (
            row ⇒
            {
                val resp = if (row.isNullAt (responsibility)) 0.0 else row.getDouble (responsibility)
                (row.getString (mrid), "power", row.getDate (date), row.getTimestamp (time), row.getString (transformer), row.getDouble (p), row.getDouble (peak), resp, "VA÷VA×100", row.getString (simulation))
            }
        )
        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "responsibility_by_day",
            SomeColumns ("mrid", "type", "date", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))
        log.info ("""Responsibility Factor: responsibility records saved to %s.responsibility_by_day""".format (access.output_keyspace))
    }

    /**
     * Voltage quality
     *
     * Determine the peak deviation (voltage drop) from the nominal voltage.
     *
     */
    def voltage_quality (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Voltage quality")
        val simulated_value = access.raw_values
            .filter ("type = 'voltage'")
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .drop ("type", "period")
            .cache
        logInfo ("""Voltage quality: %d simulation voltages to process""".format (simulated_value.count))
        show (simulated_value)

        val houses = access.geojson_points

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_points = simulated_value
            .withColumn ("voltage", magnitude [Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .withColumn ("date", simulated_value ("time").cast (DateType))
            .drop ("real_a", "imag_a")
            .join (
                houses,
                Seq ("simulation", "mrid"))

        def nominalVoltage[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse ("nominalVoltage", "400.0").toDouble)

        val consumers = simulated_value_points
            .withColumn ("nominal_voltage", nominalVoltage [Map[String, String]].apply (simulated_value_points ("properties")))
            .drop ("properties")
        logInfo ("""Voltage quality: %d voltage records to process""".format (consumers.count))
        show (consumers)

        val aggregations = consumers
            .groupBy ("mrid", "date", "nominal_voltage", "transformer", "simulation")
            .agg ("voltage" → "min", "voltage" → "avg", "voltage" → "max")
            .withColumnRenamed ("min(voltage)", "min_voltage")
            .withColumnRenamed ("avg(voltage)", "avg_voltage")
            .withColumnRenamed ("max(voltage)", "max_voltage")
        logInfo ("""Voltage quality: %d aggregations found""".format (aggregations.count))
        show (aggregations)

        val deviations = aggregations
            .withColumn ("min_deviation", (round (aggregations ("min_voltage") / aggregations ("nominal_voltage") * 100.0 * 100.0) / 100.0) - 100.0)
            .withColumn ("max_deviation", (round (aggregations ("max_voltage") / aggregations ("nominal_voltage") * 100.0 * 100.0) / 100.0) - 100.0)

        // worst case deviation
        def deviation (min: Double, max: Double): Double = if (Math.abs (min) >= Math.abs (max)) min else max

        {
            val mrid = deviations.schema.fieldIndex ("mrid")
            val date = deviations.schema.fieldIndex ("date")
            val min_voltage = deviations.schema.fieldIndex ("min_voltage")
            val avg_voltage = deviations.schema.fieldIndex ("avg_voltage")
            val max_voltage = deviations.schema.fieldIndex ("max_voltage")
            val nominal_voltage = deviations.schema.fieldIndex ("nominal_voltage")
            val min_deviation = deviations.schema.fieldIndex ("min_deviation")
            val max_deviation = deviations.schema.fieldIndex ("max_deviation")
            val simulation = deviations.schema.fieldIndex ("simulation")
            val transformer = deviations.schema.fieldIndex ("transformer")

            val work = deviations.rdd.map (row ⇒ (row.getString (mrid), "voltage", row.getDate (date), row.getDouble (min_voltage), row.getDouble (avg_voltage), row.getDouble (max_voltage), row.getDouble (nominal_voltage), deviation (row.getDouble (min_deviation), row.getDouble (max_deviation)), "V÷V×100", row.getString (simulation), row.getString (transformer)))
            logInfo ("""Voltage quality: %d voltage extrema records""".format (work.count))

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "voltage_deviation_by_day",
                SomeColumns ("mrid", "type", "date", "min_voltage", "avg_voltage", "max_voltage", "nominal_voltage", "deviation", "units", "simulation", "transformer"))
            log.info ("""Voltage quality: voltage deviation records saved to %s.voltage_deviation_by_day""".format (access.output_keyspace))
        }

        // roll up for each transformer
        val aggregations_trafos = deviations
            .groupBy ("transformer", "date", "nominal_voltage", "simulation")
            .agg ("min_voltage" → "min", "avg_voltage" → "avg", "max_voltage" → "max")
            .withColumnRenamed ("min(min_voltage)", "min_voltage")
            .withColumnRenamed ("avg(avg_voltage)", "avg_voltage")
            .withColumnRenamed ("max(max_voltage)", "max_voltage")

        val deviations_trafos = aggregations_trafos
            .withColumn ("min_deviation", (round (aggregations_trafos ("min_voltage") / aggregations_trafos ("nominal_voltage") * 100.0 * 100.0) / 100.0) - 100.0)
            .withColumn ("max_deviation", (round (aggregations_trafos ("max_voltage") / aggregations_trafos ("nominal_voltage") * 100.0 * 100.0) / 100.0) - 100.0)

        {
            val transformer = deviations_trafos.schema.fieldIndex ("transformer")
            val date = deviations_trafos.schema.fieldIndex ("date")
            val min_voltage = deviations_trafos.schema.fieldIndex ("min_voltage")
            val avg_voltage = deviations_trafos.schema.fieldIndex ("avg_voltage")
            val max_voltage = deviations_trafos.schema.fieldIndex ("max_voltage")
            val nominal_voltage = deviations_trafos.schema.fieldIndex ("nominal_voltage")
            val min_deviation = deviations_trafos.schema.fieldIndex ("min_deviation")
            val max_deviation = deviations_trafos.schema.fieldIndex ("max_deviation")
            val simulation = deviations_trafos.schema.fieldIndex ("simulation")

            val work = deviations_trafos.rdd.map (row ⇒ (row.getString (transformer), "voltage", row.getDate (date), row.getDouble (min_voltage), row.getDouble (avg_voltage), row.getDouble (max_voltage), row.getDouble (nominal_voltage), deviation (row.getDouble (min_deviation), row.getDouble (max_deviation)), "V÷V×100", row.getString (simulation)))
            logInfo ("""Voltage quality: %d transformer area voltage extrema records""".format (work.count))

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "voltage_deviation_summary_by_day",
                SomeColumns ("mrid", "type", "date", "min_voltage", "avg_voltage", "max_voltage", "nominal_voltage", "deviation", "units", "simulation"))
            log.info ("""Voltage quality: transformer area voltage deviation records saved to %s.voltage_deviation_summary_by_day""".format (access.output_keyspace))
        }
    }

    /**
     * Losses
     *
     * Determine the sum of losses for a transformer area.
     *
     */
    def losses (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Losses")
        val simulated_loss_values = access.raw_values
            .filter ("type = 'losses'")
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
            .cache
        logInfo ("""Losses: %d simulation values to process""".format (simulated_loss_values.count))
        show (simulated_loss_values)

        def energy[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf [Double, Double, Double, Int]((x: Double, y: Double, z: Int) => Math.sqrt (x * x + y * y) * z / (60 * 60 * 1000))

        val losses = simulated_loss_values
            .withColumn ("losses", energy [Double, Double, Int].apply (simulated_loss_values ("real_a"), simulated_loss_values ("imag_a"), simulated_loss_values ("period")))
            .drop ("real_a", "imag_a", "period")
            .withColumn ("date", simulated_loss_values ("time").cast (DateType))
            .drop ("time")
            .cache
        logInfo ("""Losses: %d loss totals""".format (losses.count))
        show (losses)

        val lines = access.geojson_lines.drop ("properties").cache

        val cables = losses
            .join (
                lines,
                Seq ("simulation", "mrid"))
            .groupBy ("mrid", "date", "simulation", "transformer")
            .agg ("losses" → "sum")
            .withColumnRenamed ("sum(losses)", "losses")
        logInfo ("""Losses: %d daily cable loss totals""".format (cables.count))
        show (cables)

        val polygons = access.geojson_polygons.drop ("properties").cache

        val _trafos = losses
            .join (
                polygons,
                Seq ("simulation", "mrid"))
        val trafos = _trafos
            .withColumn ("transformer", _trafos ("mrid"))
            .groupBy ("mrid", "date", "simulation", "transformer")
            .agg ("losses" → "sum")
            .withColumnRenamed ("sum(losses)", "losses")
        logInfo ("""Losses: %d daily transformer loss totals""".format (trafos.count))
        show (trafos)

        val total = cables.union (trafos)

        {
            val mrid = total.schema.fieldIndex ("mrid")
            val date = total.schema.fieldIndex ("date")
            val losses = total.schema.fieldIndex ("losses")
            val simulation = total.schema.fieldIndex ("simulation")
            val transformer = total.schema.fieldIndex ("transformer")

            val work = total.rdd.map (row ⇒ (row.getString (mrid), "energy", row.getDate (date), row.getDouble (losses), "Wh", row.getString (transformer), row.getString (simulation)))
            logInfo ("""Losses: %d transformer area loss records""".format (work.count))

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "losses_by_day",
                SomeColumns ("mrid", "type", "date", "losses", "units", "transformer", "simulation"))
            log.info ("""Losses: transformer area loss records saved to %s.losses_by_day""".format (access.output_keyspace))
        }

        // roll up each transformer area
        val sum = total
            .groupBy ("date", "simulation", "transformer")
            .agg ("losses" → "sum")
            .withColumnRenamed ("sum(losses)", "losses")
            .withColumnRenamed ("transformer", "mrid")

        {
            val mrid = sum.schema.fieldIndex ("mrid")
            val date = sum.schema.fieldIndex ("date")
            val losses = sum.schema.fieldIndex ("losses")
            val simulation = sum.schema.fieldIndex ("simulation")

            val work = sum.rdd.map (row ⇒ (row.getString (mrid), "energy", row.getDate (date), row.getDouble (losses), "Wh", row.getString (simulation)))
            logInfo ("""Losses: %d transformer area summary loss records""".format (work.count))

            // save to Cassandra
            work.saveToCassandra (access.output_keyspace, "losses_summary_by_day",
                SomeColumns ("mrid", "type", "date", "losses", "units", "simulation"))
            log.info ("""Losses: transformer area summary loss records saved to %s.losses_summary_by_day""".format (access.output_keyspace))
        }
    }

    def run (simulations: Seq[String]): Unit =
    {
        val sql = "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering"
        val keyspaces = CassandraConnector (spark.sparkContext.getConf).withSessionDo (
            session =>
            {
                val results: ResultSet = session.execute (sql)
                val iter = results.iterator ()
                var list = List[String]()
                while (iter.hasNext)
                {
                    val row = iter.next
                    list = row.getString (0) :: list
                }
                list
            }
        )
        log.info ("""found keyspaces %s""".format (keyspaces.mkString (",")))

        val lookup = keyspaces.flatMap (
            keyspace ⇒
            {
                spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulation", "keyspace" -> keyspace))
                    .load
                    .select ("id", "read_keyspace", "write_keyspace")
                    .rdd
                    .map (row ⇒ (row.getString (0), row.getString (1), row.getString (2)))
                    .collect
            }
        )

        for (simulation ← simulations)
        {
            val found = lookup.find (_._1 == simulation)
            found match
            {
                case Some ((id, input, output)) ⇒
                    val access = SimulationCassandraAccess (spark, id, input, output, options.verbose, options.unittest)
                    log.info ("""summarizing %s (input keyspace: %s, output keyspace: %s)""".format (access.simulation, access.input_keyspace, access.output_keyspace))
                    utilization (access)
                    load_factor (access)
                    coincidence_factor (access)
                    responsibility_factor (access)
                    voltage_quality (access)
                    losses (access)
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }
}
