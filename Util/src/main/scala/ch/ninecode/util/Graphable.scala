package ch.ninecode.util

import org.apache.spark.graphx.VertexId

/**
 * Common GraphX functions.
 * Used just to get a single copy of the vertex_id function.
 */
trait Graphable
{
    /**
     * Compute the vertex id.
     *
     * @param string The CIM mRID.
     * @return the node id (similar to the hash code of the mRID)
     */
    def vertex_id (string: String): VertexId =
    {
        if (null == string || string == "")
            0L
        else
        {
            @SuppressWarnings(Array("org.wartremover.warts.Var"))
            var h = 2166136261L
            for (c <- string)
                h = (h * 16777619) ^ c
            h
        }
    }
}
