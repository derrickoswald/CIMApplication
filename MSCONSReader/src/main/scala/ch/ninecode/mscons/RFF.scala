package ch.ninecode.mscons

import ch.ninecode.edifact.FieldExtractor

// To specify a reference.
case class RFF (
    referenceCodeQualifier: String = "ZZZ",
    referenceIdentifier: Option[String] = None,
    documentLineIdentifier: Option[String] = None,
    referenceVersionIdentifier: Option[String] = None,
    revisionIdentifier: Option[String] = None
)

object RFF extends FieldExtractor[RFF]
{
    // RFF+Z13:13008

//    010    C506 REFERENCE                                  M    1
//    1153  Reference code qualifier                  M      an..3
//    1154  Reference identifier                      C      an..70
//    1156  Document line identifier                  C      an..6
//    4000  Reference version identifier              C      an..35
//    1060  Revision identifier                       C      an..6

    private lazy val C506_1153 = alphanumeric (3)
    private lazy val C506_1154 = alphanumeric_? (70)
    private lazy val C506_1156 = alphanumeric_? (6)
    private lazy val C506_4000 = alphanumeric_? (35)
    private lazy val C506_1060 = alphanumeric_? (6)

    lazy val rff_fields: Parser[RFF] =
        subfields (
            C506_1153 ~ C506_1154 ~ C506_1156 ~ C506_4000 ~ C506_1060 ^^
                { case _1153 ~ _1154 ~ _1156 ~ _4000 ~ _1060 => RFF (_1153, _1154, _1156, _4000, _1060) }
        ).named ("RFF")

    override def phrase: Parser[RFF] = rff_fields
}
