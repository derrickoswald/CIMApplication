package ch.ninecode.sc

import org.scalatest.fixture.FunSuite

class FDataSuite extends FunSuite
{
    type FixtureParam = Int
    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        withFixture (test.toNoArgTest (0))
    }

    test ("Fuse+")
    {
        number: Int ⇒
            assert (FData.fuse (650.001) == 200.0, "expected 200A")
    }

    test ("Fuse-")
    {
        number: Int ⇒
            assert (FData.fuse (649.99) == 160.0, "expected 160A")
    }

    test ("FuseSmall")
    {
        number: Int ⇒
            assert (FData.fuse (25.0) == 0.0, "expected 0A")
    }

    test ("FuseZero")
    {
        number: Int ⇒
            assert (FData.fuse (0.0) == 0.0, "expected 0A")
    }

    test ("FuseOK")
    {
        val list = List ( List (("TEI124", 288282.0), ("TEI123", 73737.3)), List(("TEI134", 1323.8), ("TEI135", 100.0)), List(("TEI141", 40.0)))
        number: Int ⇒
            assert (FData.fuseOK (123.456, list), "expected OK")
    }

    test ("FuseNotOK")
    {
        val list = List ( List (("TEI12", 288282.0 )), List (("TEI13", 73737.3)), List(("TEI14", 1323.8)), List(("TEI145", 50.0)))
        number: Int ⇒ 
            assert (!FData.fuseOK (123.456, list), "expected not OK")
    }

    test ("Fuse0")
    {
        val list = List ( List (("TEI12", 288282.0)), List(("TEI13", 73737.3)), List(("TEIA14", 1323.8)), List(("TEI14", 100.0)), List(("TEI15", 40.0)), List(("TEI16", 0.0)))
        number: Int ⇒
            assert (FData.fuseOK (123.456, list), "expected OK")
    }

}
