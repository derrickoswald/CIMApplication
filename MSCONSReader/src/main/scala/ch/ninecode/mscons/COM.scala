package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

//    010    C076 COMMUNICATION CONTACT                      M    3
//    3148         Communication address identifier          M      an..512
//    3155         Communication means type code             M      an..3

case class Communication_Contact (
    communicationAddressIdentifier: String,
    communicationMeansTypeCode: String
)

case class COM (
    contacts: List[Communication_Contact]
)

// To identify a communication number of a department or a person to whom communication should be directed.
object COM extends FieldExtractor[COM]
{
    private lazy val c076_3148 = alphanumeric (512)
    private lazy val c076_3155 = alphanumeric (3)
    private lazy val c076 =
        subfields (
            c076_3148 ~ c076_3155 ^^
                { case _3148 ~ _3155  => Communication_Contact (_3148, _3155) }
        )

    lazy val com_fields: Parser[COM] =
        fields (
            repN (3, c076) ^^
                (list => COM (list))
        ).named ("COM")

    override def phrase: Parser[COM] = com_fields
}

