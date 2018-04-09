package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import ch.ninecode.gl.ThreePhaseComplexDataElement
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

    def execute (data: Iterator[ThreePhaseComplexDataElement], typ: String, interval: Int): Unit =
    {
        val session = cluster.connect
        val sql = pack (
            """
            | insert into cimapplication.simulated_value_by_day
            | (mrid, type, date, time, interval, real_a, imag_a, real_b, imag_b, real_c, imag_c, units)
            | values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            | using ttl 300
            """.stripMargin)
        val prepared = session.prepare (sql)
        val bound = prepared.bind ()
        val timestamp = new Date ()
        data.foreach (
            entry ⇒
            {
                val date = LocalDate.fromMillisSinceEpoch (entry.millis)
                timestamp.setTime (entry.millis)
                bound.setString    ( 0, entry.element)
                bound.setString    ( 1, typ)
                bound.setDate      ( 2, date)
                bound.setTimestamp ( 3, timestamp)
                bound.setInt       ( 4, interval)
                bound.setDouble    ( 5, entry.value_a.re)
                bound.setDouble    ( 6, entry.value_a.im)
                bound.setDouble    ( 7, entry.value_b.re)
                bound.setDouble    ( 8, entry.value_b.im)
                bound.setDouble    ( 9, entry.value_c.re)
                bound.setDouble    (10, entry.value_c.im)
                bound.setString    (11, entry.units)

                session.execute (bound)
            }
        )
    }
}
