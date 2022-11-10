package ch.ninecode.sim

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMSimulationType
import ch.ninecode.model.BasicElement
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Equipment
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.Switch
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode

/**
 * A container for a simulation piece of work.
 *
 * Includes the information necessary to perform a simulation (of a transformer service area - a trafokreis).
 *
 * @param simulation  the primary key for the simulation as stored in the simulation table.
 * @param island      the name of the TopologicalIsland for the low voltage side of the transformer
 * @param transformer the transformer (or set of ganged transformers) servicing the area
 * @param swing       Either "hi" or "lo" indicating the primary or secondary winding, respectively, for the swing (slack) node.
 * @param raw_nodes   topological nodes for the simulation
 * @param raw_edges   topological edges for the simulation
 * @param start_time  the simulation starting time
 * @param finish_time the simulation ending time
 * @param players     list of the GridLAB-D players - these are queried from Cassandra measured_value and written to .csv player files
 * @param recorders   list of GridLAB-D recorders - these .csv recorder files are stored into Cassandra simulated_value
 * @param directory   the directory to write the .glm, players and recorders
 * @param directions  the cable directions as sign of current value
 */
case class SimulationTrafoKreis
(
    simulation: String,
    island: String,
    transformer: TransformerSet,
    swing: String,
    var raw_nodes: Iterable[GLMNode],
    var raw_edges: Iterable[GLMEdge],
    start_time: Calendar,
    finish_time: Calendar,
    players: Iterable[SimulationPlayer],
    var recorders: Iterable[SimulationRecorder],
    directory: String,
    directions: Map[String, Int] = Map(),
    var hacked: Boolean = false,
    house_for_voltage_calculation: String = "",
    simulation_type: GLMSimulationType = GLMSimulationType.SIMULATION_NORMAL)
{
    val name: String = transformer.transformer_name

    // MASSIVE HACK:
    // due to a bug, BusbarSection power recorders do not record the correct value, just one of the edge powers when using measured_power
    // https://github.com/gridlab-d/gridlab-d/issues/1186
    // so to fix this we adjust recorders with property "measured_power" to measure power_in of a new switch edge
    // between the (only?) incoming edge and the node

    var transformer_edge: GLMTransformerEdge = GLMTransformerEdge(transformer)

    def swing_nodes: Array[GLMNode] = if (simulation_type == GLMSimulationType.SIMULATION_2) {
        nodes.filter(_.id.startsWith(house_for_voltage_calculation)).toArray
    } else {
        if ("lo" == swing)
            nodes.filter(_.id == transformer.node1).toArray
        else
            nodes.filter(_.id == transformer.node0).toArray
    }

    def attached (node: String): Iterable[GLMEdge] = (raw_edges ++ Seq(transformer_edge)).filter(edge => (edge.cn1 == node) || (edge.cn2 == node))

    def voltage (node: String): Double = raw_nodes.find(_.id == node).map(_.nominal_voltage).getOrElse(0.0)

    def newSwitch (id: String): Switch =
    {
        val basic = BasicElement(mRID = id)
        val obj = IdentifiedObject(basic, mRID = id)
        obj.bitfields = IdentifiedObject.fieldsToBitfields("mRID")
        val psr = PowerSystemResource(obj)
        psr.bitfields = PowerSystemResource.fieldsToBitfields()
        val equipment = Equipment(psr)
        equipment.bitfields = Equipment.fieldsToBitfields()
        val conducting = ConductingEquipment(equipment)
        conducting.bitfields = ConductingEquipment.fieldsToBitfields()
        val s = Switch(conducting)
        s.bitfields = Switch.fieldsToBitfields()
        s
    }

    def alterTerminal (terminal: Terminal, original_node: String, new_node: String): Terminal =
    {
        if (terminal.TopologicalNode == original_node)
        {
            val t = Terminal(
                terminal.ACDCTerminal,
                phases = terminal.phases,
                ConductingEquipment = terminal.ConductingEquipment,
                ConnectivityNode = terminal.ConnectivityNode,
                TopologicalNode = new_node)
            t.bitfields = terminal.bitfields.clone
            t
        }
        else
            terminal
    }

    def alterNode (node: TopologicalNode, original_node: String, new_node: String): TopologicalNode =
    {
        if (node.id == original_node)
        {
            val idobj = IdentifiedObject(
                BasicElement(mRID = new_node),
                aliasName = node.IdentifiedObject.aliasName,
                description = node.IdentifiedObject.description,
                mRID = new_node,
                name = node.IdentifiedObject.name)
            idobj.bitfields = node.IdentifiedObject.bitfields.clone
            IdentifiedObject.fieldsToBitfields("mRID").zipWithIndex.foreach(x => idobj.bitfields(x._2) |= x._1)
            val n = TopologicalNode(
                idobj,
                BaseVoltage = node.BaseVoltage,
                TopologicalIsland = node.TopologicalIsland)
            n.bitfields = node.bitfields.clone
            n
        }
        else
            node
    }

    def alterEdgeNode (edge: GLMEdge, original_node: String, new_node: String): GLMEdge =
    {
        edge match
        {
            case old: SimulationEdge =>
                old.copy(rawedge = alterEdgeNode(old.rawedge, original_node, new_node))
            case old: GLMSwitchEdge =>
                GLMSwitchEdge(old.data.copy(old.data.switches.map(
                    x => x.copy(
                        terminal1 = alterTerminal(x.terminal1, original_node, new_node),
                        terminal2 = alterTerminal(x.terminal2, original_node, new_node)))))
            case old: GLMLineEdge =>
                GLMLineEdge(old.data.copy(old.data.lines.map(
                    x => x.copy(
                        terminal1 = alterTerminal(x.terminal1, original_node, new_node),
                        terminal2 = alterTerminal(x.terminal2, original_node, new_node)))))
            case old: GLMTransformerEdge =>
                GLMTransformerEdge(old.transformer.copy(old.transformer.transformers.map(
                    (x: TransformerData) => x.copy(
                        nodes = x.nodes.map(y => alterNode(y, original_node, new_node))))))
            case _ =>
                val log: Logger = LoggerFactory.getLogger(getClass)
                log.error(s"unrecognized edge type ($edge)")
                edge
        }
    }

    def vote (bus: String)(now: Option[Boolean], next: Option[Boolean]): Option[Boolean] =
    {
        now match
        {
            case None => next
            case Some(current) => next match
            {
                case None => now
                case Some(v) =>
                    if (current == v)
                        now
                    else
                    {
                        // we have a problem Houston
                        val log: Logger = LoggerFactory.getLogger(getClass)
                        log.error(s"conflicting edge directions to node ($bus)")
                        Some(true)
                    }

            }
        }
    }

    /**
     * Determine if an edge is incoming or not.
     *
     * @param start the starting node
     * @param edge  the edge to check
     * @return <code>Some(true)</code> if incoming, <code>Some(false)</code> if outgoing, <code>None</code> if we can't tell.
     */
    def minitrace_incoming (start: String, edge: GLMEdge, counter: Int = 0): Option[Boolean] =
    {
        if (counter > 3)
        {
            implicit val log: Logger = LoggerFactory.getLogger(getClass)
            log.error(s"counter > 3: start: ${start}, edge: ${edge.toString}")
            None
        } else
        {
            // if this edge has a direction
            if (directions.contains(edge.id))
            {
                // use it
                val direction = directions(edge.id) // +1 = cn1->cn2 away from trafo, -1 = cn1->cn2 towards trafo
                Some(((edge.cn1 == start) && (-1 == direction)) || ((edge.cn2 == start) && (1 == direction)))
            }
            else
            {
                // otherwise, try edges at the other end
                val other = if (edge.cn1 == start) edge.cn2 else edge.cn1
                val connected = attached(other).filter(_.id != edge.id) // filter out the edge we arrived on
                if (0 < connected.size)
                {
                    val votes = connected.map(e => minitrace_incoming(other, e, counter + 1))
                    val initial: Option[Boolean] = None
                    votes.foldLeft(initial)(vote(other))
                }
                else
                    None
            }
        }
    }

    def incoming (power_recorders: Iterable[SimulationRecorder]): Iterable[(SimulationRecorder, GLMEdge)] =
    {
        // find the incoming edge for each measured_power node
        power_recorders.flatMap(
            recorder =>
            {
                val directed = attached(recorder.parent).map(e => (e, minitrace_incoming(recorder.parent, e)))
                val incoming = directed.filter(_._2.fold(false)(identity)).map(_._1)
                incoming.toList match
                {
                    case Nil =>
                        val log: Logger = LoggerFactory.getLogger(getClass)
                        log.error(s"no incoming edges to node ${recorder.parent} (${directed.map(_._1.id).mkString(" ")})")
                        None
                    case x :: Nil => Some((recorder, x))
                    case _ =>
                        val log: Logger = LoggerFactory.getLogger(getClass)
                        log.error(s"multiple incoming edges to node ${recorder.parent} (${directed.map(_._1.id).mkString(" ")})")
                        None
                }
            }
        )
    }

    /**
     * Apply the measured_power workaround.
     *
     * Inject a new edge upstream of any measured_power node recorder and change the recorder to power_in on the edge.
     * Note: this code uses option.fold(default)(f) a lot, see https://alvinalexander.com/scala/how-use-fold-scala-option-some-none-syntax/
     *
     * @return Replacement values for the nodes, edges, recorders and transformer that include the changes.
     */
    def kludge: (Iterable[GLMNode], Iterable[GLMEdge], Iterable[SimulationRecorder], GLMTransformerEdge) =
    {
        val (power_recorders, normal_recorders) = recorders.partition(_.property == "measured_power")
        val incoming_node_edges = incoming(power_recorders)

        // step 1) create the new nodes
        val new_nodes = incoming_node_edges.map(
            x =>
                SimulationNode(
                    id = s"${x._1.parent}_pseudo",
                    nominal_voltage = voltage(x._1.parent),
                    equipment = s"${x._1.parent}_pseudo_equipment"
                )
        )

        // step 2) make the incoming edges use the new nodes
        val edge_node_map = incoming_node_edges.map(x => (x._2.id, x._1.parent)).toMap
        val modified_edges = raw_edges.map(
            edge => edge_node_map.get(edge.id).fold(edge)(node => alterEdgeNode(edge, node, s"${node}_pseudo")))
        val modified_transformer = // the transformer is not part of the edge list and needs to be handled specially
            edge_node_map.get(transformer_edge.id).fold(transformer_edge)(
                node => GLMTransformerEdge(transformer_edge.transformer.copy(
                    transformer_edge.transformer.transformers.map(
                        (x: TransformerData) => x.copy(
                            nodes = x.nodes.map(y => alterNode(y, node, s"${node}_pseudo")))))))

        // step 3) create the new edges from the new node to the original node
        val new_edges = incoming_node_edges.map(
            x =>
            {
                SimulationEdge(
                    rawedge = GLMSwitchEdge(s"${x._1.parent}_pseudo", x._1.parent, Seq(newSwitch(s"${x._1.parent}_switch")))
                )
            }
        )

        // step 4) set the parent of the recorder to the new switch and alter the property to power_in
        val new_recorders = power_recorders.map(
            recorder =>
            {
                incoming_node_edges.find(_._1.name == recorder.name).fold(recorder)(
                    x => x._1.copy(parent = s"${x._1.parent}_switch", property = "power_in"))
            }
        )

        // bring it all together
        (raw_nodes ++ new_nodes, modified_edges ++ new_edges, normal_recorders ++ new_recorders, modified_transformer)
    }

    // add players and recorders to the raw nodes
    def nodes: Iterable[SimulationNode] =
    {
        if (!hacked && directions.nonEmpty) // when performing the directionality simulation, the directions are not set yet
        {
            val (_n, _e, _r, _t) = kludge
            raw_nodes = _n
            raw_edges = _e
            recorders = _r
            transformer_edge = _t
            hacked = true
        }
        (raw_nodes ++ Seq(SimulationNode(transformer_edge.cn1, transformer_edge.transformer.v0, transformer_edge.transformer.transformer_name))).map(
            {
                case node: SimulationNode =>
                    val my_players = players.filter(_.parent == node.id)
                    // for power recorders of nodes with players, it has to be attached to the player, not the node
                    // the query has been altered to make the parent name for these nodes have the form <mrid>_load_object
                    val my_player_objects = my_players.map(x => s"${x.name}_object").toArray
                    val my_recorders = recorders.filter(x => x.parent == node.id || my_player_objects.contains(x.parent))
                    node.copy(players = my_players, recorders = my_recorders)
            }
        )
    }

    // add players and recorders to the raw edges
    def edges: Iterable[SimulationEdge] =
    {
        if (!hacked && directions.nonEmpty) // when performing the directionality simulation, the directions are not set yet
        {
            val (_n, _e, _r, _t) = kludge
            raw_nodes = _n
            raw_edges = _e
            recorders = _r
            transformer_edge = _t
            hacked = true
        }

        def notTheTransformer (edge: GLMEdge): Boolean =
            edge match
            {
                case sim: SimulationEdge =>
                    sim.rawedge match
                    {
                        case trafo: GLMTransformerEdge =>
                            trafo.transformer.transformer_name != transformer.transformer_name
                        case _ =>
                            true
                    }
                case _ =>
                    false
            }

        raw_edges.filter(notTheTransformer).map(
            {
                case edge: SimulationEdge =>
                    val id = edge.id
                    val these_recorders = recorders.filter(_.parent == id)
                    val these_players = players.filter(_.parent == id)
                    SimulationEdge(
                        edge.rawedge,
                        edge.world_position,
                        edge.schematic_position,
                        these_players,
                        these_recorders
                    )

            }
        )
    }
}