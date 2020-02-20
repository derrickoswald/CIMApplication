package ch.ninecode.mscons

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Reader

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.edifact.Segment

trait MSCONSMessage extends Parsers
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    type Elem = Segment
    override type Input = Reader[Segment]

    def expect[Q] (name: String, constructor: Segment => Q): Parser[Q] =
        acceptIf (x => x.name == name)(segment => s"expected $name, got ${segment.name}") ^^
            (x => constructor (x))

    /** A parser generator for a maximum specified number of repetitions.
     *
     *  `repAtMostN(n, p)` uses `p` at most `n` times to parse the input
     *  (the result is a `List` of the consecutive results of `p`).
     *
     * @param p   a `Parser` that is to be applied successively to the input
     * @param num the maximum number of times `p` can succeed
     * @param one if <code>true</code>, at least one `p` must succeed
     * @return    A parser that returns a list of results produced by repeatedly applying `p` to the input
     *        (and that only succeeds if `p` matches at most `n` times).
     */
    def repAtMostN[T] (num: Int, one: Boolean, p: => Parser[T]): Parser[List[T]] =
    {
        if (num == 0)
            success (Nil)
        else
            Parser
            {
                in =>
                    val elems = new ListBuffer[T]
                    val p0 = p    // avoid repeatedly re-evaluating by-name parser

                    @tailrec
                    def applyp (in0: Input): ParseResult[List[T]] =
                        p0 (in0) match
                        {
                            case Success (x, rest) =>
                                if (elems.length >= num)
                                    Error (s"$num repetitions exceeded ${p0.toString}", in0)
                                else
                                {
                                    elems += x
                                    applyp (rest)
                                }
                            case e @ Error (_, _)  => e  // still have to propagate error
                            case f @ Failure (_, _) => if (one && elems.isEmpty) f else Success (elems.toList, in0)
                        }

                    applyp (in)
            }
    }
}
