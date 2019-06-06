package ch.ninecode.sc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object FData
{

    case class Amp (Ik: Double, Rating: Double)

    val recommended_fuse_sizing_1: Array[Amp] =
        Array (
            Amp (0, 0), // failsafe fallback for currents less than 65A
            Amp (65, 25),
            Amp (105, 40),
            Amp (140, 50),
            Amp (180, 63),
            Amp (240, 80),
            Amp (320, 100),
            Amp (380, 125),
            Amp (500, 160),
            Amp (650, 200),
            Amp (800, 250),
            Amp (1050, 315),
            Amp (1300, 400),
            Amp (1750, 500),
            Amp (2400, 630)
        )
    val recommended_fuse_sizing_2: Array[Amp] =
        Array (
            Amp (0, 0), // failsafe fallback for currents less than 28A
            Amp (28, 10),
            Amp (40, 16),
            Amp (55, 20),
            Amp (70, 25),
            Amp (93, 32),
            Amp (120, 40),
            Amp (160, 50),
            Amp (190, 63),
            Amp (230, 80),
            Amp (305, 100),
            Amp (380, 125),
            Amp (490, 160),
            Amp (690, 200),
            Amp (820, 250),
            Amp (1150, 315),
            Amp (1350, 400),
            Amp (1900, 500),
            Amp (2500, 630)
        )
    val recommended_fuse_sizing_3: Array[Amp] =
        Array (
            Amp (0, 0), // failsafe fallback for currents less than 200A
            Amp (200, 60),
            Amp (250, 75),
            Amp (300, 100),
            Amp (340, 125),
            Amp (500, 150),
            Amp (600, 200),
            Amp (720, 250),
            Amp (850, 300),
            Amp (1150, 400)
        )

    var fuse: (Double, Branch) ⇒ Double = fuse1

    def fuse_sizing_table (number: Int): Unit =
    {
        number match
        {
            case 1 ⇒
                fuse = fuse1

            case 2 ⇒
                fuse = fuse2

            case 3 ⇒
                fuse = fuse3

            case _ ⇒
                val log: Logger = LoggerFactory.getLogger (getClass)
                log.error ("unrecognized fuse sizing table number %s, ignored".format (number))
        }
    }

    def fuse1 (ik: Double, branch: Branch = null): Double =
    {
        if (ik.isNaN)
            recommended_fuse_sizing_1.last.Rating
        else
            recommended_fuse_sizing_1.filter (_.Ik <= Math.abs (ik)).last.Rating
    }

    def fuse2 (ik: Double, branch: Branch = null): Double =
    {
        if (ik.isNaN)
            recommended_fuse_sizing_2.last.Rating
        else
            recommended_fuse_sizing_2.filter (_.Ik <= Math.abs (ik)).last.Rating
    }

    def fuse3 (ik: Double, branch: Branch): Double =
    {
        if (ik.isNaN)
            recommended_fuse_sizing_3.last.Rating
        else
            branch match
            {
                case simple: SimpleBranch ⇒
                    if (simple.name.startsWith ("SEV"))
                        recommended_fuse_sizing_3.filter (_.Ik <= Math.abs (ik)).last.Rating
                    else
                        recommended_fuse_sizing_1.filter (_.Ik <= Math.abs (ik)).last.Rating
                case _ ⇒
                    recommended_fuse_sizing_1.filter (_.Ik <= Math.abs (ik)).last.Rating
            }
    }

    def fuses (ik: Double, branches: Branch): String =
    {
        if (ik.isNaN || (null == branches))
            fuse (Double.NaN, branches).toInt.toString
        else
            branches.ratios.map (x ⇒ (x._1 * Math.abs (ik), x._2)).map (x ⇒ fuse (x._1, x._2).toInt).mkString (",")
    }

    def fuseOK (ik: Double, branches: Branch): Boolean =
    {
        if (null == branches)
            false
        else
        {
            val status: Iterable[Boolean] = for
            {
                pair ← branches.ratios
                current = pair._1 * Math.abs (ik)
                ok = if (current.isNaN)
                    false
                else
                    pair._2 match
                    {
                        case sim: SimpleBranch ⇒
                            val rating = sim.rating.getOrElse (Double.MaxValue)
                            if (0.0 == rating)
                                false
                            else
                                fuse (current, sim) >= rating
                        case ser: SeriesBranch ⇒
                            fuseOK (current, ser.seq.last)
                        case par: ParallelBranch ⇒
                            fuseOK (current, par)
                    }
            }
            yield ok
            status.forall (x ⇒ x)
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
                case sim: SimpleBranch ⇒ sim.rating.getOrElse (Double.MinValue) <= 0.0
                case ser: SeriesBranch ⇒ ser.lastFuses.exists (lastFuseHasMissingValues)
                case par: ParallelBranch ⇒ par.parallel.exists (lastFuseHasMissingValues)
                case com: ComplexBranch ⇒ com.basket.exists(lastFuseHasMissingValues)
            }
            missing
        }
    }
}
