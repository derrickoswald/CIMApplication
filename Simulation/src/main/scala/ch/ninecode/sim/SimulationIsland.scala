package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.model.DiagramObject
import ch.ninecode.model.DiagramObjectPoint
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.net.Island
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.LineData
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.SwitchData
import ch.ninecode.net.TerminalPlus
import ch.ninecode.net.TransformerSet

class SimulationIsland (spark: SparkSession, storage_level: StorageLevel)
extends Island (spark, storage_level)
{
    override implicit val log: Logger = LoggerFactory.getLogger (getClass)

    lazy val world_points: RDD[(String, Iterable[PositionPoint])] = get[PositionPoint].groupBy (_.Location)
    lazy val schematic_points: RDD[(String, Iterable[DiagramObjectPoint])] = getOrElse[DiagramObject].keyBy (_.id).join (getOrElse[DiagramObjectPoint].groupBy (_.DiagramObject)).values.map (x => (x._1.IdentifiedObject_attr, x._2))

    def positionPointToCoordinates (points: Option[Iterable[PositionPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) =>
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p => (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ =>
                null
        }
    }

    def diagramObjectPointToCoordinates (points: Option[Iterable[DiagramObjectPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) =>
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p => (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ =>
                null
        }
    }

    override def node_maker (rdd: RDD[Iterable[TerminalPlus]]): RDD[(identifier, LoadFlowNode)] =
    {
        val just_one: RDD[TerminalPlus] = rdd.map (_.head)
        val with_psr: RDD[(TerminalPlus, PowerSystemResource)] = just_one.keyBy (_.element.id).join (get[PowerSystemResource].keyBy (_.id)).values

        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1.element.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (x => (x._1.id, SimulationNode (x._1.node.id, x._1.voltage, x._1.element.id, if (null != x._2) x._2(0) else null, if (null != x._3) x._3(0) else null)))
    }

    override def line_maker (rdd: RDD[(LineData, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        val with_psr = rdd.keyBy (_._1.lines.head.line.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.lines.head.line.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = GLMLineEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3, Iterable (), Iterable ()))
            }
        )
    }

    override def switch_maker (rdd: RDD[Iterable[(SwitchData, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        val switches = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (y => x.find (_._2._1 == y).get)
            }
        )
        val with_psr = switches.keyBy (_._1.switches.head.element.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.switches.head.element.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = GLMSwitchEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3, Iterable (), Iterable ()))
            }
        )
    }

    override def transformer_maker (rdd: RDD[Iterable[(TransformerSet, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        val transformers = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (y => x.find (_._2._1 == y).get)
            }
        )
        val with_psr = transformers.keyBy (_._1.transformers.head.transformer.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.transformers.head.transformer.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = GLMTransformerEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3, Iterable (), Iterable ()))
            }
        )
    }
}
