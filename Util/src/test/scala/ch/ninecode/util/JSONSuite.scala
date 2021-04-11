package ch.ninecode.util

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel

import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue

/**
 * Exercise the JSON serialization of options classes.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class JSONSuite extends AnyFunSuite
{
    test("good main")
    {
        val good =
"""
{
    "application": "TestCase1",
    "version": "2.12-3.0.1-3.0.5",
    "valid": true,
    "unittest": true
}
"""
        MainOptions.fromJSON(good) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert(options.application == "TestCase1", "application")
                assert(options.version == "2.12-3.0.1-3.0.5", "version")
                assert(options.valid, "valid")
                assert(options.unittest, "unittest")
        }

    }

    test("bad main")
    {
        val bad =
"""
{
    "application": "TestCase2",
    "version": "42",
    "valid": true,
    "unittest": true
}
"""
        MainOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("$.version: does not match the regex pattern ^.+-.+-.+$"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("round-trip main")
    {
        val options = MainOptions (
            application = "TestCase3",
            version = "1-0-0",
        )
        val json = options.toJSON
        val expected =
"""{
  "application" : "TestCase3",
  "version" : "1-0-0",
  "valid" : true,
  "unittest" : false
}"""
        assert (json == expected, "unexpected JSON")

        MainOptions.fromJSON(json) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert (options.application == "TestCase3", "application")
                assert (options.version == "1-0-0", "version")
                assert (options.valid, "valid")
                assert (!options.unittest, "unittest")
        }
    }

    test("good spark")
    {
        val good =
"""
{
    "master": "spark://sandbox:7070",
    "options":
    {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.sql.catalog.casscatalog": "com.datastax.spark.connector.datasource.CassandraCatalog",
        "spark.kryo.registrator": "ch.ninecode.cim.CIMRegistrator",
        "spark.graphx.pregel.checkpointInterval": "8",
        "spark.ui.showConsoleProgress": "false",
        "spark.sql.debug.maxToStringFields": "250"
    },
    "log": "DEBUG",
    "jars":
    [
        "/code/CIMApplication/Simulation/Simulation.jar"
    ],
    "kryo":
    [
        "ch.ninecode.util.Complex"
    ],
    "checkpoint": "hdfs://sandbox:8020/workdir"
}
"""
        SparkOptions.fromJSON(good) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert(options.master == "spark://sandbox:7070", "master")
                assert(options.options.size == 6, "options")
                assert(options.options.forall(
                    x =>
                    {
                        val (key, value) = x
                        key match
                        {
                            case "spark.serializer" => value == "org.apache.spark.serializer.KryoSerializer"
                            case "spark.sql.catalog.casscatalog" => value == "com.datastax.spark.connector.datasource.CassandraCatalog"
                            case "spark.kryo.registrator" => value == "ch.ninecode.cim.CIMRegistrator"
                            case "spark.graphx.pregel.checkpointInterval" => value == "8"
                            case "spark.ui.showConsoleProgress" => value == "false"
                            case "spark.sql.debug.maxToStringFields" => value == "250"
                            case _ => false
                        }
                    }
                ), "options")
                assert(options.log == Level.DEBUG, "log")
                assert(options.jars.length == 1, "jars")
                assert(options.jars(0) == "/code/CIMApplication/Simulation/Simulation.jar", "jar")
                assert(options.kryo.length == 1, "kryo")
                assert(options.kryo(0) == Complex(0.0).getClass, "kryo class")
                assert(options.checkpoint == "hdfs://sandbox:8020/workdir", "checkpoint")
        }
    }

    test("bad type spark")
    {
        val bad_type =
"""
{
    "master": "local[*]",
    "options":
    {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.sql.catalog.casscatalog": "com.datastax.spark.connector.datasource.CassandraCatalog",
        "spark.kryo.registrator": "ch.ninecode.cim.CIMRegistrator",
        "spark.graphx.pregel.checkpointInterval": 8,
        "spark.ui.showConsoleProgress": false,
        "spark.sql.debug.maxToStringFields": 250
    },
    "log": "OFF",
    "jars": [],
    "kryo": [],
    "checkpoint": ""
}
"""
        SparkOptions.fromJSON(bad_type) match
        {
            case Left(errors) =>
                assert(errors.contains("$.options.spark.graphx.pregel.checkpointInterval: integer found, string expected"))
                assert(errors.contains("$.options.spark.ui.showConsoleProgress: boolean found, string expected"))
                assert(errors.contains("$.options.spark.sql.debug.maxToStringFields: integer found, string expected"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("bad class spark")
    {
        val bad_class =
"""
{
    "master": "local[*]",
    "options": {},
    "log": "DEBUG",
    "jars": [],
    "kryo":
    [
        "ch.ninecode.util.Bogus"
    ],
    "checkpoint": ""
}
"""
        SparkOptions.fromJSON(bad_class) match
        {
            case Left(errors) =>
                assert(errors.contains("java.lang.ClassNotFoundException: ch.ninecode.util.Bogus"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("bad log spark")
    {
        val bad_class =
"""
{
    "master": "local[*]",
    "options": {},
    "log": "TOTAL",
    "jars": [],
    "kryo": [],
    "checkpoint": ""
}
"""
        SparkOptions.fromJSON(bad_class) match
        {
            case Left(errors) =>
                assert(errors.contains("pattern 1023 $.log [^ALL$|^DEBUG$|^INFO$|^WARN$|^ERROR$|^FATAL$|^OFF$|^TRACE$]  $.log: does not match the regex pattern ^ALL$|^DEBUG$|^INFO$|^WARN$|^ERROR$|^FATAL$|^OFF$|^TRACE$"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("round-trip spark")
    {
        val options = SparkOptions (
            master = "spark://sandbox:7077",
            log = Level.INFO
        )
        val json = options.toJSON
        val expected =
"""{
  "master" : "spark://sandbox:7077",
  "options" : {
    "spark.serializer" : "org.apache.spark.serializer.KryoSerializer",
    "spark.sql.catalog.casscatalog" : "com.datastax.spark.connector.datasource.CassandraCatalog",
    "spark.kryo.registrator" : "ch.ninecode.cim.CIMRegistrator",
    "spark.graphx.pregel.checkpointInterval" : "8",
    "spark.ui.showConsoleProgress" : "false",
    "spark.sql.debug.maxToStringFields" : "250"
  },
  "log" : "INFO",
  "jars" : [ ],
  "kryo" : [ ],
  "checkpoint" : ""
}"""
        assert (json == expected, "unexpected JSON")

        SparkOptions.fromJSON(json) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert (options.master == "spark://sandbox:7077", "master")
                assert (options.log ==  Level.INFO, "log")
        }
    }

    test("good cassandra")
    {
        val good =
"""
{
    "host": "beach",
    "port": 42
}
"""
        CassandraOptions.fromJSON(good) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert(options.host == "beach")
                assert(options.port == 42)
        }
    }

    test("bad port cassandra")
    {
        val bad =
"""
{
    "host": "beach",
    "port": 3.14159
}
"""
        CassandraOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("type 1029 $.port [number,integer]  $.port: number found, integer expected"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("negative port cassandra")
    {
        val bad =
"""
{
    "host": "beach",
    "port": -42
}
"""
        CassandraOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("minimum 1015 $.port [0]  $.port: must have a minimum value of 0"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("good cimreader")
    {
        val good =
"""
{
    "topology": true,
    "topology_options":
    {
        "identify_islands": true,
        "force_retain_fuses": "ForceTrue"
    },
    "dedup": true
}
"""
        CIMReaderOptions.fromJSON(good) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert(options.topology)
                assert(options.topology_options.identify_islands)
                assert(options.topology_options.force_retain_fuses == ForceTrue)
        }
    }

    test("bad topology option cimreader")
    {
        val bad =
"""
{
    "topology": true,
    "topology_options":
    {
        "identify_islands": true,
        "force_retain_fuses": true
    }
}
"""
        CIMReaderOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("type 1029 $.topology_options.force_retain_fuses [boolean,string]  $.topology_options.force_retain_fuses: boolean found, string expected"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("bad topology option 2 cimreader")
    {
        val bad =
"""
{
    "topology": true,
    "topology_options":
    {
        "identify_islands": true,
        "force_retain_fuses": "YesPlease"
    }
}
"""
        CIMReaderOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("pattern 1023 $.topology_options.force_retain_fuses [^ForceTrue$|^ForceFalse$|^Unforced$]  $.topology_options.force_retain_fuses: does not match the regex pattern ^ForceTrue$|^ForceFalse$|^Unforced$"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("bad split size cimreader")
    {
        val bad =
"""
{
    "splitsize": 42
}
"""
        CIMReaderOptions.fromJSON(bad) match
        {
            case Left(errors) =>
                assert(errors.contains("minimum 1015 $.splitsize [1048576]  $.splitsize: must have a minimum value of 1048576"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("round-trip cimreader")
    {
        val options = CIMReaderOptions (
            topology = true,
            topology_options = CIMTopologyOptions (identify_islands = true, storage = StorageLevel.NONE),
            dedup = true,
            storage = StorageLevel.NONE
        )
        val json = options.toJSON
        val expected =
"""{
  "topology" : true,
  "topology_options" : {
    "identify_islands" : true,
    "force_retain_switches" : "Unforced",
    "force_retain_fuses" : "Unforced",
    "force_switch_separate_islands" : "Unforced",
    "force_fuse_separate_islands" : "Unforced",
    "default_switch_open_state" : false,
    "debug" : false,
    "storage" : "NONE"
  },
  "about" : false,
  "normalize" : false,
  "dedup" : true,
  "edges" : false,
  "join" : false,
  "debug" : false,
  "splitsize" : 67108864,
  "cache" : "",
  "storage" : "NONE",
  "files" : [ ]
}"""
        assert (json == expected, "unexpected JSON")

        CIMReaderOptions.fromJSON(json) match
        {
            case Left(errors) => fail(errors)
            case Right(options) =>
                assert (options.topology_options.identify_islands, "islands")
                assert (options.topology_options.storage == StorageLevel.NONE, "storage in topology options")
                assert (options.dedup, "dedup")
                assert (options.storage == StorageLevel.NONE, "storage in cimreader options")
        }
    }

    test("complex string")
    {
        val complex = """"3+4j""""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => fail(s"parse complex string: $errors")
            case Right(c) => assert(c == Complex(3,4))
        }
    }

    test("bad complex string")
    {
        val complex = """"3x+4j""""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => assert(errors.contains("$: does not match the regex pattern ^((?:[+-]?(?:[0-9]*\\.?[0-9]*)|(?:\\.[0-9]+))(?:[Ee][+-]?[0-9]+)?)?[\\s]*([+-<])[\\s]*[ij]?((?:[+-]?(?:[0-9]*\\.?[0-9]*)|(?:\\.[0-9]+))(?:[Ee][+-]?[0-9]+)?)([ijd°])?$"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("complex object")
    {
        val complex = """{"re":3,"im":4}"""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => fail(s"parse complex object: $errors")
            case Right(c) => assert(c == Complex(3,4))
        }
    }

    test("bad complex object")
    {
        val complex = """{"real":3,"imaginary":4}"""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => assert(errors.contains("additionalProperties 1001 $ [real]  $.real: is not defined in the schema and the schema does not allow additional properties"))
            case Right(_) => fail("bad JSON")
        }
    }

    test("complex number")
    {
        val complex = """42.0"""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => fail(s"parse complex number: $errors")
            case Right(c) => assert(c == Complex(42.0))
        }
    }

    test("complex integer")
    {
        val complex = """42"""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => fail(s"parse complex integer: $errors")
            case Right(c) => assert(c == Complex(42))
        }
    }

    test("complex polar string")
    {
        val complex = """"17<13°""""
        Complex.fromJSON(complex) match
        {
            case Left(errors) => fail(s"parse complex polar string: $errors")
            case Right(c) => assert(c == Complex("17<13°"))
        }
    }
}
