create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 2 };

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
    units text,
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

create table if not exists cimapplication.simulated_value (
    mrid text,
    type text,
    period int,
    time timestamp,
    real_a double,
    imag_a double,
    real_b double,
    imag_b double,
    real_c double,
    imag_c double,
    units text,
    simulation text,
    primary key ((mrid, type, period), time)
) with clustering order by (time asc) and comment = '
Simulation results.
These are values obtained from load-flow simulations or other analysis software.
    mrid   - the unique CIM mRID for the element with this measurement
    type   - the type of value, e.g. energy, power, voltage, current
    time   - the time at which the simulated value was taken in GMT
    period - the time period of the simulated value in milliseconds
    real_a - the real component of the phase A (or R) value
    imag_a - the imaginary component of the phase A (or R) value
    real_b - the real component of the phase B (or S) value
    imag_b - the imaginary component of the phase A (or S) value
    real_c - the real component of the phase C (or T) value
    imag_c - the imaginary component of the phase C (or T) value
    units  - the units for the simulated value
    simulation - the simulation run identifier, UUID
';

create table if not exists cimapplication.simulation (
    id text,
    name text,
    description text,
    cim text,
    cimreaderoptions map<text,text>,
    interval map<text,timestamp>,
    transformers list<text>,
    players list<frozen <map<text,text>>>,
    recorders list<frozen <map<text,text>>>,
    primary key (id)
) with comment = '
Details about a simulation execution.
Describes each run of the Simulate code.
    id - the simulation run identifier, UUID
    name - the user supplied name of the simulation
    description - the user supplied description of the simulation
    cim - the CIM file(s) used to run the simulation
    cimreaderoptions - options used to read in the CIM file(s), see https://github.com/derrickoswald/CIMReader#reader-api
    interval - the time period simulated (start and end) in GMT
    transformers - the list of PowerTransformer mRID used to determine topological islands, an empty list indicates all
    players - the details about GridLAB-D players applied to the model
    recorders - the details about GridLAB-D recorders read from the model
';

create type if not exists cimapplication.point_data (type text, coordinates list<double>);
create type if not exists cimapplication.line_data (type text, coordinates list<frozen <list<double>>>);
create type if not exists cimapplication.polygon_data (type text, coordinates list<frozen <list<frozen <list<double>>>>>);

create table if not exists cimapplication.geojson_points (
    simulation text,
    mrid text,
    transformer text,
    type text,
    geometry frozen<cimapplication.point_data>,
    properties map<text,text>,
    primary key (simulation, mrid)
) with comment = 'GeoJSON for simulated point elements';

create table if not exists cimapplication.geojson_lines (
    simulation text,
    mrid text,
    transformer text,
    type text,
    geometry frozen<cimapplication.line_data>,
    properties map<text,text>,
    primary key (simulation, mrid)
) with comment = 'GeoJSON for simulated line elements';

create table if not exists cimapplication.geojson_polygons (
    simulation text,
    mrid text,
    type text,
    geometry frozen<cimapplication.polygon_data>,
    properties map<text,text>,
    primary key (simulation, mrid)
) with comment = 'GeoJSON for simulated polygon elements';

create table if not exists cimapplication.key_value (
    simulation text,
    query text,
    key text,
    value text,
    primary key (simulation, query, key)
) with comment = 'Key-value pairs for extra data';

/*
 * Requires adjustment of cassandra.yaml to enable user defined functions
 * and scripted user defined functions:
 *
 * $ sed --in-place 's/enable_user_defined_functions: false/enable_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml \
 * $ sed --in-place 's/enable_scripted_user_defined_functions: false/enable_scripted_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml
 *
 */
create or replace function cimapplication.add_days (t timestamp, days int)
    returns null on null input
    returns timestamp
    language java
    as $$ return (new Date (t.getTime() + (long)(days * (24*60*60*1000)))); $$;

create or replace function cimapplication.subtract_days (t timestamp, days int)
    returns null on null input
    returns timestamp
    language java
    as $$ return (new Date (t.getTime() - (long)(days * (24*60*60*1000)))); $$;

create or replace function cimapplication.add_offset (t timestamp, offset int)
    returns null on null input
    returns timestamp
    language java
    as $$ return (new Date (t.getTime() + (long)(offset))); $$;

create or replace function cimapplication.subtract_offset (t timestamp, offset int)
    returns null on null input
    returns timestamp
    language java
    as $$ return (new Date (t.getTime() - (long)(offset))); $$;

create or replace function cimapplication.add_amount (value double, amount double)
    returns null on null input
    returns double
    language java
    as $$ return (value + amount); $$;

create or replace function cimapplication.subtract_amount (value double, amount double)
    returns null on null input
    returns double
    language java
    as $$ return (value - amount); $$;

create or replace function cimapplication.multiply (value double, amount double)
    returns null on null input
    returns double
    language java
    as $$ return (value * amount); $$;

