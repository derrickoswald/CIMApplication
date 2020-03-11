package ch.ninecode.sim

import java.util.Calendar


import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwitchEdge
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerSet
import ch.ninecode.model._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    directions: Map[String, Int] = null,
    var hacked: Boolean = false)
{
    val name: String = transformer.transformer_name

    // MASSIVE HACK:
    // due to a bug, BusbarSection power recorders do not record the correct value, just one of the edge powers when using measured_power
    // https://github.com/gridlab-d/gridlab-d/issues/1186
    // so to fix this we adjust recorders with property "measured_power" to measure power_in of a new switch edge
    // between the (only?) incoming edge and the node

    var transformer_edge: TransformerEdge = TransformerEdge (transformer)

    def swing_nodes: Array[GLMNode] = if ("lo" == swing)
        nodes.filter (_.id == transformer.node1).toArray
    else
        nodes.filter (_.id == transformer.node0).toArray

    def attached (node: String): Iterable[GLMEdge] = (raw_edges ++ Seq (transformer_edge)).filter (edge => (edge.cn1 == node) || (edge.cn2 == node))

    def voltage (node: String): Double = raw_nodes.find (_.id == node).map (_.nominal_voltage).getOrElse (0.0)

    def newSwitch (id: String): Switch =
    {
        val basic = BasicElement (null, id)
        val obj = IdentifiedObject (basic, null, null, id, null, null, null, null, null, null)
        val psr = PowerSystemResource (obj, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        val equipment = Equipment (psr, false, false, false, false, null, null, null, null, null, null, null, null, null, null, null, null, null)
        val conducting = ConductingEquipment (equipment, null, null, null, null, null, null, null)
        Switch (conducting, false, false, 0, false, 0, null, null, null, null, null, null, null, null)
    }

    def alterTerminal (terminal: Terminal, original_node: String, new_node: String): Terminal =
    {
        if (terminal.TopologicalNode == original_node)
            Terminal (
                sup = terminal.ACDCTerminal,
                phases = terminal.phases,
                ConductingEquipment = terminal.ConductingEquipment,
                ConnectivityNode = terminal.ConnectivityNode,
                TopologicalNode = new_node)
        else
            terminal
    }

    def alterNode (node: TopologicalNode, original_node: String, new_node: String): TopologicalNode =
    {
        if (node.id == original_node)
            TopologicalNode (
                sup = IdentifiedObject (
                    sup = BasicElement (mRID = new_node),
                    aliasName = node.IdentifiedObject.aliasName,
                    description = node.IdentifiedObject.description,
                    mRID = new_node,
                    name = node.IdentifiedObject.name),
                BaseVoltage = node.BaseVoltage,
                TopologicalIsland = node.TopologicalIsland)
        else
            node
    }

    def alterEdgeNode (edge: GLMEdge, original_node: String, new_node: String): GLMEdge =
    {
        edge match
        {
            case old: SimulationEdge =>
                old.copy (rawedge = alterEdgeNode (old.rawedge, original_node, new_node))
            case old: SwitchEdge =>
                SwitchEdge (old.data.copy (old.data.switches.map (
                    x => x.copy (
                        terminal1 = alterTerminal (x.terminal1, original_node, new_node),
                        terminal2 = alterTerminal (x.terminal2, original_node, new_node)))))
            case old: LineEdge =>
                LineEdge (old.data.copy (old.data.lines.map (
                    x => x.copy (
                        terminal1 = alterTerminal (x.terminal1, original_node, new_node),
                        terminal2 = alterTerminal (x.terminal2, original_node, new_node)))))
            case old: TransformerEdge =>
                TransformerEdge (old.transformer.copy (old.transformer.transformers.map (
                    (x: TransformerData) => x.copy (
                        nodes = x.nodes.map (y => alterNode (y, original_node, new_node))))))
            case _ =>
                val log: Logger = LoggerFactory.getLogger (getClass)
                log.error ("unrecognized edge type (%s)".format (edge))
                edge
        }
    }

    def vote (bus: String)(now: Option[Boolean], next: Option[Boolean]): Option[Boolean] =
    {
        now match
        {
            case None => next
            case Some (current) => next match
            {
                case None => now
                case Some (v) =>
                    if (current == v)
                        now
                    else
                    {
                        // we have a problem Houston
                        val log: Logger = LoggerFactory.getLogger (getClass)
                        log.error ("conflicting edge directions to node (%s)".format (bus))
                        Some (true)
                    }

            }
        }
    }

    /**
     * Determine if an edge is incoming or not.
     *
     * @param start the starting node
     * @param edge the edge to check
     * @return <code>Some(true)</code> if incoming, <code>Some(false)</code> if outgoing, <code>None</code> if we can't tell.
     */
    def minitrace_incoming (start: String, edge: GLMEdge): Option[Boolean] =
    {
        // if this edge has a direction
        if (directions.contains (edge.id))
        {
            // use it
            val direction = directions (edge.id) // +1 = cn1->cn2 away from trafo, -1 = cn1->cn2 towards trafo
            Some (((edge.cn1 == start) && (-1 == direction)) || ((edge.cn2 == start) && (1 == direction)))
        }
        else
        {
            // otherwise, try edges at the other end
            val other = if (edge.cn1 == start) edge.cn2 else edge.cn1
            val connected = attached (other).filter (_.id != edge.id) // filter out the edge we arrived on
            if (0 < connected.size)
            {
                val votes = connected.map (e => minitrace_incoming (other, e))
                val initial: Option[Boolean] = None
                votes.foldLeft(initial)(vote (other))
            }
            else
                None
        }
    }

    /**
     * Apply the measured_power workaround.
     *
     * Inject a new edge upstream of any measured_power node recorder and change the recorder to power_in on the edge.
     *
     * @return Replacement values for the nodes, edges, recorders and transformer that include the changes.
     */
    def kludge: (Iterable[GLMNode], Iterable[GLMEdge], Iterable[SimulationRecorder], TransformerEdge) =
    {
        val normal_recorders = recorders.filter (_.property != "measured_power")
        val power_recorders = recorders.filter (_.property == "measured_power")

        // find the incoming edge for each measured_power node
        val incoming_node_edges: Iterable[(SimulationRecorder, GLMEdge)] = power_recorders.flatMap (
            recorder =>
            {
                val directed: Iterable[(GLMEdge, Option[Boolean])] = attached (recorder.parent).map (e => (e, minitrace_incoming (recorder.parent, e)))
                val incoming = directed.filter (
                    _._2 match
                    {
                        case Some (x) => x
                        case None => false
                    }
                )
                incoming.size match
                {
                    case 1 => List ((recorder, incoming.head._1))
                    case 0 =>
                        val log: Logger = LoggerFactory.getLogger (getClass)
                        log.error ("no incoming edges to node %s (%s)".format (recorder.parent, directed.map (_._1.id).mkString (" ")))
                        List ()
                    case _ =>
                        val log: Logger = LoggerFactory.getLogger (getClass)
                        log.error ("multiple incoming edges to node %s (%s)".format (recorder.parent, directed.map (_._1.id).mkString (" ")))
                        List ()
                }
            }
        )

        // step 1) create the new nodes
        val new_nodes = incoming_node_edges.map (
            x =>
                SimulationNode (
                    x._1.parent + "_pseudo",
                    voltage (x._1.parent),
                    x._1.parent + "_pseudo_equipment",
                    null,
                    null,
                    null,
                    null
                )
        )

        // step 2) make the incoming edges use the new nodes
        val edge_node_map = incoming_node_edges.map (x => (x._2.id, x._1.parent)).toMap
        val modified_edges = raw_edges.map (
            edge =>
            {
                if (edge_node_map.contains (edge.id))
                {
                    val original_node = edge_node_map(edge.id)
                    alterEdgeNode (edge, original_node, original_node + "_pseudo")
                }
                else
                    edge
            }
        )
        val modified_transformer = // the transformer is not part of the edge list and needs to be handled specially
            if (edge_node_map.contains (transformer_edge.id))
            {
                val original_node = edge_node_map(transformer_edge.id)
                alterEdgeNode (transformer_edge, original_node, original_node + "_pseudo").asInstanceOf[TransformerEdge]
            }
            else
                transformer_edge

        // step 3) create the new edges from the new node to the original node
        val new_edges = incoming_node_edges.map (
            x =>
            {
                SimulationEdge (
                    SwitchEdge (x._1.parent + "_pseudo", x._1.parent, Seq (newSwitch (x._1.parent + "_switch"))),
                    Seq[(Double, Double)](),
                    Seq[(Double, Double)]()
                )
            }
        )

        // step 4) set the parent of the recorder to the new switch and alter the property to power_in
        val new_recorders = power_recorders.map (
            recorder =>
            {
                incoming_node_edges.find (_._1.name == recorder.name) match
                {
                    case Some (x) => x._1.copy (parent = x._1.parent + "_switch", property = "power_in")
                    case None => recorder
                }
            }
        )

        // bring it all together
        (raw_nodes ++ new_nodes, modified_edges ++ new_edges, normal_recorders ++ new_recorders, modified_transformer)
    }

    // add players and recorders to the raw nodes
    def nodes: Iterable[SimulationNode] =
    {
        if (!hacked && (null != directions)) // when performing the directionality simulation, the directions are not set yet
        {
            val (_n, _e, _r, _t) = kludge
            raw_nodes = _n
            raw_edges = _e
            recorders = _r
            transformer_edge = _t
            hacked = true
        }
        (raw_nodes ++ Seq (SimulationNode (transformer_edge.cn1, transformer_edge.transformer.v0, transformer_edge.transformer.transformer_name, null, null))).map (
            raw =>
            {
                val node = raw.asInstanceOf[SimulationNode]
                val my_players = players.filter (_.parent == raw.id)
                // for power recorders of nodes with players, it has to be attached to the player, not the node
                // the query has been altered to make the parent name for these nodes have the form <mrid>_load_object
                val my_player_objects = my_players.map (_.name + "_object").toArray
                val my_recorders = recorders.filter (x => x.parent == raw.id || my_player_objects.contains (x.parent))
                SimulationNode (
                    node.id,
                    node.nominal_voltage,
                    node.equipment,
                    node.world_position,
                    node.schematic_position,
                    my_players,
                    my_recorders
                )
            }
        )
    }

    // add players and recorders to the raw edges
    def edges: Iterable[SimulationEdge] =
    {
        if (!hacked && (null != directions)) // when performing the directionality simulation, the directions are not set yet
        {
            val (_n, _e, _r, _t) = kludge
            raw_nodes = _n
            raw_edges = _e
            recorders = _r
            transformer_edge = _t
            hacked = true
        }
        def notTheTransformer (edge: GLMEdge): Boolean =
            edge.asInstanceOf[SimulationEdge].rawedge match
            {
                case trafo: TransformerEdge =>
                    trafo.transformer.transformer_name != transformer.transformer_name
                case _ =>
                    true
            }
        raw_edges.filter (notTheTransformer).map (
            raw =>
            {
                val edge: SimulationEdge = raw.asInstanceOf[SimulationEdge]
                val id = edge.id
                val these_recorders = recorders.filter (_.parent == id)
                val these_players = players.filter (_.parent == id)
                SimulationEdge (
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