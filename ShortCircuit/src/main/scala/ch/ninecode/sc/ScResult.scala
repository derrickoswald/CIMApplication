package ch.ninecode.sc

import ch.ninecode.gl.Complex

/**
 * Short circuit results.
 *
 * @param node         TopologicalNode mRID
 * @param equipment    conducting equipment mRID
 * @param terminal     number for equipment
 * @param container    container for equipment
 * @param errors       errors encountered in processing
 * @param tx           the feeding transformer
 * @param tx_impedance the feeding transformer equivalent impedance at the secondary (Ω)
 * @param prev         the previous node
 * @param low_r        minimum aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x        minimum aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_r0       minimum aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x0       minimum aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_ik       one phase short bolted short circuit current at minimum impedance (A)
 * @param low_ik3pol   three phase bolted short circuit current at minimum impedance (A)
 * @param low_ip       maximum aperiodic short-circuit current according to IEC 60909-0 at minimum impedance (A)
 * @param low_sk       short-circuit power at the point of common coupling at minimum impedance (VA)
 * @param imax_3ph_low maximum inrush current (3 phase) for repetition_rate<0.01/min (A)
 * @param imax_1ph_low maximum inrush current (1 phase, line to neutral) for repetition_rate<0.01/min (A)
 * @param imax_2ph_low maximum inrush current (1 phase, line to line) for repetition_rate<0.01/min (A)
 * @param imax_3ph_med maximum inrush current (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 * @param imax_1ph_med maximum inrush current (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 * @param imax_2ph_med maximum inrush current (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 * @param high_r       maximum aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_x       maximum aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_r0      maximum aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_x0      maximum aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_ik      one phase short bolted short circuit current at maximum impedance (A)
 * @param high_ik3pol  three phase bolted short circuit current at maximum impedance (A)
 * @param high_ip      maximum aperiodic short-circuit current according to IEC 60909-0 at maximum impedance (A)
 * @param high_sk      short-circuit power at the point of common coupling at maximum impedance (VA)
 * @param fuses        list of fuse values from the source (primary of feeding transformer) to this node
 */
case class ScResult
(
    node: String,
    equipment: String,
    terminal: Int,
    container: String,
    errors: List[String],
    tx: String,
    tx_impedance: Complex,
    prev: String,
    low_r: Double,
    low_x: Double,
    low_r0: Double,
    low_x0: Double,
    low_ik: Double = 0.0,
    low_ik3pol: Double = 0.0,
    low_ip: Double = 0.0,
    low_sk: Double = 0.0,
    costerm: Double,
    imax_3ph_low: Double = 0.0,
    imax_1ph_low: Double = 0.0,
    imax_2ph_low: Double = 0.0,
    imax_3ph_med: Double = 0.0,
    imax_1ph_med: Double = 0.0,
    imax_2ph_med: Double = 0.0,
    high_r: Double,
    high_x: Double,
    high_r0: Double,
    high_x0: Double,
    high_ik: Double = 0.0,
    high_ik3pol: Double = 0.0,
    high_ip: Double = 0.0,
    high_sk: Double = 0.0,
    fuses: Branch
)
{
    def csv: String =
        node + ";" + equipment + ";" + terminal + ";" + container + ";" + (if (null != errors) errors.mkString (",") else "") + ";" + tx + ";" +
            low_ik + ";" + low_ik3pol + ";" + low_ip + ";" + low_r + ";" + low_x + ";" + low_r0 + ";" + low_x0 + ";" + low_sk + ";" + costerm + ";" +
            imax_3ph_low + ";" + imax_1ph_low + ";" + imax_2ph_low + ";" + imax_3ph_med + ";" + imax_1ph_med + ";" + imax_2ph_med + ";" +
            high_r + ";" + high_x + ";" + high_r0 + ";" + high_x0 + ";" + high_ik + ";" + high_ik3pol + ";" + high_ip + ";" + high_sk + ";" +
            fuseString + ";" + lastFusesString + ";" + iksplitString + ";" + fuseMax + ";" + fuseOK

    def fuseString: String =
    {
        val s = if (null == fuses)
            ""
        else
            fuses.asFuse
        s
    }

    def lastFusesString: String =
    {
        val s = if (null == fuses)
            ""
        else
            fuses.lastFuses.map (_.asFuse).mkString (",")
        s
    }

    def lastFusesId: String =
    {
        val s = if (null == fuses)
            ""
        else
            fuses.lastFuses.map (_.asId).mkString (",")
        s
    }

    def iksplitString: String =
    {
        if (null == fuses)
            ""
        else
            fuses.ratios.map (x ⇒ x._1 * high_ik).map (_.toString).mkString (",")
    }

    def fuseMax: String =
    {
        if (null == fuses)
            ""
        else
            FData.fuses (high_ik, fuses)
    }

    def lastFuseHasMissingValues: Boolean =
    {
        FData.lastFuseHasMissingValues (fuses)
    }

    def fuseOK: Boolean =
    {
        if (null == fuses)
            false
        else
            FData.fuseThatBlows (high_ik, fuses).nonEmpty
    }

    def toPseudo: PseudoScResult =
    {
        PseudoScResult (
            node,
            equipment,
            terminal: Int,
            container,
            errors,
            tx,
            prev,
            low_r,
            low_x,
            low_r0,
            low_x0,
            low_ik,
            low_ik3pol,
            low_ip,
            low_sk,
            costerm,
            imax_3ph_low,
            imax_1ph_low,
            imax_2ph_low,
            imax_3ph_med,
            imax_1ph_med,
            imax_2ph_med,
            high_r,
            high_x,
            high_r0,
            high_x0,
            high_ik,
            high_ik3pol,
            high_ip,
            high_sk,
            if (null != fuses) fuses.asString else ""
        )
    }
}

object ScResult
{
    val csv_header: String = "node;equipment;terminal;container;errors;transformer;low_ik;low_ik3pol;low_ip;low_r;low_x;low_r0;low_x0;low_sk;costerm;imax_3ph_low;imax_1ph_low;imax_2ph_low;imax_3ph_med;imax_1ph_med;imax_2ph_med;high_r;high_x;high_r0;high_x0;high_ik;high_ik3pol;high_ip;high_sk;fuses;last_fuses;iksplit;fusemax;fuseOK"
}

// change fuses: Branch to a String for creating a DataSet
case class PseudoScResult
(
    node: String,
    equipment: String,
    terminal: Int,
    container: String,
    errors: List[String],
    tx: String,
    prev: String,
    low_r: Double,
    low_x: Double,
    low_r0: Double,
    low_x0: Double,
    low_ik: Double = 0.0,
    low_ik3pol: Double = 0.0,
    low_ip: Double = 0.0,
    low_sk: Double = 0.0,
    costerm: Double,
    imax_3ph_low: Double = 0.0,
    imax_1ph_low: Double = 0.0,
    imax_2ph_low: Double = 0.0,
    imax_3ph_med: Double = 0.0,
    imax_1ph_med: Double = 0.0,
    imax_2ph_med: Double = 0.0,
    high_r: Double,
    high_x: Double,
    high_r0: Double,
    high_x0: Double,
    high_ik: Double = 0.0,
    high_ik3pol: Double = 0.0,
    high_ip: Double = 0.0,
    high_sk: Double = 0.0,
    fuses: String
)
