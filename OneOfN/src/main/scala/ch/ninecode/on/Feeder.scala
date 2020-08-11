package ch.ninecode.on

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
 * @param debug   flag to turn on debug output
 */
case class Feeder (session: SparkSession, storage: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER, debug: Boolean = false) extends CIMRDD
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
     *
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
        if (0 != (switch.bitfields (openMask / 32) & (1 << (openMask % 32))))
            !switch.open // open valid
        else
            if (0 != (switch.bitfields (normalOpenMask / 32) & (1 << (normalOpenMask % 32))))
                !switch.normalOpen
            else
                true
    }

    /**
     * Predicate for external equipment.
     *
     * @param equipment The equipment to test.
     * @return <code>true</code> if the equipment is not in a substation
     */
    def isExternal (equipment: ConductingEquipment): Boolean =
    {
        val eq = equipment.Equipment
        val in_station =
            eq.PowerSystemResource.PSRType == "PSRType_Substation" ||
                (eq.PowerSystemResource.PSRType == "PSRType_Unknown" && eq.EquipmentContainer != null)
        !in_station
    }

    /**
     * Method to determine if the element is an external switch.
     *
     * @param element The element to test.
     * @return <code>true</code> if the element is an external switch, <code>false</code> otherwise.
     */
    def isExternalSwitch (element: Element): Boolean =
    {
        element match
        {
            case s: Switch ⇒ isExternal (s.ConductingEquipment)
            case c: Cut ⇒ isExternal (c.Switch.ConductingEquipment)
            case d: Disconnector ⇒ isExternal (d.Switch.ConductingEquipment)
            case f: Fuse ⇒ isExternal (f.Switch.ConductingEquipment)
            case g: GroundDisconnector ⇒ isExternal (g.Switch.ConductingEquipment)
            case j: Jumper ⇒ isExternal (j.Switch.ConductingEquipment)
            case p: ProtectedSwitch ⇒ isExternal (p.Switch.ConductingEquipment)
            case s: Sectionaliser ⇒ isExternal (s.Switch.ConductingEquipment)
            case b: Breaker ⇒ isExternal (b.ProtectedSwitch.Switch.ConductingEquipment)
            case l: LoadBreakSwitch ⇒ isExternal (l.ProtectedSwitch.Switch.ConductingEquipment)
            case r: Recloser ⇒ isExternal (r.ProtectedSwitch.Switch.ConductingEquipment)
            case _ ⇒ false
        }
    }

    /**
     * Checks that the object is a Connector at a medium voltage with the right PSRType.
     *
     * @param medium_voltages the list of acceptable medium voltage mRID values
     * @param psr_types       the list of acceptable PSRType values
     * @param element         the element to check
     * @return
     */
    def isFeeder (medium_voltages: Array[String], psr_types: Array[String])(element: Element): Boolean =
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

    /**
     * The RDD of feeder objects - elements where isFeeder is true.
     *
     * @return The RDD of feeders (usually Connector).
     */
    def feeders: RDD[Element] =
    {
        // get the list of N5 voltages
        // ToDo: fix this 1000V multiplier
        val medium_voltages = getOrElse [BaseVoltage].filter (x ⇒ x.nominalVoltage > 1.0 && x.nominalVoltage < 50.0).map (_.id).collect

        // get the list of M5 level feeders in substations
        // ToDo: is it faster to use RDD[Connector] and join with RDD[Element] ?
        val ret = getOrElse [Element]("Elements").filter (isFeeder (medium_voltages, Array ("PSRType_Substation")))

        ret.persist (storage)
        ret.name = "Feeders"
        ret
    }

    /**
     * The RDD of node mRID to feeder objects mapping.
     *
     * @return The correspondence between node mRID and feeder.
     *         Note that only nodes that correspond to feeders are included in this RDD.
     */
    def feederNodes: RDD[(String, Element)] =
    {
        val ret = getOrElse [Terminal].map (x ⇒ (x.ConductingEquipment, x.TopologicalNode)).join (feeders.keyBy (_.id)).values // (feederid, nodeid)

        ret.persist (storage)
        ret.name = "FeederNodes"
        ret
    }

    /**
     * The RDD of island mRID to feeder objects mapping.
     *
     * @return The correspondence between island mRID and feeder.
     *         Note that only islands that correspond to feeders are included in this RDD.
     */
    def feederIslands: RDD[(String, Element)] =
    {
        val t = getOrElse [TopologicalNode].map (x ⇒ (x.id, x.TopologicalIsland)) // (nodeid, islandid)
        val ret = t.join (feederNodes).values // (islandid, Feeder)

        ret.persist (storage)
        ret.name = "FeederIslands"
        ret
    }

    /**
     * Get the mapping of station and feeder number to CIM element.
     *
     * Tries to parse the "Abgang nummer" and compose a descriptive .glm header.
     *
     * @return RDD of 4tuples (stationid, abgang#, description, feeder)
     */
    def feederStations: RDD[FeederMetadata] =
    {
        def parseNumber (description: String): String =
        {
            val trigger = "Abgang nummer "
            if (null == description)
                "0"
            else
            {
                val index = description.indexOf (trigger)
                if (-1 == index)
                    "0"
                else
                {
                    val array = description.substring (trigger.length).split (" ")
                    if (0 == array.length)
                        "0"
                    else
                        array (0)
                }
            }
        }

        def station_fn (x: (Connector, Element)): FeederMetadata =
        {
            val description = x._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.description
            val alias = x._1.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.aliasName
            val number = parseNumber (description)
            val header = "%s [%s]".format (description, alias)
            val feeder = x._1.asInstanceOf [Element]

            // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
            x._2 match
            {
                case station: Substation => FeederMetadata (station.id, number, header, feeder)
                case bay: Bay => FeederMetadata (bay.Substation, number, header, feeder)
                case level: VoltageLevel => FeederMetadata (level.Substation, number, header, feeder)
                case _ => throw new Exception ("unknown container type for %s".format (x._1))
            }
        }

        val connectors = feeders.map (_.asInstanceOf [Connector])
        val ret = connectors.keyBy (_.ConductingEquipment.Equipment.EquipmentContainer).join (getOrElse [Element]("Elements").keyBy (_.id)).values.map (station_fn)

        ret.persist (storage)
        ret.name = "feederStations"
        ret
    }

    /**
     * Get the list of edges.
     *
     * @return An RDD of Edge objects suitable for GraphX initialization.
     */
    def edges: RDD[Edge[EdgeData]] =
    {
        val equipment_islands = getOrElse [Terminal].keyBy (_.TopologicalNode).join (getOrElse [TopologicalNode].keyBy (_.id)).values
            .map (x ⇒ (x._1.ConductingEquipment, x._2.TopologicalIsland)).groupByKey // (equipmentid, [islandid])
        val equipment = getOrElse [ConductingEquipment].keyBy (_.id).join (getOrElse [Element]("Elements").keyBy (_.id)).map (x ⇒ (x._1, x._2._2)) // (equipmentid, element)
            .join (equipment_islands).values // (Element, [islandid])
        equipment.flatMap (
            x ⇒
            {
                val island1 = x._2.head
                if (isExternalSwitch (x._1))
                    for
                        {
                        island2 ← x._2.tail
                        if island2 != island1 // switches only on the boundary
                        edge = EdgeData (x._1.id, island1, island2)
                    }
                        yield Edge (vertex_id (edge.island1), vertex_id (edge.island2), edge)
                else
                    List ()
            }
        ).persist (storage) // Edge[EdgeData]
    }

    /**
     * Get the list of vertices.
     *
     * @return An RDD of VertexId and vertex data pairs suitable for GraphX initialization.
     */
    def vertices: RDD[(VertexId, VertexData)] =
    {
        val sources = feederIslands.groupByKey
        edges.flatMap (x ⇒ List ((x.attr.island1, x.attr.island1), (x.attr.island2, x.attr.island2))).leftOuterJoin (sources).values // (islandid, [feederid]?)
            .map (
                x ⇒
                {
                    val starting_feeders = x._2.map (y ⇒ y.map (_.id).toSet).getOrElse (Set [String]())
                    (vertex_id (x._1), VertexData (x._1, starting_feeders, starting_feeders))
                }
            ).persist (storage) // (vertexid, VertexData)
    }

    /**
     * Get a mapping between TopologicalNode id and feeder id.
     *
     * Trace through the connected TopologicalIsland graph to identify feeder service areas.
     * This produces a processed graph with vertices identified by all feeder service areas
     * that affect it (less than two open switches distant).
     *
     * Processing basically consists of propagating the area label to all connected islands,
     * where the area label is initially set by the list of nodes in the same island as feeder.
     *
     * @return an RDD of (nodeid, feederid) pairs for every TopologicalNode
     */
    def identifyFeeders: RDD[(String, String)] =
    {
        log.info ("identifying feeders")

        def vertex_program (id: VertexId, attr: VertexData, msg: VertexData): VertexData =
        {
            if (null == msg) // do nothing initially
            attr
                else
                msg
        }

        def send_message (triplet: EdgeTriplet[VertexData, EdgeData]): Iterator[(VertexId, VertexData)] =
        {
            var ret = List [(VertexId, VertexData)]()
            // the island on the source side can hop to the destination by closing the switch
            if (!triplet.srcAttr.sources.subsetOf (triplet.dstAttr.feeders))
            {
                if (debug && log.isDebugEnabled)
                    log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.srcAttr.sources.mkString (","), triplet.dstAttr.toString))
                val union = triplet.srcAttr.sources | triplet.dstAttr.feeders
                ret = ret :+ (triplet.dstId, VertexData (triplet.dstAttr.id, triplet.dstAttr.sources, union))
            }
            if (!triplet.dstAttr.sources.subsetOf (triplet.srcAttr.feeders))
            {
                if (debug && log.isDebugEnabled)
                    log.debug ("%s %s ---> %s".format (triplet.attr.id, triplet.dstAttr.sources.mkString (","), triplet.srcAttr.toString))
                val union = triplet.dstAttr.sources | triplet.srcAttr.feeders
                ret = ret :+ (triplet.srcId, VertexData (triplet.srcAttr.id, triplet.srcAttr.sources, union))
            }
            ret.toIterator
        }

        def merge_message (a: VertexData, b: VertexData): VertexData =
        {
            VertexData (a.id, a.sources, a.feeders | b.feeders)
        }

        // traverse the graph with the Pregel algorithm
        // assigns the minimum VertexId of all electrically identical islands
        // Note: on the first pass through the Pregel algorithm all nodes get a null message
        val graph: Graph[VertexData, EdgeData] = Graph (vertices, edges, VertexData (), storage, storage).cache
        val g = graph.pregel[VertexData](null, 10000, EdgeDirection.Either)(vertex_program, send_message, merge_message).cache

        // label every node (not just the ones on the boundary switches
        val island_feeders = g.vertices.map (x ⇒ (x._2.id, x._2.feeders)).filter (null != _._2) // (islandid, [feeders])
        getOrElse [TopologicalNode].keyBy (_.TopologicalIsland).join (island_feeders).values
            .flatMap (x ⇒ x._2.map (y ⇒ (x._1.id, y))) // (nodeid, feederid)
    }
}
