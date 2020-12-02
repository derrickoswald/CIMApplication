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

create table if not exists cimapplication.maximumfeedin_run (
    id text,
    run int,
    description text,
    cim text,
    cimreaderoptions map<text,text>,
    run_time timestamp,
    three boolean,
    precalculation boolean,
    trafos text,
    export_only boolean,
    all boolean,
    erase boolean,
    simulation text,
    reference text,
    delta double,
    precalc_factor double,
    cosphi double,
    voltage_threshold double,
    voltage_threshold2 double,
    ignore_other boolean,
    cable_impedance_limit double,
    base_temperature double,
    sim_temperature double,
    primary key ((id), run)
) with comment = '
Table of parameters used for each program execution.
Describes each run of the MaximumFeedIn code.
    id                    - the maximum feed-in run identifier, UUID or user specified
    run                   - the maximum feed-in run number, distinguishes executions with the same id
    description           - the user supplied description of the maximum feed-in analysis
    cim                   - the CIM file(s) used to run the maximum feed-in analysis
    cimreaderoptions      - options used to read in the CIM file(s), see https://github.com/derrickoswald/CIMSpark/tree/master/CIMReader#reader-api
    run_time              - the time at which the maximum feed-in program was executed
    three                 - used three-phase calculations
    precalculation        - performed only the precalculation
    trafos                - list of transformers processed, an empty list means all low voltage transformers
    export_only           - only generated the GridLAB-D .glm files without analysing them
    all                   - all house connections processed, not just non-radial networks or changed PV in the network
    erase                 - generated GridLAB-D .glm files, player, and recorder files were deleted
    simulation            - prior simulation id to use in determining the transformers to process
    reference             - prior simulation id used to determine if the photo-voltaic installation status is changed
    delta                 - difference threshold used to determine if the maximum feed-in power has changed (dimensionless)
    precalc_factor        - scale factor applied to precalculation maximum values (dimensionless)
    cosphi                - maximum feed-in power factor used for new photo-voltaic installations, +lagging, -leading
    voltage_threshold     - voltage threshold used for the feeder of the house under test (%)
    voltage_threshold2    - voltage threshold used for neighboring feeders of the house under test (%)
    ignore_other          - cable currents on neighboring feeders of the house under test were checked
    cable_impedance_limit - cables with a R1 value higher than this were not calculated (Ω)
    base_temperature      - assumed temperature of elements in the input CIM file (°C)
    sim_temperature       - temperature at which the calculation was done (°C)
';

create index if not exists epoc_idx on cimapplication.maximumfeedin_run (run_time);

create table if not exists cimapplication.maximumfeedin (
    id text,
    house text,
    trafo text,
    feeder text,
    maximum double,
    eea int,
    reason text,
    details text,
    primary key ((id), house)
) with comment = '
Table of calculated maximum feed-in values.
    id                    - the maximum feed-in run identifier, UUID or user specified
    house                 - mRID of the EnergyConsumer at this feed-in node
    trafo                 - mRID of the PowerTransformer (or ganged transformers) supplying the house
    feeder                - mRID of the Connector supplying the house from the substation
    maximum               - maximum feed-in power (W)
    eea                   - the number of PV installations at this feed-in node
    reason                - the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
    details               - details regarding the limiting criteria
';

create custom index if not exists feedin_idx ON cimapplication.maximumfeedin (house)
using 'org.apache.cassandra.index.sasi.SASIIndex'
with options = {'analyzer_class': 'org.apache.cassandra.index.sasi.analyzer.StandardAnalyzer', 'case_sensitive': 'false'};