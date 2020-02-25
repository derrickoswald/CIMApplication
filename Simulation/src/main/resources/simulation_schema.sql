create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };

create or replace function cimapplication.magnitude (real double, imag double)
    returns null on null input
    returns double
    language java
    as $$ return (Math.sqrt (real * real + imag * imag)); $$;

create or replace function cimapplication.magnitude_sign(real double, imag double)
    returns null on null input
    returns double
    language java
    as $$
        if (Math.abs(real) > Math.abs(imag))
            return (Math.signum (real) * Math.sqrt (real * real + imag * imag));
        else
            return (Math.signum (imag) * Math.sqrt (real * real + imag * imag));
    $$;

create or replace function cimapplication.phase (real double, imag double)
    returns null on null input
    returns double
    language java
    as $$ return (Math.atan2 (imag, real)); $$;

create or replace function cimapplication.cosphi (real double, imag double)
    returns null on null input
    returns double
    language java
    as $$ return ( Math.cos(Math.atan2(imag, real)) ); $$;

create or replace function cimapplication.radians2degrees (radians double)
    returns null on null input
    returns double
    language java
    as $$ return (radians / Math.PI * 180.0); $$;

create or replace function cimapplication.concat (s1 text, s2 text)
    returns null on null input
    returns text
    language java
    as $$ return (s1 + s2); $$;

create or replace function cimapplication.standard_deviation_state (state tuple<int,double,double>, val double)
    called on null input
    returns tuple<int,double,double>
    language java
    as $$
        TupleValue ret;
        if (null == state)
            ret = null;
        else
        {
            int n = state.getInt(0);
            double mean = state.getDouble(1);
            double m2 = state.getDouble(2);
            n++;
            double delta = val - mean;
            mean += delta / n;
            m2 += delta * delta;
            state.setInt(0, n);
            state.setDouble(1, mean);
            state.setDouble(2, m2);
            ret = state;
        }
        return (ret);
    $$;

create or replace function cimapplication.standard_deviation_final (state tuple<int,double,double>)
    called on null input
    returns double
    language java
    as $$
        double ret = 0.0;
        if (null != state)
        {
            int n = state.getInt(0);
            double m2 = state.getDouble(2);
            if (n >= 1)
                ret = Math.sqrt (m2 / (n - 1));
        }
        return (ret);
    $$;

create or replace aggregate cimapplication.standard_deviation (double)
    sfunc standard_deviation_state
    stype tuple<int,double,double>
    finalfunc standard_deviation_final
    initcond (0, 0.0, 0.0);

create or replace function cimapplication.tick (t timestamp, period int)
    returns null on null input
    returns int
    language java
    as $$
        return ((int)((t.getTime () / period) % (24 * 60 * 60 * 1000 / period)));
$$;

create or replace function cimapplication.day (t timestamp)
    returns null on null input
    returns int
    language java
    as $$
        Calendar c = Calendar.getInstance ();
        c.setTimeZone (TimeZone.getTimeZone ("GMT"));
        c.setTime (t);
        return (c.get (Calendar.DAY_OF_WEEK));
    $$;

create or replace function cimapplication.week (t timestamp)
    returns null on null input
    returns int
    language java
    as $$
        Calendar c = Calendar.getInstance ();
        c.setTimeZone (TimeZone.getTimeZone ("GMT"));
        c.setTime (t);
        return (c.get (Calendar.WEEK_OF_YEAR));
    $$;

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

create table if not exists cimapplication.measured_value_stats (
    mrid text,
    type text,
    start timestamp,
    end timestamp,
    count int,
    missing int,
    minimum double,
    average double,
    maximum double,
    stddev double,
    primary key ((mrid, type), start)
) with clustering order by (start asc) and comment = '
Measurement value statistics.
Statistical properties of measurement_value table aggregated by mrid and type.
    mrid    - the unique CIM mRID for the element
    type    - the type of value, e.g. energy, power, voltage, current
    start   - the first time at which a measurement was taken in GMT
    end     - the last time at which a measurement was taken in GMT
    count   - the number of non-zero measurements
    missing - the number of zero or missing elements between the start and end time
    minimum - the minimum non-zero measurement value
    average - the average non-zero measurement value
    maximum - the maximum non-zero measurement value
    stddev  - the standard deviation of the non-zero measurement values
