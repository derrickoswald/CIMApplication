package ch.ninecode.net

object Net
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array(
            classOf[ch.ninecode.net.LineData],
            classOf[ch.ninecode.net.LineDetails],
            classOf[ch.ninecode.net.LineEdge],
            classOf[ch.ninecode.net.LoadFlowEdge],
            classOf[ch.ninecode.net.LoadFlowNode],
            classOf[ch.ninecode.net.SwitchData],
            classOf[ch.ninecode.net.SwitchDetails],
            classOf[ch.ninecode.net.SwitchEdge],
            classOf[ch.ninecode.net.TransformerData],
            classOf[ch.ninecode.net.TransformerEdge],
            classOf[ch.ninecode.net.TransformerIsland],
            classOf[ch.ninecode.net.TransformerSet],
            classOf[ch.ninecode.net.TransformerServiceArea.EdgeData],
            classOf[ch.ninecode.net.TransformerServiceArea.VertexData],
            classOf[ch.ninecode.net.TerminalPlus]
        )
    }
}
