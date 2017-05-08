package ch.ninecode.esl

import org.scalatest.Suites

class TestSuite extends Suites (
  new PowerFeedingSuite,
  new GridLABDSuite
)