';

create table if not exists cimapplication.measured_value_meta (
    mrid text,
    classes map<text,int>,
    lon double,
    lat double,
    primary key (mrid)
) with comment = '
Measurement value metadata.
Auxiliary properties of measurement_value table entries.
    mrid    - the unique CIM mRID for the element
    classes - the classifications and meter count, e.g. "Apartment"->6 or "House"->1, of the smart meter installation
    lon     - the longitude of the location (°)
    lat     - the latitude of the location (°)
';

create table if not exists cimapplication.simulated_value (
    simulation text,
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
    primary key ((simulation, mrid, type, period), time)
) with clustering order by (time asc) and comment = '
Simulation results.
These are values obtained from load-flow simulations or other analysis software.
    simulation - the simulation run identifier, UUID
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
';

create table if not exists cimapplication.synthesized_value (
    synthesis text,
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
    primary key ((synthesis, type, period), time)
    ) with clustering order by (time asc) and comment = '
Synthesized values.
These are synthesized values from synthetic load-profile software or machine learning algorithms generalizing real data.
    synthesis - the synthetic data set name
    type   - the type of value, e.g. energy, power, voltage, current
    time   - the time at which the synthesized value was generated in GMT
    period - the time period of the synthesized value in milliseconds
    real_a - the real component of the phase A (or R) value
    imag_a - the imaginary component of the phase A (or R) value
    real_b - the real component of the phase B (or S) value
    imag_b - the imaginary component of the phase A (or S) value
    real_c - the real component of the phase C (or T) value
    imag_c - the imaginary component of the phase C (or T) value
    units  - the units for the synthesized value
';

create table if not exists cimapplication.simulation_event (
    simulation text,
    mrid text,
    type text,
    start_time timestamp,
    end_time timestamp,
    ratio double,
    severity int,
    message text,
    primary key ((simulation, mrid, type), start_time)
) with clustering order by (start_time asc) and comment = '
Simulation events.
These are events of interest from a post-analysis of the simulated values.
    simulation - the simulation run identifier, UUID
    mrid       - the unique CIM mRID for the element with this event
    type       - the type of event, e.g. voltage, current, power
    start_time - the event start time in GMT
    end_time   - the event end time in GMT
    ratio      - the threshold ratio
    severity   - the weight of the event (1 = most severe = red, 2 = less severe = orange)
    message    - a human readable message describing the event
';

create type if not exists cimapplication.event_number (orange int, red int);

create table if not exists cimapplication.simulation_event_summary (
    simulation text,
    mrid text,
    day date,
    consumer_total frozen<cimapplication.event_number>,
    linesegments_total frozen<cimapplication.event_number>,
    transformer_total frozen<cimapplication.event_number>,
    consumer map<text, frozen <cimapplication.event_number>>,
    linesegments map<text, frozen <cimapplication.event_number>>,
    transformer map<text, frozen <cimapplication.event_number>>,
    primary key (simulation, mrid, day)
) with clustering order by (mrid asc, day asc) and comment = '
Summary of simulation events.
This is the global events of interest from a post-analysis of the simulated values.
    simulation         - the simulation run identifier, UUID
    mrid               - the transformer mRID (or concatenated mRID if ganged) of the topological island
    day                - the day of the summary record
    consumer_total     - the count of voltage events for each severity
    linesegments_total - the count of current events for each severity
    transformer_total  - the count of power events for each severity
    consumer           - the mRID of EnergyConsumer with voltage events and the count of events for each severity
    linesegments       - the mRID of ACLineSegment with current events and the count of events for each severity
    transformer        - the mRID of PowerTransformer with power events and the count of events for each severity
';

