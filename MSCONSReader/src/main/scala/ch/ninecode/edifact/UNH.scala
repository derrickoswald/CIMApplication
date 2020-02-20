package ch.ninecode.edifact

case class UNH (Reference: String, Type: String, Version: String, Release: String, Agency: String, Code: String)

object UNH
{
    def apply (fields: List[Field]): UNH =
    {
        val reference = fields.head
        val identifier = fields.tail.head
        UNH (
            reference.text,
            identifier.submembers.head.text,
            identifier.submembers.tail.head.text,
            identifier.submembers.tail.tail.head.text,
            identifier.submembers.tail.tail.tail.head.text,
            if (identifier.submembers.length >= 5)
                identifier.submembers.tail.tail.tail.tail.head.text
            else
                null
        )
    }
}
