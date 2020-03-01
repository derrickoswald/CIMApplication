create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };

create table if not exists cimapplication.measured_value (
    mrid text,
    type text,
    time timestamp,
    period int,
    real_a double,
    imag_a double,
    real_b double,
    imag_b double,
    real_c double,
    imag_c double,
    units text static,
    primary key ((mrid, type), time)
) with clustering order by (time asc) and comment = '
Measurement values.
These are typically smart meter readings, or transformer values from a SCADA system.
    mrid   - the unique CIM mRID for the element with this measurement
    type   - the type of value, e.g. energy, power, voltage, current
    time   - the time at which the measurement was taken in GMT
    period - the time period over which the measurement was taken in milliseconds
    real_a - the real component of the phase A (or R) value
    imag_a - the imaginary component of the phase A (or R) value
    real_b - the real component of the phase B (or S) value
    imag_b - the imaginary component of the phase A (or S) value
    real_c - the real component of the phase C (or T) value
    imag_c - the imaginary component of the phase C (or T) value
    units  - the units for the measurement
';

