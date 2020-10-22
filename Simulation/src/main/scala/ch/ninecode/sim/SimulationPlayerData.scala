package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * Measurement time series element.
 *
 * @param transformer The mRID of the transformer that needs this measurement.
 * @param mrid        The mRID of the element that this measurement applies to.
 * @param `type`      The measurement type - 'energy' is special (it isn't an average) so it is converted according to the period.
 * @param time        Number of milliseconds since the epoc.
 * @param period      Number of milliseconds this reading applies to.
 * @param units       The units for this reading.
 * @param readings    Array of real and imaginary meter reading values.
 */
case class SimulationPlayerData (
    transformer: String = "",
    mrid: String = "",
    `type`: String = "",
    time: Long = 0L,
    period: Int = 0,
    units: String = "",
    readings: Array[Double] = Array(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
{
    def toTimeStamp (t: Long): String =
    {
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
        calendar.setTimeInMillis(0L)

        val cassandra_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
        cassandra_date_format.setCalendar(calendar)

        val c = Calendar.getInstance
        c.setTimeZone(TimeZone.getTimeZone("GMT"))
        c.setTimeInMillis(t)
        cassandra_date_format.format(c.getTime)
    }

    override def toString: String = s"""("$transformer" "$mrid" "${`type`}" ${toTimeStamp(time)} [${readings.mkString(",")}])"""
}