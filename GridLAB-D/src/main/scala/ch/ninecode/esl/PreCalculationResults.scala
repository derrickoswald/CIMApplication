package ch.ninecode.esl

import ch.ninecode.gl.PreEdge
import org.apache.spark.rdd.RDD

/**
 * Results from the threshold calculation.
 *
 * @param simulation The simulation number under which the results were stored in the database.
 * @param has The RDD of result records, one for each house connection.
 * @param vertices The RDD of final vertex data from the Pregel trace.
 * @param edges The RDD of edge data from the Pregel trace.
 */
case class PreCalculationResults (
    simulation: Int,
    has: RDD[MaxPowerFeedingNodeEEA],
    vertices: RDD[PowerFeedingNode],
    edges: RDD[(String, PreEdge)])
