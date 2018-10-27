package ch.ninecode.mfi

import org.scalatest.Suites

class EinspeiseleistungTestSuite extends Suites (
  new PowerFeedingSuite,
  new GridLABDSuite
)
