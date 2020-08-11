package ch.ninecode.gl

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.util.Random

/**
 * Identify the islands in each transformer service area.
 *
 * Use GraphX to label islands connected together &mdash;
 * basically everything not on the other side of an open switch or transformer core.
 * Identifies island clusters sharing a (group of ganged) transformer(s) low voltage winding.
 *
 * @param session       the Spark session object
 * @param storage_level The storage level to use in persisting the edges and nodes.
 * @param debug         flag to turn on debug output
 */
case class TransformerServiceArea (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"), debug: Boolean = false, calculate_public_lighting: Boolean = false) extends CIMRDD
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Index of normalOpen field in Switch bitmask.
     */
    val normalOpenMask: Int = Switch.fields.indexOf ("normalOpen")

    /**
     * Index of open field in Switch bitmask.
     */
    val openMask: Int = Switch.fields.indexOf ("open")

    val voltage_rdd: RDD[BaseVoltage] = getOrElse [BaseVoltage]
    val conducting_equipment_rdd: RDD[ConductingEquipment] = getOrElse [ConductingEquipment]
    val element_rdd: RDD[Element] = getOrElse [Element]("Elements")
    val power_transformer_rdd: RDD[PowerTransformer] = getOrElse [PowerTransformer]
    val power_transformer_end_rdd: RDD[PowerTransformerEnd] = getOrElse [PowerTransformerEnd]
    val terminal_rdd: RDD[Terminal] = getOrElse [Terminal]
    val topological_island_rdd: RDD[TopologicalIsland] = getOrElse [TopologicalIsland]
    val topological_node_rdd: RDD[TopologicalNode] = getOrElse [TopologicalNode]

    /**
     * Compute the vertex id.
     *
     * @param string The CIM mRID.
     * @return the node id (similar to the hash code of the mRID)
     */
    def vertex_id (string: String): VertexId =
    {
        var h = 2166136261L
        for (c ← string)
            h = (h * 16777619) ^ c
        h
    }

    /**
     * Method to determine if a switch is closed (both terminals are the same topological node).
     *
     * If the switch has the <code>open</code> attribute set, use that.
     * Otherwise if it has the <code>normalOpen</code> attribute set, use that.
     * Otherwise assume it is closed.
     *
     * @param switch The switch object to test.
     * @return <code>true</code> if the switch is closed, <code>false</code> otherwise.
     */
    def switchClosed (switch: Switch): Boolean =
    {
        if (0 != (switch.bitfields (openMask / 32) & (1 << (openMask % 32))))
            !switch.open // open valid
        else
            if (0 != (switch.bitfields (normalOpenMask / 32) & (1 << (normalOpenMask % 32))))
                !switch.normalOpen
            else
                true
    }

    /**
     * Method to determine if the nodes for an element are in the same transformer service area.
     *
     * @param element The element to test.
     * @return <code>true</code> if the element is effectively one node, <code>false</code> otherwise.
     */
    def isSameArea (element: Element, v1: Double, v2: Double): Boolean =
    {
        element match
        {
            case switch: Switch ⇒ switchClosed (switch)
            case cut: Cut ⇒ switchClosed (cut.Switch)
            case disconnector: Disconnector ⇒ switchClosed (disconnector.Switch)
            case fuse: Fuse ⇒ switchClosed (fuse.Switch)
            case gd: GroundDisconnector ⇒ switchClosed (gd.Switch)
            case jumper: Jumper ⇒ switchClosed (jumper.Switch)
            case ps: ProtectedSwitch ⇒ switchClosed (ps.Switch)
            case sectionaliser: Sectionaliser ⇒ switchClosed (sectionaliser.Switch)
            case breaker: Breaker ⇒ switchClosed (breaker.ProtectedSwitch.Switch)
            case lbs: LoadBreakSwitch ⇒ switchClosed (lbs.ProtectedSwitch.Switch)
            case recloser: Recloser ⇒ switchClosed (recloser.ProtectedSwitch.Switch)
            case _: PowerTransformer ⇒ v1 <= 1000.0 && (v2 <= 1000.0 && (v2 > 230.0 || (calculate_public_lighting && v2 == 230.0))) // ToDo: don't hard code these voltage values
            case _ ⇒
                log.warn ("transformer service area processor encountered edge with unhandled class '" + element.getClass.getName + "', assumed same transformer service area")
                true
        }
    }

    /**
     * Predicate to determine if the transformer is supplying a transformer service area.
     *
     * @param voltages array of end voltages
     * @param arg      the transformer id, PowerTransformer element and PowerTransformerEnds
     * @return <code>true</code> if this transformer should be a starting point for tracing a transformer service area
     */
    def heavy (voltages: collection.Map[String, Double])(arg: (String, (PowerTransformer, Option[Iterable[PowerTransformerEnd]]))): Boolean =
    {
        val (_, (_, e)) = arg
        val ends = e.getOrElse (Iterable ()).toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
        val v = ends.map (t => voltages.getOrElse (t.TransformerEnd.BaseVoltage, 0.0))
        if (v.length < 2)
            false
        else
            v.head > 1000.0 && v.tail.forall (v => v <= 1000.0 && v > 230.0) // ToDo: don't hard code these voltage values
    }

    /**
     * Create a mapping between TopologicalIsland and PowerTransformer(s) with secondary windings in the island.
     *
     * This is just the first phase of identifying transformer service areas because a transformer service area
     * may comprise many TopologicalIsland.
     * For example when a Switch has normalOpen=false and retain=true
     * (or the NetworkTopologyProcessor is instructed to retain all switches)
     * there will be two TopologicalIsland, one on each side of the switch, that both belong to the same
     * transformer service area.
     *
     * @return a mapping between TopologicalIsland.id and PowerTransformer.id, e.g. ("PIN123_node_island", "TRA123")
     *         Where there are ganged transformers with connected secondary windings, the PowerTransformer.id
     *         is the concatenation of the sorted PowerTransformer.id values of all connected transformers,
     *         e.g. TRA1234_TRA5678.
     *         Any TopologicalIsland without a transformer secondary node is not included.
     */
    def island_trafoset_rdd: RDD[(String, String)] =
    {
        // get a map of voltages
        // ToDo: fix this 1kV multiplier on the voltages
        val voltages = getOrElse [BaseVoltage].map (v => (v.id, v.nominalVoltage * 1000.0)).collectAsMap ()

        // get all power transformers for transformer service areas
        val power_transformers = power_transformer_rdd
            .keyBy (_.id)
            .leftOuterJoin (
                power_transformer_end_rdd
                    .keyBy (_.PowerTransformer)
                    .groupByKey)
            .filter (heavy (voltages))
            .map (_._2._1)

        // get all transformer set secondary TopologicalIsland names
        val islands_trafos: RDD[(String, String)] = power_transformers
            .keyBy (_.id)
            .join (
                terminal_rdd
                    .filter (_.ACDCTerminal.sequenceNumber > 1)
                    .keyBy (_.ConductingEquipment))
            .map (x ⇒ (x._2._2.TopologicalNode, x._1)) // (nodeid, trafoid)
            .join (
                topological_node_rdd
                    .keyBy (_.id))
            .map (x ⇒ (x._1, (x._2._2.TopologicalIsland, x._2._1))) // (nodeid, (islandid, trafoid))
            .groupByKey.values // (islandid, trafoid)
            .flatMap (
                it =>
                {
                    val trafo_set_name = it.map (_._2).toArray.sortWith (_ < _).mkString ("_")
                    it.map (x => (x._1, trafo_set_name))
                }
            ) // (islandid, trafosetname)
        islands_trafos.persist (storage_level)
    }

    /**
     * Get the list of nodes in the transformer service area.
     *
     * @return An RDD suitable for GraphX initialization.
     */
    def nodes: RDD[(VertexId, VertexData)] =
    {
        topological_island_rdd.keyBy (_.id).leftOuterJoin (island_trafoset_rdd).values // (island, trafosetname)
            .map (x ⇒ (vertex_id (x._1.id), VertexData (x._2.orNull, x._1.id)))
    }

    /**
     * Get the list of edges in the transformer service area.
     *
     * @return An RDD suitable for GraphX initialization,
     */
    def edges: RDD[Edge[EdgeData]] =
    {
        // get voltages
        val voltages = voltage_rdd.map (v => (v.id, v.nominalVoltage * 1000.0)).collect.toMap // ToDo: fix this 1000V multiplier
        // get nodes by TopologicalIsland
        val members = topological_node_rdd.map (node ⇒ (node.id, (node.TopologicalIsland, voltages (node.BaseVoltage)))) // (nodeid, (islandid, volts))
        // get terminals by TopologicalIsland
        val terminals = terminal_rdd.keyBy (_.TopologicalNode).join (members).map (x ⇒ (x._2._2, x._2._1)) // ((islandid, volts), terminal)
        // get equipment with terminals in different islands as GraphX Edge objects
        conducting_equipment_rdd.keyBy (_.id).join (element_rdd.keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (equipmentid, element)
            .join (terminals.keyBy (_._2.ConductingEquipment)) // (equipmentid, (equipment, ((islandid, volts), terminal)))
            .groupByKey.values.filter (x ⇒ (x.size > 1) && !x.forall (y ⇒ y._2._1._1 == x.head._2._1._1)) // Iterable[(equipment, (islandid, terminal))]
            .map (
                x ⇒
                {
                    val equipment = x.head._1
                    val v1 = x.head._2._1._2
                    val v2 = x.tail.head._2._1._2
                    val connected = if (v1 > v2) isSameArea (x.head._1, v1, v2) else isSameArea (x.head._1, v2, v1) // ToDo: not really correct just to sort by magnitude
                    Edge (vertex_id (x.head._2._1._1), vertex_id (x.tail.head._2._1._1), EdgeData (equipment.id, connected))
                }
            ) // Edge[EdgeData]
    }

    /**
     * Trace through the connected TopologicalIsland graph to identify transformer service areas.
     *
     * Processing basically consists of propagating the area label to all connected islands,
     * where the area label is initially set by the list of nodes in the same island as the transformer secondary,
     * and "connected" is defined by switch status.
     *
     * @see island_trafoset_rdd
     * @see isSameArea (Element)
     * @return a processed graph with nodes (vertices) identified by transformer service area.
     */
    def identifyTransformerServiceAreas: Graph[VertexData, EdgeData] =
    {
        def vertex_program (id: VertexId, attr: VertexData, msg: VertexData): VertexData =
        {
            if (null == msg) // do nothing initially
            attr
                else
                {
                    if (debug && log.isDebugEnabled)
                        log.debug ("%s <-- %s".format (id, msg.toString))
                    msg
                }
        }

        def send_message (triplet: EdgeTriplet[VertexData, EdgeData]): Iterator[(VertexId, VertexData)] =
        {
            if (!triplet.attr.isConnected)
                Iterator.empty // send no message across an area boundary
            else
                if ((null != triplet.srcAttr.area_label) && (null == triplet.dstAttr.area_label))
                {
                    if (debug && log.isDebugEnabled)
                        log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.srcAttr.toString, triplet.dstAttr.toString))
                    Iterator ((triplet.dstId, VertexData (triplet.srcAttr.area_label, triplet.dstAttr.island_label)))
                }
                else
                    if ((null == triplet.srcAttr.area_label) && (null != triplet.dstAttr.area_label))
                    {
                        if (debug && log.isDebugEnabled)
                            log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.dstAttr.toString, triplet.srcAttr.toString))
                        Iterator ((triplet.srcId, VertexData (triplet.dstAttr.area_label, triplet.srcAttr.island_label)))
                    }
                    else
                        if ((null != triplet.srcAttr.area_label) && (null != triplet.dstAttr.area_label) && (triplet.srcAttr.area_label != triplet.dstAttr.area_label))
                        {
                            log.error ("""transformer service areas "%s" and "%s" are connected""".format (triplet.srcAttr.area_label, triplet.dstAttr.area_label))
                            Iterator.empty
                        }
                        else
                            Iterator.empty
        }

        def merge_message (a: VertexData, b: VertexData): VertexData =
        {
            if (debug && log.isDebugEnabled)
                log.debug ("%s >-< %s".format (a.toString, b.toString))
            if (a.area_label != b.area_label)
                log.error ("""island "%s" is serviced by two transformers (%s, %s)""".format (a.island_label, a.area_label, b.area_label))
            a
        }

        if (session.sparkContext.getCheckpointDir.isDefined)
        {
            edges.checkpoint ()
            nodes.checkpoint ()
        }

        // workaround for java.lang.ArrayIndexOutOfBoundsException: -1
        //        at org.apache.spark.graphx.util.collection.GraphXPrimitiveKeyOpenHashMap$mcJI$sp.apply$mcJI$sp(GraphXPrimitiveKeyOpenHashMap.scala:64)
        // save nodes and edges to HDFS and use the newly read RDD to make the graph
        val graph = if (session.sparkContext.getCheckpointDir.isDefined)
        {
            var root = session.sparkContext.getCheckpointDir.get
            if (!root.endsWith ("/"))
                root = root + "/"
            val magic = Random.nextInt (99999999)
            edges.saveAsObjectFile (root + "edges_" + magic)
            nodes.saveAsObjectFile (root + "nodes_" + magic)

            val _edges: RDD[Edge[EdgeData]] = session.sparkContext.objectFile (root + "edges_" + magic)
            val _nodes: RDD[(VertexId, VertexData)] = session.sparkContext.objectFile (root + "nodes_" + magic)
            Graph (_nodes, _edges, VertexData (), storage_level, storage_level)
        }
        else
        // traverse the graph with the Pregel algorithm
        // assigns the area_label (the source transformer set name) to all "connected" islands (joined by closed switches)
        // Note: on the first pass through the Pregel algorithm all nodes get a null message
            Graph (nodes, edges, VertexData (), storage_level, storage_level)
        graph.pregel[VertexData](null, 10000, EdgeDirection.Either)(vertex_program, send_message, merge_message).persist (storage_level)
    }

    def hasIslands: Boolean = !topological_island_rdd.isEmpty

    /**
     * Get a mapping between TopologicalIsland id and transformer set name.
     *
     * @return an RDD of (islandid, trafosetname) pairs for every island
     */
    def getTransformerServiceAreas: RDD[(String, String)] =
    {
        log.info ("tracing transformer service areas")
        val graph = identifyTransformerServiceAreas
        log.info ("mapping islands to transformer service areas")
        val candidates = graph.vertices.filter (null != _._2.area_label)
        val pairs = candidates.map (v ⇒ (v._2.island_label, v._2.area_label)).distinct // (islandid, areaid)
        val areas = island_trafoset_rdd.join (pairs).values.map (_.swap) // (areaid, trafosetname)
        pairs.map (_.swap).join (areas).values.distinct.persist (storage_level) // (islandid, trafosetname)
    }
}
