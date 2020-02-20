package ch.ninecode.edifact

import scala.util.parsing.input.Position
import scala.util.parsing.input.Reader

case class FieldListParser (list: List[Field]) extends Reader[Field]
{
    override def first: Field = list.head

    override def rest: Reader[Field] = FieldListParser (list.tail)

    override def pos: Position = new Position { val line = 0; val column = 0; val lineContents: String = "" }

    override def atEnd: Boolean = list.isEmpty
}
