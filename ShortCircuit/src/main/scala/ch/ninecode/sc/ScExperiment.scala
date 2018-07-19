package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl.Complex

/**
 * Experiment parameters.
 *
 * @param trafo CIM MRID of the transformer feeding the house.
 * @param house CIM MRID of house being experimented on.
 * @param t0 Origin for all experiments.
 * @param slot Unique experiment number (slot in windowed time).
 * @param window Duration of the experiment (seconds).
 * @param impedance The impedance (short circuit value) to apply.
 */
case class ScExperiment (
    trafo: String,
    house: String,
    t0: Calendar,
    slot: Int,
    window: Int,
    impedance: Complex)
{
    /**
     * Calendar duplication utility function.
     * @param c The Calendar value to be cloned.
     */
    def dup(c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    /**
     * The start time of the experiment.
     */
    def t1: Calendar = { val t = dup (t0); t.add (Calendar.SECOND, slot * window); t }

    /**
     * The end time of the experiment.
     */
    def t2: Calendar = { val t = dup (t0); t.add (Calendar.SECOND, (slot + 1) * window); t }

    override def toString: String = "Experiment(" +
        trafo + "," +
        house + "," +
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format (t0.getTime) + "[" + t0.getTimeInMillis + "]," +
        slot + "," +
        window + ")"
}
