package ch.ninecode.gl

import org.apache.spark.graphx.VertexId

/**
 * Common GraphX functions.
 * Used just to get a single copy of the vertex_id function.
 */
trait Graphable
{
    /**
     * Compute the vertex id.
     * @param string The CIM MRID.
     */
    def vertex_id (string: String): VertexId =
    {
        var h = 2166136261l
        for (c ← string)
            h = (h * 16777619) ^ c
        h
    }
}
