package ch.ninecode.edifact

// To end and check the completeness of a message.
case class UNT (
    numberOfSegments: Double, // The number of segments in a message body, plus the message header segment and message trailer segment.
    messageReferenceNumber: String // Unique message reference assigned by the sender. The value shall be identical to the value in 0062 in the corresponding UNH segment.
)

object UNT extends FieldExtractor[UNT]
{
    private lazy val _0074 = numeric (10)

    private lazy val _0062 = alphanumeric (14)

    lazy val unt_fields: Parser[UNT] =
        fields (
            _0074 ~ _0062 ^^
                { case _0074 ~ _0062 => UNT (_0074, _0062) }
        ).named ("UNT")

    override def phrase: Parser[UNT] = unt_fields
}