create table if not exists cimapplication.simulation (
    id text,
    run int,
    name text,
    description text,
    cim text,
    cimreaderoptions map<text,text>,
    run_time timestamp,
    start_time timestamp,
    end_time timestamp,
    cim_temperature double,
    simulation_temperature double,
    swing text,
    swing_voltage_factor double,
    input_keyspace text,
    output_keyspace text,
    transformers list<text>,
    primary key ((id), run)
) with comment = '
Details about a simulation execution.
Describes each run of the Simulate code.
    id - the simulation run identifier, UUID or user specified
    run - the simulation run number, distinguishes executions with the same id
    name - the user supplied name of the simulation
    description - the user supplied description of the simulation
    cim - the CIM file(s) used to run the simulation
    cimreaderoptions - options used to read in the CIM file(s), see https://github.com/derrickoswald/CIMReader#reader-api
    run_time - the time at which the simulation was executed
    start_time - the simulation start time in GMT
    end_time - the simulation end time in GMT
    cim_temperature - the assumed temperature of the CIM file(s) (°C)
    simulation_temperature - the temperature of the simulation (°C)
    swing - if "hi" the slack bus is on the primary, if "lo" on the secondary, of the transformer
    swing_voltage_factor - multiplicative factor to apply to the nominal slack voltage
    input_keyspace - the Cassandra keyspace for measurement data
    output_keyspace - The Cassandra keyspace for simulated results data
    transformers - the list of PowerTransformer mRID used to determine topological islands, an empty list indicates all
';

create table if not exists cimapplication.simulation_player (
    simulation text,
    transformer text,
    name text,
    mrid text,
    type text,
    property text,
    primary key (simulation, transformer, name)
) with comment = '
Details about GridLAB-D players in the simulation.
Describes each player used in the simulation.
    simulation  - the simulation run identifier, UUID
    transformer - the transformer mRID of the topological island in which this element is found
    name        - the user supplied name of the player
    mrid        - the cim mRID of the element being driven
    type        - the player type, e.g. energy
    property    - the GridLAB-D property being driven, e.g. constant_power
Note: not included here are the player file name, the SQL that generated this player, and the start and end times
';

