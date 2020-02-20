package ch.ninecode.edifact

case class UNB (SyntaxIdentifier: String, SyntaxVersionNumber: String)

object UNB
{
    def apply (fields: List[Field]): UNB =
    {
        val syntax = fields.head
        UNB (syntax.submembers.head.text, syntax.submembers.tail.head.text)
    }
}


