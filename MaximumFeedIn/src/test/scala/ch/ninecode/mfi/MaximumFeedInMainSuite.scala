package ch.ninecode.mfi

import org.scalatest.FunSuite

import ch.ninecode.mfi.Main.main

class MaximumFeedInMainSuite extends FunSuite
{
    test ("Help")
    {
        main (Array ("--unittest", "--help"))
    }

    test ("Version")
    {
        main (Array ("--unittest", "--version"))
    }
}
