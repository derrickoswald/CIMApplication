package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMTransformerEdge

case class SimulationDirectionGenerator
(
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    kreis: SimulationTrafoKreis) extends GLMGenerator (one_phase, 20.0, date_format, true) // ToDo: get simulation temperature from json file
{

    override def name: String = kreis.name

    override def directory: String = kreis.directory

    override def start_time: Calendar = kreis.start_time

    override def finish_time: Calendar = start_time

    override def edges: Iterable[SimulationEdge] = kreis.edges

    override def transformers: Iterable[GLMTransformerEdge] = List (GLMTransformerEdge (kreis.transformer))

    override def swing_nodes: Iterable[GLMNode] = kreis.swing_nodes

    override def nodes: Iterable[SimulationNode] = kreis.nodes

    override def extra: Iterable[String] = List ("")

    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    override def getTransformerConfigurations (transformers: Iterable[GLMTransformerEdge]): Iterable[String] =
    {
        val subtransmission_trafos = edges.flatMap (edge => edge.rawedge match { case e: GLMTransformerEdge => Some (e) case _ => None })
        val trafos = transformers ++ subtransmission_trafos
        val configurations = trafos.groupBy (_.configurationName).values
        configurations.map (config => config.head.configuration (this, config.map (_.transformer.transformer_name).mkString (", ")))
    }

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
        edge match
        {
            case e: SimulationEdge => super.emit_edge (e.rawedge)
            case _ => super.emit_edge (edge) // should never happen
        }
    }

    override def emit_node (node: GLMNode): String =
    {
        node match
        {
            case n: SimulationNode => emit_load (n)
            case _ => super.emit_node (node) // should never happen
        }
    }

    /**
     * Get raw line edges from simulation edges.
     *
     * @param edges the edges to process
     * @return line edges
     */
    def rawLineEdges (edges: Iterable[GLMEdge]): Iterable[GLMLineEdge] =
        edges.flatMap (
            _ match
            {
                case e: SimulationEdge =>
                    e.rawedge match
                    {
                        case l: GLMLineEdge => Some (l)
                        case _ => None
                    }
                case _ => None
            }
        )

    /**
     * Emit configurations for all groups of edges that are ACLineSegments.
     *
     * Get one of each type of ACLineSegment and emit a configuration for each of them.
     *
     * @param edges The edges in the model.
     * @return The configuration elements as a single string.
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    override def getACLineSegmentConfigurations (edges: Iterable[GLMEdge]): Iterable[String] =
    {
        rawLineEdges (edges).groupBy (_.configurationName).values.map (_.head.configuration (this))
    }
}