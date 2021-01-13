create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };

create table if not exists cimapplication.version (
    program text,
    build text,
    version text,
    time timestamp,
    primary key ((program), version)
) with clustering order by (version asc) and comment = '
Schema version.
    program - the name and version of the program that created the schema
    build   - the git commit hash when the program was built
    version - the schema version, increment for each schema script change
    time    - schema creation time
';

insert into cimapplication.version (program, build, version, time) values ('${artifactId} ${version}', '${buildNumber}', '35', toTimestamp(now())) if not exists;

create table if not exists cimapplication.shortcircuit_run (
    id text,
    run int,
    description text,
    cim text,
    cimreaderoptions map<text,text>,
    run_time timestamp,
    max_default_short_circuit_power double,
    max_default_short_circuit_resistance double,
    max_default_short_circuit_reactance double,
    min_default_short_circuit_power double,
    min_default_short_circuit_resistance double,
    min_default_short_circuit_reactance double,
    base_temperature double,
    low_temperature double,
    high_temperature double,
    cmax double,
    cmin double,
    worstcasepf boolean,
    cosphi double,
    primary key ((id), run)
) with comment = '
Table of parameters used for each program execution.
Describes each run of the ShortCircuit code.
    id                                   - the short circuit run identifier, UUID or user specified
    run                                  - the short circuit run number, distinguishes executions with the same id
    description                          - the user supplied description of the short circuit analysis
    cim                                  - the CIM file(s) used to run the short circuit analysis
    cimreaderoptions                     - options used to read in the CIM file(s), see https://github.com/derrickoswald/CIMSpark/tree/master/CIMReader#reader-api
    run_time                             - the time at which the short circuit program was executed
    max_default_short_circuit_power      - maximum available short circuit network power (at transformer primary) [EquivalentInjection missing] (VA)
    max_default_short_circuit_resistance - maximum network short circuit resistance [EquivalentInjection missing] (Ω)
    max_default_short_circuit_reactance  - maximum network short circuit reactance [EquivalentInjection missing] (Ω)
    min_default_short_circuit_power      - minimum available short circuit network power (at transformer primary) [EquivalentInjection missing] (VA)
    min_default_short_circuit_resistance - minimum network short circuit resistance [EquivalentInjection missing] (Ω)
    min_default_short_circuit_reactance  - minimum network short circuit reactance [EquivalentInjection missing] (Ω)
    base_temperature                     - temperature of elements in the input CIM file (°C)
    low_temperature                      - low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
    high_temperature                     - high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
    cmax double                          - voltage factor for maximum fault level (used for rating equipment) (dimensionless)
    cmin double                          - voltage factor for minimum fault level (used for protections settings) (dimensionless)
    worstcasepf                          - worst case motor power factor assumed (cos term = 1.0, cosphi ignored)
    cosphi                               - power factor of (motor) load (dimensionless)
';

create index if not exists epoc_idx on cimapplication.shortcircuit_run (run_time);