create table if not exists cimapplication.simulation_recorder (
    simulation text,
    transformer text,
    name text,
    mrid text,
    type text,
    property text,
    unit text,
    interval int,
    aggregations map<int,int>,
    primary key (simulation, transformer, name)
) with comment = '
Details about GridLAB-D recorders in the simulation.
Describes each recorder used in the simulation.
    simulation   - the simulation run identifier, UUID
    transformer  - the transformer mRID of the topological island in which this element is found
    name         - the user supplied name of the recorder
    mrid         - the cim mRID of the element being recorded
    type         - the recorder type, e.g. current
    property     - the GridLAB-D property being recorder, e.g. current_in
    unit         - the units for the recorder, e.g. Volts
    interval     - the recording interval (seconds)
    aggregations - summary aggregations as pairs of intervals:time-to-live (#:seconds)
Note: not included here are the recorder parent or file name.
';

create type if not exists cimapplication.point_data (type text, coordinates list<double>);

create type if not exists cimapplication.line_data (type text, coordinates list<frozen <list<double>>>);

create type if not exists cimapplication.polygon_data (type text, coordinates list<frozen <list<frozen <list<double>>>>>);

create table if not exists cimapplication.geojson_points (
    simulation text,
    coordinate_system text,
    mrid text,
    transformer text,
    type text,
    geometry frozen<cimapplication.point_data>,
    properties frozen<map<text,text>>,
    primary key ((simulation, coordinate_system), mrid)
) with comment = '
GeoJSON for simulated point elements.
Describes each point object in the simulation, excluding transformers.
    simulation        - the simulation run identifier, UUID
    coordinate_system - the coordinate system for the point
    mrid              - the cim mRID of the element
    transformer       - the mRID/name of the associated transformer service area
    type              - always "Feature" per the GeoJSON specification
    geometry          - the type ("Point") and point coordinates
    properties        - the attributes for this element from the extra queries
';

create index if not exists on cimapplication.geojson_points (transformer);

create table if not exists cimapplication.geojson_lines (
    simulation text,
    coordinate_system text,
    mrid text,
    transformer text,
    type text,
    geometry frozen<cimapplication.line_data>,
    properties frozen<map<text,text>>,
    primary key ((simulation, coordinate_system), mrid)
) with comment = '
GeoJSON for simulated line elements.
Describes each linear object in the simulation.
    simulation        - the simulation run identifier, UUID
    coordinate_system - the coordinate system for the line
    mrid              - the cim mRID of the element
    transformer       - the mRID/name of the associated transformer service area
    type              - always "Feature" per the GeoJSON specification
    geometry          - the type ("LineString") and line coordinates
    properties        - the attributes for this element from the extra queries
';

create index if not exists on cimapplication.geojson_lines (transformer);

create table if not exists cimapplication.geojson_polygons (
    simulation text,
    coordinate_system text,
    mrid text,
    type text,
    geometry frozen<cimapplication.polygon_data>,
    properties frozen<map<text,text>>,
    primary key ((simulation, coordinate_system), mrid)
) with comment = '
GeoJSON for simulated polygon elements.
Describes each polygonal object in the simulation.
    simulation        - the simulation run identifier, UUID
    coordinate_system - the coordinate system for the line
    mrid              - the cim mRID of the element
    type              - always "Feature" per the GeoJSON specification
    geometry          - the type ("Polygon") and polygon coordinates
    properties        - the attributes for this element from the extra queries
';

create table if not exists cimapplication.geojson_transformers (
    simulation text,
    coordinate_system text,
    mrid text,
    transformers set<text>,
    type text,
    geometry frozen<cimapplication.point_data>,
    properties frozen<map<text,text>>,
    primary key ((simulation, coordinate_system), mrid)
) with comment = '
GeoJSON for transformers.
Describes each transformer (set) in the simulation.
    simulation        - the simulation run identifier, UUID
    coordinate_system - the coordinate system for the point
    mrid              - the name of the transformer service area
    transformers      - the set of mRID/names of the transformers servicing this area
    type              - always "Feature" per the GeoJSON specification
    geometry          - the type ("Point") and point coordinates
    properties        - the attributes for this transformer (set) from the extra queries
';

create table if not exists cimapplication.geojson_stations (
    simulation text,
    coordinate_system text,
    transformer text,
    mrid text,
    type text,
    geometry frozen<cimapplication.polygon_data>,
    properties frozen<map<text,text>>,
    primary key ((simulation, coordinate_system), mrid, transformer)
    ) with comment = '
GeoJSON for stations.
Describes each station polygonal object in the simulation.
    simulation        - the simulation run identifier, UUID
    coordinate_system - the coordinate system for the polygon
    transformer       - the transformer feeding the station
    mrid              - the cim mRID of the station
    type              - always "Feature" per the GeoJSON specification
    geometry          - the type ("Polygon") and polygon coordinates
    properties        - the attributes for this station from the extra queries
';

create index if not exists on cimapplication.geojson_stations (transformer);

create table if not exists cimapplication.key_value (
    simulation text,
    query text,
    key text,
    value text,
    primary key (simulation, query, key)
) with comment = '
Key-value pairs for extra data.
Extra query results.
    simulation - the simulation run identifier, UUID
    query      - the name of the query from the simulation JSON
    key        - the key as returned by the query
    value      - the value as returned by the query
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
    units       - the units for the coincidence value
    simulation  - the simulation run identifier, UUID
';

create table if not exists cimapplication.responsibility_by_day (
   mrid text,
   type text,
   date date,
   time timestamp,
   transformer text,
   power double,
   peak double,
   responsibility double,
   units text,
   simulation text,
   primary key ((mrid, type), date)
) with clustering order by (date asc) and comment = '
Responsibility
Individual element contributions to the peak power of a transformer per day.
    mrid        - the unique CIM mRID of the element for which the responsibility factor applies
    type        - the type of value, in this case power
    date        - the date for which the responsibility factors are computed in GMT
    time        - the time at which the peak transformer power occurred in GMT
    transformer - the mRID of the transformer to which this element contributes responsibility
    power       - the power of the element at the peak power time
    peak        - the peak power of the transformer by day
    responsibility - the responsibility factor for the element
    units       - the units for the responsibility factor value
    simulation  - the simulation run identifier, UUID
';
