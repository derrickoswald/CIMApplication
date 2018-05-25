package ch.ninecode.sim

import java.sql.Date
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.reflect.runtime.universe.TypeTag

import com.datastax.spark.connector._
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
 * @param spark The Spark session
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

    /**
     * Utilization
     *
     * Utilization is the value of an element divided by the maximum rated value.
     * For cables, this is the current divided by the ratedCurrent (A).
     * For transformers, this would be the power output divided by the ratedS (VA).
     */
    def utilization (): Unit =
    {
        val simulated_current_values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b","real_c", "imag_b","imag_c")
            .filter ("type = 'current'")
            .cache
        log.info ("""%d simulated current values to process""".format (simulated_current_values.count))
        // simulated_current_values.show (5)

        val lines = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_lines", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type", "geometry")
            .cache
        log.info ("""%d GeoJSON lines to process""".format (lines.count))
        // lines.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))
        def maxCurrent[Type_x: TypeTag] = udf[Double, Map[String,String]]((map: Map[String,String]) => map.getOrElse ("ratedCurrent", "1.0").toDouble)

        val cables = simulated_current_values
            .withColumn ("current", magnitude[Double, Double].apply (simulated_current_values ("real_a"), simulated_current_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                lines,
                Seq ("simulation", "mrid"))
        val ratedCables = cables
            .withColumn ("ratedCurrent", maxCurrent[Map[String,String]].apply (cables ("properties")))
            .drop ("properties")
        val utilization = ratedCables
            .withColumn ("utilization", round (ratedCables ("current") / ratedCables ("ratedCurrent") * 100.0 * 100.0) / 100.0)
        log.info ("""%d cable records to process""".format (utilization.count))
        // utilization.show (5)

        {   // open a scope to avoid variable name clashes
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
            work.saveToCassandra ("cimapplication", "utilization",
                SomeColumns ("mrid", "type", "period", "time", "value", "reference", "utilization", "units", "transformer", "simulation"))
            log.info ("""%d utilization records saved to cimapplication.utilization""".format (work.count))
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
        log.info ("""%d daily summaries calculated""".format (summary.count))
        // summary.show (5)

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
            work.saveToCassandra ("cimapplication", "utilization_summary_by_day",
                SomeColumns ("mrid", "type", "date", "min_utilization", "avg_utilization", "max_utilization", "units", "transformer", "simulation"))
            log.info ("""%d utilization summary records saved to cimapplication.utilization_summary_by_day""".format (work.count))
        }

        val trafokreise = utilization
            .filter ("period = 900000") // ToDo: how can we not hard-code this?
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
        log.info ("""%d transformer area daily summaries calculated""".format (trafokreise.count))
        // trafokreise.show (5)

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
            work.saveToCassandra ("cimapplication", "utilization_summary_by_day_by_transformer",
                SomeColumns ("transformer", "type", "date", "min_utilization", "avg_utilization", "max_utilization", "units", "simulation"))
            log.info ("""%d transformer area utilization summary records saved to cimapplication.utilization_summary_by_day_by_transformer""".format (work.count))
        }

    }

    /**
     * Load factor
     *
     * Load factor is average load of a system divided by its peak load.
     * The higher the load factor is, the smoother the load profile is,
     * and the more the infrastructure is being utilized.
     * The highest possible load factor is 1, which indicates a flat load profile.
     */
    def load_factor (): Unit =
    {
        val simulated_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b", "real_c", "imag_b","imag_c")
            .filter ("type = 'power'")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value.count))
        simulated_value.show (5)

        val trafos = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_polygons", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .cache
        log.info ("""%d GeoJSON polygons to process""".format (trafos.count))
        trafos.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))
        val simulated_value_trafos = simulated_value
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        log.info ("""%d simulation values with polygons to process""".format (simulated_value_trafos.count))
        simulated_value_trafos.show (5)

        val peaks = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "maximum")
        log.info ("%d peaks found".format (peaks.count))
        peaks.show (5)

        val day_trafos = simulated_value_trafos
            .filter (simulated_value_trafos ("period") === 86400000)
        log.info ("%d daily transformer power values".format (day_trafos.count))
        day_trafos.show (5)

        val loadfactors = day_trafos
            .join (
                peaks,
                Seq ("mrid", "date"))
        log.info ("%d peaks joined".format (loadfactors.count))
        loadfactors.show (5)

        val mrid = loadfactors.schema.fieldIndex ("mrid")
        val typ = loadfactors.schema.fieldIndex ("type")
        val date = loadfactors.schema.fieldIndex ("date")
        val period = loadfactors.schema.fieldIndex ("period")
        val time = loadfactors.schema.fieldIndex ("time")
        val mag = loadfactors.schema.fieldIndex ("magnitude")
        val maximum = loadfactors.schema.fieldIndex ("maximum")
        val simulation = loadfactors.schema.fieldIndex ("simulation")

        val work = loadfactors.rdd.map (
            row ⇒
            {
                val id = row.getString (mrid)
                val power = row.getDouble (mag)
                val peak = row.getDouble (maximum)
                val load_factor = power / peak
                (id, row.getString (typ), row.getDate (date), row.getInt (period), row.getTimestamp (time), load_factor, "VA/VA", row.getString (simulation))
            }
        )

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "load_factor_by_day",
            SomeColumns ("mrid", "type", "date", "period", "time", "load_factor", "units", "simulation"))
        log.info ("""load factor records saved to cimapplication.load_factor_by_day""")
    }

    /**
     * Coincidence factor
     *
     * Coincidence factor is the peak of a system divided by the sum of peak loads of its individual components.
     * It tells how likely the individual components are peaking at the same time.
     * The highest possible coincidence factor is 1, when all of the individual components are peaking at the same time.
     */
    def coincidence_factor (): Unit =
    {
        val simulated_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .filter ("type = 'power'")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value.count))
        // simulated_value.show (5)

        val trafos = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_polygons", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .drop ("properties")
            .cache
        log.info ("""%d GeoJSON polygons to process""".format (trafos.count))
        // trafos.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated_value
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        log.info ("""%d simulation values with transformers to process""".format (simulated_value_trafos.count))
        // simulated_value_trafos.show (5)

        val peaks_trafos = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("max(magnitude)", "magnitude")
        log.info ("%d peaks found".format (peaks_trafos.count))
        // peaks_trafos.show (5)

        // now do the peaks for the energy consumers

        val _measured_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .filter ("type = 'energy'")
            .cache
        log.info ("""%d measured values to process""".format (_measured_value.count))
        // _measured_value.show (5)

        val houses = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_points", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .drop ("simulation")
            .drop ("properties")
            .cache
        log.info ("""%d GeoJSON points to process""".format (houses.count))
        // houses.show (5)

        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf[Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))
        val measured_value = _measured_value.withColumn ("power", power[Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a")
            .drop ("imag_a")
            .cache
        log.info ("""%d measured values to process""".format (measured_value.count))
        // measured_value.show (5)

        val peaks_houses = measured_value
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "power")
        log.info ("%d peaks found".format (peaks_houses.count))
        // peaks_houses.show (5)

        val measured_value_houses = peaks_houses
            .join (
                houses,
                Seq ("mrid"))
        log.info ("""%d measured peaks with transformers to process""".format (measured_value_houses.count))
        // measured_value_houses.show (5)

        // sum up the individual peaks for each transformer|date combination
        val sums_houses = measured_value_houses
            .groupBy ("transformer", "date")
            .agg ("power" → "sum")
            .withColumnRenamed ("sum(power)", "power")
        log.info ("""%d summed peaks with transformers to process""".format (sums_houses.count))
        // sums_houses.show (5)

        def ratio[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => x / y)
        val _coincidences = peaks_trafos
            .join (sums_houses, Seq ("transformer", "date"))
        val coincidences = _coincidences
            .withColumn ("coincidence", ratio[Double, Double].apply (_coincidences ("magnitude"), _coincidences ("power")))
        log.info ("""%d coincidence factors calculated""".format (coincidences.count))
        // coincidences.show (5)

        val transformer = coincidences.schema.fieldIndex ("transformer")
        val date = coincidences.schema.fieldIndex ("date")
        val coincidence = coincidences.schema.fieldIndex ("coincidence")

        val work = coincidences.rdd.map (
            row ⇒
            {
                val factor = if (row.isNullAt (coincidence)) 0.0 else row.getDouble (coincidence)
                (row.getString (transformer), row.getDate (date), factor)
            }
        )
        // save to Cassandra
        work.saveToCassandra ("cimapplication", "coincidence_factor_by_day",
            SomeColumns ("transformer", "date", "coincidence_factor"))
        log.info ("""coincidence factor records saved to cimapplication.coincidence_factor_by_day""")
    }

    /**
     * Responsibility factor
     *
     * Responsibility factor is the load of an individual component at the time of system peak divided by
     * the peak load of this individual component.
     * Responsibility factor tells how much of the component is contributing to the system peak.
     * When a component peaks at the same time as the system, its responsibility factor is 100%.
     */
    def responsibility_factor (): Unit =
    {
        val simulated_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("period")
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'power'")
            .drop ("type")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value.count))
        // simulated_value.show (5)

        val trafos = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_polygons", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .drop ("properties")
            .cache
        log.info ("""%d GeoJSON polygons to process""".format (trafos.count))
        // trafos.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated_value
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .withColumnRenamed ("max(magnitude)", "magnitude")
            .join (
                trafos,
                Seq ("simulation", "mrid"))

        log.info ("""%d simulation values with transformers to process""".format (simulated_value_trafos.count))
        // simulated_value_trafos.show (5)

        val peaks = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "magnitude")
        log.info ("%d peaks found".format (peaks.count))
        // peaks.show (5)

        val info = peaks.join (simulated_value_trafos, Seq ("mrid", "date", "magnitude"))
            .withColumnRenamed ("mrid", "transformer")
            .drop ("real_a")
            .drop ("imag_a")
        log.info ("%d peaks joined with simulation values".format (info.count))
        // info.show (5)

        val _measured_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'energy'")
        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf[Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))
        val measured_value = _measured_value.withColumn ("power", power[Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a")
            .drop ("imag_a")
            .cache
        log.info ("""%d measured values to process""".format (measured_value.count))
        // measured_value.show (5)

        val geojson_points = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_points", "keyspace" -> "cimapplication" ))
            .load
            .drop ("simulation")
            .drop ("geometry")
            .drop ("properties")
            .drop ("type")
            .distinct // could choose by simulation id
            .cache
        log.info ("""%d energy consumers to process""".format (geojson_points.count))
        // geojson_points.show (5)

        val measured_value_and_trafo = measured_value
            .join (
                geojson_points,
                Seq ("mrid"))
        log.info ("""%d measurements with energy consumers to process""".format (measured_value_and_trafo.count))
        // measured_value_and_trafo.show (5)

        val maximums = measured_value_and_trafo
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "peak")
        log.info ("""%d maximums found""".format (maximums.count))
        // maximums.show (5)

        val peak_times = measured_value_and_trafo.join (info, Seq ("date", "time", "transformer"))
            .drop ("magnitude")
        log.info ("""%d peak times found""".format (peak_times.count))
        // peak_times.show (5)

        val responsibilities = peak_times.join (maximums, Seq ("mrid", "date"))
            .withColumn ("responsibility", measured_value_and_trafo ("power") / maximums ("peak"))
        log.info ("""%d responsibility factors evaluated""".format (responsibilities.count))
        // responsibilities.show (5)

        val mrid = responsibilities.schema.fieldIndex ("mrid")
        val date = responsibilities.schema.fieldIndex ("date")
        val period = responsibilities.schema.fieldIndex ("period")
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
                (row.getString (mrid), "power", row.getDate (date), row.getInt (period), row.getTimestamp (time), row.getString (transformer), row.getDouble (p), row.getDouble (peak), resp, "VA/VA", row.getString (simulation))
            }
        )
        // save to Cassandra
        work.saveToCassandra ("cimapplication", "responsibility_by_day",
            SomeColumns ("mrid", "type", "date", "period", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))
        log.info ("""responsibility records saved to cimapplication.responsibility_by_day""")
    }

    /**
     * Voltage quality
     *
     * Determine the peak deviation (voltage drop) from the nominal voltage.
     *
     */
    def voltage_quality (): Unit =
    {
        val simulated_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("period")
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'voltage'")
            .drop ("type")
            .cache
        log.info ("""%d simulation voltages to process""".format (simulated_value.count))
        //simulated_value.show (5)

        val houses = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_points", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type", "geometry")
            .cache
        log.info ("""%d GeoJSON points to process""".format (houses.count))
        //houses.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_points = simulated_value
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                houses,
                Seq ("simulation", "mrid"))
        log.info ("""%d joined points to process""".format (simulated_value_points.count))
        //simulated_value_points.show (5)

        val minimums = simulated_value_points
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "min")
            .withColumnRenamed ("min(magnitude)", "magnitude")
        log.info ("%d minimums found".format (minimums.count))
        //minimums.show (5)

        val info = minimums.join (simulated_value_points, Seq ("mrid", "date", "magnitude"))
        log.info ("%d minimums joined with simulation values".format (info.count))
        //info.show (5)

        val mrid = info.schema.fieldIndex ("mrid")
        val date = info.schema.fieldIndex ("date")
        val time = info.schema.fieldIndex ("time")
        val mag = info.schema.fieldIndex ("magnitude")
        val simulation = info.schema.fieldIndex ("simulation")
        val properties = info.schema.fieldIndex ("properties")
        val transformer = info.schema.fieldIndex ("transformer")

        val work = info.rdd.flatMap (
            row ⇒
            {
                val map = row.getMap (properties).asInstanceOf[Map[String,String]]
                val nominalVoltage = map.getOrElse ("nominalVoltage", "-1.0").toDouble
                if (nominalVoltage < 0.0)
                    List ()
                else
                {
                    val voltage = row.getDouble (mag)
                    val ratio = (voltage - nominalVoltage) / nominalVoltage
                    val percent = if (Math.abs (ratio) < 1e-4) 0.0 else ratio * 100.0 // just filter out the stupid ones
                    val tx = row.getString (transformer)
                    List ((row.getString (mrid), row.getDate (date), row.getTimestamp (time), percent, "percent", row.getString (simulation), tx))
                }
            }
        )
        log.info ("""%d voltage drop records""".format (work.count))
        //println (work.take (5).mkString("\n"))

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "voltage_drop_by_day",
            SomeColumns ("mrid", "date", "time", "percent", "units", "simulation", "transformer"))
        log.info ("""voltage drop records saved to cimapplication.voltage_drop_by_day""")
    }

    /**
     * Losses
     *
     * Determine the sum of losses for a transformer area.
     *
     */
    def losses (): Unit =
    {
        val simulated_value = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'energy'")
            .drop ("type")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value.count))
        //simulated_value.show (5)

        val lines = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_lines", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .cache
        log.info ("""%d GeoJSON lines to process""".format (lines.count))
        //lines.show (5)

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val losses = simulated_value
            .filter (simulated_value ("period") === 86400000)
            .drop ("period")
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value ("real_a"), simulated_value ("imag_a")))
            .drop ("real_a", "imag_a")
        log.info ("""%d loss totals""".format (losses.count))
        //losses.show (5)

        val cables = losses
            .join (
                lines,
                Seq ("simulation", "mrid"))
            .groupBy ("transformer", "date")
            .agg ("magnitude" → "sum")
            .withColumnRenamed ("sum(magnitude)", "cable_losses")
            .withColumnRenamed ("mrid", "transformer")
        log.info ("""%d daily cable loss totals""".format (cables.count))
        //cables.show (5)

        val trafos = losses
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("magnitude", "transformer_losses")
            .join (
                cables,
                Seq ("transformer", "date"))
        log.info ("""%d daily transformer loss totals""".format (trafos.count))
        //trafos.show (5)

        val totals = trafos
            .withColumn ("total", trafos ("transformer_losses") + trafos ("cable_losses"))
            .drop ("time", "simulation")

        val transformer = totals.schema.fieldIndex ("transformer")
        val date = totals.schema.fieldIndex ("date")
        val transformer_losses = totals.schema.fieldIndex ("transformer_losses")
        val cable_losses = totals.schema.fieldIndex ("cable_losses")
        val total = totals.schema.fieldIndex ("total")

        val work = totals.rdd.map (
            row ⇒
            {
                (row.getString (transformer), row.getDate (date), row.getDouble (transformer_losses), row.getDouble (cable_losses), row.getDouble (total))
            }
        )
        log.info ("""%d losses records""".format (work.count))
        //println (work.take (5).mkString("\n"))

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "losses_by_day",
            SomeColumns ("transformer", "date", "transformer_losses", "cable_losses", "total"))
        log.info ("""losses records saved to cimapplication.losses_by_day""")
    }

    def run (): Unit =
    {
        log.info ("Utilization")
        utilization ()
//        log.info ("Load Factor")
//        load_factor ()
//        log.info ("Coincidence Factor")
//        coincidence_factor ()
//        log.info ("Responsibility Factor")
//        responsibility_factor ()
//        log.info ("Voltage quality")
//        voltage_quality ()
//        log.info ("Losses")
//        losses ()
    }
}
