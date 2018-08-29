package ch.ninecode.mv

import org.scalatest.FunSuite

import ch.ninecode.mv.Main.main

class MediumVoltageMainSuite
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
