package ch.ninecode.sc

import org.scalatest.FunSuite

class FDataSuite extends FunSuite
{
    test ("Fuse+")
    {
        assert (FData.fuse (650.001) == 200.0, "expected 200A")
    }

    test ("Fuse-")
    {
        assert (FData.fuse (649.99) == 160.0, "expected 160A")
    }

    test ("FuseSmall")
    {
        assert (FData.fuse (25.0) == 0.0, "expected 0A")
    }

    test ("FuseZero")
    {
        assert (FData.fuse (0.0) == 0.0, "expected 0A")
    }

    test ("FuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0))))
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("No Current FuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", Some (40.0))))
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("ParallelFuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI134", Some (40.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI135", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("No Current ParallelFuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("No Current Parallel Fuse Not OK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (50.0))))))
        assert (!FData.fuseOK (246.912, branch), "expected Not OK")
    }

    test ("FuseNotOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (50.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Fuse0")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", Some (0.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("FuseNone")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Series Branch within parallel Branch OK")
    {
        val branch =
            ParallelBranch ("a", "z", 10.0,
                List (
                    SimpleBranch ("a", "z", 6.0, "TEI11", Some (50.0)),
                    SeriesBranch ("a", "z", 4.0,
                        Seq (
                            SimpleBranch ("a", "z", 4.0, "TEI21", Some (50.0)),
                            SimpleBranch ("a", "z", 4.0, "TEI21", Some (40.0))
                        )
                    )
                )
            )

        assert (FData.fuseOK (280, branch), "expected OK")
    }

    test ("Table 2 Fuse+")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (690.001) == 200.0, "expected 200A")
    }

    test ("Table 2 Fuse-")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (689.99) == 160.0, "expected 160A")
    }

    test ("Table 2 FuseSmall")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (25.0) == 0.0, "expected 0A")
    }

    test ("Table 2 FuseZero")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (0.0) == 0.0, "expected 0A")
    }

    test ("Table 2 FuseOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0))))
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("Table 2 No Current FuseOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", Some (40.0))))
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("Table 2 ParallelFuseOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI134", Some (40.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI135", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("Table 2 No Current ParallelFuseOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("Table 2 FuseNotOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (50.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 2 Fuse0")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", Some (0.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 2 FuseNone")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 2 then 1 Fuse+")
    {
        FData.fuse_sizing_table (2)
        FData.fuse_sizing_table (1)
        assert (FData.fuse (650.001) == 200.0, "expected 200A")
    }

    test ("Table 1 Fuse-")
    {
        FData.fuse_sizing_table (1)
        assert (FData.fuse (649.99) == 160.0, "expected 160A")
    }

    test ("lastFuseHasMissingValues: simple branch")
    {
        val branch1 = SimpleBranch ("a", "b", 4.0, "TEI141", Some (-1.0))
        val branch2 = SimpleBranch ("c", "d", 4.0, "TEI141", Some (40.0))

        assert (FData.lastFuseHasMissingValues (branch1), "has missing valus (-1.0)")
        assert (!FData.lastFuseHasMissingValues (branch2), "has no missing valus (40.0)")
    }

    test ("lastFuseHasMissingValues: series branch")
    {
        val branch1 =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0))))
                )
            )

        val branch2 =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", Some (-1.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", Some (100.0))))
                )
            )

        assert (!FData.lastFuseHasMissingValues (branch1), "has missing values (-1.0)")
        assert (FData.lastFuseHasMissingValues (branch2), "has no missing values")
    }


    test ("lastFuseHasMissingValues: parallel branch")
    {
        val branch1 =
            ParallelBranch ("c", "d", 0.0,
                List (
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (-1.0))))
                )
            )

        val branch2 =
            ParallelBranch ("c", "d", 0.0,
                List (
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (-1.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3)))),
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", Some (-1.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", Some (73737.3))))
                )
            )

        assert (FData.lastFuseHasMissingValues (branch1), "last fuses have at last one missing value (-1.0)")
        assert (!FData.lastFuseHasMissingValues (branch2), "last fuses have no missing values")
    }

    test ("Parallel rating")
    {
        val branch =
            ParallelBranch ("a", "z", 10.0,
                List (
                    SimpleBranch ("a", "z", 6.0, "TEI11", Some (50.0)),
                    SeriesBranch ("a", "z", 4.0,
                        Seq (
                            SimpleBranch ("a", "z", 4.0, "TEI21", Some (50.0)),
                            SimpleBranch ("a", "z", 4.0, "TEI21", Some (40.0))
                        )
                    )
                )
            )

        assert (FData.fuses (280, branch) == "50.0,40.0", "expected 60:40 split")
    }
}
