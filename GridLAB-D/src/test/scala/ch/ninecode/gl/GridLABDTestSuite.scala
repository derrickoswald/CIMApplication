package ch.ninecode.gl

import java.io.File
import java.text.SimpleDateFormat
import java.util.TimeZone

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import ch.ninecode.net.Island.identifier
import ch.ninecode.model.Element
import ch.ninecode.model.EnergyConsumer
import ch.ninecode.testutil.TestUtil
import ch.ninecode.net.TransformerServiceArea
import org.scalatest.BeforeAndAfter

import ch.ninecode.net.Island
import ch.ninecode.net.Island.Edges
import ch.ninecode.net.Island.Nodes
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.TerminalPlus

case class Generator (
    override val name: String,
    override val nodes: Iterable[GLMNode],
    override val edges: Iterable[GLMEdge],
    override val transformers: Iterable[GLMTransformerEdge],
    override val swing_nodes: Iterable[GLMNode]
)
    extends GLMGenerator (emit_voltage_dump = true)

case class TestNode
(
    override val id: String,
    override val nominal_voltage: Double,
    equipment: Iterable[Element]
)
    extends LoadFlowNode (id, nominal_voltage)
        with GLMNode
{
    override def emit (generator: GLMGenerator): String =
    {
        val load = equipment.headOption.map
        {
            case _: EnergyConsumer =>
                s"""
                   |        object load
                   |        {
                   |            name "${id}_load";
                   |            parent "${id}";
                   |            phases "${if (generator.isSinglePhase) "AN" else "ABCN"}";
                   |            nominal_voltage ${nominal_voltage}V;
                   |            ${if (generator.isSinglePhase) "constant_power_A" else "constant_power_ABCN"} 1000+0j;
                   |        };""".stripMargin
            case _ => ""
        }
        s"${super.emit (generator)}$load"
    }
}

class TestIsland (session: SparkSession) extends Island (session)
{
    override def node_maker (rdd: RDD[Iterable[TerminalPlus]]): RDD[(identifier, LoadFlowNode)] =
        rdd.flatMap (parts => parts.headOption.map (x => (x.id, TestNode (x.node.id, x.voltage, parts.map (_.element)))))
}

