package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C601 STATUS CATEGORY                            C    1
//    9015  Status category code                      M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    020    C555 STATUS                                     C    1
//    4405  Status description code                   M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    4404  Status description                        C      an..35
//
//    030    C556 STATUS REASON                              C    1
//    9013  Status reason description code            M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    9012  Status reason description                 C      an..256
//
//    040    C556 STATUS REASON                              C    1
//    9013  Status reason description code            M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    9012  Status reason description                 C      an..256
//
//    050    C556 STATUS REASON                              C    1
//    9013  Status reason description code            M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    9012  Status reason description                 C      an..256
//
//    060    C556 STATUS REASON                              C    1
//    9013  Status reason description code            M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    9012  Status reason description                 C      an..256
//
//    070    C556 STATUS REASON                              C    1
//    9013  Status reason description code            M      an..3
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    9012  Status reason description                 C      an..256

case class Status_Category (
    statusCategoryCode: String,
    codeListIdenificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String]
)

case class Status (
    statusDescriptionCode: String,
    codeListIdenificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    statusDescription: Option[String]
)

case class Status_Reason (
    statusReasonDescriptionCode: String,
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    statusReasonDescription: Option[String]
)

// To specify the status of an object or service, including its category and the reason(s) for the status.
case class STS (
    statusCategory: Option[Status_Category],
    status: Option[Status],
    statusReason1: Option[Status_Reason],
    statusReason2: Option[Status_Reason],
    statusReason3: Option[Status_Reason],
    statusReason4: Option[Status_Reason],
    statusReason5: Option[Status_Reason]
)

object STS extends FieldExtractor[STS]
{
    private lazy val c601_9015 = alphanumeric (3)
    private lazy val c601_1131 = alphanumeric_? (17)
    private lazy val c601_3055 = alphanumeric_? (3)
    private lazy val c601 =
        subfields (
            c601_9015 ~ c601_1131 ~ c601_3055 ^^
                { case _9015 ~ _1131 ~ _3055  => Status_Category (_9015, _1131, _3055) }
        )

    private lazy val c555_4405 = alphanumeric (3)
    private lazy val c555_1131 = alphanumeric_? (17)
    private lazy val c555_3055 = alphanumeric_? (3)
    private lazy val c555_4404 = alphanumeric_? (35)
    private lazy val c555 =
        subfields (
            c555_4405 ~ c555_1131 ~ c555_3055 ~ c555_4404 ^^
                { case _4405 ~ _1131 ~ _3055 ~ _4404  => Status (_4405, _1131, _3055, _4404) }
        )

    //    030    C556 STATUS REASON                              C    1
    //    9013  Status reason description code            M      an..3
    //    1131  Code list identification code             C      an..17
    //    3055  Code list responsible agency code         C      an..3
    //    9012  Status reason description                 C      an..256

    private lazy val c556_9013 = alphanumeric (3)
    private lazy val c556_1131 = alphanumeric_? (17)
    private lazy val c556_3055 = alphanumeric_? (3)
    private lazy val c556_9012 = alphanumeric_? (256)
    private lazy val c556 =
        subfields (
            c556_9013 ~ c556_1131 ~ c556_3055 ~ c556_9012 ^^
                { case _9013 ~ _1131 ~ _3055 ~ _9012  => Status_Reason (_9013, _1131, _3055, _9012) }
        )

    lazy val sts_fields: Parser[STS] =
        fields (
            c601.? ~ c555.? ~ c556.? ~ c556.? ~ c556.? ~ c556.? ~ c556.? ^^
                { case c601 ~ c555 ~ c556_1 ~ c556_2 ~ c556_3 ~ c556_4 ~ c556_5 => STS (c601, c555, c556_1, c556_2, c556_3, c556_4, c556_5) }
        ).named ("STS")

    override def phrase: Parser[STS] = sts_fields
}