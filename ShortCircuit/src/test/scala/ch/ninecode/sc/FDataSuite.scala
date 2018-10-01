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
        val list = List ( List (("TEI124", 288282.0), ("TEI123", 73737.3)), List(("TEI134", 1323.8), ("TEI135", 100.0)), List(("TEI141", 40.0)))
        assert (FData.fuseOK (123.456, list), "expected OK")
    }

    test ("FuseNotOK")
    {
        val list = List ( List (("TEI12", 288282.0 )), List (("TEI13", 73737.3)), List(("TEI14", 1323.8)), List(("TEI145", 50.0)))
        assert (!FData.fuseOK (123.456, list), "expected not OK")
    }

    test ("Fuse0")
    {
        val list = List ( List (("TEI12", 288282.0)), List(("TEI13", 73737.3)), List(("TEIA14", 1323.8)), List(("TEI14", 100.0)), List(("TEI15", 40.0)), List(("TEI16", 0.0)))
        assert (FData.fuseOK (123.456, list), "expected OK")
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
        val list = List ( List (("TEI124", 288282.0), ("TEI123", 73737.3)), List(("TEI134", 1323.8), ("TEI135", 100.0)), List(("TEI141", 40.0)))
        assert (FData.fuseOK (123.456, list), "expected OK")
    }

    test ("Table 2 FuseNotOK")
    {
        FData.fuse_sizing_table (2)
        val list = List ( List (("TEI12", 288282.0 )), List (("TEI13", 73737.3)), List(("TEI14", 1323.8)), List(("TEI145", 50.0)))
        assert (!FData.fuseOK (123.456, list), "expected not OK")
    }

    test ("Table 2 Fuse0")
    {
        FData.fuse_sizing_table (2)
        val list = List ( List (("TEI12", 288282.0)), List(("TEI13", 73737.3)), List(("TEIA14", 1323.8)), List(("TEI14", 100.0)), List(("TEI15", 40.0)), List(("TEI16", 0.0)))
        assert (FData.fuseOK (123.456, list), "expected OK")
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

}
