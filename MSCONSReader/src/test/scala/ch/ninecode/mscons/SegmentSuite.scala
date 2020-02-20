package ch.ninecode.mscons

import java.util.Calendar

import org.scalatest.FunSuite

import ch.ninecode.edifact.Segment
import ch.ninecode.edifact.SegmentListParser
import ch.ninecode.edifact.SegmentParser
import ch.ninecode.edifact.SegmentScanner
import ch.ninecode.edifact.ServiceSegmentParser

class SegmentSuite extends FunSuite
{
    abstract class Mock[T] extends MSCONSMessage
    {
        def parse(p: Parser[T], in: SegmentListParser): ParseResult[T] = p(in)
        def phrase: Parser[T]
        def parseAndCheck (text: String, check: T => Unit): Unit =
        {
            val scanner = SegmentScanner (text)
            val message = SegmentParser (scanner.una)
            val segments = message.segment.*
            segments.apply (scanner) match
            {
                case message.Success (result: List[Segment], remainder) =>
                    // result.foreach (segment => println (segment))
                    assert (remainder.atEnd)
                    val reader = SegmentListParser (result)
                    parse (phrase, reader) match
                    {
                        case Success (message, rest) =>
                            assert (rest.atEnd)
                            check (message)
                        case Failure (message, _) =>
                            fail (s"parse failed '$message'")
                        case Error (message, _) =>
                            fail (s"parse error '$message'")
                    }
                case message.Failure (message, _) =>
                    fail (s"segment parse failed '$message'")
                case message.Error (message, _) =>
                    fail (s"segment parse error '$message'")
            }
        }
    }

