package ch.ninecode.mfi

import org.scalatest.Suites

class EinspeiseleistungTestSuite extends Suites (
    new MaximumFeedInMainSuite,
    new GridLABDSuite,
    new EinspeiseleistungGLMGeneratorSuite,
    new SmaxSolverSuite
)
