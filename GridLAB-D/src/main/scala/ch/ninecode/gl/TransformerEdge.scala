package ch.ninecode.gl

case class TransformerEdge
(
    node1: String,
    node2: String,
    transformer: TransformerSet
)
    extends GLMEdge
{
    def id: String = transformer.transformer_name

    /**
     * The node id connected to the first terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 1.
     */
    def cn1: String = node1

    /**
     * The node id connected to the second terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 2.
     */
    def cn2: String = node2

    /**
     * Emit a transformer.
     *
     * @param one_phase If <code>true</code>, emit a single phase transformer, otherwise emit a three phase transformer.
     * @return A transformer string (.glm text) for this edge.
     */
    override def emit (one_phase: Boolean): String =
    {
        """
        |        object transformer
        |        {
        |            name "%s";
        |            phases %s;
        |            from "%s";
        |            to "%s";
        |            configuration "%s";
        |        };
        |""".stripMargin.format (transformer.transformer_name, if (one_phase) "AN" else "ABCN", cn1, cn2, transformer.configurationName)
        // ToDo: was transformer.node0, transformer.node1: can we get rid of those
    }
}