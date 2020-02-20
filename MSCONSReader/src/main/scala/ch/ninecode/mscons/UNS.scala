package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

// To be used by message designers only when required to avoid ambiguities.
case class UNS (
    sectionIdentification: String
)

object UNS extends FieldExtractor[UNS]
{
    lazy val uns_fields: Parser[UNS] =
        fields (
            alphanumeric (1) ^^
                (id => UNS (id))
        ).named ("UNS")

    override def phrase: Parser[UNS] = uns_fields
}

