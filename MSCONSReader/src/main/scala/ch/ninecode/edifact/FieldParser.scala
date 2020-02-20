package ch.ninecode.edifact

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

case class FieldParser (una: UNA) extends Parsers
{
    type Elem = Field
    override type Input = Reader[Field]
    val parser: MemberParser = MemberParser (una)
    val members: parser.Parser[List[Field]] = parser.member.*
    implicit def field: Parser[Field] = new Parser[Field]
    {
        override def apply (in: Input): ParseResult[Field] =
        {
            if (in.atEnd)
                Failure ("EOF", in)
            else
            {
                val scanner = MemberScanner (in.first, una)
                members.apply (scanner) match
                {
                    case parser.Success (results: List[Field], _) =>
                        val field = Field (in.first.text, results)
                        Success (field, in.rest)
                    case parser.Failure (msg, _) =>
                        Failure (s"field members parse failure: $msg", in.rest)
                    case parser.Error (msg, _) =>
                        Error (s"field members parse error: $msg", in.rest)
                }
            }
        }
    }
    def parse[T](p: Parser[T], in: FieldScanner): ParseResult[T] = p(in)
}