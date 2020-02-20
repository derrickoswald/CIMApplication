package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    3139 CONTACT FUNCTION CODE                      C    1 an..3
//
//    020    C056 CONTACT DETAILS                            C    1
//    3413         Contact identifier                        C      an..17
//    3412         Contact name                              C      an..256

case class Contact_Details (
    contactIdentifier: Option[String],
    contactName: Option[String]
)

// To identify a person or a department to whom communication should be directed.
case class CTA (
    contactFunctionCode: Option[String],
    contactDetails: Option[Contact_Details]
)

object CTA extends FieldExtractor[CTA]
{
    private lazy val _3139 = alphanumeric_? (3)

    private lazy val c056_3413 = alphanumeric_? (17)
    private lazy val c056_3412 = alphanumeric_? (256)
    private lazy val c056 =
        subfields (
            c056_3413 ~ c056_3412 ^^
                { case c056_3413 ~ c056_3412  => Contact_Details (c056_3413, c056_3412) }
        )

    lazy val cta_fields: Parser[CTA] =
        fields (
            _3139 ~ c056.? ^^
                { case _3139 ~ c056 => CTA (_3139, c056) }
        ).named ("CTA")

    override def phrase: Parser[CTA] = cta_fields
}
