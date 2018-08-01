Simulation
======

Uses measured meter data and GrdLAB-D load-flow calculations to determine quality metrics for transformer service areas.

# Overview

Energy measurements (kWh) are read from Cassandra and applied as player files (house loads). Recorder files of
simulated currents, voltages and power usage are stored in Cassandra. Summary metrics are computed.

# Simulation Metrics

### Utilization
Utilization is the value of an element divided by the maximum rated value.
For cables, this is the current divided by the ratedCurrent (A). For transformers, this would be the power output divided by the ratedS (VA).

### Load Factor
Load factor is average load of a system divided by its peak load.
The higher the load factor is, the smoother the load profile is, and the more the infrastructure is being utilized. The highest possible load factor is 1, which indicates a flat load profile.

### Coincidence Factor
Coincidence factor is the peak of a system divided by the sum of peak loads of its individual components.
It tells how likely the individual components are peaking at the same time. The highest possible coincidence factor is 1, when all of the individual components are peaking at the same time.

### Diversity Factor
Diversity factor is sum of peak loads of all the components in a system divided by peak of the entire system.
It is the reciprocal of coincidence factor. The higher the diversity factor, the more diverse the individual loads are in terms of peaking time. If the individual loads are peaking at the same time, the diversity factor is 1.

### Responsibility Factor
Responsibility factor is the load of an individual component at the time of system peak divided by
the peak load of this individual component.
Responsibility factor tells how much each component is contributing to the system peak.
When a component peaks at the same time as the system, its responsibility factor is 100%.

### Voltage Deviation
Voltage deviation is the deviation from the nominal voltage at each point in the network.
Regulations require system operators to guarantee limits on the deviation.
Legacy systems call this “voltage drop” since there was no feed-in power from distributed energy resources, so the voltage could only decrease as a function of the distance from the supply.

### Losses
Losses is the cumulative energy losses for each cable, transformer or for an entire transformer area for a specific period.