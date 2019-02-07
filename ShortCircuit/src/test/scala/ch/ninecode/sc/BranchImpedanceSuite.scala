package ch.ninecode.sc

import ch.ninecode.gl.Complex
import org.scalatest.FunSuite

class BranchImpedanceSuite extends FunSuite
{
    test ("SeriesImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    SimpleBranch ("a", "b", 4.0, "TEI141", Some (40.0), Complex (2.0, 2.0), Complex (1.0, 1.0)),
                    SimpleBranch ("b", "c", 4.0, "TEI141", Some (40.0), Complex (2.0, 2.0), Complex (1.0, 1.0))))
        val z1 = branch.z1
        val z0 = branch.z0
        assert (z1 == Complex (4.0, 4.0), "expected series 4Ω")
        assert (z0 == Complex (2.0, 2.0), "expected series 2Ω")
    }

    test ("ParallelImpedance")
    {
        val branch =
            ParallelBranch ("a", "b", 0.0,
                List (
                    SimpleBranch ("a", "b", 0.0, "TEI124", Some (288282.0), Complex (2.0, 2.0), Complex (1.0, 1.0)),
                    SimpleBranch ("a", "b", 0.0, "TEI123", Some (73737.3), Complex (2.0, 2.0), Complex (1.0, 1.0))))
        val z1 = branch.z1
        val z0 = branch.z0
        assert (z1 == Complex (1.0, 1.0), "expected parallel 1Ω")
        assert (z0 == Complex (0.5, 0.5), "expected parallel 0.5Ω")
    }

    test ("SeriesParallelImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    ParallelBranch ("a", "b", 0.0,
                        List (
                            SimpleBranch ("a", "b", 0.0, "TEI124", Some (288282.0), Complex (2.0, 2.0), Complex (1.0, 1.0)),
                            SimpleBranch ("a", "b", 0.0, "TEI123", Some (73737.3), Complex (2.0, 2.0), Complex (1.0, 1.0)))),
                    SimpleBranch ("b", "c", 0.0, "TEI125", Some (73737.3), Complex (2.0, 2.0), Complex (1.0, 1.0))))
        val z1 = branch.z1
        val z0 = branch.z0
        assert (z1 == Complex (3.0, 3.0), "expected series parallel 3Ω")
        assert (z0 == Complex (1.5, 1.5), "expected series parallel 1.5Ω")
    }
}
