PandaPower
======

Distributed load-flow calculations using [PandaPower](https://www.pandapower.org/).

# Overview
This Scala library is used within Spark programs to
- create pandapower script (.py) files from [CIM RDD](https://github.com/derrickoswald/CIMReader)
- run the [python](https://www.python.org/) executable on worker nodes using the script
- collect the contents of the files written by the output writers into RDD

# pandapower
Python and pandapower must be installed on each Spark worker node,
and normally also on the Spark master for debugging.
Instructions for installation can be found in the
PandaPower [Getting Started](https://www.pandapower.org/start/) guide.
