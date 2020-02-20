package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    7059 CLASS TYPE CODE                            C    1 an..3
//
//    020    C502 MEASUREMENT DETAILS                        C    1
//    6313  Measured attribute code                   C      an..3
//    6321  Measurement significance code             C      an..3
//    6155  Non-discrete measurement name code        C      an..17
//    6154  Non-discrete measurement name             C      an..70
//
//    030    C240 CHARACTERISTIC DESCRIPTION                 C    1
//    7037  Characteristic description code           M      an..17
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    7036  Characteristic description                C      an..35
//    7036  Characteristic description                C      an..35
//
//    040    4051 CHARACTERISTIC RELEVANCE CODE              C    1 an..3

case class Measurement_Details (
    measuredAttributeCode: Option[String],
    measuredSignificanceCode: Option[String],
    nondiscreteMeasurementNameCode: Option[String],
    nondiscreteMeasurementName: Option[String]
)

case class Characteristic_Description (
    characteristicDescriptionCode: String,
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    characteristicDescription1: Option[String],
    characteristicDescription2: Option[String]
)

// To identify and describe a specific characteristic and its relevance for subsequent business processes.
case class CCI (
    classTypeCode: Option[String],
    measurementDetails: Option[Measurement_Details],
    characteristicDescription: Option[Characteristic_Description],
    characteristicRelevanceCode: Option[String]
)

object CCI extends FieldExtractor[CCI]
{
    private lazy val _7059 = alphanumeric_? (3)

    private lazy val c502_6313 = alphanumeric_? (3)
    private lazy val c502_6321 = alphanumeric_? (3)
    private lazy val c502_6155 = alphanumeric_? (17)
    private lazy val c502_6154 = alphanumeric_? (70)
    private lazy val c502 =
        subfields (
            c502_6313 ~ c502_6321 ~ c502_6155 ~ c502_6154 ^^
                { case _6313 ~ _6321 ~ _6155 ~ _6154  => Measurement_Details (_6313, _6321, _6155, _6154) }
        )

    private lazy val c240_7037 = alphanumeric (17)
    private lazy val c240_1131 = alphanumeric_? (17)
    private lazy val c240_3055 = alphanumeric_? (3)
    private lazy val c240_7036_1 = alphanumeric_? (35)
    private lazy val c240_7036_2 = alphanumeric_? (35)
    private lazy val c240 =
        subfields (
            c240_7037 ~ c240_1131 ~ c240_3055 ~ c240_7036_1 ~ c240_7036_2 ^^
                { case _7037 ~ _1131 ~ _3055 ~ _7036_1 ~ _7036_2  => Characteristic_Description (_7037,  _1131,  _3055,  _7036_1,  _7036_2) }
        )

    private lazy val _4051 = alphanumeric_? (3)

    lazy val cci_fields: Parser[CCI] =
        fields (
            _7059 ~ c502.? ~ c240.? ~ _4051 ^^
                { case _7059 ~ c502 ~ c240 ~ _4051 => CCI (_7059, c502, c240, _4051) }
        ).named ("CCI")

    override def phrase: Parser[CCI] = cci_fields
}