package ch.ninecode.edifact

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

case class SegmentParser (una: UNA) extends Parsers
{
    type Elem = String
    override type Input = Reader[String]
    val parser: FieldParser = FieldParser (una)

    implicit def segment: Parser[Segment] = new Parser[Segment]
    {
        override def apply (in: Input): ParseResult[Segment] =
        {
            if (in.atEnd)
                Failure ("EOF", in)
            else
            {
                val string = in.first
                val sep = string.indexOf (una.data_element_separator)
                val name = if (sep > 0)
                    string.substring (0, sep)
                else
                    string // see for example UIT INTERACTIVE MESSAGE TRAILER that has no mandatory fields
                if ((name.length == 3) && name.forall (_.isUpper))
                    {
                        val body = string.substring (sep + 1)
                        val seg = Segment (name, body)
                        val scanner = FieldScanner (seg, una)
                        val fields = parser.field.*
                        fields.apply (scanner) match
                        {
                            case parser.Success (results: List[Field], _) =>
                                val segment = Segment (name, body, results)
                                Success (segment, in.rest)
                            case parser.Failure (msg, _) =>
                                Failure (s"field parse failure: $msg", in.rest)
                            case parser.Error (msg, _) =>
                                Error (s"fields parse error: $msg", in.rest)
                        }
                    }
                    else
                        Error (s"illegal segment name (${name.substring (0, Math.min (20, name.length))})", in.rest)
            }
        }
    }
    def parse[T](p: Parser[T], in: SegmentScanner): ParseResult[T] = p(in)
}
