package ch.ninecode.sc

import ch.ninecode.sc.branch.Branch
import ch.ninecode.sc.branch.ComplexBranch
import ch.ninecode.sc.branch.ParallelBranch
import ch.ninecode.sc.branch.SeriesBranch
import ch.ninecode.sc.branch.SimpleBranch
import ch.ninecode.util.Complex

/**
 * Short circuit results.
 *
 * @param node         TopologicalNode mRID
 * @param equipment    conducting equipment mRID
 * @param voltage      nominal node voltage (V)
 * @param terminal     number for equipment
 * @param container    container for equipment
 * @param errors       errors encountered in processing
 * @param tx           the feeding transformer
 * @param prev         the previous node
 * @param low_r        minimum aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x        minimum aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_r0       minimum aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x0       minimum aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_ik       one phase short bolted short circuit current at minimum impedance (A)
 * @param low_ik3pol   three phase bolted short circuit current at minimum impedance (A)
 * @param low_ip       maximum aperiodic short-circuit current according to IEC 60909-0 at minimum impedance (A)
 * @param low_sk       short-circuit power at the point of common coupling at minimum impedance (VA)
 * @param costerm      cos(Ψ-φ) value used in calculating imax values (dimensionless)
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
 * @param branches     network from the source (primary of feeding transformer) to this node
 */
