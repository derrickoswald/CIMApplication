package ch.ninecode.edifact

import scala.util.parsing.input.Position
import scala.util.parsing.input.Reader

case class SegmentListParser (list: List[Segment]) extends Reader[Segment]
{
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    override def first: Segment = list.head

    override def rest: Reader[Segment] = SegmentListParser (list.drop (1))

    override def pos: Position = new Position { val line = 0; val column = 0; val lineContents: String = "" }

    override def atEnd: Boolean = list.isEmpty
}
