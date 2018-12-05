package ch.ninecode.mfi

import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * Stepped experiment parameters.
 *
 * @param trafo    CIM mRID of the transformer feeding the house.
 * @param feeder   CIM mRID of the connector in the substation feeding the house.
 * @param node     CIM mRID of the topological node being experimented on
 * @param house    CIM mRID of house being experimented on.
 * @param t0       Origin for all experiments.
 * @param slot     Unique experiment number (slot in windowed time).
 * @param window   Duration of the experiment (seconds).
 * @param interval Duration between steps in the experiment (seconds).
 * @param from     Starting PV power (kW).
 * @param to       Ending PV power (kW).
 * @param step     Power increment, resolution of the Einspeiseleistung value (kW).
 */
case class Experiment
(
    trafo: String,
    feeder: String,
    node: String,
    house: String,
    t0: Calendar,
    slot: Int,
    window: Int,
    interval: Int,
    from: Double,
    to: Double,
    step: Double)
{
    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    def dup (c: Calendar): Calendar = c.clone ().asInstanceOf [Calendar]

    /**
     * The start time of the experiment.
     */
    def t1: Calendar =
    {
        val t = dup (t0)
        t.add (Calendar.SECOND, slot * window)
        t
    }

    /**
     * The end time of the experiment.
     */
    def t2: Calendar =
    {
        val t = dup (t0)
        t.add (Calendar.SECOND, (slot + 1) * window)
        t
    }

    override def toString: String = "Experiment(" +
        trafo + "," +
        feeder + "," +
        node + "," +
        house + "," +
        new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z").format (t0.getTime) + "[" + t0.getTimeInMillis + "]," +
        slot + "," +
        window + "," +
        interval + "," +
        from + "," +
        to + "," +
        step + ")"
}
