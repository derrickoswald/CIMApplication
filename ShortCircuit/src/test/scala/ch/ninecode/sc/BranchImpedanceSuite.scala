package ch.ninecode.sc

import ch.ninecode.util.Complex

import org.scalatest.funsuite.AnyFunSuite

class BranchImpedanceSuite extends AnyFunSuite
{
    test ("SeriesImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    SimpleBranch ("a", "b", 4.0, "KLE141", "", Some (40.0), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    ),
                    SimpleBranch ("b", "c", 4.0, "KLE142", "", Some (40.0), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    )
                )
            )
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (4.0, 4.0),
            Complex (2.0, 2.0),
            Complex (4.0, 4.0),
            Complex (2.0, 2.0)), "expected series z1=4Ω, z0=2Ω"
        )
    }

    test ("ParallelImpedance2")
    {
        val branch =
            ParallelBranch ("a", "b", 0.0,
                List (
                    SimpleBranch ("a", "b", 0.0, "KLE124", "", Some (288282.0), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    ),
                    SimpleBranch ("a", "b", 0.0, "KLE123", "", Some (73737.3), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    )
                )
            )
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (1.0, 1.0),
            Complex (0.5, 0.5),
            Complex (1.0, 1.0),
            Complex (0.5, 0.5)), "expected parallel z1=1Ω z0=0.5Ω"
        )
    }

    test ("ParallelImpedance3")
    {
        val branch =
            ParallelBranch ("a", "b", 0.0,
                List (
                    SimpleBranch ("a", "b", 0.0, "KLE124", "", Some (288282.0), Impedanzen (
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75),
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75))
                    ),
                    SimpleBranch ("a", "b", 0.0, "KLE123", "", Some (73737.3), Impedanzen (
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75),
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75))
                    ),
                    SimpleBranch ("a", "b", 0.0, "KLE122", "", Some (53636.9), Impedanzen (
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75),
                        Complex (3.0, 3.0),
                        Complex (0.75, 0.75))
                    )
                )
            )
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (1.0, 1.0),
            Complex (0.25, 0.25),
            Complex (1.0, 1.0),
            Complex (0.25, 0.25)), "expected parallel z1=1Ω z0=0.25Ω"
        )
    }

    test ("SeriesParallelImpedance")
    {
        val branch =
            SeriesBranch ("a", "c", 0.0,
                Seq (
                    ParallelBranch ("a", "b", 0.0,
                        List (
                            SimpleBranch ("a", "b", 0.0, "KLE124", "", Some (288282.0), Impedanzen (
                                Complex (2.0, 2.0),
                                Complex (1.0, 1.0),
                                Complex (2.0, 2.0),
                                Complex (1.0, 1.0))),
                            SimpleBranch ("a", "b", 0.0, "KLE123", "", Some (73737.3), Impedanzen (
                                Complex (2.0, 2.0),
                                Complex (1.0, 1.0),
                                Complex (2.0, 2.0),
                                Complex (1.0, 1.0))
                            )
                        )
                    ),
                    SimpleBranch ("b", "c", 0.0, "KLE125", "", Some (73737.3), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    )
                )
            )
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (3.0, 3.0),
            Complex (1.5, 1.5),
            Complex (3.0, 3.0),
            Complex (1.5, 1.5)), "expected series parallel z1=3Ω z0=1.5Ω"
        )
    }

    test ("FakeComplexImpedance")
    {
        val branches = Array (
            SeriesBranch ("PIN26199_topo", "MUF103383_topo", 1.5770241595079082,
                Seq (
                    SimpleBranch ("PIN26199_topo", "PIN26208_topo", 0.0, "TEI22090", "Sicherung (Sicherung)",
                        Some (400.0)),
                    SimpleBranch ("PIN26208_topo", "MUF103383_topo", 1.5770241595079082, "KLE268115", "4x95",
                        None, Impedanzen (
                            Complex ("0.03211701+0.0122421j"),
                            Complex ("0.12845152+0.0495378j"),
                            Complex ("0.03543946+0.0122421j"),
                            Complex ("0.14173961+0.0495378j")
                        )
                    )
                )
            ),
            SeriesBranch ("PIN26199_topo", "PIN18526_topo", 0.8362477741993831,
                Seq (
                    SimpleBranch ("PIN26199_topo", "PIN26206_topo", 0.0, "TEI22089", "Sicherung (Sicherung)",
                        Some (400.0)),
                    SimpleBranch ("PIN26206_topo", "PIN18526_topo", 0.8362477741993831, "KLE268118", "4x95",
                        None, Impedanzen (
                            Complex ("0.05102282+0.01944847j"),
                            Complex ("0.20406506+0.07869844j"),
                            Complex ("0.05630104+0.01944847j"),
                            Complex ("0.22517523+0.07869844j")
                        )
                    )
                )
            ),
            SeriesBranch ("PIN26199_topo", "MUF103384_topo", 1.585741942856221,
                Seq (
                    SimpleBranch ("PIN26199_topo", "ABG21022_topo", 0.0, "TEI22091", "Sicherung (Sicherung)",
                        Some (400.0)),
                    SimpleBranch ("ABG21022_topo", "MUF103355_topo", 1.5857338663376332, "KLE267997", "4x95",
                        None, Impedanzen (
                            Complex ("0.01462868+0.00557604j"),
                            Complex ("0.0585072+0.02256352j"),
                            Complex ("0.01614199+0.00557604j"),
                            Complex ("0.06455967+0.02256352j")
                        )
                    ),
                    SimpleBranch ("MUF103355_topo", "MUF103384_topo", 1.5857338663376332, "KLE267999", "4x95", None,
                        Impedanzen (
                            Complex ("0.01726267+0.00658004j"),
                            Complex ("0.06904181+0.02662623j"),
                            Complex ("0.01904846+0.00658004j"),
                            Complex ("0.07618406+0.02662623j")
                        )
                    )
                )
            ),
            SeriesBranch ("PIN18526_topo", "MUF103383_topo", 0.42263343824223776,
                Seq (
                    SimpleBranch ("PIN18526_topo", "PIN18518_topo", 0.0, "KLE16234", "Sicherung (Sicherung)",
                        Some (250.0)),
                    SimpleBranch ("PIN18518_topo", "MUF103383_topo", 0.42263343824223776, "KLE268113", "4x95",
                        None, Impedanzen (
                            Complex ("0.01890581+0.00720636j"),
                            Complex ("0.07561353+0.02916064j"),
                            Complex ("0.02086159+0.00720636j"),
                            Complex ("0.08343562+0.02916064j")
                        )
                    )
                )
            ),
            SeriesBranch ("PIN18526_topo", "MUF103384_topo", 0.41355581035269445,
                Seq (
                    SimpleBranch ("PIN18526_topo", "PIN18516_topo", 0.0, "KLE16232", "Sicherung (Sicherung)",
                        Some (250.0)),
                    SimpleBranch ("PIN18516_topo", "MUF103384_topo", 0.41355581035269445, "KLE267996", "4x95",
                        None, Impedanzen (
                            Complex ("0.01913147+0.00729238j"),
                            Complex ("0.07651605+0.0295087j"),
                            Complex ("0.02111059+0.00729238j"),
                            Complex ("0.0844315+0.0295087j")
                        )
                    )
                )
            ),
            SimpleBranch ("MUF103383_topo", "HAS118345_topo", 1.9997048466130651, "KLE577967", "3x95/95",
                None, Impedanzen (
                    Complex ("0.01893134+0.00587356j"),
                    Complex ("0.07453788+0.01678161j"),
                    Complex ("0.02088975+0.00587356j"),
                    Complex ("0.0822487+0.01678161j")
                )
            ),
            SimpleBranch ("MUF103384_topo", "HAS118345_topo", 1.9997048466130651, "KLE577967", "3x95/95",
                None, Impedanzen (
                    Complex ("0.01897492+0.00588709j"),
                    Complex ("0.07470949+0.01682025j"),
                    Complex ("0.02093785+0.00588709j"),
                    Complex ("0.08243806+0.01682025j")
                )
            )
        )

        val branch = ComplexBranch ("PIN26199_topo", "HAS118345_topo", 4.0, branches.asInstanceOf [Array[Branch]])
        // fake z
        val z = Impedanzen (
            Complex ("0.03116704+0.01120796j"),
            Complex ("0.12406259+0.04185622j"),
            Complex ("0.21073073+0.07010604j"),
            Complex ("0.84021245+0.26969719j")
        )
        assert (branch.z (Impedanzen ()).toString == z.toString)
    }

    test ("TransformerImpedanceReduce")
    {
        val branch =
            SeriesBranch ("a", "d", 0.0,
                Seq (
                    SimpleBranch ("a", "b", 0.0, "KLE123", "", Some (1.6), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))),
                    TransformerBranch ("b", "c", 0.0, "TX0001", "250kVA", 400000, 1000.0, 400.0,
                        Complex (1.0, 1.0)),
                    SimpleBranch ("c", "d", 0.0, "KLE789", "", Some (4.0), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0)))))
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (2.72, 2.72),
            Complex (1.56, 1.56),
            Complex (2.72, 2.72),
            Complex (1.56, 1.56)), "expected series z1=2.72Ω z0=1.56Ω"
        )
    }

    test ("TransformerImpedanceIncrease")
    {
        val branch =
            SeriesBranch ("a", "d", 0.0,
                Seq (
                    SimpleBranch ("a", "b", 0.0, "KLE123", "", Some (1.6), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    ),
                    TransformerBranch ("b", "c", 0.0, "TX0001", "250kVA", 400000, 400.0, 1000.0, Complex (1.0, 1.0)),
                    SimpleBranch ("c", "d", 0.0, "KLE789", "", Some (4.0), Impedanzen (
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0),
                        Complex (2.0, 2.0),
                        Complex (1.0, 1.0))
                    )))
        assert (branch.z (Impedanzen ()) == Impedanzen (
            Complex (17.0, 17.0),
            Complex (9.75, 9.75),
            Complex (17.0, 17.0),
            Complex (9.75, 9.75)), "expected series z1=17Ω z0=9.75Ω"
        )
    }
}
