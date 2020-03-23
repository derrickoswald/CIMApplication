package ch.ninecode.edifact

import java.nio.ByteBuffer

import scala.util.parsing.input.Position
import scala.util.parsing.input.Reader

/**
 * `SegmentScanner` is essentially¹ a parser that produces `String`s
 * from a `ByteBuffer`. The strings it produces are typically
 * passed to parsers in `SegmentParsers`.
 *
 * @see https://github.com/scala/scala-parser-combinators/blob/v1.0.7/shared/src/main/scala/scala/util/parsing/combinator/lexical/Scanners.scala
 * @param buffer the byte buffer
 * @param una EDIFACT UNA segment values with special character definitions
 */
case class SegmentScanner (buffer: ByteBuffer, una: UNA) extends Reader[String]
{
    /**
     * Scan a segment.
     *
     * @return the contents of the segment as a String, or "" if there are no more bytes to read
     */
    def scan: String =
    {
        var skip = false
        var stop = false
        var start = buffer.position
        var size = 0
        var c = 0
        var intervals = List[(Int,Int)] () // start and size of each piece of the segment

        while ((0 < buffer.remaining) && !stop)
        {
            c = buffer.get.asInstanceOf[Int]
            size += 1
            stop = false
            if (skip)
            {
                if (c == una.segment_terminator)
                {
                    intervals = intervals :+ ((start, size - 2))
                    start = start + size - 1
                    size = 1
                }
                skip = false
            }
            else
            if (c == una.segment_terminator)
                stop = true
            else
            if (c == una.release_character)
                skip = true
        }

        intervals = intervals :+ ((start, if (stop) size - 1 else size))

        // copy the data piece by piece to a new array
        val bytes = new Array[Byte] (intervals.map (_._2).sum)
        var offset = 0
        intervals.foreach (
            item =>
            {
                buffer.position (item._1)
                buffer.get (bytes, offset, item._2)
                offset += item._2
            }
        )
        if (stop)
            buffer.get // discard terminator
        new String (bytes)
    }

    /**
     * Returns the first element of the reader.
     */
    lazy val first: String = scan

    /** Returns an abstract reader consisting of all elements except the first
     *
     * @return If `atEnd` is `true`, the result will be `this`;
     *         otherwise, it's a `Reader` containing more elements.
     */
    def rest: Reader[String] = if (0 < buffer.remaining) SegmentScanner (buffer.slice, una) else this

    /** The position of the first element in the reader.
     */
    def pos: Position = new Position { val line = 0; val column = 0; val lineContents = ""}

    /** `true` iff there are no more elements in this reader.
     */
    def atEnd: Boolean = 0 >= buffer.remaining
}

object SegmentScanner
{
    // Parse the optional UNA segment if present.
    // See http://en.wikipedia.org/wiki/EDIFACT
    // There are six characters following UNA in this order (default in brackets):
    //     component data element separator (:)
    //     data element separator (+)
    //     decimal notification (.)
    //     release character (?)
    //     reserved, must be a space
    //     segment terminator (')
    def parseUNA (buffer: ByteBuffer): UNA =
    {
        if (buffer.limit >= 9)
        {
            buffer.mark
            val buna = new Array[Char] (9)
            for (i <- 0 until 9)
                buna(i) = buffer.get.asInstanceOf[Char]
            val una = new String (buna)
            if (("UNA" == una.substring (0, 3)) && (" " == una.substring (7, 8)))
            {
                UNA (
                    component_data_element_separator = una.codePointAt (3),
                    data_element_separator = una.codePointAt (4),
                    decimal_notification = una.codePointAt (5),
                    release_character = una.codePointAt (6),
                    segment_terminator = una.codePointAt (8)
                )
            }
            else
            {
                buffer.reset
                UNA ()
            }
        }
        else
            UNA ()

    }

    /**
     * Convenience routine for parsing the UNA segment and constructing a scanner.
     *
     * @param buffer the bytes to scan
     * @return a scanner over the bytes
     */
    def apply (buffer: ByteBuffer): SegmentScanner =
    {
        val una = parseUNA (buffer)
        SegmentScanner (buffer, una)
    }

    /**
     * Convenience routine for unit tests.
     *
     * @param string The text of a "MSCONS file"
     * @return a scanner over the string
     */
    def apply (string: String): SegmentScanner =
    {
        val buffer = ByteBuffer.wrap (string.getBytes)
        val una = parseUNA (buffer)
        SegmentScanner (buffer, una)
    }
}