package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    4347 PRODUCT IDENTIFIER CODE QUALIFIER          M    1 an..3
//
//    020    C212 ITEM NUMBER IDENTIFICATION                 M    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    030    C212 ITEM NUMBER IDENTIFICATION                 C    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    040    C212 ITEM NUMBER IDENTIFICATION                 C    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    050    C212 ITEM NUMBER IDENTIFICATION                 C    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    060    C212 ITEM NUMBER IDENTIFICATION                 C    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3

// duplicate: see LIN
//case class Item_Number_Identification (
//    itemIdentifier: Option[String],
//    itemTypeIdentificationCode: Option[String],
//    codeListIdentificationCode: Option[String],
//    codeListResponsibleAgencyCode: Option[String]
//)

// To specify additional or substitutional item identification codes.
case class PIA (
    productIdentifierCodeQualifier: String,
    itemNumberIdentification1: Item_Number_Identification,
    itemNumberIdentification2: Option[Item_Number_Identification],
    itemNumberIdentification3: Option[Item_Number_Identification],
    itemNumberIdentification4: Option[Item_Number_Identification],
    itemNumberIdentification5: Option[Item_Number_Identification]
)

object PIA extends FieldExtractor[PIA]
{
    private lazy val _4347 = alphanumeric (3)

    private lazy val c212_7140 = alphanumeric_? (35)
    private lazy val c212_7143 = alphanumeric_? (3)
    private lazy val c212_1131 = alphanumeric_? (17)
    private lazy val c212_3055 = alphanumeric_? (3)
    private lazy val c212 =
        subfields (
            c212_7140 ~ c212_7143 ~ c212_1131 ~ c212_3055 ^^
                { case _7140 ~ _7143 ~ _1131 ~ _3055  => Item_Number_Identification (_7140, _7143, _1131, _3055) }
        )

    private lazy val _5479 = alphanumeric_? (3)

    lazy val pia_fields: Parser[PIA] =
        fields (
            _4347 ~ c212 ~ c212.? ~ c212.? ~ c212.? ~ c212.? ^^
                { case _4347 ~ c212_1 ~ c212_2 ~ c212_3 ~ c212_4 ~ c212_5 => PIA (_4347, c212_1, c212_2, c212_3, c212_4, c212_5) }
        ).named ("PIA")

    override def phrase: Parser[PIA] = pia_fields
}