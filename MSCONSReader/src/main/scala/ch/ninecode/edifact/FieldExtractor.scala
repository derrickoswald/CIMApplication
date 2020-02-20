package ch.ninecode.edifact

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class FieldExtractor[T] extends Parsers
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    type Elem = Field
    override type Input = Reader[Field]

    /**
     * Simple alpha-numeric text parser with a size limit.
     *
     * @param size the maximum number of characters
     * @return the parsed string
     */
    def alphanumeric (size: Int): Parser[String] = acceptIf (x => x.text.length <= size)(field => s"$field > $size characters") ^^ (x => x.text)

    def alphanumeric_? (size: Int): Parser[Option[String]] = Parser
    {
        in =>
            if (in.atEnd)
                Success (None, in)
            else
            {
                val field = in.first
                val string = field.text
                if (string.length <= size)
                    Success (if (string.length > 0) Some (string) else None, in.rest)
                else
                    Failure (s"$field > $size characters", in)
            }
    }

    /**
     * Simple numeric text parser with a size limit.
     *
     * @param size the maximum number of characters
     * @return the parsed number
     */
    def numeric (size: Int): Parser[Double] = acceptIf (x => x.text.length <= size)(field => s"$field > $size characters") ^^ (x => x.text.toDouble)

    def numeric_? (size: Int): Parser[Option[Double]] = Parser
    {
        in =>
            if (in.atEnd)
                Success (None, in)
            else
            {
                val field = in.first
                val string = field.text
                if (string.length <= size)
                    Success (if (string.length > 0) Some (string.toDouble) else None, in.rest)
                else
                    Failure (s"$field > $size characters", in)
            }
    }

    /**
     * The subfield parser using Reader[Field].
     *
     * @param phrase the composite phrase of contents needed to satisfy correct parsing
     * @tparam Q the type of this parser
     * @return a parser for the subfields
     */
    def subfields[Q] (phrase: Parser[Q]): Parser[Q] =
        Parser
        {
            in =>
                def parse (p: Parser[Q], in: FieldListParser): ParseResult[Q] = p(in)

                if (!in.atEnd)
                    parse (phrase, FieldListParser (in.first.submembers)) match
                    {
                        case Success (r, rest) =>
                            if (rest.atEnd)
                                Success (r, in.rest)
                            else
                            {
                                log.error ("too many subfields")
                                Error ("too many subfields", in.rest)
                            }
                        case Failure (msg, rest) =>
                            log.error (s"bad subfields $msg")
                            Error ("bad subfields", in.rest)
                        case Error (msg, rest) =>
                            log.error (s"bad subfields: $msg")
                            Error ("bad subfields", in.rest)
                    }
                else
                    Failure ("no subfields", in)
        }

    /**
     * The field parser using Reader[Field].
     *
     * @param phrase the composite phrase of contents needed to satisfy correct parsing
     * @tparam Q the type of this parser
     * @return a parser for the fields
     */
    def fields[Q] (phrase: Parser[Q]): Parser[Q] =
        Parser
        {
            in =>
                def parse (p: Parser[Q], in: Input): ParseResult[Q] = p(in)

                parse (phrase, in) match
                {
                    case Success (r, rest) =>
                        if (rest.atEnd)
                            Success (r, rest)
                        else
                        {
                            log.error ("too many fields")
                            Error ("too many fields", rest)
                        }
                    case Failure (msg, rest) =>
                        log.error (s"bad fields $msg")
                        Error ("bad fields", rest)
                    case Error (msg, rest) =>
                        log.error (s"bad fields: $msg")
                        Error ("bad fields", rest)
                }
        }

    /**
     * The parser for the contents of the segment.
     *
     * @return a parser using Reader[Field] to produce T
     */
    def phrase: Parser[T]

    /**
     * Generic apply method using the phrase.
     *
     * @param segment the segment with the contents to apply to create T
     * @return the instantiated object
     */
    def apply (segment: Segment): T =
    {
        phrase (FieldListParser (segment.fields)) match
        {
            case Success (r, _) => r
            case Failure (msg, _) =>
                log.error (s"${segment.name} segment parse failure: $msg")
                val constructor = classOf[Class[T]].getConstructors()(0)
                constructor.newInstance().asInstanceOf[T]
            case Error (msg, _) =>
                log.error (s"${segment.name} segment parse error: $msg")
                val constructor = classOf[Class[T]].getConstructors()(0)
                constructor.newInstance().asInstanceOf[T]
        }
    }
}
