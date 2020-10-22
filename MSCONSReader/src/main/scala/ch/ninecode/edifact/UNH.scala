package ch.ninecode.edifact

case class UNH (Reference: String, Type: String, Version: String, Release: String, Agency: String, Code: String)

object UNH
{
    def apply (fields: List[Field]): UNH =
    {
        fields match
        {
            case reference :: identifier :: _ =>
                identifier.submembers match
                {
                    case typ :: version :: release :: agency :: code :: _ =>
                        UNH(reference.text, typ.text, version.text, release.text, agency.text, code.text)
                    case typ :: version :: release :: agency :: _ =>
                        UNH(reference.text, typ.text, version.text, release.text, agency.text, "")
                    case _ => UNH("error", "in subfields", "", "", "", "")
                }
            case _ => UNH("error", "in fields", "", "", "", "")
        }
    }
}
