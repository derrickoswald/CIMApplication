package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C002 DOCUMENT/MESSAGE NAME                      C    1
//    1001  Document name code                        C      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    1000  Document name                             C      an..35
//
//    020    C106 DOCUMENT/MESSAGE IDENTIFICATION            C    1
//    1004  Document identifier                       C      an..35
//    1056  Version identifier                        C      an..9
//    1060  Revision identifier                       C      an..6
//
//    030    1225 MESSAGE FUNCTION CODE                      C    1 an..3
//
//    040    4343 RESPONSE TYPE CODE                         C    1 an..3

/** DOCUMENT/MESSAGE NAME */
case class Document_Message_Name (
    documentNameCode: Option[String] = None,
    codeListIdentificationCode: Option[String] = None,
    codeListResponsibleAgencyCode: Option[String] = None,
    documentName: Option[String] = None
)

/** DOCUMENT/MESSAGE IDENTIFICATION */
case class Document_Message_Identification (
    documentIdentifier: Option[String] = None,
    versionIdentifier: Option[String] = None,
    revisionIdentifier: Option[String] = None
)

/** To indicate the type and function of a message and to transmit the identifying number. */
case class BGM (
    documentMessageName: Option[Document_Message_Name] = None,
    documentMessageIdentification: Option[Document_Message_Identification] = None,
    messageFunctionCode: Option[String] = None,
    responseTypeCode: Option[String] = None
)

object BGM extends FieldExtractor[BGM]
{
    //        010    C002 DOCUMENT/MESSAGE NAME                      C    1
    //        010    1001  Document name code                        C      an..3
    //        020    1131  Code list identification code             C      an..17
    //        030    3055  Code list responsible agency code         C      an..3
    //        040    1000  Document name                             C      an..35

    private lazy val c002_1001 = alphanumeric_? (3)
    private lazy val c002_1131 = alphanumeric_? (17)
    private lazy val c002_3055 = alphanumeric_? (3)
    private lazy val c002_1000 = alphanumeric_? (35)
    private lazy val c002 =
        subfields (
            c002_1001 ~ c002_1131 ~ c002_3055 ~ c002_1000 ^^
                { case c002_1001 ~ c002_1131 ~ c002_3055 ~ c002_1000  => Document_Message_Name (c002_1001, c002_1131, c002_3055, c002_1000) }
        )

    //    1004  Document identifier                       C      an..35
    //    1056  Version identifier                        C      an..9
    //    1060  Revision identifier                       C      an..6

    private lazy val c106_1004 = alphanumeric_? (35)
    private lazy val c106_1056 = alphanumeric_? (9)
    private lazy val c106_1060 = alphanumeric_? (6)
    private lazy val c106 =
        subfields (
            c106_1004 ~ c106_1056 ~ c106_1060 ^^
                { case c106_1004 ~ c106_1056 ~ c106_1060 => Document_Message_Identification (c106_1004, c106_1056, c106_1060) }
        )

    //    030    1225 MESSAGE FUNCTION CODE                      C    1 an..3

    private lazy val _1225 = alphanumeric (3)

    //    040    4343 RESPONSE TYPE CODE                         C    1 an..3

    private lazy val _4343 = alphanumeric (3)

    lazy val bgm_fields: Parser[BGM] =
        fields (
            c002.? ~ c106.? ~ _1225.? ~ _4343.? ^^
                { case c002 ~ c106 ~ _1225 ~ _4343 => BGM (c002, c106, _1225, _4343) }
        ).named ("BGM")

    override def phrase: Parser[BGM] = bgm_fields
}
