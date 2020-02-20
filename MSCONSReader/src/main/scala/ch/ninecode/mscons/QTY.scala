package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C186 QUANTITY DETAILS                           M    1
//    6063  Quantity type code qualifier              M      an..3
//    6060  Quantity                                  M      an..35
//    6411  Measurement unit code                     C      an..8

// To specify a pertinent quantity.
case class QTY (
    quantityTypeCodeQualifier: String,
    quantity: String,
    measurementUnitCode: Option[String]
)

object QTY extends FieldExtractor[QTY]
{
    private lazy val _6063 = alphanumeric (3)

    private lazy val _6060 = alphanumeric (35)

    private lazy val _6411 = alphanumeric_? (8)

    lazy val qty_fields: Parser[QTY] =
        subfields (
            _6063 ~ _6060 ~ _6411 ^^
                { case _6063 ~ _6060 ~ _6411 => QTY (_6063, _6060, _6411) }
        ).named ("QTY")

    override def phrase: Parser[QTY] = qty_fields
}
