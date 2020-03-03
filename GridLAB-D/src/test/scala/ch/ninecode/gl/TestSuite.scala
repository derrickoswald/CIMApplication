package ch.ninecode.gl

import org.scalatest.Suites

class TestSuite extends Suites (
    new LineSuite,
    new GridLABDTestSuite
)