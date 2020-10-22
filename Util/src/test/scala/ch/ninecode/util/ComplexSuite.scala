package ch.ninecode.util

import org.scalatest.funsuite.AnyFunSuite

class ComplexSuite extends AnyFunSuite
{
    test("rectangular no suffix")
    {
        assert(Complex(123.45, 2.884) == Complex("123.45+2.884"), "no suffix")
    }

    test("rectangular j suffix")
    {
        assert(Complex(123.45, 2.884) == Complex("123.45+2.884j"), "no suffix")
    }

    test("rectangular i suffix")
    {
        assert(Complex(123.45, 2.884) == Complex("123.45+2.884i"), "no suffix")
    }

    test("polar no suffix")
    {
        assert(Complex(69.0323639334637, 102.34468833191991) == Complex("123.45<56"), "no suffix")
    }

    test("polar ° suffix")
    {
        assert(Complex(123.28081606545214, 6.4608737981914155) == Complex("123.45<3°"), "° suffix")
    }

    test("positive angle ° suffix")
    {
        assert(Complex(123.28081606545214, 6.4608737981914155) == Complex("123.45+3°"), "° suffix")
    }

    test("negative angle ° suffix")
    {
        assert(Complex(123.28081606545214, -6.4608737981914155) == Complex("123.45-3°"), "° suffix")
    }

    test("positive angle d suffix")
    {
        assert(Complex(123.23270675718884, 7.321371817952462) == Complex("123.45+3.4d"), "d suffix")
    }

    test("negative angle d suffix")
    {
        assert(Complex(123.28081606545214, -6.4608737981914155) == Complex("123.45-3d"), "d suffix")
    }
}
