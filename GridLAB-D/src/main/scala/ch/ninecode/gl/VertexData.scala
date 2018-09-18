package ch.ninecode.gl

import org.apache.spark.graphx.VertexId

/**
 * Vertex data for transformer service area processing.
 *
 * @param area_label a user friendly label for the area
 * @param island the minimum (hash code) of all connected ConnectivityNode (single topological island)
 * @param island_label a user friendly label for the island
 */
case class VertexData (var area_label: String = "", var island: VertexId = Long.MaxValue, var island_label: String = "")
