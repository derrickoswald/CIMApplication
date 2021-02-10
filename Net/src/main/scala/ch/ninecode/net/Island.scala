package ch.ninecode.net

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.Element
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.Island.Edges
import ch.ninecode.net.Island.IslandMap
import ch.ninecode.net.Island.Nodes
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.Island.island_id
import ch.ninecode.net.Island.node_id

/**
 * A topological island utility class to get edges and nodes.
 *
 * @param spark         The current spark session.
 * @param storage_level The storage level to use in persisting the edges and nodes.
 */
class Island (
    spark: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString("MEMORY_AND_DISK_SER"))
    extends CIMRDD with Serializable
{
    implicit val session: SparkSession = spark
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Construct the name of the (ganged) transformer.
     *
     * @return The name of this transformer
     */
    def getTransformerId (transformer: TransformerData): String =
    {
        transformer.nodes.map(_.id).mkString("_")
    }

    // get the edges we understand
    lazy val lines: RDD[LineData] = Lines(session, storage_level).getLines() // line filter
    lazy val switches: RDD[SwitchData] = Switches(session, storage_level).getSwitches
    lazy val transformers: RDD[TransformerSet] = Transformers(session, storage_level).getTransformers() // transformer filter, substation filter
        // legacy naming: TransformerData should be TransformerDetails, TransformerSet should be TransformerData
        .groupBy(getTransformerId)
        .values
        .map(trafos => TransformerSet(trafos.toArray)) // default_power_rating, default_impedance

    /**
     * Get the list of node ids.
     *
     * @param set the transformer set to operate on
     * @return the node ids
     */
    def getTransformerNodes (set: TransformerSet): Seq[String] =
    {
        set.transformers(0).nodes.map(_.id)
    }

    // we only need the TopologicalNode that have associated edges we care about
    lazy val line_nodes: RDD[node_id] = lines.flatMap(x => List(x.node0, x.node1))
    lazy val switch_nodes: RDD[node_id] = switches.flatMap(x => List(x.node0, x.node1))
    lazy val transformer_nodes: RDD[node_id] = transformers.flatMap(getTransformerNodes)

    lazy val all_nodes: RDD[(node_id, node_id)] = session.sparkContext
        .union(line_nodes, switch_nodes, transformer_nodes)
        .distinct
        .keyBy(identity)

    /**
     * The composite terminal objects.
     *
     * Terminals have two pieces of information that we want:
     *  - the identifier (transformer service area, feeder) they are in by joining through TopologicalNode.TopologicalIsland
     *  - the ConductingEquipment they belong to
     *    The mRID of the TopologicalNode is used to name the network nodes
     *    Note that the desired equipment for a node may not be an edge, but rather a single terminal Busbar, EnergyConsumer, etc.
     */
    def terminals_plus (islands_identifiers: RDD[(island_id, identifier)]): RDD[TerminalPlus] =
    {
        val voltages = Voltages(session).getVoltages
        val topological_islands = getOrElse[Terminal]
            .keyBy(terminal => terminal.TopologicalNode)
            .join(getOrElse[TopologicalNode].keyBy(_.id))
            .values
            .keyBy(_._1.ConductingEquipment)
            .join(getOrElse[Element].keyBy(_.id))
            .values
            .keyBy(_._1._2.TopologicalIsland)

        islands_identifiers.join(topological_islands)
            .values
            .map(
                x =>
                {
                    val (id, ((terminal, node), element)) = x
                    TerminalPlus(id, terminal, node, voltages.getOrElse(node.BaseVoltage, 0.0), element)
                }
            )
    }

    // for these network nodes, find single terminal equipment if possible
    lazy val one_terminal_equipment: RDD[(String, Iterable[Terminal])] =
        getOrElse[Terminal]
            .groupBy(_.ConductingEquipment)
            .values
            .flatMap(
                _.toList match
                {
                    case t :: Nil => Some(t) // only one terminal
                    case _ => None
                }
            )
            .groupBy(_.TopologicalNode)

    /**
     * Create nodes from TopologicalNode TerminalData.
     *
     * @param rdd the rdd of terminal, transformer service area, TopologicalNode, voltage, and element tuples
     * @return an RDD of nodes
     */
    def node_maker (rdd: RDD[Iterable[TerminalPlus]]): RDD[(identifier, LoadFlowNode)] =
        rdd.flatMap(parts => parts.headOption.map(x => (x.id, new LoadFlowNode(x.node.id, x.voltage))))

    /**
     * Create edges from ACLineSegment LineData.
     *
     * @param rdd the rdd of a pair of lines and a transformer service area & a node
     * @return an RDD of edges to be used in load flow simulation
     */
    def line_maker (rdd: RDD[(LineData, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
        rdd.map(x => (x._2._1, new LineEdge(x._1)))

    /**
     * Create edges from Switch SwitchData.
     *
     * @param rdd the rdd of switches and the transformer service area & node pairs it belongs to
     * @return an RDD of edges to be used in load flow simulation
     */
    def switch_maker (rdd: RDD[Iterable[(SwitchData, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.flatMap(
            x =>
            {
                val unique_identifiers = x.map(_._2._1).toSet
                unique_identifiers.flatMap(
                    y => x.find(_._2._1 == y).map(switch => (switch._2._1, new SwitchEdge(switch._1)))
                )
            }
        )
    }

    /**
     * Create edges from PowerTransformer TransformerSet.
     *
     * @param rdd the rdd of transformers and the transformer service area & node pairs it belongs to
     * @return an RDD of edges to be used in load flow simulation
     */
    def transformer_maker (rdd: RDD[Iterable[(TransformerSet, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.flatMap(
            x =>
            {
                val unique_identifiers = x.map(_._2._1).toSet
                unique_identifiers.flatMap(
                    y => x.find(_._2._1 == y).map(trafo => (trafo._2._1, new TransformerEdge(trafo._1)))
                )
            }
        )
    }

    def keep_one (arg: (Iterable[TerminalPlus], Option[Iterable[Terminal]])): Iterable[TerminalPlus] =
    {
        val (many, one_terminals) = arg
        one_terminals match
        {
            case Some(ones) =>
                val ids = ones.map(_.id).toList
                many.filter(x => ids.contains(x.terminal.id))
            case None =>
                many
        }
    }

    def makeNodes (net_nodes: RDD[TerminalPlus]): RDD[(identifier, LoadFlowNode)] =
    {
        node_maker(
            net_nodes
                .groupBy(x => x.node.id) // TopologicalNode.id
                .leftOuterJoin(one_terminal_equipment)
                .values
                .map(keep_one)
        )
            .persist(storage_level)
    }

    // for cables, they are always in the same island, so it's easy
    def makeLines (keyed_nodes: RDD[(String, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        line_maker(
            lines
                .map(x => (x.node0, x))
                .join(keyed_nodes)
                .values
        )
    }

    // switches may separate islands, so we possibly need to keep both
    def makeSwitches (keyed_nodes: RDD[(String, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        switch_maker(
            switches
                .flatMap(x => List((x.node0, x), (x.node1, x)))
                .join(keyed_nodes)
                .values
                .groupBy(x => s"${x._1.switches.map(_.element.id).mkString("||")}${x._2._1}")
                .values
                // keep only switches with both terminals in this island
                .filter(_.size > 1)
        )
    }

    // transformers definitely separate islands
    def makeTransformers (keyed_nodes: RDD[(String, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        transformer_maker(
            transformers
                .flatMap(x => x.transformers.head.nodes.map(y => (y.id, x)))
                .join(keyed_nodes)
                .values
                .groupBy(x => s"${x._1.transformer_name}${x._2._1}")
                .values
        )
    }

    def queryNetwork (identifiers_islands: IslandMap): (Nodes, Edges) =
    {
        // the mapping between island and identifier (transformer service area, feeder)
        val islands_identifiers: RDD[(island_id, identifier)] = identifiers_islands.map(_.swap).distinct.groupByKey.map(x => {
            (x._1, x._2.mkString("_"))
        })

        // get all the Terminal with
        // - the identifier (transformer service area, feeder) it belongs to
        // - the TopologicalNode it refers to
        // - its voltage
        val terminals: RDD[TerminalPlus] = terminals_plus(islands_identifiers)

        val net_nodes: RDD[TerminalPlus] =
            all_nodes
                .join(terminals.keyBy(_.node.id))
                .values
                .values

        val nodes = makeNodes(net_nodes)

        // for each edge, we join to the nodes it connects to and keep one edge for each island it's in
        // we'll be joining to the nodes a lot

        val keyed_nodes: RDD[(String, (identifier, LoadFlowNode))] = nodes.keyBy(_._2.id) // (GMLNode.id, (identifier, GMLNode))
        val ll = makeLines(keyed_nodes)
        val ss = makeSwitches(keyed_nodes)
        val tt = makeTransformers(keyed_nodes)
        val edges: RDD[(identifier, LoadFlowEdge)] = session.sparkContext
            .union(
                ll,
                ss,
                tt)
            .persist(storage_level)

        (nodes, edges)
    }
}

object Island
{
    type identifier = String
    type island_id = String
    type node_id = String
    type IslandMap = RDD[(identifier, island_id)]
    type Nodes = RDD[(identifier, LoadFlowNode)]
    type Edges = RDD[(identifier, LoadFlowEdge)]
}