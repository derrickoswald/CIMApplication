One of N
========

This module performs [GridLAB-D](http://gridlabd.org) model extraction to support _one of N_ analysis.

# Overview
In order to determine how robust a medium voltage network is, in the face of single point of failure disconnections,
the analyst must determine whether all customers can be served when a cable break or switchgear fault occurs
that impedes normal service power flows.

For the purposes of explaination, a `feeder` is defined as the connection point and feeding cable(s)
exiting a transformer substation. A `feeder service area` is the equipent attached  to a `feeder`
in the normal designed configuration. An `interconnection switch` is a (normally open) switch or disconnector,
located in the field, that provides a parallel path for power between `feeder service areas`.

By configuring switch status on `interconnection switches`
it may be possible to provide most or all customers with service from a different `feeder`
exiting the same, or a different transformer substation.

The analysis would proceed by producing a model of a `feeder` and the adjacent `feeders`
connected by `interconnection switches`.
The the feeder under test would be disconnected from power in the model,
and various configurations of the `interconnecton switches` tested to determine
which configurations can supply nominal power to most or all of the disconnected loads of the `feeder`
without exceeding normal operation limits such as:
- node **voltage drop**
- cable **rated current**
- transformer **power rating**
- etc.

A quality metric for the `feeder` can then be computed which quantifies the
robustness as the ratio of correctly supplied loads to the total number of loads - possibly
incorporating weighting factors for number of customers or some other figure of merit.

The analysis is repeated for each feeder in turn, and a
robustness metric for the system can then be computed.
