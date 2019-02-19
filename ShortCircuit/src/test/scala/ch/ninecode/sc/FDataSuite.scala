package ch.ninecode.sc

import ch.ninecode.gl.Complex
import org.scalatest.FunSuite

class FDataSuite extends FunSuite
{
    test ("Fuse+")
    {
        assert (FData.fuse (650.001, null) == 200.0, "expected 200A")
    }

    test ("Fuse-")
    {
        assert (FData.fuse (649.99, null) == 160.0, "expected 160A")
    }

    test ("FuseSmall")
    {
        assert (FData.fuse (25.0, null) == 0.0, "expected 0A")
    }

    test ("FuseZero")
    {
        assert (FData.fuse (0.0, null) == 0.0, "expected 0A")
    }

    test ("FuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (400.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (400.0)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (125.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0))))
        assert (branch.asFuse == "([400,400],[125,100],40)", "fuse string")
        assert (branch.asId == "([TEI124,TEI123],[TEI134,TEI135],TEI141)", "id string")
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("No Current FuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (400.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (400.0)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (125.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", "", Some (40.0))))
        assert (branch.asFuse == "([400,400],[125,100],40)", "fuse string")
        assert (branch.asId == "([TEI124,TEI123],[TEI134,TEI135],TEI141)", "id string")
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("ParallelFuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (400.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (400.0)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (125.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI141", "", Some (40.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI142", "", Some (40.0))))))
        assert (branch.asFuse == "([400,400],[125,100],[40,40])", "fuse string")
        assert (branch.asId == "([TEI124,TEI123],[TEI134,TEI135],[TEI141,TEI142])", "id string")
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("No Current ParallelFuseOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("No Current Parallel Fuse Not OK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (50.0))))))
        assert (!FData.fuseOK (246.912, branch), "expected Not OK")
    }

    test ("FuseNotOK")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (50.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Fuse0")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "", Some (0.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("FuseNone")
    {
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Series Branch within parallel Branch OK")
    {
        val branch =
            ParallelBranch ("a", "z", 10.0,
                List (
                    SimpleBranch ("a", "z", 6.0, "TEI11", "", Some (50.0)),
                    SeriesBranch ("a", "z", 4.0,
                        Seq (
                            SimpleBranch ("a", "z", 4.0, "TEI21", "", Some (50.0)),
                            SimpleBranch ("a", "z", 4.0, "TEI21", "", Some (40.0))
                        )
                    )
                )
            )

        assert (FData.fuseOK (280, branch), "expected OK")
    }

    test ("Table 2 Fuse+")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (690.001, null) == 200.0, "expected 200A")
    }

    test ("Table 2 Fuse-")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (689.99, null) == 160.0, "expected 160A")
    }

    test ("Table 2 FuseSmall")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (25.0, null) == 0.0, "expected 0A")
    }

    test ("Table 2 FuseZero")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuse (0.0, null) == 0.0, "expected 0A")
    }

    test ("Table 2 FuseOK")
    {
        FData.fuse_sizing_table (2)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", "", Some (40.0))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI134", "", Some (40.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI135", "", Some (40.0))))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (40.0))))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (50.0))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "", Some (0.0))))
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
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 2 then 1 Fuse+")
    {
        FData.fuse_sizing_table (2)
        FData.fuse_sizing_table (1)
        assert (FData.fuse (650.001, null) == 200.0, "expected 200A")
    }

    test ("Table 1 Fuse-")
    {
        FData.fuse_sizing_table (1)
        assert (FData.fuse (649.99, null) == 160.0, "expected 160A")
    }

    test ("Table 3 Fuse+ DIN")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (650.001, SimpleBranch ("a", "b", 0.0, "TEI135", "DIN yadda", Some (100.0))) == 200.0, "expected 200A")
    }

    test ("Table 3 Fuse+ SEV")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (600.001, SimpleBranch ("a", "b", 0.0, "TEI135", "SEV yadda", Some (100.0))) == 200.0, "expected 200A")
    }

    test ("Table 3 Fuse- DIN")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (649.99, SimpleBranch ("a", "b", 0.0, "TEI135", "DIN yadda", Some (100.0))) == 160.0, "expected 160A")
    }

    test ("Table 3 Fuse- SEV")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (599.99, SimpleBranch ("a", "b", 0.0, "TEI135", "SEV yadda", Some (100.0))) == 150.0, "expected 160A")
    }

    test ("Table 3 FuseSmall DIN")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (25.0, SimpleBranch ("a", "b", 0.0, "TEI135", "DIN yadda", Some (100.0))) == 0.0, "expected 0A")
    }

    test ("Table 3 FuseSmall SEV")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (125.0, SimpleBranch ("a", "b", 0.0, "TEI135", "SEV yadda", Some (100.0))) == 0.0, "expected 0A")
    }

    test ("Table 3 FuseZero DIN")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (0.0, SimpleBranch ("a", "b", 0.0, "TEI135", "DIN yadda", Some (100.0))) == 0.0, "expected 0A")
    }

    test ("Table 3 FuseZero SEV")
    {
        FData.fuse_sizing_table (3)
        assert (FData.fuse (0.0, SimpleBranch ("a", "b", 0.0, "TEI135", "SEV yadda", Some (100.0))) == 0.0, "expected 0A")
    }

    test ("Table 3 No Current FuseOK DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", "DIN yadda", Some (40.0))))
        assert (FData.fuseOK (123.456, branch), "expected OK")
    }

    test ("Table 3 No Current FuseOK SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 0.0, "TEI141", "SEV yadda", Some (60.0))))
        assert (FData.fuseOK (223.456, branch), "expected OK")
    }

    test ("Table 3 ParallelFuseOK DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI134", "DIN yadda", Some (40.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI135", "DIN yadda", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("Table 3 ParallelFuseOK SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 2.0, "TEI134", "SEV yadda", Some (60.0)),
                            SimpleBranch ("d", "e", 2.0, "TEI135", "SEV yadda", Some (60.0))))))
        assert (FData.fuseOK (446.912, branch), "expected OK")
    }

    test ("Table 3 No Current ParallelFuseOK DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (40.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (40.0))))))
        assert (FData.fuseOK (246.912, branch), "expected OK")
    }

    test ("Table 3 No Current ParallelFuseOK SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    ParallelBranch ("e", "f", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (60.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (60.0))))))
        assert (FData.fuseOK (446.912, branch), "expected OK")
    }

    test ("Table 3 FuseNotOK DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "DIN yadda", Some (50.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 3 FuseNotOK SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "SEV yadda", Some (100.0))))
        assert (!FData.fuseOK (253.456, branch), "expected not OK")
    }

    test ("Table 3 Fuse0 DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "DIN yadda", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "DIN yadda", Some (0.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 3 Fuse0 SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "SEV yadda", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "SEV yadda", Some (0.0))))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 3 FuseNone DIN")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "DIN yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "DIN yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "DIN yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "DIN yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "DIN yadda", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "DIN yadda", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("Table 3 FuseNone SEV")
    {
        FData.fuse_sizing_table (3)
        val branch =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "SEV yadda", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "SEV yadda", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "SEV yadda", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "SEV yadda", Some (100.0)))),
                    SimpleBranch ("e", "f", 4.0, "TEI141", "SEV yadda", Some (40.0)),
                    SimpleBranch ("e", "f", 4.0, "TEI15", "SEV yadda", None)))
        assert (!FData.fuseOK (123.456, branch), "expected not OK")
    }

    test ("lastFuseHasMissingValues: simple branch")
    {
        FData.fuse_sizing_table (1)
        val branch1 = SimpleBranch ("a", "b", 4.0, "TEI141", "", Some (-1.0))
        val branch2 = SimpleBranch ("c", "d", 4.0, "TEI141", "", Some (40.0))

        assert (FData.lastFuseHasMissingValues (branch1), "has missing valus (-1.0)")
        assert (!FData.lastFuseHasMissingValues (branch2), "has no missing valus (40.0)")
    }

    test ("lastFuseHasMissingValues: series branch")
    {
        FData.fuse_sizing_table (1)
        val branch1 =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (1323.8)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0))))
                )
            )

        val branch2 =
            SeriesBranch ("a", "z", 0.0,
                Seq (
                    ParallelBranch ("c", "d", 0.0,
                        List (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    ParallelBranch ("d", "e", 0.0,
                        List (
                            SimpleBranch ("d", "e", 0.0, "TEI134", "", Some (-1.0)),
                            SimpleBranch ("d", "e", 0.0, "TEI135", "", Some (100.0))))
                )
            )

        assert (!FData.lastFuseHasMissingValues (branch1), "has missing values (-1.0)")
        assert (FData.lastFuseHasMissingValues (branch2), "has no missing values")
    }


    test ("lastFuseHasMissingValues: parallel branch")
    {
        FData.fuse_sizing_table (1)
        val branch1 =
            ParallelBranch ("c", "d", 0.0,
                List (
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (288282.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (-1.0))))
                )
            )

        val branch2 =
            ParallelBranch ("c", "d", 0.0,
                List (
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (-1.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3)))),
                    SeriesBranch ("a", "z", 0.0,
                        Seq (
                            SimpleBranch ("c", "d", 0.0, "TEI124", "", Some (-1.0)),
                            SimpleBranch ("c", "d", 0.0, "TEI123", "", Some (73737.3))))
                )
            )

        assert (FData.lastFuseHasMissingValues (branch1), "last fuses have at last one missing value (-1.0)")
        assert (!FData.lastFuseHasMissingValues (branch2), "last fuses have no missing values")
    }

    test ("Parallel rating")
    {
        FData.fuse_sizing_table (1)
        val branch =
            ParallelBranch ("a", "z", 10.0,
                List (
                    SimpleBranch ("a", "z", 6.0, "TEI11", "", Some (50.0)),
                    SeriesBranch ("a", "z", 4.0,
                        Seq (
                            SimpleBranch ("a", "z", 4.0, "TEI21", "", Some (50.0)),
                            SimpleBranch ("a", "z", 4.0, "TEI22", "", Some (40.0))
                        )
                    )
                )
            )
        assert (branch.asFuse == "[50,(50,40)]", "asFuse")
        assert (FData.fuses (280, branch) == "50,40", "expected 60:40 split")

        val scr = ScResult (
            node = "test",
            equipment = "house",
            terminal = 1,
            container = null,
            errors = List(),
            tx = "TRAXXX",
            tx_impedance = Complex(0),
            prev = "previous",
            costerm = 1.0,
            low_r = 0.0,
            low_x = 0.0,
            low_r0 = 0.0,
            low_x0 = 0.0,
            high_ik = 280.0,
            high_r = 0.0,
            high_x = 0.0,
            high_r0 = 0.0,
            high_x0 = 0.0,
            fuses = branch
        )
        assert (scr.iksplitString == "168.0,112.0", "ik split")
        assert (scr.fuseString == "[50,(50,40)]", "fuseString")
        assert (scr.lastFusesString == "50,40", "lastFusesString")
        assert (scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert (scr.fuseMax == "50,40", "expected 60:40 split")
        assert (scr.fuseOK, "expected OK")
    }

    test ("Series wrapped parallel rating")
    {
        FData.fuse_sizing_table (1)
        val branch =
            SeriesBranch ("wrap", "up", 10.0,
                Seq (
                    ParallelBranch ("a", "z", 10.0,
                        List (
                            SimpleBranch ("a", "z", 6.0, "TEI11", "", Some (50.0)),
                            SeriesBranch ("a", "z", 4.0,
                                Seq (
                                    SimpleBranch ("a", "z", 4.0, "TEI21", "", Some (50.0)),
                                    SimpleBranch ("a", "z", 4.0, "TEI22", "", Some (40.0))
                                )
                            )
                        )
                    )
                )
            )
        assert (branch.asFuse == "([50,(50,40)])", "asFuse")
        assert (FData.fuses (280, branch) == "50,40", "expected 60:40 split")

        val scr = ScResult (
            node = "test",
            equipment = "house",
            terminal = 1,
            container = null,
            errors = List(),
            tx = "TRAXXX",
            tx_impedance = Complex(0),
            prev = "previous",
            costerm = 1.0,
            low_r = 0.0,
            low_x = 0.0,
            low_r0 = 0.0,
            low_x0 = 0.0,
            high_ik = 280.0,
            high_r = 0.0,
            high_x = 0.0,
            high_r0 = 0.0,
            high_x0 = 0.0,
            fuses = branch
        )
        assert (scr.fuseString == "([50,(50,40)])", "fuseString")
        assert (scr.iksplitString == "168.0,112.0", "ik split")
        assert (scr.lastFusesString == "50,40", "lastFusesString")
        assert (scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert (scr.fuseMax == "50,40", "expected 60:40 split")
        assert (scr.fuseOK, "expected OK")
    }

    test ("Series wrapped parallel rating not OK")
    {
        FData.fuse_sizing_table (1)
        val branch =
            SeriesBranch ("wrap", "up", 10.0,
                Seq (
                    ParallelBranch ("a", "z", 10.0,
                        List (
                            SimpleBranch ("a", "z", 5.0, "TEI11", "", Some (50.0)),
                            SeriesBranch ("a", "z", 5.0,
                                Seq (
                                    SimpleBranch ("a", "z", 5.0, "TEI21", "", Some (50.0)),
                                    SimpleBranch ("a", "z", 5.0, "TEI22", "", Some (80.0))
                                )
                            )
                        )
                    )
                )
            )
        assert (branch.asFuse == "([50,(50,80)])", "asFuse")
        assert (FData.fuses (280, branch) == "50,50", "expected 50:50 split")

        val scr = ScResult (
            node = "test",
            equipment = "house",
            terminal = 1,
            container = null,
            errors = List(),
            tx = "TRAXXX",
            tx_impedance = Complex(0),
            prev = "previous",
            costerm = 1.0,
            low_r = 0.0,
            low_x = 0.0,
            low_r0 = 0.0,
            low_x0 = 0.0,
            high_ik = 280.0,
            high_r = 0.0,
            high_x = 0.0,
            high_r0 = 0.0,
            high_x0 = 0.0,
            fuses = branch
        )
        assert (scr.fuseString == "([50,(50,80)])", "fuseString")
        assert (scr.iksplitString == "140.0,140.0", "ik split")
        assert (scr.lastFusesString == "50,80", "lastFusesString")
        assert (scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert (scr.fuseMax == "50,50", "expected 50:50 split")
        assert (!scr.fuseOK, "expected not OK")
    }
}
