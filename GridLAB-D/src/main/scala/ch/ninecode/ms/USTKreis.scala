package ch.ninecode.ms

import java.util.Calendar

import ch.ninecode.gl.{PreEdge, PreNode, TData}

case class USTKreis (
    transformers: Array[TData],
    nodes: Iterable[PreNode],
    edges: Iterable[Iterable[PreEdge]])
{
    lazy val start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04T12:00:00")
    lazy val finish_time: Calendar = start_time
    lazy val trafokreis_key: String = transformers.map(_.transformer.id).sortWith(_ < _).mkString("_")
    lazy val swing_node: String = transformers(0).node0
    lazy val swing_node_voltage: Double = transformers(0).voltage0 * 1000.0
}
