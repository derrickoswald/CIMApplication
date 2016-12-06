
library (RSQLite)

connection = dbConnect (RSQLite::SQLite(), "/home/derrick/code/CIMApplication/GridLAB-D/results.db")

# sqlite> .schema
# CREATE TABLE simulation (id integer primary key autoincrement, house text, power double, time text);
# CREATE TABLE results (id integer primary key autoincrement, simulation integer, node text, time text, vreal double, vimag double);

simulations = dbGetQuery (connection, "select * from simulation")
# 1|HAS174735|30000.0|1480932414832

results = dbGetQuery (connection, "select * from results")
# 22285|1|HAS3100_topo|1447929000000|400.237|0.892362
results$time = as.POSIXct (as.numeric (results$time) / 1000.0, origin="1970-01-01")

sim = simulations$id[1]
node = "HAS2782_topo_fuse"
some_results = dbGetQuery (connection, "select * from results where simulation = ? and node = ?", list (sim, node))
some_results$time = as.POSIXct (as.numeric (some_results$time) / 1000.0, origin="1970-01-01")

dbDisconnect (connection)