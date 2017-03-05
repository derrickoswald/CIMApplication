package ch.ninecode.gl

import org.scalatest.Suites

class TestSuite extends Suites (
  new PowerFeedingSuite,
  new GridLABDSuite
)
