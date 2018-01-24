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

    test ("FuseOK")
    {
        number: Int ⇒
            assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0)), "expected OK")
    }

    test ("FuseNotOK")
    {
        number: Int ⇒
            assert (!FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 50.0)), "expected not OK")
    }

    test ("Fuse0")
    {
        number: Int ⇒
            assert (FData.fuseOK (123.456, List (288282.0, 73737.3, 1323.8, 100.0, 40.0, 0.0)), "expected OK")
    }

}
