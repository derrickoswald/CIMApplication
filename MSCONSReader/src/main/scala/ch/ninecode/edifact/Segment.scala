package ch.ninecode.edifact

/**
 * Name and contents of a single segment.
 *
 * @param name the three uppercase character name of the segment
 * @param contents the raw contents of the segment, exclusive of the name and segment terminator
 * @param fields the parsed field list
 */
case class Segment (name: String, contents: String, fields: List[Field] = null)
{
    override def toString: String =
        if (null == fields)
            s"($name,$contents)"
        else
            s"($name ${fields.mkString (",")})"
}
