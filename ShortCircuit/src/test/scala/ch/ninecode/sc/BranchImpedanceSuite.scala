package ch.ninecode.sc

import org.scalatest.FunSuite

import ch.ninecode.gl.Complex

class BranchImpedanceSuite extends FunSuite
{
    test ("SeriesImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    SimpleBranch ("a", "b", 4.0, "TEI141", Some (40.0), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0))),
                    SimpleBranch ("b", "c", 4.0, "TEI141", Some (40.0), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0)))))
        assert (branch.z == Impedanzen (Complex (4.0, 4.0), Complex (2.0, 2.0), Complex (4.0, 4.0), Complex (2.0, 2.0)), "expected series z1=4Ω, z0=2Ω")
    }

    test ("ParallelImpedance2")
    {
        val branch =
            ParallelBranch ("a", "b", 0.0,
                List (
                    SimpleBranch ("a", "b", 0.0, "TEI124", Some (288282.0), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0))),
                    SimpleBranch ("a", "b", 0.0, "TEI123", Some (73737.3), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0)))))
        assert (branch.z == Impedanzen (Complex (1.0, 1.0), Complex (0.5, 0.5), Complex (1.0, 1.0), Complex (0.5, 0.5)), "expected parallel z1=1Ω z0=0.5Ω")
    }

    test ("ParallelImpedance3")
    {
        val branch =
            ParallelBranch ("a", "b", 0.0,
                List (
                    SimpleBranch ("a", "b", 0.0, "TEI124", Some (288282.0), Impedanzen (Complex (3.0, 3.0), Complex (0.75, 0.75), Complex (3.0, 3.0), Complex (0.75, 0.75))),
                    SimpleBranch ("a", "b", 0.0, "TEI123", Some (73737.3), Impedanzen (Complex (3.0, 3.0), Complex (0.75, 0.75), Complex (3.0, 3.0), Complex (0.75, 0.75))),
                    SimpleBranch ("a", "b", 0.0, "TEI122", Some (53636.9), Impedanzen (Complex (3.0, 3.0), Complex (0.75, 0.75), Complex (3.0, 3.0), Complex (0.75, 0.75)))))
        assert (branch.z == Impedanzen (Complex (1.0, 1.0), Complex (0.25, 0.25), Complex (1.0, 1.0), Complex (0.25, 0.25)), "expected parallel z1=1Ω z0=0.25Ω")
    }

    test ("SeriesParallelImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    ParallelBranch ("a", "b", 0.0,
                        List (
                            SimpleBranch ("a", "b", 0.0, "TEI124", Some (288282.0), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0))),
                            SimpleBranch ("a", "b", 0.0, "TEI123", Some (73737.3), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0))))),
                    SimpleBranch ("b", "c", 0.0, "TEI125", Some (73737.3), Impedanzen (Complex (2.0, 2.0), Complex (1.0, 1.0), Complex (2.0, 2.0), Complex (1.0, 1.0)))))
        assert (branch.z == Impedanzen (Complex (3.0, 3.0), Complex (1.5, 1.5), Complex (3.0, 3.0), Complex (1.5, 1.5)), "expected series parallel z1=3Ω z0=1.5Ω")
    }
}
