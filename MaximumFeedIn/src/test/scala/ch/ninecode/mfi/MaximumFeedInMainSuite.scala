package ch.ninecode.mfi

import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

import org.scalatest.FunSuite

import ch.ninecode.mfi.MaximumFeedIn.main

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

    test ("Verbose")
    {
        val baos = new ByteArrayOutputStream ()
        System.setErr (new PrintStream (baos))
        main (Array ("--unittest", "--verbose"))
        val line = baos.toString
        assert (line.contains ("0 trafokreise"))
        System.setErr (new PrintStream (new FileOutputStream (FileDescriptor.err)))
    }
}
