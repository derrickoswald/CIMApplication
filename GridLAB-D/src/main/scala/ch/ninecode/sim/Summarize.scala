package ch.ninecode.sim

import java.sql.Date
import java.sql.Timestamp

import scala.reflect.runtime.universe.TypeTag
import com.datastax.spark.connector._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
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

    /**
     * Utilization
     *
     * Utilization is the value (power of voltage) of a component
     * divided by the maximum rated value.
     * For cables, this is the current divided by the ratedCurrent (A).
     * For transformers, this is the power output divided by the rated power (VA).
     */
    def utilization (): Unit =
    {
        val simulated_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .filter ("type = 'current'")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value_by_day.count))
        // simulated_value_by_day.show (5)

        val lines = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_lines", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .cache
        log.info ("""%d GeoJSON lines to process""".format (lines.count))
        // lines.show (5)

        val join = simulated_value_by_day
            .join (
                lines,
                Seq ("simulation", "mrid"))
        log.info ("""%d joined lines to process""".format (join.count))
        // join.show (5)

        val mrid = join.schema.fieldIndex ("mrid")
        val typ = join.schema.fieldIndex ("type")
        val date = join.schema.fieldIndex ("date")
        val interval = join.schema.fieldIndex ("interval")
        val time = join.schema.fieldIndex ("time")
        val real_a = join.schema.fieldIndex ("real_a")
        val imag_a = join.schema.fieldIndex ("imag_a")
        val simulation = join.schema.fieldIndex ("simulation")
        val properties = join.schema.fieldIndex ("properties")
        val transformer = join.schema.fieldIndex ("transformer")

        //        mrid text,
        //        type text,
        //        date date,
        //        interval int,
        //        time timestamp,
        //        percent double,
        //        units text,
        //        simulation text,
        //        transformer text
        type Record = (String, String, Date, Int, Timestamp, Double, String, String, String)
        val work = join.rdd.map (
            row ⇒
            {
                val real = row.getDouble (real_a)
                val imag = row.getDouble (imag_a)
                val map = row.getMap (properties).asInstanceOf[Map[String,String]]
                val ratedCurrent = map.getOrElse ("ratedCurrent", "1.0").toDouble
                val utilization = Math.sqrt (real * real + imag * imag) / ratedCurrent
                val percent = if (utilization < 1e-4) 0.0 else utilization * 100.0 // just filter out the stupid ones
                val tx = row.getString (transformer)
                (row.getString (mrid), row.getString (typ), row.getDate (date), row.getInt (interval), row.getTimestamp (time), percent, "percent", row.getString (simulation), tx)
            }
        )
        log.info ("""%d utilization records""".format (work.count))
        // println (work.take (5).mkString("\n"))

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "utilization_by_day",
            SomeColumns ("mrid", "type", "date", "interval", "time", "percent", "units", "simulation", "transformer"))
        log.info ("""utilization records saved to cimapplication.utilization_by_day""")

        // reduce by day to get min, avg and max
        type Aggregate = (Int, Double, Double, Double)
        val initial: Aggregate = (0, Double.MaxValue, 0.0, Double.MinValue)
        def seqOp (aggregate: Aggregate, row: Record): Aggregate =
        {
            val n = aggregate._1 + 1
            val min = if (row._6 < aggregate._2) row._6 else aggregate._2
            val avg = aggregate._3 + row._6
            val max = if (row._6 > aggregate._4) row._6 else aggregate._4
            (n, min, avg, max)
        }
        def combOp (aggregate1: Aggregate, aggregate2: Aggregate): Aggregate =
        {
            val n = aggregate1._1 + aggregate2._1
            val min = if (aggregate1._2 < aggregate2._2) aggregate1._2 else aggregate2._2
            val avg = aggregate1._3 + aggregate2._3
            val max = if (aggregate1._4 > aggregate2._4) aggregate1._4 else aggregate2._4
            (n, min, avg, max)
        }
        // apparently interval is a keyword, so we can't use it in the SQL clauses of Dataset[Row].filter(), so we filter here
        // and my brain exploded trying to figure out what the min and max of an average really means
        val fifteen_minute = work.filter (_._4 == 900000)
        // make a multiple key of transformer and date separated by |
        val summary = fifteen_minute.keyBy (record ⇒ record._9 + "|" + record._3).aggregateByKey (initial) (seqOp, combOp)
            .map (day ⇒ (day._1.substring (0, day._1.indexOf ("|")), day._1.substring (day._1.indexOf ("|") + 1), day._2._2, day._2._3 / day._2._1, day._2._4))
        log.info ("""%d daily utilization records""".format (summary.count))
        // println (summary.take (5).mkString("\n"))

        // save to Cassandra
        summary.saveToCassandra ("cimapplication", "utilization_summary_by_day",
            SomeColumns ("mrid", "date", "min", "avg", "max"))
        log.info ("""daily utilization records saved to cimapplication.utilization_summary_by_day""")

        // do the same for cables

        // make a multiple key of cable and date separated by |
        val cable_summary = fifteen_minute.keyBy (record ⇒ record._1 + "|" + record._3).aggregateByKey (initial) (seqOp, combOp)
            .map (day ⇒ (day._1.substring (0, day._1.indexOf ("|")), day._1.substring (day._1.indexOf ("|") + 1), day._2._2, day._2._3 / day._2._1, day._2._4))
        log.info ("""%d daily cable utilization records""".format (cable_summary.count))
        // println (cable_summary.take (5).mkString("\n"))

        // save to Cassandra
        cable_summary.saveToCassandra ("cimapplication", "utilization_summary_by_day",
            SomeColumns ("mrid", "date", "min", "avg", "max"))
        log.info ("""daily utilization records saved to cimapplication.utilization_summary_by_day""")

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
        val simulated_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b", "real_c", "imag_b","imag_c")
            .filter ("type = 'power'")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value_by_day.count))
        simulated_value_by_day.show (5)

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
        val simulated_value_by_day_trafos = simulated_value_by_day
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value_by_day ("real_a"), simulated_value_by_day ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        log.info ("""%d simulation values with polygons to process""".format (simulated_value_by_day_trafos.count))
        simulated_value_by_day_trafos.show (5)

        val peaks = simulated_value_by_day_trafos.groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "maximum")
        log.info ("%d peaks found".format (peaks.count))
        peaks.show (5)

        val day_trafos = simulated_value_by_day_trafos
            .filter (simulated_value_by_day_trafos ("interval") === 86400000)
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
        val interval = loadfactors.schema.fieldIndex ("interval")
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
                (id, row.getString (typ), row.getDate (date), row.getInt (interval), row.getTimestamp (time), load_factor, "VA/VA", row.getString (simulation))
            }
        )

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "load_factor_by_day",
            SomeColumns ("mrid", "type", "date", "interval", "time", "load_factor", "units", "simulation"))
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
        val simulated_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .filter ("type = 'power'")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value_by_day.count))
        // simulated_value_by_day.show (5)

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

        val simulated_value_by_day_trafos = simulated_value_by_day
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value_by_day ("real_a"), simulated_value_by_day ("imag_a")))
            .join (
                trafos,
                Seq ("simulation", "mrid"))
        log.info ("""%d simulation values with transformers to process""".format (simulated_value_by_day_trafos.count))
        // simulated_value_by_day_trafos.show (5)

        val peaks_trafos = simulated_value_by_day_trafos.groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("max(magnitude)", "magnitude")
        log.info ("%d peaks found".format (peaks_trafos.count))
        // peaks_trafos.show (5)

        // now do the peaks for the energy consumers

        val _measured_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .filter ("type = 'energy'")
            .cache
        log.info ("""%d measured values to process""".format (_measured_value_by_day.count))
        // _measured_value_by_day.show (5)

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
        val measured_value_by_day = _measured_value_by_day.withColumn ("power", power[Double, Double, Int].apply (_measured_value_by_day ("real_a"), _measured_value_by_day ("imag_a"), _measured_value_by_day ("interval")))
            .drop ("real_a")
            .drop ("imag_a")
            .cache
        log.info ("""%d measured values to process""".format (measured_value_by_day.count))
        // measured_value_by_day.show (5)

        val peaks_houses = measured_value_by_day.groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "power")
        log.info ("%d peaks found".format (peaks_houses.count))
        // peaks_houses.show (5)

        val measured_value_by_day_houses = peaks_houses
            .join (
                houses,
                Seq ("mrid"))
        log.info ("""%d measured peaks with transformers to process""".format (measured_value_by_day_houses.count))
        // measured_value_by_day_houses.show (5)

        // sum up the individual peaks for each transformer|date combination
        val sums_houses = measured_value_by_day_houses.groupBy ("transformer", "date")
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
        val simulated_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("interval")
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'power'")
            .drop ("type")
            .cache
        log.info ("""%d simulation values to process""".format (simulated_value_by_day.count))
        // simulated_value_by_day.show (5)

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

        val simulated_value_by_day_trafos = simulated_value_by_day
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value_by_day ("real_a"), simulated_value_by_day ("imag_a")))
            .withColumnRenamed ("max(magnitude)", "magnitude")
            .join (
                trafos,
                Seq ("simulation", "mrid"))

        log.info ("""%d simulation values with transformers to process""".format (simulated_value_by_day_trafos.count))
        // simulated_value_by_day_trafos.show (5)

        val peaks = simulated_value_by_day_trafos.groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "magnitude")
        log.info ("%d peaks found".format (peaks.count))
        // peaks.show (5)

        val info = peaks.join (simulated_value_by_day_trafos, Seq ("mrid", "date", "magnitude"))
            .withColumnRenamed ("mrid", "transformer")
            .drop ("real_a")
            .drop ("imag_a")
        log.info ("%d peaks joined with simulation values".format (info.count))
        // info.show (5)

        val _measured_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'energy'")
        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf[Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))
        val measured_value_by_day = _measured_value_by_day.withColumn ("power", power[Double, Double, Int].apply (_measured_value_by_day ("real_a"), _measured_value_by_day ("imag_a"), _measured_value_by_day ("interval")))
            .drop ("real_a")
            .drop ("imag_a")
            .cache
        log.info ("""%d measured values to process""".format (measured_value_by_day.count))
        // measured_value_by_day.show (5)

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

        val measured_value_by_day_and_trafo = measured_value_by_day
            .join (
                geojson_points,
                Seq ("mrid"))
        log.info ("""%d measurements with energy consumers to process""".format (measured_value_by_day_and_trafo.count))
        // measured_value_by_day_and_trafo.show (5)

        val maximums = measured_value_by_day_and_trafo.groupBy ("mrid", "date").agg ("power" → "max").withColumnRenamed ("max(power)", "peak")
        log.info ("""%d maximums found""".format (maximums.count))
        // maximums.show (5)

        val peak_times = measured_value_by_day_and_trafo.join (info, Seq ("date", "time", "transformer"))
            .drop ("magnitude")
        log.info ("""%d peak times found""".format (peak_times.count))
        // peak_times.show (5)

        val responsibilities = peak_times.join (maximums, Seq ("mrid", "date"))
            .withColumn ("responsibility", measured_value_by_day_and_trafo ("power") / maximums ("peak"))
        log.info ("""%d responsibility factors evaluated""".format (responsibilities.count))
        // responsibilities.show (5)

        val mrid = responsibilities.schema.fieldIndex ("mrid")
        val date = responsibilities.schema.fieldIndex ("date")
        val interval = responsibilities.schema.fieldIndex ("interval")
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
                (row.getString (mrid), "power", row.getDate (date), row.getInt (interval), row.getTimestamp (time), row.getString (transformer), row.getDouble (p), row.getDouble (peak), resp, "VA/VA", row.getString (simulation))
            }
        )
        // save to Cassandra
        work.saveToCassandra ("cimapplication", "responsibility_by_day",
            SomeColumns ("mrid", "type", "date", "interval", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))
        log.info ("""responsibility records saved to cimapplication.responsibility_by_day""")
    }

    /**
     * Voltage quality
     *
     * Determine the peak devation (voltage drop) from the nominal voltage.
     *
     */
    def voltage_quality (): Unit =
    {
        val simulated_value_by_day = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value_by_day", "keyspace" -> "cimapplication" ))
            .load
            .drop ("interval")
            .drop ("real_b")
            .drop ("real_c")
            .drop ("imag_b")
            .drop ("imag_c")
            .drop ("units")
            .filter ("type = 'voltage'")
            .drop ("type")
            .cache
        log.info ("""%d simulation voltages to process""".format (simulated_value_by_day.count))
        //simulated_value_by_day.show (5)

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

        val simulated_value_by_day_points = simulated_value_by_day
            .withColumn ("magnitude", magnitude[Double, Double].apply (simulated_value_by_day ("real_a"), simulated_value_by_day ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                houses,
                Seq ("simulation", "mrid"))
        log.info ("""%d joined points to process""".format (simulated_value_by_day_points.count))
        //simulated_value_by_day_points.show (5)

        val minimums = simulated_value_by_day_points.groupBy ("mrid", "date")
            .agg ("magnitude" → "min")
            .withColumnRenamed ("min(magnitude)", "magnitude")
        log.info ("%d minimums found".format (minimums.count))
        //minimums.show (5)

        val info = minimums.join (simulated_value_by_day_points, Seq ("mrid", "date", "magnitude"))
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

    def run (): Unit =
    {
        log.info ("Utilization")
        utilization ()
        log.info ("Load Factor")
        load_factor ()
        log.info ("Coincidence Factor")
        coincidence_factor ()
        log.info ("Responsibility Factor")
        responsibility_factor ()
        voltage_quality ()
        log.info ("Voltage quality")
    }
}
