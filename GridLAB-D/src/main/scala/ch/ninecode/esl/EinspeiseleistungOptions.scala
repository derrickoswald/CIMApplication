package ch.ninecode.esl

import scala.collection.mutable.HashMap

/**
 * Options for the Einspeiseleistung calculation.
 * @param verbose If <code>true</code> turns on the INFO logging if it was not on. Default <code>false</code>.
 * @param cim_reader_options Options to the CIMReader, such as <code>ch.ninecode.cim.do_deduplication</code>.
 * @param three If <code>true</code> uses three-phase calculations. Default <code>false</code> - single phase caclulations.
 * @param precalculation If <code>true</code> performs only the precalculation and stores the results in the database.
 * @param trafos The list of transformers to process. Default is an empty list which means all low voltage transformers in the input file(s) are processeed.
 * @param export_only If <code>true</code> only generates the GridLAB-D .glm files without simulating them. Default <code>false</code>.
 * @param all If <code>true</code> forces all house connections to be processed, rather than just the ones with a changed photo-voltaic installation. Default <code>false</code>.
 * @param erase If <code>true</code> deletes the generated GridLAB-D .glm files and player and recorder files. Default <code>false</code>.
 * @param simulation The prior simulation number to use in determining the transformers to process. Default -1 - use either the trafos list if specified or all low voltage transformers.
 * @param reference The prior simulation number to determine if the photo-voltaic installation status is changed. Default -1 - use the current precalculation simulation.
 * @param delta The difference threshold to determine if the maximum feed-in power has changed between precalculations. Default 1.0e-6.
 * @param workdir The shared directory (among Spark executors) to use for staging GridLAB-D simulations. Each simulation is created in a subdirectory of this directory.
 * @param files The list of input CIM files (RDF).
 * @param precalc_factor The scale factor to apply to precalculation maximum values - which is used as an upper bound for the stepped simulation calculation. Default 1.5.
 */
case class EinspeiseleistungOptions (
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
    three: Boolean = false,
    precalculation: Boolean = false,
    trafos: String = "",
    export_only: Boolean = false,
    all: Boolean = false,
    erase: Boolean = false,
    simulation: Int = -1,
    reference: Int = -1,
    delta: Double = 1e-6,
    workdir: String = "",
    files: Seq[String] = Seq(),
    precalc_factor: Double = 1.5
)
