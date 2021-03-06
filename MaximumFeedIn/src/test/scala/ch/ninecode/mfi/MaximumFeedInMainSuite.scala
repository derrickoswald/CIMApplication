package ch.ninecode.mfi

//import java.io.ByteArrayOutputStream
//import java.io.FileDescriptor
//import java.io.FileOutputStream
//import java.io.PrintStream

import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.mfi.MaximumFeedIn.main

class MaximumFeedInMainSuite extends AnyFunSuite
{
    test("Help")
    {
        main(Array("--unittest", "--help"))
    }

    test("Version")
    {
        main(Array("--unittest", "--version"))
    }

    // This is commented out because scalatest does not allow capturing stdout.

    //    test ("Verbose")
    //    {
    //        val baos = new ByteArrayOutputStream ()
    //        System.setErr (new PrintStream (baos))
    //        main (Array ("--unittest", "--verbose"))
    //        val line = baos.toString
    //        assert (line.contains ("no CIM files specified"), line)
    //        System.setErr (new PrintStream (new FileOutputStream (FileDescriptor.err)))
    //    }
}
