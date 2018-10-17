package ch.ninecode.on

import org.scalatest.FunSuite

import ch.ninecode.on.Main.main

class OneOfNMainSuite
extends FunSuite
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
