package ch.ninecode.edifact

case class UNB (SyntaxIdentifier: String, SyntaxVersionNumber: String)

object UNB
{
    def apply (fields: List[Field]): UNB =
    {
        fields match
        {
            case syntax :: _ =>
                syntax.submembers match
                {
                    case id :: version :: _ => UNB (id.text, version.text)
                    case _ => UNB ("error", "in subfields")
                }
            case _ => UNB ("error", "in fields")
        }
    }
}


