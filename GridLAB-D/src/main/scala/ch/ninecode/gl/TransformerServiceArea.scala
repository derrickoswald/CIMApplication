package ch.ninecode.gl

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

/**
 * Identify the islands in each transformer service area.
 *
 * Use GraphX to label islands connected together &mdash;
 * basically everything not on the other side of an open switch or transformer core.
 * Identifies island clusters sharing a (group of ganged) transformer(s) low voltage winding.
 *
 * @param session the Spark session object
 * @param debug flag to turn on debug output
 */
case class TransformerServiceArea (session: SparkSession, debug: Boolean = false)
    extends CIMRDD
        with Serializable
{
    import session.sqlContext.implicits._

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

    /**
     * Compute the vertex id.
     * @param string The CIM mRID.
     * @return the node id (similar to the hash code of the mRID)
     */
    def vertex_id (string: String): VertexId =
    {
        var h = 2166136261l
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
        if (0 != (switch.bitfields(openMask / 32) & (1 << (openMask % 32))))
            !switch.open // open valid
        else if (0 != (switch.bitfields(normalOpenMask / 32) & (1 << (normalOpenMask % 32))))
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
    def isSameArea (element: Element): Boolean =
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
            case _: PowerTransformer ⇒ false
            case _ ⇒
                log.warn ("transformer service area processor encountered edge with unhandled class '" + element.getClass.getName +"', assumed same transformer service area")
                true
        }
    }

    /**
     * Vertex data for transformer service area processing.
     *
     * @param area_label a user friendly label for the area
     * @param island the minimum (hash code) of all connected ConnectivityNode (single topological island)
     * @param island_label a user friendly label for the island
     */
    case class VertexData (var area_label: String = "", var island: VertexId = Long.MaxValue, var island_label: String = "") extends Serializable

    /**
     * Edge data for transformer service area processing.
     *
     * @param isConnected <code>true</code> if there is a connection between the islands, i.e. a closed switch,
     *        which means the islands are in the same transformer service area
     */
    case class EdgeData (id: String, isConnected: Boolean) extends Serializable

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
     * Where there are ganged transformers with connected secondary windings, the PowerTransformer.id
     * is the concatenation of the sorted PowerTransformer.id values of all connected transformers,
     * e.g. TRA1234_TRA5678.
     * Any TopologicalIsland without a transformer secondary node is not included.
     */
    def island_trafoset_rdd: RDD[(String, String)] =
    {
        // get all transformer set secondary TopologicalIsland names
        val islands_trafos = getOrElse[PowerTransformer]
            .keyBy (_.id)
            .join (
                getOrElse[Terminal]
                    .filter (_.ACDCTerminal.sequenceNumber == 2)
                    .keyBy (_.ConductingEquipment))
            .map (x ⇒ (x._2._2.TopologicalNode, x._1)) // (nodeid, trafoid)
            .join (
            getOrElse[TopologicalNode]
                .keyBy (_.id))
            .map (x ⇒ (x._2._2.TopologicalIsland, x._2._1)) // (islandid, trafoid)
            .groupByKey.mapValues (_.toArray.sortWith (_ < _).mkString ("_")).cache // (islandid, trafosetname) // ToDo: multiple transformers in the same island that aren't ganged?
        islands_trafos.cache
    }

    /**
     * Get the list of nodes in the transformer service area.
     *
     * @return An RDD suitable for GraphX initialization.
     */
    def nodes: RDD[(VertexId, VertexData)] =
    {
        getOrElse[TopologicalIsland].keyBy (_.id).leftOuterJoin (island_trafoset_rdd).values // (island, trafosetname)
            .map (
            x ⇒
            {
                val hash = vertex_id (x._1.id)
                val data = x._2 match
                {
                    case Some (trafosetname) ⇒ VertexData (trafosetname, hash, x._1.id)
                    case None ⇒ VertexData (null, hash, x._1.id)
                }
                (hash, data)
            }
        )
    }

    /**
     * Get the list of edges in the transformer service area.
     *
     * @return An RDD suitable for GraphX initialization,
     */
    def edges: RDD[Edge[EdgeData]] =
    {
        // get nodes by TopologicalIsland
        val members = getOrElse[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland)) // (nodeid, islandid)
        // get terminals by TopologicalIsland
        val terminals = getOrElse[Terminal].keyBy (_.TopologicalNode).join (members).map (x ⇒ (x._2._2, x._2._1)) // (islandid, terminal)
        // get equipment with terminals in different islands as GraphX Edge objects
        getOrElse[ConductingEquipment].keyBy (_.id).join (getOrElse[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (equipmentid, element)
            .join (terminals.keyBy (_._2.ConductingEquipment)) // (equipmentid, (equipment, (islandid, terminal)))
            .groupByKey.values.filter (x ⇒ (x.size > 1) && !x.forall (y ⇒ y._2._1 == x.head._2._1)) // Iterable[(equipment, (islandid, terminal))]
            .map (
                x ⇒
                {
                    val equipment = x.head._1
                    val connected = isSameArea (x.head._1)
                    Edge (vertex_id (x.head._2._1), vertex_id (x.tail.head._2._1), EdgeData (x.head._1.id, connected))
                }) // Edge[EdgeData]
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
     *
     * @return a processed graph with nodes (vertices) identified by transformer service area.
     */
    def identifyTransformerServiceAreas: Graph[VertexData, EdgeData] =
    {
        def vertex_program (id: VertexId, attr: VertexData, msg: VertexData): VertexData =
        {
            if (null == msg) // do nothing initially
                attr
            else
                msg
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
                    Iterator ((triplet.dstId, VertexData (triplet.srcAttr.area_label, triplet.dstAttr.island, triplet.dstAttr.island_label)))
                }
                else if ((null == triplet.srcAttr.area_label) && (null != triplet.dstAttr.area_label))
                {
                    if (debug && log.isDebugEnabled)
                        log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.dstAttr.toString, triplet.srcAttr.toString))
                    Iterator ((triplet.srcId, VertexData (triplet.dstAttr.area_label, triplet.srcAttr.island, triplet.srcAttr.island_label)))
                }
                else if ((null != triplet.srcAttr.area_label) && (null != triplet.dstAttr.area_label) && (triplet.srcAttr.area_label != triplet.dstAttr.area_label))
                {
                    log.error ("""transformer service areas "%s" and "%s" are connected""".format (triplet.srcAttr.area_label, triplet.dstAttr.area_label))
                    Iterator.empty
                }
                else
                    Iterator.empty
        }

        def merge_message (a: VertexData, b: VertexData): VertexData =
        {
            if (a.area_label != b.area_label)
                log.error ("""island "%s" is serviced by two transformers (%s, %s)""".format (a.island_label, a.area_label, b.area_label))
            a
        }

        // traverse the graph with the Pregel algorithm
        // assigns the minimum VertexId of all electrically identical islands
        // Note: on the first pass through the Pregel algorithm all nodes get a null message
        val graph: Graph[VertexData, EdgeData] = Graph (nodes, edges).cache ()
        graph.pregel[VertexData] (null, 10000, EdgeDirection.Either) (vertex_program, send_message, merge_message).cache
    }

    def hasIslands: Boolean = !getOrElse[TopologicalIsland].isEmpty

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
        val pairs = graph.vertices.map (v ⇒ (v._2.island_label, v._2.area_label)) // (islandid, areaid)
        val areas = island_trafoset_rdd.join (pairs).values.map (_.swap) // (areaid, trafosetname)
        pairs.map (_.swap).join (areas).values.cache // (islandid, trafosetname)
    }
}