create or replace function cimapplication.divide (value double, amount double)
    returns null on null input
    returns double
    language java
    as $$ return (value / amount); $$;

create or replace function cimapplication.magnitude (real double, imag double)
    returns null on null input
    returns double
    language java
    as $$ return (Math.sqrt (real * real + imag * imag)); $$;

/*
 * Summarize tables.
 * Quality metric and summary information generated by the --summarize option.
 */

create table if not exists cimapplication.utilization (
   mrid text,
   type text,
   period int,
   time timestamp,
   value double,
   reference double,
   utilization double,
   units text,
   transformer text,
   simulation text,
   primary key ((mrid, type, period), time)
) with clustering order by (time asc) and comment = '
Utilization.
The value of a simulated quantity referenced to the maximum value specified for the element.
    mrid        - the unique CIM mRID for the element with this utilization
    type        - the type of value, e.g. energy, power, voltage, current
    period      - the time period of the utilization value in milliseconds
    time        - the time at which the simulated value was taken in GMT
    value       - the value for which the utilization is computed
    reference   - the reference value for which the utilization is computed
    utilization - the utilization value in percent
    units       - the units for the utilization value
    transformer - the transformer mRID of the topological island in which this element is found
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.utilization_summary_by_day (
    mrid text,
    type text,
    date date,
    min_utilization double,
    avg_utilization double,
    max_utilization double,
    units text,
    transformer text,
    simulation text,
    primary key ((mrid, type), date)
) with clustering order by (date asc) and comment = '
Utilization summary by day.
Aggregations of the utilization table by day.
    mrid        - the unique CIM mRID for the element with this utilization
    type        - the type of value, e.g. energy, power, voltage, current
    date        - the date for which the aggregations are computed in GMT
    min_utilization - the minumum utilization value for the day in percent
    avg_utilization - the average utilization value for the day in percent
    max_utilization - the maximum utilization value for the day in percent
    units       - the units for the utilization value
    transformer - the transformer mRID of the topological island in which this element is found
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.utilization_summary_by_day_by_transformer (
    mrid text,
    type text,
    date date,
    min_utilization double,
    avg_utilization double,
    max_utilization double,
    units text,
    simulation text,
    primary key ((mrid, type), date)
) with clustering order by (date asc) and comment = '
Transformer area utilization summary by day.
Aggregations of the utilization table by day and by transformer area.
    mrid        - the transformer mRID of the topological island for which the utilizations are aggregated
    type        - the type of value, e.g. energy, power, voltage, current
    date        - the date for which the aggregations are computed in GMT
    min_utilization - the minumum utilization value for the transformer area for the day in percent
    avg_utilization - the average utilization value for the transformer area for the day in percent
    max_utilization - the maximum utilization value for the transformer area for the day in percent
    units       - the units for the utilization value
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.load_factor_by_day (
   mrid text,
   type text,
   date date,
   avg_power double,
   peak_power double,
   load_factor double,
   units text,
   simulation text,
   primary key ((mrid, type), date)
) with clustering order by (date asc) and comment = '
Load factor
Transformer load divided by daily peak load.
    mrid        - the transformer mRID of the topological island for which the load factors are calculated
    type        - the type of value, in this case power
    date        - the date for which the load factors are computed in GMT
    avg_power   - the average power used in the transformer area by day
    peak_power  - the peak power used in the transformer area by day
    load_factor - the load factor for the transformer area
    units       - the units for the load factor value
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.coincidence_factor_by_day (
   mrid text,
   type text,
   date date,
   peak_power double,
   sum_power double,
   coincidence_factor double,
   units text,
   simulation text,
   primary key ((mrid, type), date)
) with clustering order by (date asc) and comment = '
Coincidence factor
Transformer peak power divided by the sum of the peak powers of all connected elements.
    mrid        - the transformer mRID of the topological island for which the coincidence factors are aggregated
    type        - the type of value, in this case power
    date        - the date for which the coincidence factors are computed in GMT
    peak_power  - the peak power for the transformer by day
    sum_power   - the sum of the component peak powers by day
    coincidence_factor - the ratio of the peak power to the sum of the peak powers
    units       - the units for the utilization value
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.responsibility_by_day (
   mrid text,
   type text,
   date date,
   interval int,
   time timestamp,
   transformer text,
   power double,
   peak double,
   responsibility double,
   units text,
   simulation text,
   primary key ((mrid, type, date, interval), time)
) with clustering order by (time asc) and comment = 'Responsibility';

create table if not exists cimapplication.voltage_drop_by_day (
   mrid text,
   date date,
   time timestamp,
   percent double,
   units text,
   simulation text,
   transformer text,
   primary key ((mrid, date), time)
) with clustering order by (time asc) and comment = 'Voltage drop';

create table if not exists cimapplication.losses_by_day (
   transformer text,
   date date,
   transformer_losses double,
   cable_losses double,
   total double,
   primary key (transformer, date)
) with comment = 'Losses';
