package ch.ninecode.gl

import org.apache.spark.graphx.VertexId

/**
 * Vertex data for transformer service area processing.
 *
 * @param area_label the area label
 * @param island_label the mRID of the island
 */
case class VertexData (var area_label: String = "", var island_label: String = "")
