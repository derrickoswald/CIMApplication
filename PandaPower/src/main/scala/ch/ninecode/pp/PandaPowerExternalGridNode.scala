package ch.ninecode.pp

import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.TransformerEdge
import ch.ninecode.util.Complex

case class PandaPowerExternalGridNode
(
    _id: String,
    _nominal_voltage: Double,
    pmax: Double,
    pmin: Double,
    zmax: Complex,
    zmin: Complex
)
    extends LoadFlowNode (_id, _nominal_voltage)
{
}

object PandaPowerExternalGridNode
{
    def apply (transformer: TransformerEdge): PandaPowerExternalGridNode =
    {
        val set = transformer.transformer
        PandaPowerExternalGridNode (
            set.node0,
            set.v0,
            set.network_short_circuit_power_max,
            set.network_short_circuit_power_min,
            set.network_short_circuit_impedance_max,
            set.network_short_circuit_impedance_min)
    }
}