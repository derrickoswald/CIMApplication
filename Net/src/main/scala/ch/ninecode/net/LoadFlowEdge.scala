package ch.ninecode.net

import ch.ninecode.util.Graphable

/**
 * Generic load-flow edge.
 *
 * Base class for extension by load-flow engine implementing classes.
 *
 * @param _id the unique edge identifier, usually the mRID of the electrical element
 * @param _cn1 node id connected to the first end, refers to a LoadFlowNode id
 * @param _cn2 node id connected to the second end, refers to a LoadFlowNode id
 */
class LoadFlowEdge (
    _id: String,
    _cn1: String,
    _cn2: String
)
extends Graphable
with Serializable
{
    def id: String = _id
    def cn1: String = _cn1
    def cn2: String = _cn2

    /**
     * Ordered key.
     * Provide a key on the two connections, independent of to-from from-to ordering.
     */
    def key: String = if (cn1 < cn2) s"$cn1$cn2" else s"$cn2$cn1"

    /**
     * Generate a valid configuration name.
     *
     * Use the given string, usually a library type description (e.g. "GKN 3x95se/95 1/0.6 kV" or "4x95, Ceanderkabel",
     * to create a valid GridLAB-D configuration name.
     * The intent is to allow human-readable configuration names while adhering to GrdLAB-D rules such as:
     *
     * - no leading digits: ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
     * - no decimal points: KLE199604 (underground_line:227) reference to TT 3x2.5 is missing match value
     *
     */
    def valid_config_name (string: String): String =
    {
        val s = if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
        s.replace (".", "d").replace (":", "$")
    }
}
