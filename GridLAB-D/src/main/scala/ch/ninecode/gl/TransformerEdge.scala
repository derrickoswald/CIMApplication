package ch.ninecode.gl

case class TransformerEdge
(
    cn1: String,
    cn2: String,
    transformer: TransformerSet
)
    extends GLMEdge
{
    def id: String = transformer.transformer_name

    /**
     * Emit a transformer.
     *
     * @param generator the driver program
     * @return A transformer string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
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
        |""".stripMargin.format (transformer.transformer_name, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2, transformer.configurationName)
        // ToDo: was transformer.node0, transformer.node1: can we get rid of those
    }
}