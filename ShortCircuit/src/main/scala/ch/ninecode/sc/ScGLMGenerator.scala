package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class ScGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    area: SimulationTransformerServiceArea) extends GLMGenerator (one_phase, date_format)
{

    override def name: String = area.name

    override def directory: String = area.directory

    override def start_time: Calendar = area.start_time

    override def edge_groups: Iterable[Iterable[GLMEdge]] = area.edges

    override def transformers: Array[TransformerSet] = Array(area.transformer)

    override def swing_nodes: Iterable[GLMNode] = area.swing_nodes

    override def finish_time: Calendar = area.finish_time

    override def nodes: Iterable[GLMNode] = area.nodes

    override def extra: Iterable[String] = List ("")
}