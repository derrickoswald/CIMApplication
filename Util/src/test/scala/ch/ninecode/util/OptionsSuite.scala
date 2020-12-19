package ch.ninecode.util

import org.apache.log4j.Level
import org.apache.spark.storage.StorageLevel
import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.cim.ForceTrue

class OptionsSuite extends AnyFunSuite
{
    test("help")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        (new OptionsMainBasic).main(Array("--unittest", "--help"))
    }

    test("basic options")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        {
            override def run (options: OptionsSuiteOptions): Unit =
            {
                assert(options.main_options.unittest, "unit test")
                assert(options.spark_options.log == Level.DEBUG, "log")
                assert(options.cim_options.dedup, "dedup")
                assert(options.cim_options.topology, "topology")
                assert(options.cim_options.topology_options.identify_islands, "islands")
                assert(options.cim_options.topology_options.force_retain_fuses == ForceTrue, "retain_fuse")
            }
        }
        (new OptionsMainBasic).main(Array("--unittest", "--log", "DEBUG", "--dedup", "--topology", "--identify_islands", "--retain_fuse", "ForceTrue"))
    }

    test("storage level")
    {
        class OptionsMainBasic extends OptionsSuiteMain
        {
            override def run (options: OptionsSuiteOptions): Unit =
            {
                assert(options.cim_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "cim storage")
                assert(options.cim_options.topology_options.storage == StorageLevel.MEMORY_AND_DISK_SER_2, "topology storage")
            }
        }
        (new OptionsMainBasic).main(Array("--unittest", "--storage", "MEMORY_AND_DISK_SER_2"))
    }
}
