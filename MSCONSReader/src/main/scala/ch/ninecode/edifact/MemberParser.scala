package ch.ninecode.edifact

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

case class MemberParser (una: UNA) extends Parsers
{
    type Elem = Field
    override type Input = Reader[Field]
    implicit def member: Parser[Field] = new Parser[Field]
    {
        override def apply (in: Input): ParseResult[Field] =
        {
            if (in.atEnd)
                Failure ("EOF", in)
            else
                Success (in.first, in.rest)
        }
    }
    def parse[T](p: Parser[T], in: MemberScanner): ParseResult[T] = p(in)
}