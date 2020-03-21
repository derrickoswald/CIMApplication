package ch.ninecode.sc

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.model.BusbarSection
import ch.ninecode.model.Element
import ch.ninecode.model.EnergyConsumer
import ch.ninecode.net.Island
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.LineData
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.SwitchData
import ch.ninecode.net.TerminalPlus
import ch.ninecode.net.TransformerSet

class ShortCircuitIsland (session: SparkSession, storageLevel: StorageLevel) extends Island (session, storageLevel)
{
    override implicit val log: Logger = LoggerFactory.getLogger (getClass)

    override def node_maker (rdd: RDD[Iterable[TerminalPlus]]): RDD[(identifier, LoadFlowNode)] =
    {
        def house (element: Element): Boolean = element match
        {
            case _: EnergyConsumer => true
            case _ => false
        }

        def busbar (element: Element): Boolean = element match
        {
            case _: BusbarSection => true
            case _ => false
        }

        rdd.map (
            parts =>
            {
                val has = parts.find (h => house (h.element))
                val bus = parts.find (b => busbar (b.element))
                val ele: Element = has.getOrElse (bus.getOrElse (parts.head)).element
                (parts.head.id, SimulationNode (parts.head.node.id, parts.head.voltage, ele.id, house (ele), busbar (ele)))
            }
        )
    }

    /**
     * Create edges from ACLineSegment LineData.
     *
     * @param rdd the rdd of a pair of lines and a transformer service area & a node
     * @return an RDD of edges to be used in load flow simulation
     */
    override def line_maker (rdd: RDD[(LineData, (identifier, LoadFlowNode))]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.map (x => (x._2._1, new GLMLineEdge (x._1)))
    }

    /**
     * Create edges from Switch SwitchData.
     *
     * @param rdd the rdd of switches and the transformer service area & node pairs it belongs to
     * @return an RDD of edges to be used in load flow simulation
     */
    override def switch_maker (rdd: RDD[Iterable[(SwitchData, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toSet
                unique_identifiers.map (
                    y =>
                    {
                        val switch = x.find (_._2._1 == y).get
                        (switch._2._1, new GLMSwitchEdge (switch._1))
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
    override def transformer_maker (rdd: RDD[Iterable[(TransformerSet, (identifier, LoadFlowNode))]]): RDD[(identifier, LoadFlowEdge)] =
    {
        rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (
                    y =>
                    {
                        val transformer = x.find (_._2._1 == y).get
                        (transformer._2._1, new GLMTransformerEdge (transformer._1))
                    }
                )
            }
        )
    }

}

