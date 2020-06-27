package ch.ninecode.gl

import org.apache.spark.sql.SparkSession

import ch.ninecode.net.LineData
import ch.ninecode.net.Lines

/**
 * Fetch all lines using the current value of static LineDetails properties.
 *
 * @param session The Spark session to use.
 */
case class Probe (session: SparkSession)
{
    def acceptAllLines (line_filter: LineData): Boolean = true

    def stringify (line: LineData): (String, String) =
    {
        val id = line.lines.map (_.line.id).toArray.sortWith (_ < _).mkString ("_")
        val z = line.lines.map (_.impedance.toString).mkString("_")
        (id, z)
    }

    def getLines: Array[(String, String)] = Lines (session).getLines (acceptAllLines).map (stringify).collect
}
