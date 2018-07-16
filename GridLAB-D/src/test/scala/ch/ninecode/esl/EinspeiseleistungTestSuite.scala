package ch.ninecode.esl

import org.scalatest.Suites

class EinspeiseleistungTestSuite extends Suites (
  new PowerFeedingSuite,
  new GridLABDSuite
)
