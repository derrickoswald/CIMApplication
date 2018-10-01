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
        assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0)), "expected OK")
    }

    test ("FuseNotOK")
    {
        assert (!FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 50.0)), "expected not OK")
    }

    test ("Fuse0")
    {
        assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0, 0.0)), "expected OK")
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
        assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0)), "expected OK")
    }

    test ("Table 2 FuseNotOK")
    {
        FData.fuse_sizing_table (2)
        assert (!FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 50.0)), "expected not OK")
    }

    test ("Table 2 Fuse0")
    {
        FData.fuse_sizing_table (2)
        assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0, 0.0)), "expected OK")
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
