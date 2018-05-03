package ch.ninecode.sim

import java.sql.Date
import java.sql.Timestamp

import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.SparkSession

import org.apache.spark.rdd.RDD

case class Summarize (spark: SparkSession)
{

    def main (): Unit =
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
        println (simulated_value_by_day.count)
        simulated_value_by_day.show (5)

        val lines = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "geojson_lines", "keyspace" -> "cimapplication" ))
            .load
            .drop ("type")
            .drop ("geometry")
            .cache
        println (lines.count)
        lines.show (5)

        val join = simulated_value_by_day
            .join (
                lines,
                Seq ("simulation", "mrid"))

        join.show (5)

        val mrid = join.schema.fieldIndex ("mrid")
        val typ = join.schema.fieldIndex ("type")
        val date = join.schema.fieldIndex ("date")
        val interval = join.schema.fieldIndex ("interval")
        val time = join.schema.fieldIndex ("time")
        val real_a = join.schema.fieldIndex ("real_a")
        val imag_a = join.schema.fieldIndex ("imag_a")
        val units = join.schema.fieldIndex ("units")
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
                (row.getString (mrid), row.getString (typ), row.getDate (date), row.getInt (interval), row.getTimestamp (time), percent, row.getString (units), row.getString (simulation), row.getString (transformer))
            }
        )
        println (work.count)
        println (work.take (5).mkString("\n"))

        // save to Cassandra
        work.saveToCassandra ("cimapplication", "utilization_by_day",
            SomeColumns ("mrid", "type", "date", "interval", "time", "percent", "units", "simulation", "transformer"))

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
        val fifteen_minute = work.filter (_._4 == 900)
        // make a multiple key of transformer and date separated by |
        val summary = fifteen_minute.keyBy (record ⇒ record._9 + "|" + record._3).aggregateByKey (initial) (seqOp, combOp)
            .map (day ⇒ (day._1.substring (0, day._1.indexOf ("|")), day._1.substring (day._1.indexOf ("|") + 1), day._2._2, day._2._3 / day._2._1, day._2._4))
        println (summary.count)
        println (summary.take (5).mkString("\n"))

        // save to Cassandra
        summary.saveToCassandra ("cimapplication", "utilization_summary_by_day",
            SomeColumns ("transformer", "date", "min", "avg", "max"))
    }
}
