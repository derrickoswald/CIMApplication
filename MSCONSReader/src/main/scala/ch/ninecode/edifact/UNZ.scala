package ch.ninecode.edifact

//    Ref.   Repr.       Name                         Remarks
//
//    0036   n..6    M   INTERCHANGE CONTROL COUNT The count of the number of messages or, if used, the number of functional groups in the interchange. One of these counts shall appear.
//    ___________________________________________________________________
//
//    0020   an..14  M   INTERCHANGE CONTROL REFERENCE Shall be identical to 0020 in UNB
//

// To end and check the completeness of an interchange
case class UNZ (
    interchangeControlCount: Double,
    interchangeControlReference: String
)

object UNZ extends FieldExtractor[UNZ]
{
    private lazy val _0036 = numeric (6)

    private lazy val _0020 = alphanumeric (14)

    lazy val unz_fields: Parser[UNZ] =
        fields (
            _0036 ~ _0020 ^^
                { case _0036 ~ _0020 => UNZ (_0036, _0020) }
        ).named ("UNZ")

    override def phrase: Parser[UNZ] = unz_fields
}