class GridLABDTestSuite extends TestUtil with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData.rdf"
    val filename1: String = FILE_DEPOT + FILENAME1
    val FILENAME2 = "DemoDataParallel.rdf"
    val filename2: String = FILE_DEPOT + FILENAME2

    before
    {
        // unpack the zip files
        if (!new File (filename1).exists)
            new Unzip ().unzip (FILE_DEPOT + "DemoData.zip", FILE_DEPOT)
        if (!new File (filename2).exists)
            new Unzip ().unzip (FILE_DEPOT + "DemoDataParallel.zip", FILE_DEPOT)
    }

    after
    {
        deleteRecursive (new File (filename1))
        deleteRecursive (new File (filename2))
    }

    test ("Basic")
    {
        session: SparkSession =>

            val start = System.nanoTime
            val files = filename1.split (",")
            val options = Map [String, String](
                "path" -> filename1,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
                "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
                "ch.ninecode.cim.default_switch_open_state" -> "false",
                "ch.ninecode.cim.debug" -> "true"
            )

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            info (s"${elements.count} elements")
            val read = System.nanoTime
            info (s"read: ${(read - start) / 1e9} seconds")

            val gen = new GLMGenerator ()
            val text = gen.make_glm ()

            val generate = System.nanoTime ()
            info (s"generate: ${(generate - read) / 1e9} seconds")

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
            info (s"total: ${(generate - start) / 1e9} seconds")
    }

    test ("Solve")
    {
        session: SparkSession =>

            val start = System.nanoTime
            val files = filename1.split (",")
            val options = Map [String, String](
                "path" -> filename1,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
                "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
                "ch.ninecode.cim.default_switch_open_state" -> "false",
                "ch.ninecode.cim.debug" -> "true"
            )

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            info (s"${elements.count} elements")
            val read = System.nanoTime
            info (s"read: ${(read - start) / 1e9} seconds")

            val gen = new GLMGenerator ()
            val gridlabd = new GridLABD (session, workdir = "simulation/")
            gridlabd.export (gen)

            val generate = System.nanoTime ()
            info (s"generate: ${(generate - read) / 1e9} seconds")

            val glm = session.sparkContext.parallelize (List (gen.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            info (s"generate: ${(solve - generate) / 1e9} seconds")

            assert (results.isEmpty, "should have no errors")

            info (s"total: ${(solve - start) / 1e9} seconds")
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
        session: SparkSession =>

            val start = System.nanoTime
            val files = filename1.split (",")
            val options = Map [String, String](
                "path" -> filename1,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
                "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
                "ch.ninecode.cim.default_switch_open_state" -> "false",
                "ch.ninecode.cim.debug" -> "true"
            )

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            info (s"${elements.count} elements")
            val read = System.nanoTime
            info (s"read: ${(read - start) / 1e9} seconds")

            val gen1 = new broken1 ()
            val gen2 = new broken2 ()
            val gridlabd = new GridLABD (session, workdir = "simulation/")
            gridlabd.export (gen1)
            gridlabd.export (gen2)

            val generate = System.nanoTime ()
            info (s"generate: ${(generate - read) / 1e9} seconds")

            val glm = session.sparkContext.parallelize (List (gen1.name, gen2.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            info (s"generate: ${(solve - generate) / 1e9} seconds")

            assert (results.nonEmpty, "should have errors")
            assert (results.length == 2, "should have 2 results")
            assert (results (0).errorMessages.mkString ("\n").contains ("ERROR    [INIT] : keyword 'foo' is not valid for property powerflow::solver_method"), "foo")
            assert (results (1).errorMessages.mkString ("\n").contains ("ERROR    [INIT] : keyword 'bar' is not valid for property powerflow::solver_method"), "bar")

            info (s"total: ${(solve - start) / 1e9} seconds")
    }

    test ("Parallel")
    {
        session: SparkSession =>

            val start = System.nanoTime
            val files = filename2.split (",")
            val options = Map [String, String](
                "path" -> filename2,
                "StorageLevel" -> "MEMORY_AND_DISK_SER",
                "ch.ninecode.cim.do_topo" -> "true",
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue",
                "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
                "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
                "ch.ninecode.cim.default_switch_open_state" -> "false",
                "ch.ninecode.cim.debug" -> "true"
            )

            val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
            info (s"${elements.count} elements")
            val read = System.nanoTime
            info (s"read: ${(read - start) / 1e9} seconds")

            val gridlabd = new GridLABD (session, workdir = "simulation/")
            val trafos_islands = TransformerServiceArea (session).getTransformerServiceAreas.map (_.swap) // (trafosetid, islandid)
            val island = new TestIsland (session)
            val (nodes: Nodes, edges: Edges) = island.queryNetwork (trafos_islands)

            // determine the set of transformers to work on
            def heavy (transformer: GLMTransformerEdge): Boolean =
                (transformer.transformer.v0 > 1000.0) && (transformer.transformer.v1 == 400.0)

            val transformers: Array[(identifier, GLMTransformerEdge)] =
                edges
                    .flatMap
                    {
                        case (id: identifier, transformer: GLMTransformerEdge) =>
                            if (heavy (transformer))
                                Some ((id, transformer))
                            else
                                None
                        case _ => None
                    }
                    .collect

            def notTheTransformer (transformer: GLMTransformerEdge)(edge: LoadFlowEdge): Boolean =
                edge match
                {
                    case tx: GLMTransformerEdge => if (heavy (tx)) false else true
                    case _ => true
                }

            val glms = for
                {
                (id, trafo) <- transformers
                n = nodes.filter (_._1 == id).map (_._2).collect.collect ({ case l: GLMNode => l })
                e = edges.filter (_._1 == id).map (_._2).filter (notTheTransformer (trafo)).collect.collect ({ case e: GLMEdge => e })
            }
                yield
                    {
                        gridlabd.export (Generator (id, n, e, List (trafo), List (TestNode (trafo.cn1, trafo.primary.toDouble, trafo.transformer.transformers.map (_.transformer)))))
                        trafo.transformer.transformer_name
                    }

            val generate = System.nanoTime ()
            info (s"generate: ${(generate - read) / 1e9} seconds")

            val results = gridlabd.solve (session.sparkContext.parallelize (glms))

            val solve = System.nanoTime ()
            info (s"solve: ${(solve - generate) / 1e9} seconds")

            assert (results.isEmpty, "should have no errors")

            info (s"total: ${(solve - start) / 1e9} seconds")
    }
}
