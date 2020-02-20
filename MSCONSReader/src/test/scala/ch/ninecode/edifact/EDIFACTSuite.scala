package ch.ninecode.edifact

import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption

import org.scalatest.FunSuite

class EDIFACTSuite extends FunSuite
{
    test ("UNA 1 - default")
    {
        val scanner = SegmentScanner ("")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == '\'')
    }

    test ("UNA 2 - read default")
    {
        val scanner = SegmentScanner ("UNA:+.? 'XYZ")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == '\'')
        assert (!scanner.atEnd)
    }

    test ("UNA 3 - read non-default")
    {
        val scanner = SegmentScanner ("UNA:+,? 'XYZ")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == ',')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == '\'')
        assert (!scanner.atEnd)
    }

    test ("UNA 4 - terminator")
    {
        val scanner = SegmentScanner ("UNA:+.? ;XYZ;0")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == ';')
        val seg = scanner.first
        assertResult ("XYZ", "segment incorrect")(seg)
        assert (!scanner.atEnd)
    }

    test ("UNA 5 - none")
    {
        val scanner = SegmentScanner ("UNB'")
        val unb = scanner.first
        assertResult ("UNB", "segment incorrect")(unb)
    }

    test ("UNA 6 - none no terminator")
    {
        val scanner = SegmentScanner ("XYZ;")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == '\'')
        assert (!scanner.atEnd)
    }

    test ("ParseSegmentwithUNA")
    {
        val scanner = SegmentScanner ("UNA:+.? 'UNB'")
        val unb = scanner.first
        assertResult ("UNB", "segment incorrect")(unb)
    }

    test ("ParseSegmentwithoutSegmentTerminator")
    {
        val scanner = SegmentScanner ("UNB")
        val unb = scanner.first
        assertResult ("UNB", "segment incorrect")(unb)
    }

    test ("ParseSegmentWithEscapedDefaultReleaseCharacter")
    {
        val scanner = SegmentScanner ("XY?'Z'A")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '?')
        assert (scanner.una.segment_terminator == '\'')
        val seg = scanner.first
        assertResult ("XY'Z", "segment incorrect")(seg)
        assert (!scanner.atEnd)
    }

    test ("ParseSegmentWithNondefaultReleaseCharacter")
    {
        val scanner = SegmentScanner ("UNA:+.\\ 'XY\\'Z'A")
        assert (scanner.una.component_data_element_separator == ':')
        assert (scanner.una.data_element_separator == '+')
        assert (scanner.una.decimal_notification == '.')
        assert (scanner.una.release_character == '\\')
        assert (scanner.una.segment_terminator == '\'')
        val seg = scanner.first
        assertResult ("XY'Z", "segment incorrect")(seg)
        assert (!scanner.atEnd)    }

    test ("ParseMultipleSegments")
    {
        val scanner = SegmentScanner ("UNA:+.? 'FOO'BAR'")
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], rest) =>
                assert (result.length == 2)
                assert (result.head.name == "FOO")
                assert (result.tail.head.name == "BAR")
                assert (rest.atEnd)
            case message.Failure (msg, _) =>
                fail (s"parse failure: $msg")
            case message.Error (msg, _) =>
                fail (s"parse error: $msg")
        }
    }

    test ("ParseMultipleSegmentsTruncated")
    {
        val scanner = SegmentScanner ("UNA:+.? 'FOO'BA")
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], _) =>
                result.foreach ((x: Segment) => println (x.name))
                fail (s"parse should fail")
            case message.Failure (msg, _) =>
                fail (s"parse error: $msg")
            case message.Error (msg, _) =>
                assert (msg.startsWith ("illegal segment name"), "error message is wrong")
        }
    }

    test ("ParseSegmentWithReleaseCharacter")
    {
        val scanner = SegmentScanner ("DTM+163:200901010000?+01:303")
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], rest) =>
                assert (result.length == 1)
                assert (rest.atEnd)
                val segment = result.head
                assert (segment.name == "DTM")
                assert (segment.fields != null)
                assert (segment.fields.length == 1)
                val field = segment.fields.head
                assert (field.text == "163:200901010000+01:303")
                assert (field.submembers != null)
                assert (field.submembers.length == 3)
                assert (field.submembers.head.text == "163")
                assert (field.submembers.tail.head.text == "200901010000+01")
                assert (field.submembers.tail.tail.head.text == "303")
            case message.Failure (msg, _) =>
                fail (s"parse failure: $msg")
            case message.Error (msg, _) =>
                fail (s"parse error: $msg")
        }
    }

    test ("BGM")
    {
    }

    test ("ParseSample")
    {
        val before = System.nanoTime

        val path = FileSystems.getDefault.getPath ("data/MSCONS_LG_12X-0000000858-F_12X-0000000858-F_20140314_1407300597853.txt")
        val file = FileChannel.open (path, StandardOpenOption.READ)
        val size = file.size ()
        val buffer = file.map (FileChannel.MapMode.READ_ONLY, 0L, size)
        file.close ()

        val scanner = SegmentScanner (buffer)
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], _) =>
                assertResult ("UNB", "name incorrect") (result.head.name)
            case message.Failure (msg, _) =>
                fail (msg)
            case message.Error (msg, _) =>
                fail (msg)
        }

        val after = System.nanoTime
        info ("reading %d bytes took %g seconds".format (size, (after - before) / 1e9))
    }
}

