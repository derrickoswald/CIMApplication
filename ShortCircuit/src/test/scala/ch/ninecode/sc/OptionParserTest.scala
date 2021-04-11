package ch.ninecode.sc

import java.io.File
import java.io.PrintWriter

import scala.collection.mutable.ArrayBuffer

import org.junit.Test

import ch.ninecode.testutil.Using

class OptionParserTest extends Using
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

    @Test def round_trip ()
    {
        val fuse_table = FuseData(
            Array(
                FuseTable(
                    "DIN",
                    Array(
                        Amp(0, 0), // failsafe fallback for currents less than 65A
                        Amp(65, 25),
                        Amp(105, 40),
                        Amp(140, 50),
                        Amp(180, 63),
                        Amp(240, 80),
                        Amp(320, 100),
                        Amp(380, 125),
                        Amp(500, 160),
                        Amp(650, 200),
                        Amp(800, 250),
                        Amp(1050, 315),
                        Amp(1300, 400),
                        Amp(1750, 500),
                        Amp(2400, 630)
                    )
                ),
                FuseTable(
                    "SEV",
                    Array(
                        Amp(0, 0), // failsafe fallback for currents less than 200A
                        Amp(200, 60),
                        Amp(250, 75),
                        Amp(300, 100),
                        Amp(340, 125),
                        Amp(500, 150),
                        Amp(600, 200),
                        Amp(720, 250),
                        Amp(850, 300),
                        Amp(1150, 400)
                    )
                )
            )
        )
        val trafos = Array("TX0001", "TX0002")
        val options = ShortCircuitOptions (
            fuse_table = fuse_table,
            trafos = trafos
        )
        val json = options.toJSON
        val expected =
"""{
  "main_options" : {
    "application" : "SparkApplication",
    "version" : "2.12-3.0.1-3.0.5",
    "valid" : true,
    "unittest" : false
  },
  "spark_options" : {
    "master" : "local[*]",
    "options" : {
      "spark.serializer" : "org.apache.spark.serializer.KryoSerializer",
      "spark.sql.catalog.casscatalog" : "com.datastax.spark.connector.datasource.CassandraCatalog",
      "spark.kryo.registrator" : "ch.ninecode.cim.CIMRegistrator",
      "spark.graphx.pregel.checkpointInterval" : "8",
      "spark.ui.showConsoleProgress" : "false",
      "spark.sql.debug.maxToStringFields" : "250"
    },
    "log" : "OFF",
    "jars" : [ ],
    "kryo" : [ ],
    "checkpoint" : ""
  },
  "cim_options" : {
    "topology" : true,
    "topology_options" : {
      "identify_islands" : true,
      "force_retain_switches" : "Unforced",
      "force_retain_fuses" : "ForceTrue",
      "force_switch_separate_islands" : "Unforced",
      "force_fuse_separate_islands" : "Unforced",
      "default_switch_open_state" : false,
      "debug" : false,
      "storage" : "MEMORY_AND_DISK_SER"
    },
    "about" : false,
    "normalize" : false,
    "dedup" : false,
    "edges" : false,
    "join" : false,
    "debug" : false,
    "splitsize" : 67108864,
    "cache" : "",
    "storage" : "MEMORY_AND_DISK_SER",
    "files" : [ ]
  },
  "cassandra_options" : {
    "host" : "localhost",
    "port" : 9042
  },
  "id" : "",
  "verbose" : true,
  "description" : "",
  "default_short_circuit_power_max" : 2.0E8,
  "default_short_circuit_impedance_max" : "0.43778578-1.20280655j",
  "default_short_circuit_angle_max" : null,
  "default_short_circuit_power_min" : 1.0E8,
  "default_short_circuit_impedance_min" : "0.87557157-2.40561311j",
  "default_short_circuit_angle_min" : null,
  "default_transformer_power_rating" : 630000.0,
  "default_transformer_impedance" : "0.0059+0.03956248j",
  "base_temperature" : 20.0,
  "low_temperature" : 60.0,
  "high_temperature" : 90.0,
  "cmax" : 1.0,
  "cmin" : 0.9,
  "worstcasepf" : true,
  "cosphi" : 0.5,
  "fuse_table" : {
    "DIN" : [ {
      "ik" : 0.0,
      "rating" : 0.0
    }, {
      "ik" : 65.0,
      "rating" : 25.0
    }, {
      "ik" : 105.0,
      "rating" : 40.0
    }, {
      "ik" : 140.0,
      "rating" : 50.0
    }, {
      "ik" : 180.0,
      "rating" : 63.0
    }, {
      "ik" : 240.0,
      "rating" : 80.0
    }, {
      "ik" : 320.0,
      "rating" : 100.0
    }, {
      "ik" : 380.0,
      "rating" : 125.0
    }, {
      "ik" : 500.0,
      "rating" : 160.0
    }, {
      "ik" : 650.0,
      "rating" : 200.0
    }, {
      "ik" : 800.0,
      "rating" : 250.0
    }, {
      "ik" : 1050.0,
      "rating" : 315.0
    }, {
      "ik" : 1300.0,
      "rating" : 400.0
    }, {
      "ik" : 1750.0,
      "rating" : 500.0
    }, {
      "ik" : 2400.0,
      "rating" : 630.0
    } ],
    "SEV" : [ {
      "ik" : 0.0,
      "rating" : 0.0
    }, {
      "ik" : 200.0,
      "rating" : 60.0
    }, {
      "ik" : 250.0,
      "rating" : 75.0
    }, {
      "ik" : 300.0,
      "rating" : 100.0
    }, {
      "ik" : 340.0,
      "rating" : 125.0
    }, {
      "ik" : 500.0,
      "rating" : 150.0
    }, {
      "ik" : 600.0,
      "rating" : 200.0
    }, {
      "ik" : 720.0,
      "rating" : 250.0
    }, {
      "ik" : 850.0,
      "rating" : 300.0
    }, {
      "ik" : 1150.0,
      "rating" : 400.0
    } ]
  },
  "messagemax" : 5,
  "batchsize" : 10000,
  "trafos" : [ "TX0001", "TX0002" ],
  "cable_impedance_limit" : 5.0,
  "workdir" : "",
  "calculate_public_lighting" : false,
  "output" : "SQLite",
  "outputfile" : "results/shortcircuit.db",
  "keyspace" : "cimapplication",
  "replication" : 1
}"""
        assert (json == expected, "unexpected JSON")

        ShortCircuitOptions.fromJSON(json) match
        {
            case Right(options) =>
                // ToDo: change the Array type in FuseData and FuseTable to be Seq, so we can uses == comparison
                assert(options.fuse_table.toString == fuse_table.toString, "fuse table incorrect")
                assert(options.trafos.sameElements(trafos), "trafo table incorrect")
            case Left(errors) =>
                assert(false, s"parse errors $errors")
        }
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

