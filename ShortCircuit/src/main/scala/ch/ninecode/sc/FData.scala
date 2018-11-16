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
    var recommended: Array[Amp] = recommended_fuse_sizing_1

    def fuse_sizing_table (number: Int): Unit =
    {
        number match
        {
            case 1 ⇒ recommended = recommended_fuse_sizing_1
            case 2 ⇒ recommended = recommended_fuse_sizing_2
            case _ ⇒
                val log: Logger = LoggerFactory.getLogger (getClass)
                log.error ("unrecognized fuse sizing table number %s, ignored".format (number))
        }
    }
    def fuse (ik: Double): Double =
    {
        val rating = if (ik.isNaN)
            recommended.last.Rating
        else
        {
            val i = Math.abs (ik)
            recommended.filter (_.Ik <= i).last.Rating
        }
        rating
    }
    def fuseOK (ik: Double, fuses: Branch): Boolean =
    {
        if (null == fuses)
            false
        else
        {
            val ok = fuses match
            {
                case sim: SimpleBranch ⇒
                    val rating = sim.rating.getOrElse (Double.MaxValue)
                    if (0.0 == rating)
                        false
                    else
                        fuse (ik) >= sim.rating.getOrElse (Double.MaxValue)
                case ser: SeriesBranch ⇒
                    fuseOK (ik, ser.seq.last)
                case par: ParallelBranch ⇒
                    // split the current according to the ratio of currents,
                    // or fall back to just divided equally among fuses
                    val last = par.lastFuses
                    val count = last.size
                    val split = if (0 == count) 1.0 else 1.0 / count
                    val total = last.map (_.current).sum
                    last.forall (x ⇒ fuseOK (ik * (if (0.0 == total) split else x.current / total), x))
            }
            ok
        }
    }
    def lastFuseHasMissingValues (fuses: Branch): Boolean =
    {
        if (null == fuses)
            false
        else
        {
            val missing = fuses match
            {
                case sim: SimpleBranch ⇒ sim.rating.getOrElse (Double.MinValue) <= 0.0
                case ser: SeriesBranch ⇒ ser.lastFuses.exists (lastFuseHasMissingValues)
                case par: ParallelBranch ⇒ par.parallel.exists (lastFuseHasMissingValues)
            }
            missing
        }
    }
}
