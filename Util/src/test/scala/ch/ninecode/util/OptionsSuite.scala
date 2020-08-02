package ch.ninecode.util

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel
import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced

class OptionsSuite extends AnyFunSuite
{
    test ("help")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        (new OptionsMainBasic).main (Array ("--unittest", "--help"))
    }

    test ("basic options")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        {
            override def run (options: OptionsSuiteOptions): Unit =
            {
                assert (options.main_options.unittest, "unit test")
                assert (options.spark_options.log == Level.DEBUG, "log")
                assert (options.cim_options.dedup, "dedup")
                assert (options.cim_options.topology, "topology")
                assert (options.cim_options.topology_options.identify_islands, "islands")
                assert (options.cim_options.topology_options.force_retain_fuses == ForceTrue, "retain_fuse")
            }
        }
        (new OptionsMainBasic).main (Array ("--unittest", "--log", "DEBUG", "--dedup", "--topology", "--identify_islands", "--retain_fuse", "ForceTrue"))
    }

    test ("storage level")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        {
            override def run (options: OptionsSuiteOptions): Unit =
            {
                assert (options.cim_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "cim storage")
                assert (options.cim_options.topology_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "topology storage")
            }
        }
        (new OptionsMainBasic).main (Array ("--unittest", "--storage", "MEMORY_AND_DISK_SER_2"))
    }

    test ("map")
    {
        val COMMA = ","
        val EQUAL = "="
        val FILES = "file1.rdf,file2.rdf"
        val required = Map[String,String] (
            "StorageLevel" -> "MEMORY_AND_DISK_SER_2",
            "ch.ninecode.cim.do_topo" -> "true",
            "ch.ninecode.cim.do_topo_islands" -> "true",
            "ch.ninecode.cim.force_retain_switches" -> "Unforced",
            "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
            "ch.ninecode.cim.do_deduplication" -> "true",
            "ch.ninecode.cim.split_maxsize" -> "134217728"
        )
        val required_as_string = required.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)
        class OptionsMainBasic extends OptionsSuiteMain
        {
            override def run (options: OptionsSuiteOptions): Unit =
            {
                val have = options.cim_options.toMap
                for (pair <- required) assert (pair._2 == have (pair._1), pair._1)
                assert (have ("path") == FILES, "files")
                assert (options.cim_options.files == Seq (FILES), "files")
                assert (options.cim_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "cim storage")
                assert (options.cim_options.topology_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "topology storage")
                assert (options.cim_options.topology, "topology")
                assert (options.cim_options.topology_options.identify_islands, "islands")
                assert (options.cim_options.topology_options.force_retain_switches == Unforced, "retain_switch")
                assert (options.cim_options.topology_options.force_retain_fuses == ForceTrue, "retain_fuse")
                assert (options.cim_options.dedup, "dedup")
                assert (options.cim_options.splitsize == 134217728L, "dedup")
            }
        }

        (new OptionsMainBasic).main (Array ("--unittest", "--cim_options", required_as_string, "file1.rdf,file2.rdf"))
    }
}
