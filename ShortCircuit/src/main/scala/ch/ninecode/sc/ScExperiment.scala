package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.util.Complex

/**
 * Experiment parameters.
 *
 * @param trafo     CIM MRID of the transformer feeding the house.
 * @param mrid      CIM MRID of the node to apply the short circuit to.
 * @param equipment CIM MRID of equipment (house) being experimented on.
 * @param t0        Origin for all experiments.
 * @param slot      Unique experiment number (slot in windowed time).
 * @param window    Duration of the experiment (seconds).
 * @param voltage   The node nominal voltage.
 * @param impedance The impedance (short circuit value) to apply (Ω).
 */
case class ScExperiment
(
    trafo: String,
    mrid: String,
    equipment: String,
    t0: Calendar,
    slot: Int,
    window: Int,
    voltage: Double,
    impedance: Complex)
{
    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     * @return A clone of the calendar.
     */
    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    def dup (c: Calendar): Calendar = c.clone ().asInstanceOf [Calendar]

    /**
     * The start time of the experiment.
     */
    lazy val t1: Calendar =
    {
        val t = dup (t0)
        t.add (Calendar.SECOND, slot * window)
        t
    }

    /**
     * The end time of the experiment.
     */
    lazy val t2: Calendar =
    {
        val t = dup (t0)
        t.add (Calendar.SECOND, (slot + 1) * window)
        t
    }

    override def toString: String =
    {
        val time = ScExperiment.TIMEFORMAT.format (t0.getTime)
        s"Experiment($trafo,$mrid,$equipment,$time[${t0.getTimeInMillis}],$slot,$window,${voltage}V,${impedance.toString}Ω)"
    }
}
object ScExperiment
{
    lazy val TIMEFORMAT = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
}