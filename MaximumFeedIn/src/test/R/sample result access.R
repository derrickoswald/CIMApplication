
library (RSQLite)

connection = dbConnect (RSQLite::SQLite(), "./simulation/results.db")

# sqlite> .schema
# sqlite> .headers ON

simulations = dbGetQuery (connection, "select * from simulation")
# sqlite> select * from simulation;
# id|description|time
# 1|Threshold Precalculation|1540628820623
# 2|Einspeiseleistung|1540628853716

results = dbGetQuery (connection, "select * from results")
# sqlite>  select * from results limit 2;
# id|simulation|trafo|house|maximum|eea|reason|details
# 1|1|TRA13025|HAS169937|70832.7238736201|0|voltage limit|assuming no EEA
# 2|1|TRA5200|MUF93955|24927.9870502405|0|voltage limit|assuming no EEA
results$time = as.POSIXct (as.numeric (results$time) / 1000.0, origin="1970-01-01")

house = "HAS122916"
some_results = dbGetQuery (connection, "select * from feedin where house = ?", list (house))
some_results$time = as.POSIXct (as.numeric (some_results$time) / 1000.0, origin="1970-01-01")
# sqlite> select * from feedin where House='HAS122916';
# Analysis|Transformer|House|Maximum|Reason|Details|When_Epoc
# Einspeiseleistung|TRA13025|HAS122916|13000.0|voltage limit|HAS122916 > 412.0 Volts|1540629878730

dbDisconnect (connection)