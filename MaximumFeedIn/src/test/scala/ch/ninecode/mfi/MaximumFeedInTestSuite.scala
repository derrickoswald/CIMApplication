package ch.ninecode.mfi

import org.scalatest.Suites

class MaximumFeedInTestSuite extends Suites(
    new MaximumFeedInMainSuite,
    new GridLABDSuite,
    new EinspeiseleistungGLMGeneratorSuite,
    new SmaxSolverSuite,
    new FeederSuite,
    new PrecalculationSuite
)