case class ScResult
(
    node: String,
    equipment: String,
    voltage: Double,
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
    costerm: Double = 1.0,
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
    branches: Branch
)
{
    def csv (options: ShortCircuitOptions): String =
        s"""$node;$equipment;$terminal;$container;${if (null != errors) errors.mkString(",") else ""};$tx$low_ik;$low_ik3pol;$low_ip;$low_r;$low_x;$low_r0;$low_x0;$low_sk;$costerm;$imax_3ph_low;$imax_1ph_low;$imax_2ph_low;$imax_3ph_med;$imax_1ph_med;$imax_2ph_med$high_r;$high_x;$high_r0;$high_x0;$high_ik;$high_ik3pol;$high_ip;$high_sk$fuseString;$lastFusesString;$iksplitString;${fuseMax(options, Some("DIN"))};${fuseMax(options, Some("SEV"))};${fuseOK(options)}"""

    def fuseString: String =
    {
        val s = if (null == branches)
            ""
        else
            branches.justFuses match
            {
                case Some(branch) => branch.asFuse
                case None => ""
            }
        s
    }

    def lastFusesString: String =
    {
        if (branches == null)
            ""
        else
            branches.justLastFuses.map(_.asFuse).mkString(",")
    }

    def lastFuseStandard: String =
    {
        if (branches == null)
            ""
        else
            branches.justLastFuses.map(std).mkString(",")
    }

    def lastFusesId: String =
    {
        if (branches == null)
            ""
        else
            branches.justLastFuses.map(_.asId).mkString(",")
    }

    def iksplitString: String =
    {
        def getIkSplitStringForBranch(branch: Branch): String = branch.ratios.map(x => x._1 * high_ik).map(_.toInt.toString).mkString(",")

        if (null == branches)
            ""
        else
            if (branches.isInstanceOf[ComplexBranch])
            {
                getIkSplitStringForBranch(branches)
            } else
            {
                branches.justFuses match
                {
                    case Some(branch) => getIkSplitStringForBranch(branch)
                    case None => ""
                }
            }
    }

    def std (branch: Branch): String =
    {
        branch match
        {
            case simple: SimpleBranch => simple.standard
            case series: SeriesBranch => series.lastFuses.lastOption match
            {
                case Some(last) => std(last)
                case _ => ""
            }
            case _ => ""
        }

    }

    def fuses (ik: Double, options: ShortCircuitOptions, branches: Branch, standard: Option[String] = None): String =
    {
        if (ik.isNaN || (null == branches))
        {
            val fuseType = standard match
            {
                case Some(std) => std
                case None => std(branches)
            }
            options.fuse_table.fuse(Double.NaN, fuseType).toInt.toString
        } else
        {
            def getFuseFromBranch (x: (Double, Branch)): Int =
            {
                val fuseType = standard match
                {
                    case Some(std) => std
                    case None => std(x._2)
                }
                options.fuse_table.fuse(x._1, fuseType).toInt
            }

            val ikSplitPerBranch = branches.ratios.map(x => (x._1 * Math.abs(ik), x._2))
            ikSplitPerBranch.map(getFuseFromBranch).mkString(",")
        }
    }

    def fuseMax (options: ShortCircuitOptions, standard: Option[String] = None): String =
    {
        if (null == branches)
            ""
        else
            if (branches.isInstanceOf[ComplexBranch])
            {
                fuses(high_ik, options, branches, standard)
            } else
                branches.justFuses match
                {
                    case Some(branch) => fuses(high_ik, options, branch, standard)
                    case None => ""
                }
    }

    def lastFuseHasMissingValues (branches: Branch): Boolean =
    {
        if (null == branches)
            true
        else
        {
            val missing = branches match
            {
                case sim: SimpleBranch => sim.rating.getOrElse(Double.MinValue) <= 0.0
                case ser: SeriesBranch => ser.lastFuses.exists(lastFuseHasMissingValues)
                case par: ParallelBranch => par.parallel.exists(lastFuseHasMissingValues)
                case com: ComplexBranch => com.basket.exists(lastFuseHasMissingValues)
            }
            missing
        }
    }

    def lastFuseHasMissingValues: Boolean =
    {
        branches.justFuses match
        {
            case Some(branch) => lastFuseHasMissingValues(branch)
            case None => true
        }
    }

    def calculate_ik (voltage: Double, cmin: Double, impedanz: Complex, null_impedanz: Complex): Double =
    {
        val root3 = Math.sqrt(3.0)

        // Einpoligen Kurzschlussstrom berechnen
        val ik_z = root3 * cmin * voltage
        val ik_n_sqrt1 = !impedanz
        val ik_n_sqrt2 = !null_impedanz
        val ik_n = 2 * ik_n_sqrt1 + ik_n_sqrt2
        ik_z / ik_n
    }

    /**
     * Check the network fuses to see if the short-circuit current could be interrupted.
     *
     * @return <code>true</code> if the network would be interrupted because of the short circuit, or <code>false</code> otherwise
     */
    @SuppressWarnings(Array("org.wartremover.warts.Return"))
    def fuseOK (options: ShortCircuitOptions): Boolean =
    {
        if (null == branches)
            false
        else
        {
            var network: Option[Branch] = Some(branches)
            var changed = false
            // recompute the impedance of the trafo and the EquivalentInjection together
            val high_z = Impedanzen(Complex(low_r, low_x), Complex(low_r0, low_x0), Complex(high_r, high_x), Complex(high_r0, high_x0))
            val supply_z = high_z - branches.z(Impedanzen())

            do
            {
                network match
                {
                    case Some(n: Branch) =>
                        val z = n.z(supply_z)
                        // first time through this should be high_ik
                        val ik = calculate_ik(voltage, options.cmin, z.impedanz_high, z.null_impedanz_high)
                        val res = n.checkFuses(ik, options)
                        changed = res._1
                        network = res._2
                    case None =>
                }
            }
            while (changed && network.isDefined)
            network.isEmpty
        }
    }
}

object ScResult
{
    val csv_header: String = "node;equipment;terminal;container;errors;transformer;low_ik;low_ik3pol;low_ip;low_r;low_x;low_r0;low_x0;low_sk;costerm;imax_3ph_low;imax_1ph_low;imax_2ph_low;imax_3ph_med;imax_1ph_med;imax_2ph_med;high_r;high_x;high_r0;high_x0;high_ik;high_ik3pol;high_ip;high_sk;fuses;last_fuses;iksplit;fusemaxDIN;fusemaxSEV;fuseOK"
}
