package ch.ninecode.gl

case class SwingNode (node: String, voltage: Double) extends GLMNode
{
    override def id: String = node
    override def nominal_voltage: Double = voltage
}
