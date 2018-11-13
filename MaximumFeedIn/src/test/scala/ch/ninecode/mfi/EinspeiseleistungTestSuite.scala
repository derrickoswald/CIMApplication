package ch.ninecode.mfi

import org.scalatest.Suites

class EinspeiseleistungTestSuite extends Suites (
  new MaximumFeedInMainSuite,
  new PowerFeedingSuite,
  new GridLABDSuite
)
