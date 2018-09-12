package ch.ninecode.mv

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

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.Bay
import ch.ninecode.model.Breaker
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Connector
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Substation
import ch.ninecode.model.Switch
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.VoltageLevel

/**
 * Identify the nodes fed by each feeder.
 *
 * Use GraphX to label nodes connected together &mdash;
 * basically everything downstream of a feeder (Connector).
 *
 * @param session the Spark session object
 * @param debug flag to turn on debug output
 */
case class Feeder (session: SparkSession, storage: StorageLevel, debug: Boolean = false) extends CIMRDD with Serializable
{
    if (debug)
        org.apache.log4j.LogManager.getLogger (getClass).setLevel (org.apache.log4j.Level.DEBUG)
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

    // is the switch out in the field and not in a containing station
    def fieldSwitch (switch: Switch): Boolean =
    {
        switch.ConductingEquipment.Equipment.EquipmentContainer == null
    }

    // is the cable an external cable
    def externalCable (cable: ACLineSegment): Boolean =
    {
        // cable.Conductor.ConductingEquipment.Equipment.EquipmentContainer == null (those without a container) doesn't work: the first cable out of a station also has the station as a container
        // cable.Conductor.len != 0.0 is our best guess (nis_el_int_connection is given a length of zero)
        // could also use !cable.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName.contains ("nis_el_int_connection")
        // could also use Complex (cable.r, cable.x) == Complex (0)
        cable.Conductor.len != 0.0
    }

    /**
     * Method to determine if both nodes of an element are in the same feeder.
     *
     * @param element The element to test.
     * @return <code>true</code> if the element is effectively one node, <code>false</code> otherwise.
     */
    def isSameFeeder (element: Element): Boolean =
    {
        element match
        {
            // trace only external cables
            case cable: ACLineSegment ⇒ externalCable (cable)
            // switches have to be closed and not be in a substation
            case switch: Switch ⇒ switchClosed (switch) && fieldSwitch (switch)
            case cut: Cut ⇒ switchClosed (cut.Switch) && fieldSwitch (cut.Switch)
            case disconnector: Disconnector ⇒ switchClosed (disconnector.Switch) && fieldSwitch (disconnector.Switch)
            case fuse: Fuse ⇒ switchClosed (fuse.Switch) && fieldSwitch (fuse.Switch)
            case gd: GroundDisconnector ⇒ switchClosed (gd.Switch) && fieldSwitch (gd.Switch)
            case jumper: Jumper ⇒ switchClosed (jumper.Switch) && fieldSwitch (jumper.Switch)
            case ps: ProtectedSwitch ⇒ switchClosed (ps.Switch) && fieldSwitch (ps.Switch)
            case sectionaliser: Sectionaliser ⇒ switchClosed (sectionaliser.Switch) && fieldSwitch (sectionaliser.Switch)
            case breaker: Breaker ⇒ switchClosed (breaker.ProtectedSwitch.Switch) && fieldSwitch (breaker.ProtectedSwitch.Switch)
            case lbs: LoadBreakSwitch ⇒ switchClosed (lbs.ProtectedSwitch.Switch) && fieldSwitch (lbs.ProtectedSwitch.Switch)
            case recloser: Recloser ⇒ switchClosed (recloser.ProtectedSwitch.Switch) && fieldSwitch (recloser.ProtectedSwitch.Switch)
            case _: PowerTransformer ⇒ false
            case _ ⇒
                log.warn ("feeder processor encountered edge with unhandled class '" + element.getClass.getName +"', assumed same feeder")
                true
        }
    }

    /**
     * Checks that the object is a Connector at a medium voltage with the right PSRType
     * @param medium_voltages the list of acceptable medium voltage mRID values
     * @param psr_types the list of acceptable PSRType values
     * @param element the element to check
     * @return
     */
    def isFeeder (medium_voltages: Array[String], psr_types: Array[String]) (element: Element): Boolean =
    {
        element match
        {
            case c: Connector ⇒
                medium_voltages.contains (c.ConductingEquipment.BaseVoltage) &&
                psr_types.contains (c.ConductingEquipment.Equipment.PowerSystemResource.PSRType)
            case _ ⇒
                false
        }
    }

    def feeders: RDD[Element] =
    {
        // get the list of M5 voltages
        // ToDo: fix this 1000V multiplier
        val medium_voltages = getOrElse[BaseVoltage].filter (x ⇒ x.nominalVoltage > 1.0 && x.nominalVoltage < 50.0).map (_.id).collect

        // get the list of M5 level feeders in substations
        // ToDo: is it faster to use RDD[Connector] and join with RDD[Element] ?
        val ret = getOrElse[Element]("Elements").filter (isFeeder (medium_voltages, Array ("PSRType_Substation")))

        ret.persist (storage)
        ret.name = "Feeders"
        ret
    }

    def feederNodes: RDD[(String, Element)] =
    {
        val ret = getOrElse[Terminal].map (x ⇒ (x.ConductingEquipment, x.TopologicalNode)).join (feeders.keyBy (_.id)).values

        ret.persist (storage)
        ret.name = "FeederNodes"
        ret
    }

    def feederStations: RDD[(String, Int, String, Element)] =
    {
        def parseNumber (description: String): Int =
        {
            val trigger = "Abgang nummer "
            if (null == description)
                0
            else
            {
                val index = description.indexOf (trigger)
                if (-1 == index)
                    0
                else
                {
                    val array = description.substring (trigger.length).split (" ")
                    if (0 == array.length)
                        0
                    else
                        array(0).toInt
                }
            }
        }

        // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: (Connector, Element)): (String, Int, String, Element) =
        {
            val description = x._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description
            val alias = x._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName
            val number = parseNumber (description)
            val header = "%s [%s]".format (description, alias)

            val feeder = x._1.asInstanceOf[Element]
            x._2 match
            {
                case station: Substation => (station.id, number, header, feeder)
                case bay: Bay => (bay.Substation, number, header, feeder)
                case level: VoltageLevel => (level.Substation, number, header, feeder)
                case _ => throw new Exception ("unknown container type for %s".format (x._1))
            }
        }

        val connectors = feeders.map (_.asInstanceOf[Connector])
        val ret = connectors.keyBy (_.ConductingEquipment.Equipment.EquipmentContainer).join (getOrElse[Element]("Elements").keyBy (_.id)).values.map (station_fn)

        ret.persist (storage)
        ret.name = "feederStations"
        ret
    }

    /**
     * Vertex data for feeder processing.
     *
     * @param id the mRID of the node
     * @param feeders the feeders connected to this node
     */
    case class VertexData (id: String, feeders: Set[String]) extends Serializable

    /**
     * Edge data for feeder processing.
     *
     * @param id the mRID of the edge
     * @param isConnected <code>true</code> if there is a connection between the nodes, i.e. a closed switch,
     *        which means the nodes are part of the same feeder
     */
    case class EdgeData (id: String, isConnected: Boolean) extends Serializable

    /**
     * Get the list of nodes.
     *
     * @return An RDD of VertexId and data pairs suitable for GraphX initialization.
     */
    def nodes: RDD[(VertexId, VertexData)] =
    {
        getOrElse[TopologicalNode].keyBy (_.id).leftOuterJoin (feederNodes).values // (Node, Connector?)
            .map (x ⇒ (vertex_id (x._1.id), VertexData (x._1.id, x._2.map (y ⇒ Set (y.id)).orNull))) // (VertexId, VertexData)
    }

    /**
     * Get the list of edges.
     *
     * @return An RDD of Edge objects suitable for GraphX initialization.
     */
    def edges: RDD[Edge[EdgeData]] =
    {
        val terminals = getOrElse[Terminal]
        getOrElse[ConductingEquipment].keyBy (_.id).join (getOrElse[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (equipmentid, element)
            .join (getOrElse[Terminal].groupBy (_.ConductingEquipment)).values // (Element, [Terminal])
            .flatMap (
                x ⇒
                {
                    if ((x._2.size == 2) // ToDo: handle 3 terminal devices
                        && (null != x._2.head.TopologicalNode)
                        && (null != x._2.tail.head.TopologicalNode))
                    {
                        val edge = EdgeData (x._1.id, isSameFeeder (x._1))
                        List (Edge (vertex_id (x._2.head.TopologicalNode), vertex_id (x._2.tail.head.TopologicalNode), edge))
                    }
                    else
                        List ()
                }
            ) // Edge[EdgeData]
    }

    /**
     * Trace through the connected TopologicalIsland graph to identify feeder service areas.
     *
     * Processing basically consists of propagating the area label to all connected islands,
     * where the area label is initially set by the list of nodes in the same island as feeder,
     * and "connected" is defined by switch status.
     *
     * @see island_feeder_rdd
     * @see isSameArea (Element)
     *
     * @return a processed graph with nodes (vertices) identified by feeder service area.
     */
    def identifyFeeders: Graph[VertexData, EdgeData] =
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
                Iterator.empty // send no message across a feeder boundary
            else
                if ((null != triplet.srcAttr.feeders) && (null == triplet.dstAttr.feeders))
                {
                    if (debug && log.isDebugEnabled)
                        log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.srcAttr.toString, triplet.dstAttr.toString))
                    Iterator ((triplet.dstId, VertexData (triplet.dstAttr.id, triplet.srcAttr.feeders)))
                }
                else if ((null == triplet.srcAttr.feeders) && (null != triplet.dstAttr.feeders))
                {
                    if (debug && log.isDebugEnabled)
                        log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.dstAttr.toString, triplet.srcAttr.toString))
                    Iterator ((triplet.srcId, VertexData (triplet.srcAttr.id, triplet.dstAttr.feeders)))
                }
                else if ((null != triplet.srcAttr.feeders) && (null != triplet.dstAttr.feeders) && (triplet.srcAttr.feeders != triplet.dstAttr.feeders))
                {
                    val union = triplet.srcAttr.feeders | triplet.dstAttr.feeders
                    Iterator ((triplet.srcId, VertexData (triplet.srcAttr.id, union)), (triplet.dstId, VertexData (triplet.dstAttr.id, union)))
                }
                else
                    Iterator.empty
        }

        def merge_message (a: VertexData, b: VertexData): VertexData =
        {
            VertexData (a.id, a.feeders | b.feeders)
        }

        // traverse the graph with the Pregel algorithm
        // assigns the minimum VertexId of all electrically identical islands
        // Note: on the first pass through the Pregel algorithm all nodes get a null message
        val graph: Graph[VertexData, EdgeData] = Graph (nodes, edges).cache ()
        graph.pregel[VertexData] (null, 10000, EdgeDirection.Either) (vertex_program, send_message, merge_message).cache
    }

    /**
     * Get a mapping between TopologicalNode id and feeder id.
     *
     * @return an RDD of (nodeid, feederid) pairs for every node
     */
    def getFeederMap: RDD[(String, String)] =
    {
        log.info ("tracing feeders")
        val graph = identifyFeeders
        graph.vertices.flatMap (v ⇒ if (null != v._2.feeders) v._2.feeders.map (x ⇒ (v._2.id, x)) else List()) // (nodeid, feederid)
    }
}
