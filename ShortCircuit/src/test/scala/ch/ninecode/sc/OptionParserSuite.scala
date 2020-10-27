package ch.ninecode.sc

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer

import org.junit.Test

import ch.ninecode.testutil.Using

class OptionParserSuite extends Using
{
    val FILE_DEPOT = "data/"
    val JSON = "test.json"
    val FILE = s"$FILE_DEPOT$JSON"
    val UNUSED: FuseData = FuseData(
        Array(
            FuseTable(
                "Not Used",
                Array(
                    Amp(0.0, 0.0)
                )
            )
        )
    )

    @Test def validFuses ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |            { "ik": 0.0, "rating": 0.0 },
               |            { "ik": 65.0, "rating": 25.0 },
               |            { "ik": 105.0, "rating": 40.0 },
               |            { "ik": 140.0, "rating": 50.0 },
               |            { "ik": 180.0, "rating": 63.0 },
               |            { "ik": 240.0, "rating": 80.0 },
               |            { "ik": 320.0, "rating": 100.0 },
               |            { "ik": 380.0, "rating": 125.0 },
               |            { "ik": 500.0, "rating": 160.0 },
               |            { "ik": 650.0, "rating": 200.0 },
               |            { "ik": 800.0, "rating": 250.0 },
               |            { "ik": 1050.0, "rating": 315.0 },
               |            { "ik": 1300.0, "rating": 400.0 },
               |            { "ik": 1750.0, "rating": 500.0 },
               |            { "ik": 2400.0, "rating": 630.0 }
               |        ]
               |    }
               |}""".stripMargin,
            FuseData(
                Array(
                    FuseTable(
                        "DIN",
                        Array(
                            Amp(0.0, 0.0),
                            Amp(65.0, 25.0),
                            Amp(105.0, 40.0),
                            Amp(140.0, 50.0),
                            Amp(180.0, 63.0),
                            Amp(240.0, 80.0),
                            Amp(320.0, 100.0),
                            Amp(380.0, 125.0),
                            Amp(500.0, 160.0),
                            Amp(650.0, 200.0),
                            Amp(800.0, 250.0),
                            Amp(1050.0, 315.0),
                            Amp(1300.0, 400.0),
                            Amp(1750.0, 500.0),
                            Amp(2400.0, 630.0)
                        )
                    )
                )
            ),
            ""
        )
    }

    def checkFuseJSON (json: String, expected: FuseData, error: String)
    {
        writeFuseJSON(json)
        val parser = MockShortCircuitOptionsParser()
        parser.parse(Array("--fuse_table", FILE), ShortCircuitOptions()) match
        {
            case Some(options) =>
                assert(options.fuse_table.toString == expected.toString, "fuse table incorrect")
                assert(parser.stderr.isEmpty)
                assert(parser.stdout.isEmpty)
            case None =>
                assert(parser.stderr.contains(error))
                assert(parser.stdout.isEmpty)
        }
        delete(FILE)
    }

    def delete (filename: String): Unit =
    {
        val _ = new File(filename).delete
    }

    def writeFuseJSON (json: String): Unit =
    {
        using(new PrintWriter(new File(FILE), "UTF-8"))
        {
            writer => writer.write(json)
        }
    }

    @Test def extraComma ()
    {
        val jsonhelp = """Expected tokens are: [CURLYOPEN, SQUAREOPEN, STRING, NUMBER, TRUE, FALSE, NULL]"""
        val jsonerror = s"""Invalid token=SQUARECLOSE at (line no=7, column no=9, offset=180). $jsonhelp"""
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |            { "ik": 0.0, "rating": 0.0 },
               |            { "ik": 65.0, "rating": 25.0 },
               |            { "ik": 105.0, "rating": 40.0 },
               |        ]
               |    }
               |}""".stripMargin,
            UNUSED,
            s"Option --fuse_table failed when given 'data/test.json'. non valid input for fuse mapping table ($jsonerror)"
        )
    }

    @Test def missingRating ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |            { "ik": 0.0, "rating": 0.0 },
               |            { "ik": 65.0, "rating": 25.0 },
               |            { "ik": 105.0, "rateing": 40.0 }
               |        ]
               |    }
               |}""".stripMargin,
            UNUSED,
            "Option --fuse_table failed when given 'data/test.json'. missing rating element in \"{\"ik\":105.0,\"rateing\":40.0}\""
        )
    }

    @Test def wrongTypeMapping ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": [
               |        { "ik": 0.0, "rating": 0.0 },
               |        { "ik": 65.0, "rating": 25.0 },
               |        { "ik": 105.0, "rating": 40.0 }
               |    ]
               |}""".stripMargin,
            UNUSED,
            "Option --fuse_table failed when given 'data/test.json'. JSON member \"fuse_mapping\" is not a JSON object (type \"ARRAY\") for fuse mapping table"
        )
    }

    @Test def wrongTypeTable ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": {
               |            "a": { "ik": 0.0, "rating": 0.0 },
               |            "b": { "ik": 65.0, "rating": 25.0 },
               |            "c": { "ik": 105.0, "rating": 40.0 }
               |        }
               |    }
               |}""".stripMargin,
            UNUSED,
            "Option --fuse_table failed when given 'data/test.json'. expected JSON array type, got \"OBJECT\" for fuse mapping table"
        )
    }

    @Test def wrongTypeElement ()
    {
        val oops = """expected JSON object type, got "ARRAY" for fuse mapping table"""
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |            [ 0.0, 0.0 ],
               |            [ 65.0, 25.0 ]
               |        ]
               |    }
               |}""".stripMargin,
            UNUSED,
            s"Option --fuse_table failed when given 'data/test.json'. $oops,$oops"
        )
    }

    @Test def wrongTypeRating ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |            { "ik": 0.0, "rating": 0.0 },
               |            { "ik": 65.0, "rating": 25.0 },
               |            { "ik": 105.0, "rating": "40.0" }
               |        ]
               |    }
               |}""".stripMargin,
            UNUSED,
            "Option --fuse_table failed when given 'data/test.json'. unexpected JSON type for {\"ik\":105.0,\"rating\":\"40.0\"} element (\"STRING\")"
        )
    }

    @Test def emptyArray ()
    {
        checkFuseJSON(
            s"""{
               |    "fuse_mapping": {
               |        "DIN": [
               |        ]
               |    }
               |}""".stripMargin,
            UNUSED,
            "Option --fuse_table failed when given 'data/test.json'. no fuse elements found for fuse mapping table"
        )
    }

    case class MockShortCircuitOptionsParser () extends ShortCircuitOptionsParser(ShortCircuitOptions())
    {
        val stderr = new ArrayBuffer[String]()
        val stdout = new ArrayBuffer[String]()

        override def reportError (msg: String): Unit = stderr.append(msg)

        override def reportWarning (msg: String): Unit = stdout.append(msg)

        override def displayToErr (msg: String): Unit =
        {}
    }
}

