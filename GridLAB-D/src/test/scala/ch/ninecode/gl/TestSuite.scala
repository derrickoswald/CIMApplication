package ch.ninecode.gl

import org.scalatest.Suites


class TestSuite extends Suites (
    new GridLABDTestSuite,
    new TransformersSuite
)