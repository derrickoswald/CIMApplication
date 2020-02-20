package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C504 CURRENCY DETAILS                           C    1
//    6347  Currency usage code qualifier             M      an..3
//    6345  Currency identification code              C      an..3
//    6343  Currency type code qualifier              C      an..3
//    6348  Currency rate                             C      n..4
//
//    020    C504 CURRENCY DETAILS                           C    1
//    6347  Currency usage code qualifier             M      an..3
//    6345  Currency identification code              C      an..3
//    6343  Currency type code qualifier              C      an..3
//    6348  Currency rate                             C      n..4
//
//    030    5402 CURRENCY EXCHANGE RATE                     C    1 n..12
//
//    040    6341 EXCHANGE RATE CURRENCY MARKET IDENTIFIER   C    1 an..3

case class Currency_Details (
    currencyUsageCodeQualifier: String = "",
    currencyIdentificationCode: Option[String] = None,
    currencyTypeCodeQualifier: Option[String] = None,
    currencyRate: Option[Double] = None
)

// To specify currencies used in the transaction and relevant details for the rate of exchange.
case class CUX (
    currencyDetails1: Option[Currency_Details] = None,
    currencyDetails2: Option[Currency_Details] = None,
    currencyExchangeRate: Option[Double] = None,
    exchangeRateCurrencyMarketIdentifier: Option[String] = None
)

object CUX extends FieldExtractor[CUX]
{
    //    010    C504 CURRENCY DETAILS                           C    1
    //    6347  Currency usage code qualifier             M      an..3
    //    6345  Currency identification code              C      an..3
    //    6343  Currency type code qualifier              C      an..3
    //    6348  Currency rate                             C      n..4

    private lazy val c504_6347 = alphanumeric (3)
    private lazy val c504_6345 = alphanumeric_? (3)
    private lazy val c504_6343 = alphanumeric_? (3)
    private lazy val c504_6348 = numeric_? (4)
    private lazy val c504 =
        subfields (
            c504_6347 ~ c504_6345 ~ c504_6343 ~ c504_6348 ^^
                { case c504_6347 ~ c504_6345 ~ c504_6343 ~ c504_6348  => Currency_Details (c504_6347, c504_6345, c504_6343, c504_6348) }
        )

    //    030    5402 CURRENCY EXCHANGE RATE                     C    1 n..12

    private lazy val _5402 = numeric_? (12)

    //    040    6341 EXCHANGE RATE CURRENCY MARKET IDENTIFIER   C    1 an..3

    private lazy val _6341 = alphanumeric_? (3)

    lazy val cux_fields: Parser[CUX] =
        fields (
            c504.? ~ c504.? ~ _5402 ~ _6341 ^^
                { case c504_1 ~ c504_2 ~ _5402 ~ _6341 => CUX (c504_1, c504_2, _5402, _6341) }
        ).named ("CUX")

    override def phrase: Parser[CUX] = cux_fields
}