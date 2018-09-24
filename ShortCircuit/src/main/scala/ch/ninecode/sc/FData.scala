package ch.ninecode.sc

object FData
{
    case class Amp (Ik: Double, Rating: Double)
    val recommended: Array[Amp] =
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
    def fuse (ik: Double): Double =
    {
        if (ik.isNaN)
            recommended.last.Rating
        else
        {
            val i = Math.abs (ik)
            recommended.filter (_.Ik <= i).last.Rating
        }
    }
    def fuseOK (ik: Double, fuses: List[List[(String, Double)]]): Boolean =
    {
        if (null == fuses)
            false
        else
        {
            var len = fuses.last.length
            val ok = fuses.last.map(l => fuse(ik / len) >= l._2)
            ok.forall(identity)
        }
    }
}