create table if not exists cimapplication.shortcircuit (
    id text,
    node text,
    equipment text,
    terminal int,
    container text,
    errors text,
    trafo text,
    prev text,
    low_r double,
    low_x double,
    low_r0 double,
    low_x0 double,
    low_ik double,
    low_ik3pol double,
    low_ip double,
    low_sk double,
    high_r double,
    high_x double,
    high_r0 double,
    high_x0 double,
    high_ik double,
    high_ik3pol double,
    high_ip double,
    high_sk double,
    costerm double,
    imax_3ph_low double,
    imax_1ph_low double,
    imax_2ph_low double,
    imax_3ph_med double,
    imax_1ph_med double,
    imax_2ph_med double,
    fuses text,
    last_fuses text,
    last_fuses_id text,
    iksplit text,
    fusemax text,
    fusemaxDIN text,
    fusemaxSEV text,
    fuseok boolean,
    primary key (id, node, equipment, terminal)
) with clustering order by (node asc, equipment asc, terminal asc) and comment = '
Table of fault level values.
The short circuit powers, currents and impedances at
low temperature (highest current, lowest impedance) labeled low_XXX and
high temperature (lowest current, highest impedance) labeled high_XXX.
    id            - the short circuit run identifier, UUID or user specified
    node          - CIM ConnectivityNode mRID
    equipment     - CIM ConductingEquipment mRID
    terminal      - CIM Terminal sequence number referring to the node and equipment
    container     - CIM EquipmentContainer mRID the equipment resides in
    errors        - comma separated list of error and warning messages encountered in processing
    trafo         - CIM PowerTransformer supplying the node
    prev          - previous (on path from trafo to node) CIM ConnectivityNode mRID
    low_r         - aggregate positive sequence resistance from the trafo (primary) to this node at low temperature (Ω)
    low_x         - aggregate positive sequence reactance from the trafo (primary) to this node at low temperature (Ω)
    low_r0        - aggregate zero sequence resistance from the trafo (primary) to this node at low temperature (Ω)
    low_x0        - aggregate zero sequence reactance from the trafo (primary) to this node at low temperature (Ω)
    low_ik        - one phase bolted short circuit current at low temperature (A)
    low_ik3pol    - three phase bolted short circuit current at low temperature (A)
    low_ip        - maximum aperiodic short-circuit current according to IEC 60909-0 at low temperature (A)
    low_sk        - short-circuit power at the point of common coupling at low temperature (VA)
    high_r        - aggregate positive sequence resistance from the trafo (primary) to this node at high temperature (Ω)
    high_x        - aggregate positive sequence reactance from the trafo (primary) to this node at high temperature (Ω)
    high_r0       - aggregate zero sequence resistance from the trafo (primary) to this node at high temperature (Ω)
    high_x0       - aggregate zero sequence reactance from the trafo (primary) to this node at high temperature (Ω)
    high_ik       - one phase bolted short circuit current at high temperature (A)
    high_ik3pol   - three phase bolted short circuit current at high temperature (A)
    high_ip       - maximum aperiodic short-circuit current according to IEC 60909-0 at high temperature (A)
    high_sk       - short-circuit power at the point of common coupling at high temperature (VA)
    costerm       - cos(Ψ-φ) value used in calculating imax values (dimensionless)
    imax_3ph_low  - maximum inrush current (3 phase) for repetition_rate<0.01/min (A)
    imax_1ph_low  - maximum inrush current (1 phase, line to neutral) for repetition_rate<0.01/min (A)
    imax_2ph_low  - maximum inrush current (line to line) for repetition_rate<0.01/min (A)
    imax_3ph_med  - maximum inrush current (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (A)
    imax_1ph_med  - maximum inrush current (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (A)
    imax_2ph_med  - maximum inrush current (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (A)
    fuses         - fuse values from the source (primary of feeding transformer) to this node (A)
    last_fuses    - fuse(s) connected directly to the node (A)
    last_fuses_id - mRID of fuse(s) connected directly to the node (A)
    iksplit       - short circuit current(s) (A)
    fusemax       - maximum recommended fuse value(s) for the calculated fault current(s) (A)
    fusemaxDIN    - maximum recommended DIN fuse value(s) for the calculated fault current(s) (A)
    fusemaxSEV    - maximum recommended SEV fuse value(s) for the calculated fault current(s) (A)
    fuseok        - evaluation of whether the fuse(s) has(have) appropriate value(s) (true) or not (false)
';

create index if not exists sc_equipment_index on cimapplication.shortcircuit (equipment);

create table if not exists cimapplication.fusesummary (
    id text,
    container text,
    allok boolean,
    ok int,
    bad int,
    unknown int,
    primary key (id, container)
) with clustering order by (container asc) and comment = '
Table summarizing fuse results.
Per transformer service area summary of fuse evaluation.
    id        - the short circuit run identifier, UUID or user specified
    container - CIM EquipmentContainer mRID the equipment resides in
    allok     - evaluation of whether all fuses in the container are ok (true) or not (false)
    ok        - number of appropriate fuse values in the container
    bad       - number of inappropriate fuse values in the container
    unknown   - number of unknown fuse status values in the container
';

create index if not exists fs_container_index on cimapplication.fusesummary (container)
