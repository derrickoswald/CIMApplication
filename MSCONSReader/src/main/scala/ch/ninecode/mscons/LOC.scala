package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    3227 LOCATION FUNCTION CODE QUALIFIER           M    1 an..3
//
//    020    C517 LOCATION IDENTIFICATION                    C    1
//    3225  Location identifier                       C      an..35
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    3224  Location name                             C      an..256
//
//    030    C519 RELATED LOCATION ONE IDENTIFICATION        C    1
//    3223  First related location identifier         C      an..35
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    3222  First related location name               C      an..70
//
//    040    C553 RELATED LOCATION TWO IDENTIFICATION        C    1
//    3233  Second related location identifier        C      an..35
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    3232  Second related location name              C      an..70
//
//    050    5479 RELATION CODE                              C    1 an..3

case class Location_Identification (
    locationIdentifier: Option[String],
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    locationName: Option[String]
)

case class Related_Location_One_Identification (
    firstRelatedLocationIdentifier: Option[String],
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    firstRelatedLocationName: Option[String]
)

case class Related_Location_Two_Identification (
    secondRelatedLocationIdentifier: Option[String],
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    secondRelatedLocationName: Option[String]
)

// To identify a place or a location and/or related locations.
case class LOC (
    locationFunctionCodeQualifier: String,
    locationIdentification: Option[Location_Identification],
    relatedLocationOneIdentification: Option[Related_Location_One_Identification],
    relatedLocationTwoIdentification: Option[Related_Location_Two_Identification],
    relationCode: Option[String]
)

object LOC extends FieldExtractor[LOC]
{
    private lazy val _3227 = alphanumeric (3)

    private lazy val c517_3225 = alphanumeric_? (35)
    private lazy val c517_1131 = alphanumeric_? (17)
    private lazy val c517_3055 = alphanumeric_? (3)
    private lazy val c517_3224 = alphanumeric_? (256)
    private lazy val c517 =
        subfields (
            c517_3225 ~ c517_1131 ~ c517_3055 ~ c517_3224 ^^
                { case _3225 ~ _1131 ~ _3055 ~ _3224  => Location_Identification (_3225, _1131, _3055, _3224) }
        )

    private lazy val c519_3223 = alphanumeric_? (35)
    private lazy val c519_1131 = alphanumeric_? (17)
    private lazy val c519_3055 = alphanumeric_? (3)
    private lazy val c519_3222 = alphanumeric_? (70)
    private lazy val c519 =
        subfields (
            c519_3223 ~ c519_1131 ~ c519_3055 ~ c519_3222 ^^
                { case _3223 ~ _1131 ~ _3055 ~ _3222  => Related_Location_One_Identification (_3223, _1131, _3055, _3222) }
        )

    private lazy val c553_3223 = alphanumeric_? (35)
    private lazy val c553_1131 = alphanumeric_? (17)
    private lazy val c553_3055 = alphanumeric_? (3)
    private lazy val c553_3222 = alphanumeric_? (70)
    private lazy val c553 =
        subfields (
            c553_3223 ~ c553_1131 ~ c553_3055 ~ c553_3222 ^^
                { case _3223 ~ _1131 ~ _3055 ~ _3222  => Related_Location_Two_Identification (_3223, _1131, _3055, _3222) }
        )

    private lazy val _5479 = alphanumeric_? (3)

    lazy val loc_fields: Parser[LOC] =
        fields (
            _3227 ~ c517.? ~ c519.? ~ c553.? ~ _5479 ^^
                { case _3227 ~ c517 ~ c519 ~ c553 ~ _5479 => LOC (_3227, c517, c519, c553, _5479) }
        ).named ("LOC")

    override def phrase: Parser[LOC] = loc_fields
}