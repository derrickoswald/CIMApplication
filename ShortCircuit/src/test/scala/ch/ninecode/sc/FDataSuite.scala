package ch.ninecode.sc

import ch.ninecode.util.Complex
import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.sc.branch.Branch
import ch.ninecode.sc.branch.ParallelBranch
import ch.ninecode.sc.branch.SeriesBranch
import ch.ninecode.sc.branch.SimpleBranch

class FDataSuite extends AnyFunSuite
{
    val options_table1: ShortCircuitOptions = ShortCircuitOptions()
    val options_table2: ShortCircuitOptions = ShortCircuitOptions(
        fuse_table = FuseData(
            Array(
                FuseTable(
                    "DIN",
                    Array(
                        Amp(0, 0), // failsafe fallback for currents less than 28A
                        Amp(28, 10),
                        Amp(40, 16),
                        Amp(55, 20),
                        Amp(70, 25),
                        Amp(93, 32),
                        Amp(120, 40),
                        Amp(160, 50),
                        Amp(190, 63),
                        Amp(230, 80),
                        Amp(305, 100),
                        Amp(380, 125),
                        Amp(490, 160),
                        Amp(690, 200),
                        Amp(820, 250),
                        Amp(1150, 315),
                        Amp(1350, 400),
                        Amp(1900, 500),
                        Amp(2500, 630)
                    )
                )
            )
        )
    )
    val options_table3: ShortCircuitOptions = ShortCircuitOptions(
        fuse_table = FuseData(
            Array(
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
                ),
                FuseTable(
                    "SEV",
                    Array(
                        Amp(0, 0), // failsafe fallback for currents less than 200A
                        Amp(200, 60),
                        Amp(250, 75),
                        Amp(300, 100),
                        Amp(340, 125),
                        Amp(500, 150),
                        Amp(600, 200),
                        Amp(720, 250),
                        Amp(850, 300),
                        Amp(1150, 400)
                    )
                )
            )
        )
    )
    val options_table4: ShortCircuitOptions = ShortCircuitOptions(
        fuse_table = FuseData(
            Array(
                FuseTable(
                    "DIN",
                    Array(
                        Amp(0, 6), // failsafe fallback for currents less than 65A
                        Amp(65, 25),
                        Amp(105, 35),
                        Amp(140, 50),
                        Amp(180, 50),
                        Amp(240, 63),
                        Amp(320, 100),
                        Amp(380, 100),
                        Amp(500, 160),
                        Amp(650, 160),
                        Amp(800, 200),
                        Amp(1050, 250),
                        Amp(1300, 400),
                        Amp(1750, 400),
                        Amp(2400, 500)
                    )
                )
            )
        )
    )

    test("Fuse+")
    {
        assert(options_table1.fuse_table.fuse(650.001, "DIN") == 200.0, "expected 200A")
    }

    test("Fuse-")
    {
        assert(options_table1.fuse_table.fuse(649.99, "DIN") == 160.0, "expected 160A")
    }

    test("FuseSmall")
    {
        assert(options_table1.fuse_table.fuse(25.0, "DIN") == 0.0, "expected 0A")
    }

    test("FuseZero")
    {
        assert(options_table1.fuse_table.fuse(0.0, "DIN") == 0.0, "expected 0A")
    }

    test("FuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(400.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(400.0), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(125.0), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "DIN"))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(40.0), "DIN")))
        assert(branch.asFuse == "([400,400],[125,100],40)", "fuse string")
        assert(branch.asId == "([TEI124,TEI123],[TEI134,TEI135],TEI141)", "id string")
        assert(branch.checkFuses(123.456, options_table1)._1, "expected OK")
    }

    test("No Current FuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(400.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(400.0), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(125.0), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "DIN"))),
                    SimpleBranch("e", "f", 0.0, "TEI141", "", Some(40.0), "DIN")))
        assert(branch.asFuse == "([400,400],[125,100],40)", "fuse string")
        assert(branch.asId == "([TEI124,TEI123],[TEI134,TEI135],TEI141)", "id string")
        assert(branch.checkFuses(123.456, options_table1)._1, "expected OK")
    }

    test("ParallelFuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(400.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(400.0), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(125.0), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "DIN"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 2.0, "TEI141", "", Some(40.0), "DIN"),
                            SimpleBranch("d", "e", 2.0, "TEI142", "", Some(40.0), "DIN")))))
        assert(branch.asFuse == "([400,400],[125,100],[40,40])", "fuse string")
        assert(branch.asId == "([TEI124,TEI123],[TEI134,TEI135],[TEI141,TEI142])", "id string")
        assert(branch.checkFuses(246.912, options_table1)._1, "expected OK")
    }

    test("ParallelFuseOK2")
    {
        val branch =
            ParallelBranch("c", "d", 0.0,
                List(
                    SimpleBranch("c", "d", 2.0, "TEI141", "", Some(40.0), "DIN"),
                    SimpleBranch("c", "d", 2.0, "TEI142", "", Some(40.0), "DIN")))
        assert(branch.asFuse == "[40,40]", "fuse string")
        assert(branch.asId == "[TEI141,TEI142]", "id string")
        assert(branch.checkFuses(211.0, options_table1)._1, "expected OK")
    }

    test("No Current ParallelFuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "DIN"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(40.0), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(40.0), "DIN")))))
        assert(branch.checkFuses(246.912, options_table1)._1, "expected OK")
    }

    test("No Current Parallel Fuse Not OK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("e", "f", 0.0, "TEI134", "", Some(50.0), ""),
                            SimpleBranch("e", "f", 0.0, "TEI135", "", Some(50.0), "")))))
        assert(!branch.checkFuses(246.912, options_table1)._1, "expected Not OK")
    }

    test("FuseNotOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(50.0), "")))
        assert(!branch.checkFuses(123.456, options_table1)._1, "expected not OK")
    }

    test("Fuse0")
    {
        val branch =
            SeriesBranch("c", "f", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(40.0), ""),
                    SimpleBranch("e", "f", 4.0, "TEI15", "", Some(0.0), "")))
        assert(!branch.checkFuses(123.456, options_table1)._1, "expected not OK")
    }

    test("Series Branch within parallel Branch OK")
    {
        val branch =
            ParallelBranch("a", "z", 10.0,
                List[Branch](
                    SimpleBranch("a", "z", 6.0, "TEI11", "", Some(50.0), "DIN"),
                    SeriesBranch("a", "z", 4.0,
                        Seq(
                            SimpleBranch("a", "z", 4.0, "TEI21", "", Some(50.0), "DIN"),
                            SimpleBranch("a", "z", 4.0, "TEI21", "", Some(40.0), "DIN")
                        )
                    )
                )
            )

        assert(branch.checkFuses(280, options_table1)._1, "expected OK")
    }

    test("Table 2 Fuse+")
    {
        assert(options_table2.fuse_table.fuse(690.001, "") == 200.0, "expected 200A")
    }

    test("Table 2 Fuse-")
    {
        assert(options_table2.fuse_table.fuse(689.99, "") == 160.0, "expected 160A")
    }

    test("Table 2 FuseSmall")
    {
        assert(options_table2.fuse_table.fuse(25.0, "") == 0.0, "expected 0A")
    }

    test("Table 2 FuseZero")
    {
        assert(options_table2.fuse_table.fuse(0.0, "") == 0.0, "expected 0A")
    }

    test("Table 2 FuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(40.0), "")))
        assert(branch.checkFuses(123.456, options_table2)._1, "expected OK")
    }

    test("Table 2 No Current FuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 0.0, "TEI141", "", Some(40.0), "")))
        assert(branch.checkFuses(123.456, options_table2)._1, "expected OK")
    }

    test("Table 2 ParallelFuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 2.0, "TEI134", "", Some(40.0), ""),
                            SimpleBranch("d", "e", 2.0, "TEI135", "", Some(40.0), "")))))
        assert(branch.checkFuses(246.912, options_table2)._1, "expected OK")
    }

    test("Table 2 No Current ParallelFuseOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(40.0), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(40.0), "")))))
        assert(branch.checkFuses(246.912, options_table2)._1, "expected OK")
    }

    test("Table 2 FuseNotOK")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(50.0), "")))
        assert(!branch.checkFuses(123.456, options_table2)._1, "expected not OK")
    }

    test("Table 2 Fuse0")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), ""))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "", Some(40.0), ""),
                    SimpleBranch("e", "f", 4.0, "TEI15", "", Some(0.0), "")))
        assert(!branch.checkFuses(123.456, options_table2)._1, "expected not OK")
    }

    test("Table 3 Fuse+ DIN")
    {
        assert(options_table3.fuse_table.fuse(650.001, "DIN") == 200.0, "expected 200A")
    }

    test("Table 3 Fuse+ SEV")
    {
        assert(options_table3.fuse_table.fuse(600.001, "SEV") == 200.0, "expected 200A")
    }

    test("Table 3 Fuse- DIN")
    {
        assert(options_table3.fuse_table.fuse(649.99, "DIN") == 160.0, "expected 160A")
    }

    test("Table 3 Fuse- SEV")
    {
        assert(options_table3.fuse_table.fuse(599.99, "SEV") == 150.0, "expected 160A")
    }

    test("Table 3 FuseSmall DIN")
    {
        assert(options_table3.fuse_table.fuse(25.0, "DIN") == 0.0, "expected 0A")
    }

    test("Table 3 FuseSmall SEV")
    {
        assert(options_table3.fuse_table.fuse(125.0, "SEV") == 0.0, "expected 0A")
    }

    test("Table 3 FuseZero DIN")
    {
        assert(options_table3.fuse_table.fuse(0.0, "DIN") == 0.0, "expected 0A")
    }

    test("Table 3 FuseZero SEV")
    {
        assert(options_table3.fuse_table.fuse(0.0, "SEV") == 0.0, "expected 0A")
    }

    test("Table 3 No Current FuseOK DIN")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "DIN yadda", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "DIN yadda", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(100.0), "DIN"))),
                    SimpleBranch("e", "f", 0.0, "TEI141", "DIN yadda", Some(40.0), "DIN")))
        assert(branch.checkFuses(123.456, options_table3)._1, "expected OK")
    }

    test("Table 3 No Current FuseOK SEV")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "SEV yadda", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "SEV yadda", Some(73737.3), "SEV"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(1323.8), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(100.0), "SEV"))),
                    SimpleBranch("e", "f", 0.0, "TEI141", "SEV yadda", Some(60.0), "SEV")))
        assert(branch.checkFuses(223.456, options_table3)._1, "expected OK")
    }

    test("Table 3 ParallelFuseOK DIN")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "DIN yadda", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "DIN yadda", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(100.0), "DIN"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 2.0, "TEI134", "DIN yadda", Some(40.0), "DIN"),
                            SimpleBranch("d", "e", 2.0, "TEI135", "DIN yadda", Some(40.0), "DIN")))))
        assert(branch.checkFuses(246.912, options_table3)._1, "expected OK")
    }

    test("Table 3 ParallelFuseOK SEV")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "SEV yadda", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "SEV yadda", Some(73737.3), "SEV"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(1323.8), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(100.0), "SEV"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 2.0, "TEI134", "SEV yadda", Some(60.0), "SEV"),
                            SimpleBranch("d", "e", 2.0, "TEI135", "SEV yadda", Some(60.0), "SEV")))))
        assert(branch.checkFuses(446.912, options_table3)._1, "expected OK")
    }

    test("Table 3 No Current ParallelFuseOK DIN")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "DIN yadda", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "DIN yadda", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(100.0), "DIN"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(40.0), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(40.0), "DIN")))))
        assert(branch.checkFuses(246.912, options_table3)._1, "expected OK")
    }

    test("Table 3 No Current ParallelFuseOK SEV")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "SEV yadda", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "SEV yadda", Some(73737.3), "SEV"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(1323.8), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(100.0), "SEV"))),
                    ParallelBranch("e", "f", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(60.0), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(60.0), "SEV")))))
        assert(branch.checkFuses(446.912, options_table3)._1, "expected OK")
    }

    test("Table 3 FuseNotOK DIN")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "DIN yadda", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "DIN yadda", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(100.0), "DIN"))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "DIN yadda", Some(50.0), "DIN")))
        assert(!branch.checkFuses(123.456, options_table3)._1, "expected not OK")
    }

    test("Table 3 FuseNotOK SEV")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "SEV yadda", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "SEV yadda", Some(73737.3), "SEV"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(1323.8), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(100.0), "SEV"))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "SEV yadda", Some(100.0), "SEV")))
        assert(!branch.checkFuses(253.456, options_table3)._1, "expected not OK")
    }

    test("Table 3 Fuse0 DIN")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "DIN yadda", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "DIN yadda", Some(73737.3), "DIN"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "DIN yadda", Some(1323.8), "DIN"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "DIN yadda", Some(100.0), "DIN"))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "DIN yadda", Some(40.0), "DIN"),
                    SimpleBranch("e", "f", 4.0, "TEI15", "DIN yadda", Some(0.0), "DIN")))
        assert(!branch.checkFuses(123.456, options_table3)._1, "expected not OK")
    }

    test("Table 3 Fuse0 SEV")
    {
        val branch =
            SeriesBranch("a", "z", 0.0,
                Seq[Branch](
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "SEV yadda", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "SEV yadda", Some(73737.3), "SEV"))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "SEV yadda", Some(1323.8), "SEV"),
                            SimpleBranch("d", "e", 0.0, "TEI135", "SEV yadda", Some(100.0), "SEV"))),
                    SimpleBranch("e", "f", 4.0, "TEI141", "SEV yadda", Some(40.0), "SEV"),
                    SimpleBranch("e", "f", 4.0, "TEI15", "SEV yadda", Some(0.0), "SEV")))
        assert(!branch.checkFuses(123.456, options_table3)._1, "expected not OK")
    }

    test("lastFuseHasMissingValues: simple branch")
    {
        val branch1 = SimpleBranch("a", "b", 4.0, "TEI141", "", Some(-1.0), "")
        val branch2 = SimpleBranch("c", "d", 4.0, "TEI141", "", Some(40.0), "")

        val result = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        assert(result.lastFuseHasMissingValues(branch1), "has missing values (-1.0)")
        assert(!result.lastFuseHasMissingValues(branch2), "has no missing values (40.0)")
    }

    test("lastFuseHasMissingValues: series branch")
    {
        val branch1 =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "")))
                )
            )

        val branch2 =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    ParallelBranch("c", "d", 0.0,
                        List(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    ParallelBranch("d", "e", 0.0,
                        List(
                            SimpleBranch("d", "e", 0.0, "TEI134", "", Some(-1.0), ""),
                            SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "")))
                )
            )

        val result = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        assert(!result.lastFuseHasMissingValues(branch1), "has missing values (-1.0)")
        assert(result.lastFuseHasMissingValues(branch2), "has no missing values")
    }

    test("lastFuseHasMissingValues: parallel branch")
    {
        val branch1 =
            ParallelBranch("c", "d", 0.0,
                List(
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(-1.0), "")))
                )
            )

        val branch2 =
            ParallelBranch("c", "d", 0.0,
                List(
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(-1.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), ""))),
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(-1.0), ""),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), "")))
                )
            )

        val result = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        assert(result.lastFuseHasMissingValues(branch1), "last fuses have at last one missing value (-1.0)")
        assert(!result.lastFuseHasMissingValues(branch2), "last fuses have no missing values")
    }

    test("Fuse type: Simple Branch")
    {
        val branch1 = SimpleBranch("a", "b", 4.0, "TEI141", "", Some(-1.0), "DIN")
        val branch2 = SimpleBranch("c", "d", 4.0, "TEI141", "", Some(40.0), "SEV")

        val result1 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        val result2 = result1.copy(branches = branch2)
        assert(result1.lastFuseStandard == "DIN", "simple branch 1 has DIN fuse")
        assert(result2.lastFuseStandard == "SEV", "simple branch 2 has SEV fuse")
    }

    test("Fuse type: Series Branch")
    {
        val branch1 =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), "SEV"),
                    SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "DIN")
                )
            )

        val branch2 =
            SeriesBranch("a", "z", 0.0,
                Seq(
                    SimpleBranch("d", "e", 0.0, "TEI134", "", Some(1323.8), "DIN"),
                    SimpleBranch("d", "e", 0.0, "TEI135", "", Some(100.0), "SEV")
                )
            )

        val result1 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        val result2 = result1.copy(branches = branch2)
        assert(result1.lastFuseStandard == "DIN", "last simple branch 1 has DIN fuse")
        assert(result2.lastFuseStandard == "SEV", "simple branch 2 has SEV fuse")
    }

    test("Fuse type: Parallel Branch")
    {
        val branch1 =
            ParallelBranch("c", "d", 0.0,
                List(
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), "DIN"))),
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), "SEV"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(-1.0), "DIN")))
                )
            )

        val branch2 =
            ParallelBranch("c", "d", 0.0,
                List(
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(73737.3), "DIN"))),
                    SeriesBranch("a", "z", 0.0,
                        Seq(
                            SimpleBranch("c", "d", 0.0, "TEI124", "", Some(288282.0), "DIN"),
                            SimpleBranch("c", "d", 0.0, "TEI123", "", Some(-1.0), "SEV")))
                )
            )

        val result1 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, branch1)
        val result2 = result1.copy(branches = branch2)
        assert(result1.lastFuseStandard == "DIN,DIN", "last simple branch 1 has DIN fuse")
        assert(result2.lastFuseStandard == "DIN,SEV", "simple branch 2 has SEV fuse")
    }

    test("Max Fuse: Simple Branch (DIN and SEV)")
    {
        val branch1 = SimpleBranch("a", "b", 390.0, "TEI141", "", Some(-1.0), "DIN")

        val result1 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 390.0, 0.0, 0.0, 0.0, branch1)
        assert(result1.fuseMax(options_table3, Some("DIN")) == "125", "simple branch has DIN fuse with 125")
        assert(result1.fuseMax(options_table3, Some("SEV")) == "125", "simple branch has SEV fuse with 125")

        val branch2 = SimpleBranch("a", "b", 150.0, "TEI141", "", Some(-1.0), "DIN")

        val result2 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 150.0, 0.0, 0.0, 0.0, branch2)
        assert(result2.fuseMax(options_table3, Some("DIN")) == "50", "simple branch has DIN fuse with 50")
        assert(result2.fuseMax(options_table3, Some("SEV")) == "0", "simple branch has SEV fuse with 0")

        val branch3 = SimpleBranch("a", "b", 220.0, "TEI141", "", Some(1.0), "DIN")

        val result3 = ScResult("", "", 0.0, 1, "", Nil, "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 220.0, 0.0, 0.0, 0.0, branch3)
        assert(result3.fuseMax(options_table3, Some("DIN")) == "63", "simple branch has DIN fuse with 63")
        assert(result3.fuseMax(options_table3, Some("SEV")) == "60", "simple branch has SEV fuse with 60")
    }

    test("Max Fuse: use DIN as default if nothing has been defined") {
        val branch1 = SeriesBranch("BUS0004_topo", "FUS0057_node_2_topo", 0.0,
            Seq(
                SimpleBranch("BUS0004_topo", "FUS0032_node_2_topo", 0.0, "FUS0032", "FUS0032", Some(125.0), ""),
                SimpleBranch("BUS0007_topo", "FUS0057_node_2_topo", 0.0, "FUS0057", "FUS0057", Some(100.0), "")
            )
        )
        val result1 = ScResult("USR0023_topo","USR0023",400.0,1, "",List(),"TX0002",
            "FUS0057_node_2_topo",0.013775603901670673,0.01664061754139094,0.03328393451039017,0.018086960653563765,
            7689.812433403841,10690.326566474603,16657.192643207654,7406475.505054943,1.0,641.4195939884762,
            320.7097969942381,555.4856628791207,320.7097969942381,160.35489849711905,277.74283143956035,
            0.015006393156075075,0.015618231970695817,0.036178824287327084,0.018086960653563765,7443.770187655024,
            10662.447953257199,16208.032458448206,7387160.635240101, branch1)
        assert(result1.fuseOK(options_table3), "fuse should be OK")
        assert(result1.fuseMax(options_table3) == "630", "check fusemax")
        assert(result1.fuseMax(options_table3, Some("DIN")) == "630", "check fusemaxDIN")
        assert(result1.fuseMax(options_table3, Some("SEV")) == "400", "check fusemaxSEV")
    }

    test("Parallel rating")
    {
        val z1 = Impedanzen(Complex(1.20, 0.02), Complex(4.80, 0.13), Complex(1.30, 0.02), Complex(5.20, 0.13))
        val z2 = Impedanzen(Complex(0.44, 0.01), Complex(1.76, 0.05), Complex(0.45, 0.01), Complex(1.80, 0.05))
        val branch =
            ParallelBranch("a", "z", 10.0,
                List[Branch](
                    SimpleBranch("a", "z", 6.0, "TEI11", "", Some(50.0), "DIN", z2),
                    SeriesBranch("a", "z", 4.0,
                        Seq(
                            SimpleBranch("a", "z", 4.0, "TEI21", "", Some(50.0), "DIN", z1),
                            SimpleBranch("a", "z", 4.0, "TEI22", "", Some(40.0), "DIN", z1)
                        )
                    )
                )
            )
        assert(branch.asFuse == "[50,(50,40)]", "asFuse")
        val z_total = Impedanzen(
            Complex(0.37183277, +0.00810154),
            Complex(1.48732429, 0.04194802),
            Complex(0.38360881, 0.00813756),
            Complex(1.53442772, 0.04199405)
        )
        val scr = ScResult(
            node = "test",
            equipment = "house",
            voltage = 400.0,
            terminal = 1,
            container = "",
            errors = List(),
            tx = "TRAXXX",
            prev = "previous",
            costerm = 1.0,
            low_r = z_total.impedanz_low.re,
            low_x = z_total.impedanz_low.im,
            low_r0 = z_total.null_impedanz_low.re,
            low_x0 = z_total.null_impedanz_low.im,
            high_ik = 280.0,
            high_r = z_total.impedanz_high.re,
            high_x = z_total.impedanz_high.im,
            high_r0 = z_total.null_impedanz_high.re,
            high_x0 = z_total.null_impedanz_high.im,
            branches = branch
        )
        assert(scr.fuses(280, options_table1, branch) == "50,40", "expected 60:40 split")
        assert(scr.iksplitString == "168,112", "ik split")
        assert(scr.fuseString == "[50,(50,40)]", "fuseString")
        assert(scr.lastFusesString == "50,40", "lastFusesString")
        assert(scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert(scr.fuseMax(options_table1) == "50,40", "expected 60:40 split")
        assert(scr.fuseOK(options_table1), "expected OK")
    }

    test("Series wrapped parallel rating")
    {
        val z1 = Impedanzen(Complex(1.20, 0.02), Complex(4.80, 0.13), Complex(1.30, 0.02), Complex(5.20, 0.13))
        val z2 = Impedanzen(Complex(0.44, 0.01), Complex(1.76, 0.05), Complex(0.45, 0.01), Complex(1.80, 0.05))
        val branch =
            SeriesBranch("wrap", "up", 10.0,
                Seq[Branch](
                    ParallelBranch("a", "z", 10.0,
                        List[Branch](
                            SimpleBranch("a", "z", 6.0, "TEI11", "", Some(50.0), "DIN", z2),
                            SeriesBranch("a", "z", 4.0,
                                Seq(
                                    SimpleBranch("a", "z", 4.0, "TEI21", "", Some(50.0), "DIN", z1),
                                    SimpleBranch("a", "z", 4.0, "TEI22", "", Some(40.0), "DIN", z1)
                                )
                            )
                        )
                    )
                )
            )
        assert(branch.asFuse == "([50,(50,40)])", "asFuse")

        val z_total = Impedanzen(
            Complex(0.37183277, +0.00810154),
            Complex(1.48732429, 0.04194802),
            Complex(0.38360881, 0.00813756),
            Complex(1.53442772, 0.04199405)
        )
        val scr = ScResult(
            node = "test",
            equipment = "house",
            voltage = 400.0,
            terminal = 1,
            container = "",
            errors = List(),
            tx = "TRAXXX",
            prev = "previous",
            costerm = 1.0,
            low_r = z_total.impedanz_low.re,
            low_x = z_total.impedanz_low.im,
            low_r0 = z_total.null_impedanz_low.re,
            low_x0 = z_total.null_impedanz_low.im,
            high_ik = 280.0,
            high_r = z_total.impedanz_high.re,
            high_x = z_total.impedanz_high.im,
            high_r0 = z_total.null_impedanz_high.re,
            high_x0 = z_total.null_impedanz_high.im,
            branches = branch
        )
        assert(scr.fuses(280, options_table1, branch) == "50,40", "expected 60:40 split")
        assert(scr.fuseString == "[50,(50,40)]", "fuseString")
        assert(scr.iksplitString == "168,112", "ik split")
        assert(scr.lastFusesString == "50,40", "lastFusesString")
        assert(scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert(scr.fuseMax(options_table1) == "50,40", "expected 60:40 split")
        assert(scr.fuseOK(options_table1), "expected OK")
    }

    test("Series wrapped parallel rating not OK")
    {
        val z1 = Impedanzen(
            Complex(1.20, 0.02),
            Complex(4.80, 0.13),
            Complex(1.30, 0.02),
            Complex(5.20, 0.13)
        )
        val z2 = Impedanzen(
            Complex(0.44, 0.01),
            Complex(1.76, 0.05),
            Complex(0.45, 0.01),
            Complex(1.80, 0.05)
        )
        val branch =
            SeriesBranch("wrap", "up", 10.0,
                Seq[Branch](
                    ParallelBranch("a", "z", 10.0,
                        List[Branch](
                            SimpleBranch("a", "z", 5.0, "TEI11", "", Some(50.0), "DIN", z2),
                            SeriesBranch("a", "z", 5.0,
                                Seq(
                                    SimpleBranch("a", "z", 5.0, "TEI21", "", Some(100.0), "DIN", z1),
                                    SimpleBranch("a", "z", 5.0, "TEI22", "", Some(100.0), "DIN", z1)
                                )
                            )
                        )
                    )
                )
            )
        assert(branch.asFuse == "([50,(100,100)])", "asFuse")

        val z_total = Impedanzen(
            Complex(0.37183277, +0.00810154),
            Complex(1.48732429, 0.04194802),
            Complex(0.38360881, 0.00813756),
            Complex(1.53442772, 0.04199405)
        )
        val scr = ScResult(
            node = "test",
            equipment = "house",
            voltage = 400.0,
            terminal = 1,
            container = "",
            errors = List(),
            tx = "TRAXXX",
            prev = "previous",
            costerm = 1.0,
            low_r = z_total.impedanz_low.re,
            low_x = z_total.impedanz_low.im,
            low_r0 = z_total.null_impedanz_low.re,
            low_x0 = z_total.null_impedanz_low.im,
            high_ik = 280.0,
            high_r = z_total.impedanz_high.re,
            high_x = z_total.impedanz_high.im,
            high_r0 = z_total.null_impedanz_high.re,
            high_x0 = z_total.null_impedanz_high.im,
            branches = branch
        )
        assert(scr.fuses(280, options_table1, branch) == "50,50", "expected 50:50 split")
        assert(scr.fuseString == "[50,(100,100)]", "fuseString")
        assert(scr.iksplitString == "140,140", "ik split")
        assert(scr.lastFusesString == "50,100", "lastFusesString")
        assert(scr.lastFusesId == "TEI11,TEI22", "lastFusesId")
        assert(scr.fuseMax(options_table1) == "50,50", "expected 50:50 split")
        assert(!scr.fuseOK(options_table1), "expected not OK")
    }
}
