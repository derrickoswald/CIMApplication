package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class SimulationDirectionGenerator
(
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    kreis: SimulationTrafoKreis) extends GLMGenerator (one_phase, 20.0, date_format, true) // ToDo: get simulation temparature from json file
{

    override def name: String = kreis.name

    override def directory: String = kreis.directory

    override def start_time: Calendar = kreis.start_time

    override def finish_time: Calendar = start_time

    override def edges: Iterable[SimulationEdge] = kreis.edges

    override def transformers: Iterable[TransformerEdge] = List (TransformerEdge (kreis.transformer.node0, kreis.transformer.node1, kreis.transformer))

    override def swing_nodes: Iterable[GLMNode] = kreis.swing_nodes

    override def nodes: Iterable[SimulationNode] = kreis.nodes

    override def extra: Iterable[String] = List ("")

    def emit_load (node: SimulationNode): String =
    {
        val power = if (one_phase)
            "            constant_power_A 1000.0+0j;"
        else
        {
          """            constant_power_A 333.33333333+0j;
            |            constant_power_B 333.33333333+0j;
            |            constant_power_C 333.33333333+0j;""".stripMargin
        }
        val phases = if (one_phase) "AN" else "ABCN"
        val voltage = node.nominal_voltage
        s"""
        |        object load
        |        {
        |            name "${node.id}";
        |$power
        |            phases $phases;
        |            nominal_voltage ${voltage}V;
        |        };
        """.stripMargin
    }

    override def emit_edge (edge: GLMEdge): String =
    {
        super.emit_edge (edge.asInstanceOf [SimulationEdge].rawedge)
    }

    override def emit_node (node: GLMNode): String =
    {
        emit_load (node.asInstanceOf [SimulationNode])
    }

    /**
     * Emit configurations for all groups of edges that are ACLineSegments.
     *
     * Get one of each type of ACLineSegment and emit a configuration for each of them.
     *
     * @param edges The edges in the model.
     * @return The configuration elements as a single string.
     */
    override def getACLineSegmentConfigurations (edges: Iterable[GLMEdge]): Iterable[String] =
    {
        edges.filter (_.asInstanceOf [SimulationEdge].rawedge.isInstanceOf [LineEdge]).map (_.asInstanceOf [SimulationEdge].rawedge.asInstanceOf [LineEdge]).groupBy (_.configurationName).values.map (_.head.configuration (this))
    }
}