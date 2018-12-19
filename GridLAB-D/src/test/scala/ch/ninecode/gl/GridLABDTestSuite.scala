package ch.ninecode.gl

import java.io.File
import java.text.SimpleDateFormat
import java.util.TimeZone

import scala.collection.mutable
import org.scalatest.BeforeAndAfter
import org.apache.spark.sql.SparkSession

class GridLABDTestSuite extends SparkSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData.rdf"
    val filename: String = FILE_DEPOT + FILENAME1

    before
    {
        // unpack the zip files
        if (!new File (filename).exists)
            new Unzip ().unzip (FILE_DEPOT + "DemoData.zip", FILE_DEPOT)
    }

    after
    {
        new File (filename).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.do_topo", "true")
            options.put ("ch.ninecode.cim.do_topo_islands", "true")
            options.put ("ch.ninecode.cim.force_retain_switches", "ForceTrue")
            options.put ("ch.ninecode.cim.force_retain_fuses", "ForceTrue")
            options.put ("ch.ninecode.cim.force_switch_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.default_switch_open_state", "false")
            options.put ("ch.ninecode.cim.debug", "true")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

            val gen = new GLMGenerator ()
            val text = gen.make_glm ()

            val generate = System.nanoTime ()
            println ("generate: " + (generate - read) / 1e9 + " seconds")

            assert (text ==
                """// gridlabd.glm
// GridLAB-D
//*********************************************

        module tape;

        module powerflow
        {
            solver_method NR;
            default_maximum_voltage_error 10e-6;
            NR_iteration_limit 500;
            NR_superLU_procs 16;
            nominal_frequency 50;
        };

        clock
        {
            timezone "UTC0UTC";
            starttime "2017-05-08 10:00:00 UTC";
            stoptime "2017-05-08 10:00:00 UTC";
        };

        class player
        {
            complex value;
        };
""")
            println ("total: " + (generate - start) / 1e9 + " seconds")
    }

    test ("Solve")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.do_topo", "true")
            options.put ("ch.ninecode.cim.do_topo_islands", "true")
            options.put ("ch.ninecode.cim.force_retain_switches", "ForceTrue")
            options.put ("ch.ninecode.cim.force_retain_fuses", "ForceTrue")
            options.put ("ch.ninecode.cim.force_switch_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.default_switch_open_state", "false")
            options.put ("ch.ninecode.cim.debug", "true")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

            val gen = new GLMGenerator ()
            val gridlabd = new GridLABD (session, workdir = "target/")
            gridlabd.export (gen)

            val generate = System.nanoTime ()
            println ("generate: " + (generate - read) / 1e9 + " seconds")

            val glm = session.sparkContext.parallelize (List (gen.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            println ("generate: " + (solve - generate) / 1e9 + " seconds")

            assert (results._1, "should succeed")
            assert (results._2.isEmpty, "no errors")

            println ("total: " + (solve - start) / 1e9 + " seconds")
    }

    test ("Error")
    {
        class broken1 extends GLMGenerator
        {
            val date_format: SimpleDateFormat =
            {
                val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
                format.setTimeZone (TimeZone.getTimeZone ("UTC"))
                format
            }

            override def name: String = "broken1"
            override def prefix: String =
            {
                val t0 = date_format.format (start_time.getTime)
                val t1 = date_format.format (finish_time.getTime)
                val preamble =
                    """// %s.glm
                      |// %s
                      |//*********************************************
                      |
                      |        module tape;
                      |
                      |        module powerflow
                      |        {
                      |            solver_method foo;
                      |            default_maximum_voltage_error 10e-6;
                      |            NR_iteration_limit 500;
                      |            NR_superLU_procs 16;
                      |            nominal_frequency 50;
                      |        };
                      |
                      |        clock
                      |        {
                      |            timezone "%s";
                      |            starttime "%s";
                      |            stoptime "%s";
                      |        };
                      |
                      |        class player
                      |        {
                      |            complex value;
                      |        };
                      |""".stripMargin.format (name, header, tzString, t0, t1)
                preamble
            }
        }
        class broken2 extends GLMGenerator
        {
            val date_format: SimpleDateFormat =
            {
                val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
                format.setTimeZone (TimeZone.getTimeZone ("UTC"))
                format
            }

            override def name: String = "broken2"
            override def prefix: String =
            {
                val t0 = date_format.format (start_time.getTime)
                val t1 = date_format.format (finish_time.getTime)
                val preamble =
                    """// %s.glm
                      |// %s
                      |//*********************************************
                      |
                      |        module tape;
                      |
                      |        module powerflow
                      |        {
                      |            solver_method bar;
                      |            default_maximum_voltage_error 10e-6;
                      |            NR_iteration_limit 500;
                      |            NR_superLU_procs 16;
                      |            nominal_frequency "50";
                      |        };
                      |
                      |        clock
                      |        {
                      |            timezone "%s";
                      |            starttime "%s";
                      |            stoptime "%s";
                      |        };
                      |
                      |        class player
                      |        {
                      |            complex value;
                      |        };
                      |""".stripMargin.format (name, header, tzString, t0, t1)
                preamble
            }
        }
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename)
            options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
            options.put ("ch.ninecode.cim.do_topo", "true")
            options.put ("ch.ninecode.cim.do_topo_islands", "true")
            options.put ("ch.ninecode.cim.force_retain_switches", "ForceTrue")
            options.put ("ch.ninecode.cim.force_retain_fuses", "ForceTrue")
            options.put ("ch.ninecode.cim.force_switch_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.force_fuse_separate_islands", "Unforced")
            options.put ("ch.ninecode.cim.default_switch_open_state", "false")
            options.put ("ch.ninecode.cim.debug", "true")

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            println (elements.count + " elements")
            val read = System.nanoTime
            println ("read: " + (read - start) / 1e9 + " seconds")

            val gen1 = new broken1 ()
            val gen2 = new broken2 ()
            val gridlabd = new GridLABD (session, workdir = "target/")
            gridlabd.export (gen1)
            gridlabd.export (gen2)

            val generate = System.nanoTime ()
            println ("generate: " + (generate - read) / 1e9 + " seconds")

            val glm = session.sparkContext.parallelize (List (gen1.name, gen2.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            println ("generate: " + (solve - generate) / 1e9 + " seconds")

            assert (!results._1, "should fail")
            assert (results._2.contains ("broken2 ERROR    [INIT] : keyword 'bar' is not valid for property powerflow::solver_method"), "bar")
            assert (results._2.contains ("broken1 ERROR    [INIT] : keyword 'foo' is not valid for property powerflow::solver_method"), "foo")

            println ("total: " + (solve - start) / 1e9 + " seconds")
    }
}
