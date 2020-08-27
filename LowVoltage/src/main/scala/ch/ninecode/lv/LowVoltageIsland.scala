package ch.ninecode.lv

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.model.EnergyConsumer
import ch.ninecode.net.Island
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.LineData
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.SwitchData
import ch.ninecode.net.TerminalPlus
import ch.ninecode.net.TransformerSet

/**
 * A topological island utility class to get edges and nodes.
 *
 * This island processor differs from the base class in that it returns
 * LowVoltageNode and GLMEdge objects suitable for use in creating
 * GridLAB-D model (.glm) files.
 *
 * @param spark         The current spark session.
 * @param storage_level The storage level to use in persisting the edges and nodes.
 */
class LowVoltageIsland (spark: SparkSession, storage_level: StorageLevel)
    extends Island (spark, storage_level)
{
    override implicit val log: Logger = LoggerFactory.getLogger (getClass)

    def getOne (terminals: Iterable[TerminalPlus]): TerminalPlus =
    {
        val sorted = terminals.toArray.sortBy (_.element.id)
        sorted (0)
    }

    def toLowVoltageNode (terminal: TerminalPlus): LowVoltageNode =
    {
        val energyConsumer = terminal.element match
        {
            case _: EnergyConsumer => true
            case _ => false
        }
        LowVoltageNode (terminal.node.id, terminal.voltage, terminal.element.id, energyConsumer)
    }

    override def node_maker (rdd: RDD[Iterable[TerminalPlus]]): RDD[(identifier, LoadFlowNode)] =
    {
        rdd.map (getOne).map (x => (x.id, toLowVoltageNode (x)))
    }

    def toLineEdge (line: LineData): GLMLineEdge =
    {
        GLMLineEdge (line)
    }

    override def line_maker (rdd: RDD[(LineData, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.map (x => (x._2._1, toLineEdge (x._1)))
    }

    def toSwitchEdge (switch: SwitchData): GLMSwitchEdge =
    {
        GLMSwitchEdge (switch)
    }

    override def switch_maker (rdd: RDD[Iterable[(SwitchData, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        // get two switch items if the switch separates two islands (distinct identifiers)
        val switches: RDD[(SwitchData, (identifier, LoadFlowNode))] = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.flatMap (y => x.find (_._2._1 == y))
            }
        )
        switches.map (x => (x._2._1, toSwitchEdge (x._1)))
    }

    def toTransformerEdge (transformer: TransformerSet): GLMTransformerEdge =
    {
        GLMTransformerEdge (transformer)
    }

    override def transformer_maker (rdd: RDD[Iterable[(TransformerSet, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        // get two transformer items if the transformer separates two islands (distinct identifiers)
        val transformers = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.flatMap (y => x.find (_._2._1 == y))
            }
        )
        transformers.map (x => (x._2._1, toTransformerEdge (x._1)))
    }
}