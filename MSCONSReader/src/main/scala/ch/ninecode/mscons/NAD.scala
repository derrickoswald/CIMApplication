package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    3035 PARTY FUNCTION CODE QUALIFIER              M    1 an..3
//
//    020    C082 PARTY IDENTIFICATION DETAILS               C    1
//    3039  Party identifier                          M      an..35
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//
//    030    C058 NAME AND ADDRESS                           C    1
//    3124  Name and address description              M      an..35
//    3124  Name and address description              C      an..35
//    3124  Name and address description              C      an..35
//    3124  Name and address description              C      an..35
//    3124  Name and address description              C      an..35
//
//    040    C080 PARTY NAME                                 C    1
//    3036  Party name                                M      an..35
//    3036  Party name                                C      an..35
//    3036  Party name                                C      an..35
//    3036  Party name                                C      an..35
//    3036  Party name                                C      an..35
//    3045  Party name format code                    C      an..3
//
//    050    C059 STREET                                     C    1
//    3042  Street and number or post office box
//          identifier                                M      an..35
//    3042  Street and number or post office box
//          identifier                                C      an..35
//    3042  Street and number or post office box
//          identifier                                C      an..35
//    3042  Street and number or post office box
//          identifier                                C      an..35
//
//    060    3164 CITY NAME                                  C    1 an..35
//
//    070    C819 COUNTRY SUB-ENTITY DETAILS                 C    1
//    3229  Country sub-entity name code              C      an..9
//    1131  Code list identification code             C      an..17
//    3055  Code list responsible agency code         C      an..3
//    3228  Country sub-entity name                   C      an..70
//
//    080    3251 POSTAL IDENTIFICATION CODE                 C    1 an..17
//
//    090    3207 COUNTRY NAME CODE                          C    1 an..3

case class Party_Identification_Details (
    partyIdentifier: String,
    codeListIdetificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String]
)

case class Name_And_Address (
    nameAndAddressDescription1: String,
    nameAndAddressDescription2: Option[String],
    nameAndAddressDescription3: Option[String],
    nameAndAddressDescription4: Option[String],
    nameAndAddressDescription5: Option[String]
)

case class Party_Name (
    partyName1: String,
    partyName2: Option[String],
    partyName3: Option[String],
    partyName4: Option[String],
    partyName5: Option[String],
    partyNameFormatCode: Option[String]
)

case class Street (
    streetAndNumberOrPostOfficeBoxIdentifier1: String,
    streetAndNumberOrPostOfficeBoxIdentifier2: Option[String],
    streetAndNumberOrPostOfficeBoxIdentifier3: Option[String],
    streetAndNumberOrPostOfficeBoxIdentifier4: Option[String]
)

case class Country_Sub_Entity_Details (
    countrySubEntityIdentifier: Option[String],
    codeListIdentificationCode: Option[String],
    codeListResponsibleAgencyCode: Option[String],
    countrySubEntityName: Option[String]
)

// To specify the name/address and their related function
case class NAD (
    partyFunctionCodeQualifier: String = "ZZZ",
    partyIdentificationDetails: Option[Party_Identification_Details] = None,
    nameAndAddress: Option[Name_And_Address] = None,
    partyName: Option[Party_Name] = None,
    street: Option[Street] = None,
    cityName: Option[String] = None,
    countrySubdivisionDetails: Option[Country_Sub_Entity_Details] = None,
    postalIdentificationCode: Option[String] = None,
    countryIdentifier: Option[String] = None
)

object NAD extends FieldExtractor[NAD]
{
    private lazy val _3035 = alphanumeric (3)

    private lazy val c082_3039 = alphanumeric (35)
    private lazy val c082_1131 = alphanumeric_? (17)
    private lazy val c082_3055 = alphanumeric_? (3)
    private lazy val c082 =
        subfields (
            c082_3039 ~ c082_1131 ~ c082_3055 ^^
                { case c082_3039 ~ c082_1131 ~ c082_3055  => Party_Identification_Details (c082_3039, c082_1131, c082_3055) }
        )

    private lazy val C058_3124_1 = alphanumeric (35)
    private lazy val C058_3124_2 = alphanumeric_? (35)
    private lazy val C058_3124_3 = alphanumeric_? (35)
    private lazy val C058_3124_4 = alphanumeric_? (35)
    private lazy val C058_3124_5 = alphanumeric_? (35)
    private lazy val c058 =
        subfields (
            C058_3124_1 ~ C058_3124_2 ~ C058_3124_3 ~ C058_3124_4 ~ C058_3124_5 ^^
                { case _3124_1 ~ _3124_2 ~ _3124_3 ~ _3124_4 ~ _3124_5  => Name_And_Address (_3124_1, _3124_2, _3124_3, _3124_4, _3124_5) }
        )

    private lazy val C080_3036_1 = alphanumeric (35)
    private lazy val C080_3036_2 = alphanumeric_? (35)
    private lazy val C080_3036_3 = alphanumeric_? (35)
    private lazy val C080_3036_4 = alphanumeric_? (35)
    private lazy val C080_3036_5 = alphanumeric_? (35)
    private lazy val C080_3045 = alphanumeric_? (35)
    private lazy val c080 =
        subfields (
            C080_3036_1 ~ C080_3036_2 ~ C080_3036_3 ~ C080_3036_4 ~ C080_3036_5 ~ C080_3045 ^^
                { case _3036_1 ~ _3036_2 ~ _3036_3 ~ _3036_4 ~ _3036_5 ~ _3045  => Party_Name (_3036_1, _3036_2, _3036_3, _3036_4, _3036_5, _3045) }
        )

    private lazy val C059_3042_1 = alphanumeric (35)
    private lazy val C059_3042_2 = alphanumeric_? (35)
    private lazy val C059_3042_3 = alphanumeric_? (35)
    private lazy val C059_3042_4 = alphanumeric_? (35)
    private lazy val c059 =
        subfields (
            C059_3042_1 ~ C059_3042_2 ~ C059_3042_3 ~ C059_3042_4 ^^
                { case _3042_1 ~ _3042_2 ~ _3042_3 ~ _3042_4  => Street (_3042_1, _3042_2, _3042_3, _3042_4) }
        )

    private lazy val _3164 = alphanumeric_? (35)

    private lazy val C819_3229 = alphanumeric_? (9)
    private lazy val C819_1131 = alphanumeric_? (17)
    private lazy val C819_3055 = alphanumeric_? (3)
    private lazy val C819_3228 = alphanumeric_? (70)
    private lazy val c819 =
        subfields (
            C819_3229 ~ C819_1131 ~ C819_3055 ~ C819_3228 ^^
                { case _3229 ~ _1131 ~ _3055 ~ _3228  => Country_Sub_Entity_Details (_3229, _1131, _3055, _3228) }
        )

    private lazy val _3251 = alphanumeric_? (17)

    private lazy val _3207 = alphanumeric_? (3)

    lazy val nad_fields: Parser[NAD] =
        fields (
            _3035 ~ c082.? ~ c058.? ~ c080.? ~ c059.? ~ _3164 ~ c819.? ~ _3251 ~ _3207 ^^
                { case _3035 ~ c082 ~ c058 ~ c080 ~ c059 ~ _3164 ~ c819 ~ _3251 ~ _3207 => NAD (_3035, c082, c058, c080, c059, _3164, c819, _3251, _3207) }
        ).named ("NAD")

    override def phrase: Parser[NAD] = nad_fields
}
