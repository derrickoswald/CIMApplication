package ch.ninecode.sc

/**
 * The fuse breakpoint table for a given standard.
 *
 * @param Standard fusing standard, e.g.:
 *                 "DIN" Deutsches Institut für Normung
 *                 "SEV" Schweizer Elektrotechnischer Verein
 *                 "" unspecified standard
 * @param Table    ordered list of breakpoints for the stepwise ascending fuse size based on short circuit current
 *                 the first element, Table(0), should be the minimum size fuse where Ik is zero (0A)
 */
case class FuseTable (
    Standard: String,
    Table: Array[Amp]
)
{
    override def toString: String = s"""$Standard: [${Table.map(_.toString).mkString(",")}]"""
}

object FuseTable
{
    def default: FuseTable =
        FuseTable(
            "DIN",
            Array(
                Amp(0, 0), // failsafe fallback for currents less than 65A
                Amp(65, 25),
                Amp(105, 40),
                Amp(140, 50),
                Amp(180, 63),
                Amp(240, 80),
                Amp(320, 100),
                Amp(380, 125),
                Amp(500, 160),
                Amp(650, 200),
                Amp(800, 250),
                Amp(1050, 315),
                Amp(1300, 400),
                Amp(1750, 500),
                Amp(2400, 630)
            )
        )
}