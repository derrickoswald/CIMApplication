package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import ch.ninecode.gl.ThreePhaseComplexDataElement
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.LocalDate

case class SimulationCassandraInsert (cluster: Cluster)
{
    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)
    val just_date: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd")
    just_date.setCalendar (calendar)

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    case class Accumulator (
        sql: String,
        statement: BoundStatement,
        intervals: Int,
        average: Boolean,
        var count: Int = 0,
        var value_a_re: Double = 0.0,
        var value_a_im: Double = 0.0,
        var value_b_re: Double = 0.0,
        var value_b_im: Double = 0.0,
        var value_c_re: Double = 0.0,
        var value_c_im: Double = 0.0
        )
    {
        def reset (): Unit =
        {
            count = 0
            value_a_re = 0.0
            value_a_im = 0.0
            value_b_re = 0.0
            value_b_im = 0.0
            value_c_re = 0.0
            value_c_im = 0.0
        }
    }

    def execute (data: Iterator[ThreePhaseComplexDataElement], typ: String, interval: Int, simulation: String, aggregates: List[SimulationAggregate]): Int =
    {
        var ret = 0
        val session = cluster.connect
        val accumulators = aggregates.map (
            aggregate ⇒
            {
                val sql = pack (
                    """
                    | insert into cimapplication.simulated_value_by_day
                    | (mrid, type, date, interval, time, real_a, imag_a, real_b, imag_b, real_c, imag_c, units, simulation)
                    | values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.stripMargin) + aggregate.time_to_live
                val prepared = session.prepare (sql)
                val bound = prepared.bind ()
                Accumulator (sql, bound, aggregate.intervals, typ != "energy")
            }
        )
        val timestamp = new Date ()
        data.foreach (
            entry ⇒
            {
                accumulators.foreach (
                    accumulator ⇒
                    {
                        accumulator.count = accumulator.count + 1
                        accumulator.value_a_re = accumulator.value_a_re + entry.value_a.re
                        accumulator.value_a_im = accumulator.value_a_im + entry.value_a.im
                        accumulator.value_b_re = accumulator.value_b_re + entry.value_b.re
                        accumulator.value_b_im = accumulator.value_b_im + entry.value_b.im
                        accumulator.value_c_re = accumulator.value_c_re + entry.value_c.re
                        accumulator.value_c_im = accumulator.value_c_im + entry.value_c.im
                        if (accumulator.count >= accumulator.intervals)
                        {
                            // Java and Cassandra timestamps are in milliseconds, but Spark is in seconds not milliseconds
                            val timepoint = entry.millis - 1000L * (interval * (accumulator.intervals - 1))
                            val date = LocalDate.fromMillisSinceEpoch (timepoint)
                            timestamp.setTime (timepoint)

                            accumulator.statement.setString        ( 0, entry.element)
                            accumulator.statement.setString        ( 1, typ)
                            accumulator.statement.setDate          ( 2, date)
                            accumulator.statement.setInt           ( 3, interval * accumulator.intervals * 1000)
                            accumulator.statement.setTimestamp     ( 4, timestamp)
                            if (accumulator.average)
                            {
                                val n = accumulator.intervals
                                accumulator.statement.setDouble    ( 5, accumulator.value_a_re / n)
                                accumulator.statement.setDouble    ( 6, accumulator.value_a_im / n)
                                accumulator.statement.setDouble    ( 7, accumulator.value_b_re / n)
                                accumulator.statement.setDouble    ( 8, accumulator.value_b_im / n)
                                accumulator.statement.setDouble    ( 9, accumulator.value_c_re / n)
                                accumulator.statement.setDouble    (10, accumulator.value_c_im / n)
                            }
                            else
                            {
                                accumulator.statement.setDouble    ( 5, accumulator.value_a_re)
                                accumulator.statement.setDouble    ( 6, accumulator.value_a_im)
                                accumulator.statement.setDouble    ( 7, accumulator.value_b_re)
                                accumulator.statement.setDouble    ( 8, accumulator.value_b_im)
                                accumulator.statement.setDouble    ( 9, accumulator.value_c_re)
                                accumulator.statement.setDouble    (10, accumulator.value_c_im)
                            }
                            accumulator.statement.setString        (11, entry.units)
                            accumulator.statement.setString        (12, simulation)

                            session.execute (accumulator.statement)
                            ret = ret + 1
                            accumulator.reset ()
                        }
                    }
                )
            }
        )
        ret
    }
}
