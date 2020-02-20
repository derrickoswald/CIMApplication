package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C270 CONTROL                                    M    1
//    6069  Control total type code qualifier         M      an..3
//    6066  Control total quantity                    M      n..18
//    6411  Measurement unit code                     C      an..8

// To provide control total.
case class CNT (
    controlTotalTypeCodeQualifier: String,
    controlTotalQuantity: Double,
    measurementUnitCode: Option[String]
)

object CNT extends FieldExtractor[CNT]
{
    private lazy val _6069 = alphanumeric (3)

    private lazy val _6066 = numeric (18)

    private lazy val _6411 = alphanumeric_? (8)

    lazy val cnt_fields: Parser[CNT] =
        fields (
            _6069 ~ _6066 ~ _6411 ^^
                { case _6069 ~ _6066 ~ _6411 => CNT (_6069, _6066, _6411) }
        ).named ("CNT")

    override def phrase: Parser[CNT] = cnt_fields
}