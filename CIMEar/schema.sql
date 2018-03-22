create keyspace if not exists cimapplication with replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };
use cimapplication;
create table if not exists measured_value_by_day (
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
) with clustering order by (time asc);

