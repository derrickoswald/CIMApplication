package ch.ninecode.edifact

import scala.util.parsing.input.Position
import scala.util.parsing.input.Reader

case class MemberScanner (field: Field, una: UNA, begin: Int = 0) extends Reader[Field]
{
    var start: Int = begin
    var end: Int = start
    lazy val first: Field =
    {
        var skip = false
        var stop = false
        var c = 0
        var intervals = List[(Int,Int)] () // start and size of each piece of the segment

        while (end < field.text.length && !stop)
        {
            c = field.text.charAt (end)
            end += 1
            if (skip)
            {
                if (c == una.component_data_element_separator)
                {
                    intervals = intervals :+ (start, end - 2)
                    start = end - 1
                }
                skip = false
            }
            else
                if (c == una.component_data_element_separator)
                    stop = true
                else
                    if (c == una.release_character)
                        skip = true
        }

        intervals = intervals :+ (start, if (stop) end - 1 else end)

        val string = intervals.map (
            item => field.text.substring (item._1, item._2)
        ).mkString ("")
        Field (string)
    }

    override def rest: Reader[Field] = MemberScanner (field, una: UNA, end)

    override def pos: Position = new Position { val line: Int = 0; val column: Int = begin; val lineContents: String = field.text }

    override def atEnd: Boolean = end >= field.text.length
}