package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    6311 MEASUREMENT PURPOSE CODE QUALIFIER         M    1 an..3
//
//    020    C502 MEASUREMENT DETAILS                        C    1
//    6313  Measured attribute code                   C      an..3
//    6321  Measurement significance code             C      an..3
//    6155  Non-discrete measurement name code        C      an..17
//    6154  Non-discrete measurement name             C      an..70
//
//    030    C174 VALUE/RANGE                                C    1
//    6411  Measurement unit code                     C      an..8
//    6314  Measure                                   C      an..18
//    6162  Range minimum quantity                    C      n..18
//    6152  Range maximum quantity                    C      n..18
//    6432  Significant digits quantity               C      n..2
//
//    040    7383 SURFACE OR LAYER CODE                      C    1 an..3

case class Value_Range (
    measurementUnitCode: Option[String],
    measureme: Option[String],
    rangeMinimumQuantity: Option[Double],
    rangeMaximumQuantity: Option[Double],
    significantDigitsQuantity: Option[Double]
)

// To specify physical measurements, including dimension tolerances, weights and counts.
case class MEA (
    measurementPurposeCodeQualifier: String,
    measurement_Details: Option[Measurement_Details], // see CCI
    valueRange: Option[Value_Range],
    surfaceOrLayerCode: Option[String]
)

object MEA extends FieldExtractor[MEA]
{
    private lazy val _6311 = alphanumeric (3)

    private lazy val c502_6313 = alphanumeric_? (3)
    private lazy val c502_6321 = alphanumeric_? (3)
    private lazy val c502_6155 = alphanumeric_? (17)
    private lazy val c502_6154 = alphanumeric_? (70)
    private lazy val c502 =
        subfields (
            c502_6313 ~ c502_6321 ~ c502_6155 ~ c502_6154 ^^
                { case _6313 ~ _6321 ~ _6155 ~ _6154  => Measurement_Details (_6313, _6321, _6155, _6154) }
        )
    //    030    C174 VALUE/RANGE                                C    1
    //    6411  Measurement unit code                     C      an..8
    //    6314  Measure                                   C      an..18
    //    6162  Range minimum quantity                    C      n..18
    //    6152  Range maximum quantity                    C      n..18
    //    6432  Significant digits quantity               C      n..2
    private lazy val c174_6411 = alphanumeric_? (8)
    private lazy val c174_6314 = alphanumeric_? (18)
    private lazy val c174_6162 = numeric_? (18)
    private lazy val c174_6152 = numeric_? (18)
    private lazy val c174_6432 = numeric_? (2)
    private lazy val c174 =
        subfields (
            c174_6411 ~ c174_6314 ~ c174_6162 ~ c174_6152 ~ c174_6432 ^^
                { case _6411 ~ _6314 ~ _6162 ~ _6152 ~ _6432  => Value_Range (_6411, _6314, _6162, _6152, _6432) }
        )

    private lazy val _7383 = alphanumeric_? (3)

    lazy val mea_fields: Parser[MEA] =
        fields (
            _6311 ~ c502.? ~ c174.? ~ _7383 ^^
                { case _6311 ~ c502 ~ c174 ~ _7383 => MEA (_6311, c502, c174, _7383) }
        ).named ("MEA")

    override def phrase: Parser[MEA] = mea_fields
}