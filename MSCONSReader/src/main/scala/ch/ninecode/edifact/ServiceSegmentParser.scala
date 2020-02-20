package ch.ninecode.edifact

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

object ServiceSegmentParser extends Parsers
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    type Elem = Segment
    override type Input = Reader[Segment]

    def unb: Parser[UNB] = acceptIf (_.name =="UNB")(seg => s"unexpected ${seg.name}") ^^ (x => UNB (x.fields))

    def unh: Parser[UNH] = acceptIf (_.name =="UNH")(seg => s"unexpected ${seg.name}") ^^ (x => UNH (x.fields))

    val header = unb ~ unh
    def parse[T](p: Parser[T], in: SegmentListParser): ParseResult[T] = p(in)

    def read[ServiceSegments](in: List[Segment]): ParseResult[ch.ninecode.edifact.ServiceSegments] =
    {
        val x = parse (header, SegmentListParser (in))
        x match
        {
            case Success (r, rest) =>
                Success (ServiceSegments (r._1, r._2), rest)
            case Failure (msg, rest) =>
                Failure (s"service segments parse failure: $msg", rest)
            case Error (msg, rest) =>
                Error (s"service segments parse error: $msg", rest)
        }
    }
}
