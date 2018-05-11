package ch.ninecode.ingest

import ch.ninecode.ingest.Main.LogLevels
import ch.ninecode.ingest.Main.LogLevels.LogLevels

case class IngestOptions
(
    /**
     * If <code>true</code>, don't call sys.exit().
     */
    unittest: Boolean = false,

    /**
     * If <code>true</code>, emit progress messages.
     */
    verbose: Boolean = false,

    /**
     * Spark master.
     */
    master: String = "local[*]",

    /**
     * Spark options.
     */
    options: Map[String,String] = Map(),

    /**
     * Cassandra connection host.
     */
    host: String = "localhost",

    /**
     * Storage level for RDD serialization.
     */
    storage: String = "MEMORY_AND_DISK_SER",

    /**
     * Logging level.
     */
    log_level: LogLevels = LogLevels.OFF,

    /**
     * Mapping CSV measurement identifier column name (column containing CH############################### values).
     */
    metercol: String = "Messpunktbezeichnung",

    /**
     * Mapping CSV mRID column name (column containing HAS##### values).
     */
    mridcol: String = "nis_number",

    /**
     * Source Belvis files.
     */
    belvis: Seq[String] = Seq ()

)