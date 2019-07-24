package ch.ninecode.sim

import java.util.Calendar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.SwitchEdge
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.gl.TransformerSet
import ch.ninecode.model._

/**
 * A container for a simulation piece of work.
 *
 * Includes the information necessary to perform a simulation (of a transformer service area - a trafokreis).
 *
 * @param simulation  the primary key for the simulation as stored in the simulation table.
 * @param island      the name of the TopologicalIsland for the low voltage side of the transformer
 * @param transformer the transformer (or set of ganged transformers) servicing the area
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
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))

    // MASSIVE HACK:
    // due to a bug, BusbarSection power recorders do not record the correct value, just one of the edge powers when using measured_power
    // https://github.com/gridlab-d/gridlab-d/issues/1186
    // so to fix this we adjust recorders with property "measured_power" to measure power_in of a new switch edge
    // between the (only?) incoming edge and the node

    var transformer_edge = TransformerEdge (transformer.node0, transformer.node1, transformer)

    def attached (node: String): Iterable[GLMEdge] = (raw_edges ++ Seq (transformer_edge)).filter (edge ⇒ (edge.cn1 == node) || (edge.cn2 == node))

    def voltage (node: String): Double = raw_nodes.find (_.id == node).map (_.nominal_voltage).getOrElse (0.0)

    def newSwitch (id: String): Switch =
    {
        val basic = BasicElement (null, id)
        val obj = IdentifiedObject (basic, null, null, id, null, List(), List())
        val psr = PowerSystemResource (obj, null, List(), List(), List(), null, List(), List(), List(), List(), null, List())
        val equipment = Equipment (psr, false, false, List(), List(), null, List(), List(), List(), List(), List(), List(), List(), List(), List())
        val conducting = ConductingEquipment (equipment, null, null, null, List(), List(), null, List())
        Switch (conducting, false, false, 0, false, 0, null, null, List(), null, null, List(), List())
    }

    def alterEdgeNode (edge: GLMEdge, original_node: String, new_node: String): GLMEdge =
    {
        edge match
        {
            case old: SimulationEdge ⇒
                SimulationEdge (
                    if (old.cn1 == original_node) new_node else old.cn1,
                    if (old.cn2 == original_node) new_node else old.cn2,
                    alterEdgeNode (old.rawedge, original_node, new_node),
                    old.world_position,
                    old.schematic_position,
                    old.players,
                    old.recorders
                )
            case old: SwitchEdge ⇒
                SwitchEdge (
                    if (old.cn1 == original_node) new_node else old.cn1,
                    if (old.cn2 == original_node) new_node else old.cn2,
                    old.switches
                )
            case old: LineEdge ⇒
                LineEdge (
                    if (old.cn1 == original_node) new_node else old.cn1,
                    if (old.cn2 == original_node) new_node else old.cn2,
                    old.lines,
                    old.base_temperature,
                    old.DEFAULT_R,
                    old.DEFAULT_X
                )
            case old: TransformerEdge ⇒
                TransformerEdge (
                    if (old.cn1 == original_node) new_node else old.cn1,
                    if (old.cn2 == original_node) new_node else old.cn2,
                    old.transformer
                )
            case _ ⇒
                val log: Logger = LoggerFactory.getLogger (getClass)
                log.error ("unrecognized edge type (%s)".format (edge))
                edge
        }
    }

    def vote (bus: String)(now: Option[Boolean], next: Option[Boolean]): Option[Boolean] =
    {
        now match
        {
            case None ⇒ next
            case Some (current) ⇒ next match
            {
                case None ⇒ now
                case Some (v) ⇒
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
                val votes = connected.map (e ⇒ minitrace_incoming (other, e))
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
            recorder ⇒
            {
                val directed: Iterable[(GLMEdge, Option[Boolean])] = attached (recorder.parent).map (e ⇒ (e, minitrace_incoming (recorder.parent, e)))
                val incoming = directed.filter (
                    _._2 match
                    {
                        case Some (x) ⇒ x
                        case None ⇒ false
                    }
                )
                incoming.size match
                {
                    case 1 ⇒ List ((recorder, incoming.head._1))
                    case 0 ⇒
                        val log: Logger = LoggerFactory.getLogger (getClass)
                        log.error ("no incoming edges to node %s (%s)".format (recorder.parent, directed.map (_._1.id).mkString (" ")))
                        List ()
                    case _ ⇒
                        val log: Logger = LoggerFactory.getLogger (getClass)
                        log.error ("multiple incoming edges to node %s (%s)".format (recorder.parent, directed.map (_._1.id).mkString (" ")))
                        List ()
                }
            }
        )

        // step 1) create the new nodes
        val new_nodes = incoming_node_edges.map (
            x ⇒
                SimulationNode (
                    x._1.parent + "_pseudo",
                    voltage (x._1.parent),
                    x._1.parent + "_pseudo_equipment",
                    (0,0),
                    (0,0),
                    null,
                    null
                )
        )

        // step 2) make the incoming edges use the new nodes
        val edge_node_map = incoming_node_edges.map (x ⇒ (x._2.id, x._1.parent)).toMap
        val modified_edges = raw_edges.map (
            edge ⇒
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
            x ⇒
            {
                SimulationEdge (
                    x._1.parent + "_pseudo",
                    x._1.parent,
                    SwitchEdge (x._1.parent + "_pseudo", x._1.parent, Seq (newSwitch (x._1.parent + "_switch"))),
                    Seq[(Double, Double)](),
                    Seq[(Double, Double)]()
                )
            }
        )

        // step 4) set the parent of the recorder to the new switch and alter the property to power_in
        val new_recorders = power_recorders.map (
            recorder ⇒
            {
                incoming_node_edges.find (_._1.name == recorder.name) match
                {
                    case Some (x) ⇒ x._1.copy (parent = x._1.parent + "_switch", property = "power_in")
                    case None ⇒ recorder
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
        raw_nodes.map (
            raw ⇒
            {
                val node = raw.asInstanceOf [SimulationNode]
                val my_players = players.filter (_.parent == raw.id)
                // for power recorders of nodes with players, it has to be attached to the player, not the node
                // the query has been altered to make the parent name for these nodes have the form <mrid>_load_object
                val my_player_objects = my_players.map (_.name + "_object").toArray
                var my_recorders = recorders.filter (x ⇒ x.parent == raw.id || my_player_objects.contains (x.parent))
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
        raw_edges.map (
            raw ⇒
            {
                val edge: SimulationEdge = raw.asInstanceOf [SimulationEdge]
                val id = edge.id
                var these_recorders = recorders.filter (_.parent == id)
                var these_players = players.filter (_.parent == id)
                SimulationEdge (
                    edge.cn1,
                    edge.cn2,
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