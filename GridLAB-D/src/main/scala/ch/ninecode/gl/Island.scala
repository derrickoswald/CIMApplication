package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

/**
 * A topological island utility class to get edges and nodes.
 *
 * @param spark                 The current spark session.
 * @param storage_level         The storage level to use in persisting the edges and nodes.
 * @param cable_impedance_limit cables with a R1 value higher than this are not calculated with GridLAB-D, the reason is bad performance in GridLAB-D with to high
 *                              impedance values
 */
class Island (spark: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"), cable_impedance_limit: Double = 5.0) extends CIMRDD with Serializable
{
    implicit val session: SparkSession = spark
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    import Island._

    /**
     * Create nodes from TopologicalNode TerminalData.
     *
     * @param rdd the rdd of terminal, transformer service area, TopologicalNode, voltage, and element tuples
     * @return an RDD of nodes
     */
    def process_nodes (rdd: RDD[NodeParts]): RDD[Node] =
        rdd.map (parts => (parts.head._2, PreNode (parts.head._3.id, parts.head._4, null)))

    /**
     * Create edges from ACLineSegment LineData.
     *
     * @param rdd the rdd of a pair of lines and a transformer service area & a node
     * @return an RDD of edges to be used in load flow simulation
     */
    def process_lines (rdd: RDD[(LineData, (identifier, GLMNode))]): RDD[(identifier, GLMEdge)] =
    {
        rdd.map (x => (x._2._1, LineEdge (x._1)))
    }

    /**
     * Create edges from Switch SwitchData.
     *
     * @param rdd the rdd of switches and the transformer service area & node pairs it belongs to
     * @return an RDD of edges to be used in load flow simulation
     */
    def process_switches (rdd: RDD[Iterable[(SwitchData, (identifier, GLMNode))]]): RDD[(identifier, GLMEdge)] =
    {
        rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (
                    y =>
                    {
                        val switch = x.find (_._2._1 == y).get
                        (switch._2._1, SwitchEdge (switch._1))
                    }
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
    def process_transformers (rdd: RDD[Iterable[(TransformerSet, (identifier, GLMNode))]]): RDD[(identifier, GLMEdge)] =
    {
        rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (
                    y =>
                    {
                        val transformer = x.find (_._2._1 == y).get
                        (transformer._2._1, TransformerEdge (transformer._1))
                    }
                )
            }
        )
    }

    def queryNetwork (
        identifiers_islands: IslandMap,
        node_maker: RDD[NodeParts] => RDD[Node] = process_nodes,
        line_maker: RDD[(LineData, (identifier, GLMNode))] => RDD[(identifier, GLMEdge)] = process_lines,
        switch_maker: RDD[Iterable[(SwitchData, (identifier, GLMNode))]] => RDD[(identifier, GLMEdge)] = process_switches,
        transformer_maker: RDD[Iterable[(TransformerSet, (identifier, GLMNode))]] => RDD[(identifier, GLMEdge)] = process_transformers)
        : (Nodes, Edges) =
    {
        // create a collection of all BaseVoltage
        val voltages: Map[String, Double] = getOrElse[BaseVoltage]
            .map (voltage => (voltage.id, voltage.nominalVoltage * 1000.0)) // ToDo: remove this 1000.0V multiplier
            .collect
            .toMap

        // terminals have two pieces of information that we want:
        // - the identifier (transformer service area, feeder) they are in by joining through TopologicalNode.TopologicalIsland
        // - the ConductingEquipment they belong to
        // the mRID of the TopologicalNode is used to name the network nodes
        // note that the desired equipment for a node may not be an edge, but rather a single terminal Busbar, EnergyConsumer, etc.

        // the mapping between island and identifier (transformer service area, feeder)
        val islands_identifiers: RDD[(island_id, identifier)] = identifiers_islands.map (_.swap).distinct

        // get all the Terminal with
        // - the identifier (transformer service area, feeder) it belongs to
        // - the TopologicalNode it refers to
        // - its voltage
        val terminals: RDD[(Terminal, identifier, TopologicalNode, Double)] =
            getOrElse[Terminal]
            .keyBy (terminal => terminal.TopologicalNode)
            .join (getOrElse [TopologicalNode].keyBy (_.id))
            .values
            .map (
                tn =>
                {
                    val (terminal, node) = tn
                    (node.TopologicalIsland, (terminal, node, voltages.getOrElse (node.BaseVoltage, 0.0)))
                }
            )
            .join (islands_identifiers)
            .values
            .map (x => (x._1._1, x._2, x._1._2, x._1._3))

        // get the edges we understand
        val lines: RDD[LineData] = Lines (session, storage_level).getLines () // line filter
        val switches: RDD[SwitchData] = Switches (session, storage_level).getSwitches
        val transformers: RDD[TransformerSet] = Transformers (session, storage_level).getTransformers () // transformer filter, substation filter
            // legacy naming: TransformerData should be TransformerDetails, TransformerSet should be TransformerData
            .groupBy (transformer => transformer.nodes.map (_.id).mkString ("_"))
            .values
            .map (trafos => TransformerSet (trafos.toArray)) // default_power_rating, default_impedance

        // we only need the TopologicalNode that have associated edges we care about

        val line_nodes: RDD[node_id] = lines.flatMap (x => List (x.node0, x.node1))
        val switch_nodes: RDD[node_id] = switches.flatMap (x => List (x.node0, x.node1))
        val transformer_nodes: RDD[node_id] = transformers.flatMap (x => x.transformers(0).nodes.map (_.id))
        val all_nodes: RDD[(node_id, node_id)] = session.sparkContext
            .union (line_nodes, switch_nodes, transformer_nodes)
            .distinct
            .keyBy (x => x)

        val net_nodes: RDD[TerminalData] =
            all_nodes
            .join (terminals.keyBy (_._3.id))
            .values
            .values
            .keyBy (_._1.ConductingEquipment)
            .join (getOrElse[Element]("Elements").keyBy (_.id))
            .values
            .map (
                x =>
                {
                    val (term, element) = x
                    val (terminal, id, node, voltage) = term
                    (terminal, id, node, voltage, element)
                }
            )

        // for these network nodes, find single terminal equipment if possible
        val one_terminal_equipment: RDD[(String, Iterable[Terminal])] =
            getOrElse[Terminal]
            .groupBy (_.ConductingEquipment)
            .values
            .filter (_.size == 1)
            .map (_.head)
            .groupBy (_.TopologicalNode)

        def keep_one (arg: (Iterable[TerminalData], Option[Iterable[Terminal]])): NodeParts =
        {
            val (many, one_terminals) = arg
            one_terminals match
            {
                case Some (ones) =>
                    val ids = ones.map (_.id).toList
                    many.filter (x => ids.contains (x._1.id))
                case None =>
                    many
            }
        }

        val nodes: RDD[(identifier, GLMNode)] =
            node_maker (
                net_nodes
                .groupBy (x => x._3.id) // TopologicalNode.id
                .leftOuterJoin (one_terminal_equipment)
                .values
                .map (keep_one)
            )
            .persist (storage_level)

        // for each edge, we join to the nodes it connects to and keep one edge for each island it's in
        // we'll be joining to the nodes a lot

        val keyed_nodes = nodes.keyBy (_._2.id) // (GMLNode.id, (identifier, GMLNode))

        // for cables, they are always in the same island, so it's easy
        val ll: RDD[(identifier, GLMEdge)] =
            line_maker (
                lines
                .map (x => (x.node0, x))
                .join (keyed_nodes)
                .values
            )

        // switches may separate islands, so we possibly need to keep both
        val ss: RDD[(identifier, GLMEdge)] =
            switch_maker (
                switches
                .flatMap (x => List ((x.node0, x), (x.node1, x)))
                .join (keyed_nodes)
                .values
                .groupBy (x => s"${x._1.switches.head.element.id}${x._2._1}")
                .values
            )

        // transformers definitely separate islands
        val tt: RDD[(identifier, GLMEdge)] =
            transformer_maker (
                transformers
                .flatMap (x => x.transformers.head.nodes.map (y => (y.id, x)))
                .join (keyed_nodes)
                .values
                .groupBy (x => s"${x._1.transformer_name}${x._2._1}")
                .values
            )

        val edges: RDD[(identifier, GLMEdge)] = session.sparkContext
            .union (
                ll,
                ss,
                tt)
            .persist (storage_level)

        (nodes, edges)
    }
}

object Island
{
    type identifier = String
    type island_id = String
    type node_id = String
    type IslandMap = RDD[(identifier, island_id)]
    // (a terminal, the service area it belongs to, the node it references, voltage (V) of the node, and the element it belongs to)
    type TerminalData = (Terminal, identifier, TopologicalNode, Double, Element)
    type Node = (identifier, GLMNode)
    type Nodes = RDD[Node]
    type Edges = RDD[(identifier, GLMEdge)]
    type NodeParts = Iterable[TerminalData]
    type EdgeParts = Iterable[(Iterable[(identifier, Terminal)], Element)]
}