create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };

create table if not exists cimapplication.measured_value_by_day (
    mrid text,
    type text,
    date date,
    time timestamp,
    interval int,
    real_a double,
    imag_a double,
    real_b double,
    imag_b double,
    real_c double,
    imag_c double,
    units text,
    primary key ((mrid,type,date),time)
) with clustering order by (time asc) and comment = 'Measurement values, e.g. smart meter readingss';

create table if not exists cimapplication.simulated_value_by_day (
   mrid text,
   type text,
   date date,
   interval int,
   time timestamp,
   real_a double,
   imag_a double,
   real_b double,
   imag_b double,
   real_c double,
   imag_c double,
   units text,
   simulation text,
   primary key ((mrid,type,date, interval),time)
) with clustering order by (time asc) and comment = 'Simulation results';

create table if not exists cimapplication.simulation (
    run text,
    name text,
    description text,
    cim text,
    cimreaderoptions map<text,text>,
    interval map<text,timestamp>,
    transformers list<text>,
    players list<frozen <map<text,text>>>,
    recorders list<frozen <map<text,text>>>,
    primary key (run)
) with comment = 'Details about a simulation execution';

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