package ch.ninecode.edifact

case class Field (text: String, submembers: List[Field] = null)
{
    override def toString: String =
    if (null == submembers)
        s"[$text]"
    else
        s"[${submembers.mkString (",")}]"
}