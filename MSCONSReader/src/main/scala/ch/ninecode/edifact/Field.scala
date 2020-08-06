package ch.ninecode.edifact

case class Field (text: String, submembers: List[Field] = List ())
{
    override def toString: String =
        if (submembers.isEmpty)
            s"[$text]"
        else
            s"[${submembers.mkString (",")}]"
}