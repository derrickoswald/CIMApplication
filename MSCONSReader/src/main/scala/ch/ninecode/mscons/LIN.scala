package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    1082 LINE ITEM IDENTIFIER                       C    1 an..6
//
//    020    1229 ACTION CODE                                C    1 an..3
//
//    030    C212 ITEM NUMBER IDENTIFICATION                 C    1
//    7140  Item identifier                           C      an..35
//    7143  Item type identification code             C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    040    C829 SUB-LINE INFORMATION                       C    1
//    5495  Sub-line indicator code                   C      an..3
//    1082  Line item identifier                      C      an..6
//
//    050    1222 CONFIGURATION LEVEL NUMBER                 C    1 n..2
//
//    060    7083 CONFIGURATION OPERATION CODE               C    1 an..3

case class Item_Number_Identification (
    itemIdentifier: Option[String],
    itemTypeIdentificationCode: Option[String],
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String]
)

case class Sub_Line_Information (
    subLineIndicatorCode: Option[String],
    lineItemIdentifier: Option[String]
)

// To identify a line item and configuration.
case class LIN (
    lineItemIdentifier: Option[String],
    actionCode: Option[String],
    itemNumberIdentification: Option[Item_Number_Identification],
    subLineInformation: Option[Sub_Line_Information],
    configurationLevelNumber: Option[String],
    configurationOperationCode: Option[String]
)

object LIN extends FieldExtractor[LIN]
{
    private lazy val _1082 = alphanumeric_? (6)

    private lazy val _1229 = alphanumeric_? (3)

    private lazy val c212_7140 = alphanumeric_? (35)
    private lazy val c212_7143 = alphanumeric_? (3)
    private lazy val c212_1131 = alphanumeric_? (17)
    private lazy val c212_3055 = alphanumeric_? (3)
    private lazy val c212 =
        subfields (
            c212_7140 ~ c212_7143 ~ c212_1131 ~ c212_3055 ^^
                { case _7140 ~ _7143 ~ _1131 ~ _3055  => Item_Number_Identification (_7140, _7143, _1131, _3055) }
        )

    //    040    C829 SUB-LINE INFORMATION                       C    1
    //    5495  Sub-line indicator code                   C      an..3
    //    1082  Line item identifier                      C      an..6
    private lazy val c829_5495 = alphanumeric_? (3)
    private lazy val c829_1082 = alphanumeric_? (6)
    private lazy val c829 =
        subfields (
            c829_5495 ~ c829_1082 ^^
                { case _5495 ~ _1082  => Sub_Line_Information (_5495, _1082) }
        )

    private lazy val _1222 = alphanumeric_? (2)

    private lazy val _7083 = alphanumeric_? (3)

    lazy val lin_fields: Parser[LIN] =
        fields (
            _1082 ~ _1229 ~ c212.? ~ c829.? ~ _1222 ~ _7083 ^^
                { case _1082 ~ _1229 ~ c212 ~ c829 ~ _1222 ~ _7083 => LIN (_1082, _1229, c212, c829, _1222, _7083) }
        ).named ("LIN")

    override def phrase: Parser[LIN] = lin_fields
}