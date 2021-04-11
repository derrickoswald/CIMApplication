package ch.ninecode.lv

import java.io.File
import java.io.PrintWriter

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import ch.ninecode.lv.LowVoltage.main
import ch.ninecode.testutil.Unzip
import ch.ninecode.testutil.Using

class LowVoltageTests extends Using
{

    import LowVoltageTests.FILE_DEPOT
    import LowVoltageTests.FILENAME
    import LowVoltageTests.delete

    @Test def doBasic ()
    {
        val begin = System.nanoTime()

        val filename = s"$FILE_DEPOT$FILENAME.rdf"

        main(Array("--unittest", "--verbose", "--workdir", "./target/", "--cache", s"./target/$FILENAME", filename))

        val total = System.nanoTime()
        println(s"total: ${(total - begin) / 1e9} seconds")

        assert(new File("./target/TX0001/TX0001.glm").exists, "TX0001")
        assert(new File("./target/TX0002/TX0002.glm").exists, "TX0002")
        delete("./target/TX0001/input_data")
        delete("./target/TX0001/output_data")
        delete("./target/TX0001/TX0001.glm")
        delete("./target/TX0001")
        delete("./target/TX0002/input_data")
        delete("./target/TX0002/output_data")
        delete("./target/TX0002/TX0002.glm")
        delete("./target/TX0002")
    }

    @Test def doJSON (): Unit =
    {
        val filename = s"$FILE_DEPOT$FILENAME.rdf"
        val json =
s"""
{
  "main_options" : {
    "application" : "LowVoltage",
    "valid" : true,
    "unittest" : true
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
    "log" : "ERROR",
    "jars" : [ ],
    "kryo" : [ ],
    "checkpoint" : ""
  },
  "cim_options" : {
    "topology" : true,
    "topology_options" : {
      "identify_islands" : true,
      "force_retain_switches" : "Unforced",
      "force_retain_fuses" : "Unforced",
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
    "files" : [ "$filename" ]
  },
  "verbose" : true,
  "three" : false,
  "trafos" : [ ],
  "workdir" : "./target/"
}
"""
        val file = s"$FILE_DEPOT$FILENAME.json"
        using(new PrintWriter(new File(file), "UTF-8"))
        {
            writer =>
                writer.write(json)
        }

        main(Array("--json", file))

        assert(new File("./target/TX0001/TX0001.glm").exists, "TX0001")
        assert(new File("./target/TX0002/TX0002.glm").exists, "TX0002")
        delete("./target/TX0001/input_data")
        delete("./target/TX0001/output_data")
        delete("./target/TX0001/TX0001.glm")
        delete("./target/TX0001")
        delete("./target/TX0002/input_data")
        delete("./target/TX0002/output_data")
        delete("./target/TX0002/TX0002.glm")
        delete("./target/TX0002")
        delete(json)
    }

    @Test def doPartialJSON (): Unit =
    {
        val filename = s"$FILE_DEPOT$FILENAME.rdf"
        val json =
s"""
{
  "verbose" : true,
  "three" : true,
  "trafos" : [ "TX0002" ],
  "workdir" : "./target/"
}
"""
        val file = s"$FILE_DEPOT$FILENAME.json"
        using(new PrintWriter(new File(file), "UTF-8"))
        {
            writer =>
                writer.write(json)
        }

        main(Array("--json", file, "--unittest", filename))

        assert(!new File("./target/TX0001/TX0001.glm").exists, "TX0001")
        assert(new File("./target/TX0002/TX0002.glm").exists, "TX0002")
        delete("./target/TX0002/input_data")
        delete("./target/TX0002/output_data")
        delete("./target/TX0002/TX0002.glm")
        delete("./target/TX0002")
        delete(json)
    }
}

object LowVoltageTests extends Unzip
{
    val FILE_DEPOT = "data/"
    val FILENAME = "DemoData"

    def delete (filename: String): Unit =
    {
        val _ = new File(filename).delete
    }

    @BeforeClass()
    def unzip (): Unit =
    {
        if (!new File(s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    @AfterClass()
    def delete (): Unit =
    {
        val _ = new File(s"$FILE_DEPOT$FILENAME.rdf").delete
    }
}