    def parseAndCheck (text: String, check: MSCONSMessage04B => Unit): Unit =
    {
        val scanner = SegmentScanner (text)
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], _) =>
                // result.foreach (segment => println (segment))
                val x = ServiceSegmentParser.read (result)
                x match
                {
                    case ServiceSegmentParser.Success (r, rest) =>
                        if (   (r.unh.Type == "MSCONS")
                            && (r.unh.Version == "D"))
                        {
                            r.unh.Release match
                            {
                                case "04B" =>
                                    MSCONSMessage04B.phrase (rest) match
                                    {
                                        case MSCONSMessage04B.Success (message, rest) =>
                                            assert (rest.atEnd)
                                            check (message)
                                        case MSCONSMessage04B.Failure (message, _) =>
                                            fail (s"parse failed '$message'")
                                        case MSCONSMessage04B.Error (message, _) =>
                                            fail (s"parse error '$message'")
                                    }
                                case _ => fail (s"${r.unh.Type} version ${r.unh.Version} release ${r.unh.Release} is not supported")
                            }
                        }

                    case ServiceSegmentParser.Failure (msg, _) =>
                        fail (s"parse failure: $msg")
                    case ServiceSegmentParser.Error (msg, _) =>
                        fail (s"parse error: $msg")
                }
            case message.Failure (msg, _) =>
                fail (s"parse failure: $msg")
            case message.Error (msg, _) =>
                fail (s"parse error: $msg")
        }
    }

    test ("BGM")
    {
        new Mock[BGM] { def phrase: Parser[BGM] = MSCONSMessage04B.bgm.asInstanceOf[Parser[BGM]] }
            .parseAndCheck (
                "BGM+7+slevu14572840D+9'",
                bgm =>
                {
                    assert (bgm.documentMessageName.get.documentNameCode.contains ("7"))
                    assert (bgm.documentMessageIdentification.get.documentIdentifier.contains ("slevu14572840D"))
                    assert (bgm.messageFunctionCode.contains ("9"))
                    assert (bgm.responseTypeCode.isEmpty)
                }
            )
    }

    test ("DTM")
    {
        new Mock[DTM] { def phrase: Parser[DTM] = MSCONSMessage04B.dtm.asInstanceOf[Parser[DTM]] }
            .parseAndCheck (
                "DTM+137:201912140000:203'",
                dtm =>
                {
                    assert (dtm.functionCodeQualifier == "137")
                    assert (dtm.text.contains ("201912140000"))
                    assert (dtm.formatCode.contains ("203"))
                    val calendar = dtm.getTime
                    assert (calendar.get (Calendar.YEAR) == 2019)
                    assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                    assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                    assert (calendar.get (Calendar.HOUR) == 0)
                    assert (calendar.get (Calendar.MINUTE) == 0)
                }
            )
    }

    test ("RFF")
    {
        new Mock[RFF] { def phrase: Parser[RFF] = MSCONSMessage04B.rff.asInstanceOf[Parser[RFF]] }
            .parseAndCheck (
                "RFF+Z13:13008'",
                rff =>
                {
                    assert (rff.referenceCodeQualifier.contains ("Z13"))
                    assert (rff.referenceIdentifier.contains ("13008"))
                    assert (rff.documentLineIdentifier.isEmpty)
                    assert (rff.referenceVersionIdentifier.isEmpty)
                    assert (rff.revisionIdentifier.isEmpty)
                }
            )
    }

    test ("Group1")
    {
        new Mock[Option[List[Group1]]] { def phrase: Parser[Option[List[Group1]]] = MSCONSMessage04B.group1.asInstanceOf[Parser[Option[List[Group1]]]] }
            .parseAndCheck (
                "RFF+Z13:13008'",
                group1 =>
                {
                    assert (group1.nonEmpty)
                    assert (group1.get.length == 1)
                    val item1 = group1.get.head
                    assert (item1.rff.referenceCodeQualifier.contains ("Z13"))
                    assert (item1.rff.referenceIdentifier.contains ("13008"))
                    assert (item1.rff.documentLineIdentifier.isEmpty)
                    assert (item1.rff.referenceVersionIdentifier.isEmpty)
                    assert (item1.rff.revisionIdentifier.isEmpty)
                    assert (item1.dtm_p.isEmpty)
                }
            )
    }

    test ("NAD")
    {
        new Mock[NAD] { def phrase: Parser[NAD] = MSCONSMessage04B.nad.asInstanceOf[Parser[NAD]] }
            .parseAndCheck (
                "NAD+MS+12X-SAK-N------6::293'",
                nad =>
                {
                    assert (nad.partyFunctionCodeQualifier.contains ("MS"))
                    assert (nad.partyIdentificationDetails.isDefined)
                    val details = nad.partyIdentificationDetails.get
                    assert (details.partyIdentifier.contains ("12X-SAK-N------6"))
                    assert (details.codeListIdetificationCode.isEmpty)
                    assert (details.codeListResponsibleAgencyCode.contains ("293"))
                }
            )
    }

    test ("Group2")
    {
        new Mock[Option[List[Group2]]] { def phrase: Parser[Option[List[Group2]]] = MSCONSMessage04B.group2.asInstanceOf[Parser[Option[List[Group2]]]] }
            .parseAndCheck (
                "NAD+MS+12X-SAK-N------6::293'",
                group2 =>
                {
                    assert (group2.nonEmpty)
                    assert (group2.get.length == 1)
                    val g2 = group2.get.head
                    val nad = g2.nad
                    assert (nad.partyFunctionCodeQualifier.contains ("MS"))
                    assert (nad.partyIdentificationDetails.isDefined)
                    val details = nad.partyIdentificationDetails.get
                    assert (details.partyIdentifier.contains ("12X-SAK-N------6"))
                    assert (details.codeListIdetificationCode.isEmpty)
                    assert (details.codeListResponsibleAgencyCode.contains ("293"))
                    assert (g2.group3.isEmpty)
                    assert (g2.group4.isEmpty)
                }
            )
    }

    test ("Group5")
    {
        new Mock[List[Group5]] { def phrase: Parser[List[Group5]] = MSCONSMessage04B.group5.asInstanceOf[Parser[List[Group5]]] }
            .parseAndCheck (
                "NAD+DP'LOC+172+CH1008801234500000000000000113813'DTM+163:201912140000?+01:303'DTM+164:201912140000?+01:303'LIN+1'PIA+5+1-1?:1.29.0*255:SRW'QTY+220:36.300'DTM+163:201912140000?+01:303'DTM+164:201912140015?+01:303'",
                group5 =>
                {
                    assert (group5.nonEmpty)
                    assert (group5.length == 1)
                    val g5 = group5.head
                    val nad = g5.nad
                    assert (nad.partyFunctionCodeQualifier.contains ("DP"))
                    val group6 = g5.group6
                    assert (group6.nonEmpty)
                    assert (group6.length == 1)
                    val g6 = group6.head
                    val loc = g6.loc
                    assert (loc.locationFunctionCodeQualifier == "172")
                    assert (loc.locationIdentification.isDefined)
                    val id = loc.locationIdentification.get
                    assert (id.locationIdentifier.contains ("CH1008801234500000000000000113813"))
                    assert (id.codeListIdentificationCode.isEmpty)
                    assert (id.codeListResponsibleAgencyCode.isEmpty)
                    assert (id.locationName.isEmpty)
                    assert (loc.relatedLocationOneIdentification.isEmpty)
                    assert (loc.relatedLocationTwoIdentification.isEmpty)
                    assert (loc.relationCode.isEmpty)
                    assert (g6.dtm.nonEmpty)

                    val dtms = g6.dtm.get
                    assert (dtms.nonEmpty)
                    assert (dtms.length == 2)
                    var dtm = dtms.head
                    assert (dtm.functionCodeQualifier == "163")
                    assert (dtm.text.contains ("201912140000+01"))
                    assert (dtm.formatCode.contains ("303"))
                    var calendar = dtm.getTime
                    assert (calendar.get (Calendar.YEAR) == 2019)
                    assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                    assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                    assert (calendar.get (Calendar.HOUR) == 0)
                    assert (calendar.get (Calendar.MINUTE) == 0)
                    dtm = dtms.tail.head
                    assert (dtm.functionCodeQualifier == "164")
                    assert (dtm.text.contains ("201912140000+01"))
                    assert (dtm.formatCode.contains ("303"))
                    calendar = dtm.getTime
                    assert (calendar.get (Calendar.YEAR) == 2019)
                    assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                    assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                    assert (calendar.get (Calendar.HOUR) == 0)
                    assert (calendar.get (Calendar.MINUTE) == 0)

                    assert (g6.group7.isEmpty)
                    assert (g6.group8.isEmpty)

                    assert (g6.group9.nonEmpty)
                    assert (g6.group9.get.nonEmpty)
                    assert (g6.group9.get.length == 1)
                    val g9 = g6.group9.get.head
                    val lin = g9.lin
                    assert (lin.lineItemIdentifier.contains ("1"))
                    assert (lin.actionCode.isEmpty)
                    assert (lin.itemNumberIdentification.isEmpty)
                    assert (lin.subLineInformation.isEmpty)
                    assert (lin.configurationLevelNumber.isEmpty)
                    assert (lin.configurationOperationCode.isEmpty)

                    assert (g9.pia.isDefined)
                    assert (g9.pia.get.nonEmpty)
                    assert (g9.pia.get.length == 1)
                    val pia = g9.pia.get.head
                    assert (pia.productIdentifierCodeQualifier == "5")
                    val id1 = pia.itemNumberIdentification1
                    assert (id1.itemIdentifier.contains ("1-1:1.29.0*255"))
                    assert (id1.itemTypeIdentificationCode.contains ("SRW"))
                    assert (id1.codeListIdentificationCode.isEmpty)
                    assert (id1.codeListResponsibleAgencyCode.isEmpty)
                    assert (pia.itemNumberIdentification2.isEmpty)
                    assert (pia.itemNumberIdentification3.isEmpty)
                    assert (pia.itemNumberIdentification4.isEmpty)
                    assert (pia.itemNumberIdentification5.isEmpty)

                    assert (g9.group10.nonEmpty)
                    assert (g9.group10.length == 1)
                    val g10 = g9.group10.head
                    val qty = g10.qty
                    assert (qty.quantityTypeCodeQualifier == "220")
                    assert (qty.quantity == "36.300")
                    assert (qty.measurementUnitCode.isEmpty)

                    assert (g10.dtm.nonEmpty)
                    assert (g10.dtm.get.length == 2)
                    dtm = g10.dtm.get.head
                    assert (dtm.functionCodeQualifier == "163")
                    assert (dtm.text.contains ("201912140000+01"))
                    assert (dtm.formatCode.contains ("303"))
                    calendar = dtm.getTime
                    assert (calendar.get (Calendar.YEAR) == 2019)
                    assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                    assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                    assert (calendar.get (Calendar.HOUR) == 0)
                    assert (calendar.get (Calendar.MINUTE) == 0)
                    dtm = g10.dtm.get.tail.head
                    assert (dtm.functionCodeQualifier == "164")
                    assert (dtm.text.contains ("201912140015+01"))
                    assert (dtm.formatCode.contains ("303"))
                    calendar = dtm.getTime
                    assert (calendar.get (Calendar.YEAR) == 2019)
                    assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                    assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                    assert (calendar.get (Calendar.HOUR) == 0)
                    assert (calendar.get (Calendar.MINUTE) == 15)

                    assert (g10.sts.isEmpty)

                    assert (g9.group11.isEmpty)
                }
            )
    }

    test ("Message")
    {
        val mscons = "UNB+UNOC:3+12X-SAK-N------6:500+12X-SAK-N------6:500+191215:0430+eslevu14572840++TL'" +
            "UNH+slevu14572840D+MSCONS:D:04B:UN:2.2e'BGM+7+slevu14572840D+9'DTM+137:201912140000:203'" +
            "RFF+Z13:13008'NAD+MS+12X-SAK-N------6::293'NAD+MR+12X-SAK-N------6::293'" +
            "UNS+D'NAD+DP'LOC+172+CH1008801234500000000000000113813'DTM+163:201912140000?+01:303'" +
            "DTM+164:201912140000?+01:303'LIN+1'PIA+5+1-1?:1.29.0*255:SRW'" +
            "QTY+220:36.300'DTM+163:201912140000?+01:303'DTM+164:201912140015?+01:303'" +
            "UNT+109+slevu14572840D'UNZ+1+eslevu14572840'"
        parseAndCheck (
            mscons,
            message =>
            {
                assert (message.bgm.documentMessageName.get.documentNameCode.contains ("7"))
                assert (message.bgm.documentMessageIdentification.get.documentIdentifier.contains ("slevu14572840D"))
                assert (message.bgm.messageFunctionCode.contains ("9"))
                assert (message.bgm.responseTypeCode.isEmpty)

                assert (message.dtm.functionCodeQualifier == "137")
                assert (message.dtm.text.contains ("201912140000"))
                assert (message.dtm.formatCode.contains ("203"))
                var calendar = message.dtm.getTime
                assert (calendar.get (Calendar.YEAR) == 2019)
                assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                assert (calendar.get (Calendar.HOUR) == 0)
                assert (calendar.get (Calendar.MINUTE) == 0)

                assert (message.group1.nonEmpty)
                assert (message.group1.get.length == 1)
                val item1 = message.group1.get.head
                assert (item1.rff.referenceCodeQualifier.contains ("Z13"))
                assert (item1.rff.referenceIdentifier.contains ("13008"))
                assert (item1.rff.documentLineIdentifier.isEmpty)
                assert (item1.rff.referenceVersionIdentifier.isEmpty)
                assert (item1.rff.revisionIdentifier.isEmpty)
                assert (item1.dtm_p.isEmpty)

                assert (message.group2.nonEmpty)
                assert (message.group2.get.length == 2)
                message.group2.get.zipWithIndex.foreach (
                    item =>
                    {
                        val (group2, index) = item
                        val nad = group2.nad
                        assert (nad.partyFunctionCodeQualifier.contains (if (0 == index) "MS" else "MR"))
                        assert (nad.partyIdentificationDetails.nonEmpty)
                        assert (nad.partyIdentificationDetails.get.partyIdentifier.contains ("12X-SAK-N------6"))
                        assert (nad.partyIdentificationDetails.get.codeListIdetificationCode.isEmpty)
                        assert (nad.partyIdentificationDetails.get.codeListResponsibleAgencyCode.contains ("293"))
                        assert (nad.nameAndAddress.isEmpty)
                        assert (nad.partyName.isEmpty)
                        assert (nad.street.isEmpty)
                        assert (nad.cityName.isEmpty)
                        assert (nad.countrySubdivisionDetails.isEmpty)
                        assert (nad.postalIdentificationCode.isEmpty)
                        assert (nad.countryIdentifier.isEmpty)
                        assert (group2.group3.isEmpty)
                        assert (group2.group4.isEmpty)
                    }
                )

                assert (message.uns.sectionIdentification == "D")

                val group5 = message.group5
                assert (group5.nonEmpty)
                assert (group5.length == 1)
                val g5 = group5.head
                val nad = g5.nad
                assert (nad.partyFunctionCodeQualifier.contains ("DP"))
                val group6 = g5.group6
                assert (group6.nonEmpty)
                assert (group6.length == 1)
                val g6 = group6.head
                val loc = g6.loc
                assert (loc.locationFunctionCodeQualifier == "172")
                assert (loc.locationIdentification.isDefined)
                val id = loc.locationIdentification.get
                assert (id.locationIdentifier.contains ("CH1008801234500000000000000113813"))
                assert (id.codeListIdentificationCode.isEmpty)
                assert (id.codeListResponsibleAgencyCode.isEmpty)
                assert (id.locationName.isEmpty)
                assert (loc.relatedLocationOneIdentification.isEmpty)
                assert (loc.relatedLocationTwoIdentification.isEmpty)
                assert (loc.relationCode.isEmpty)
                assert (g6.dtm.nonEmpty)

                val dtms = g6.dtm.get
                assert (dtms.nonEmpty)
                assert (dtms.length == 2)
                var dtm = dtms.head
                assert (dtm.functionCodeQualifier == "163")
                assert (dtm.text.contains ("201912140000+01"))
                assert (dtm.formatCode.contains ("303"))
                calendar = dtm.getTime
                assert (calendar.get (Calendar.YEAR) == 2019)
                assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                assert (calendar.get (Calendar.HOUR) == 0)
                assert (calendar.get (Calendar.MINUTE) == 0)
                dtm = dtms.tail.head
                assert (dtm.functionCodeQualifier == "164")
                assert (dtm.text.contains ("201912140000+01"))
                assert (dtm.formatCode.contains ("303"))
                calendar = dtm.getTime
                assert (calendar.get (Calendar.YEAR) == 2019)
                assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                assert (calendar.get (Calendar.HOUR) == 0)
                assert (calendar.get (Calendar.MINUTE) == 0)

                assert (g6.group7.isEmpty)
                assert (g6.group8.isEmpty)

                assert (g6.group9.nonEmpty)
                assert (g6.group9.get.nonEmpty)
                assert (g6.group9.get.length == 1)
                val g9 = g6.group9.get.head
                val lin = g9.lin
                assert (lin.lineItemIdentifier.contains ("1"))
                assert (lin.actionCode.isEmpty)
                assert (lin.itemNumberIdentification.isEmpty)
                assert (lin.subLineInformation.isEmpty)
                assert (lin.configurationLevelNumber.isEmpty)
                assert (lin.configurationOperationCode.isEmpty)

                assert (g9.pia.isDefined)
                assert (g9.pia.get.nonEmpty)
                assert (g9.pia.get.length == 1)
                val pia = g9.pia.get.head
                assert (pia.productIdentifierCodeQualifier == "5")
                val id1 = pia.itemNumberIdentification1
                assert (id1.itemIdentifier.contains ("1-1:1.29.0*255"))
                assert (id1.itemTypeIdentificationCode.contains ("SRW"))
                assert (id1.codeListIdentificationCode.isEmpty)
                assert (id1.codeListResponsibleAgencyCode.isEmpty)
                assert (pia.itemNumberIdentification2.isEmpty)
                assert (pia.itemNumberIdentification3.isEmpty)
                assert (pia.itemNumberIdentification4.isEmpty)
                assert (pia.itemNumberIdentification5.isEmpty)

                assert (g9.group10.nonEmpty)
                assert (g9.group10.length == 1)
                val g10 = g9.group10.head
                val qty = g10.qty
                assert (qty.quantityTypeCodeQualifier == "220")
                assert (qty.quantity == "36.300")
                assert (qty.measurementUnitCode.isEmpty)

                assert (g10.dtm.nonEmpty)
                assert (g10.dtm.get.length == 2)
                dtm = g10.dtm.get.head
                assert (dtm.functionCodeQualifier == "163")
                assert (dtm.text.contains ("201912140000+01"))
                assert (dtm.formatCode.contains ("303"))
                calendar = dtm.getTime
                assert (calendar.get (Calendar.YEAR) == 2019)
                assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                assert (calendar.get (Calendar.HOUR) == 0)
                assert (calendar.get (Calendar.MINUTE) == 0)
                dtm = g10.dtm.get.tail.head
                assert (dtm.functionCodeQualifier == "164")
                assert (dtm.text.contains ("201912140015+01"))
                assert (dtm.formatCode.contains ("303"))
                calendar = dtm.getTime
                assert (calendar.get (Calendar.YEAR) == 2019)
                assert (calendar.get (Calendar.MONTH) == 12 - 1) // months are 0 to 11
                assert (calendar.get (Calendar.DAY_OF_MONTH) == 14)
                assert (calendar.get (Calendar.HOUR) == 0)
                assert (calendar.get (Calendar.MINUTE) == 15)

                assert (g10.sts.isEmpty)

                assert (g9.group11.isEmpty)

                val unt = message.unt
                assert (unt.numberOfSegments == 109)
                assert (unt.messageReferenceNumber == "slevu14572840D")
            }
        )
    }
}
