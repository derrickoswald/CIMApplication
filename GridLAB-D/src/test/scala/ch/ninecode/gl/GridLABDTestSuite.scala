package ch.ninecode.gl

import java.io.File
import java.text.SimpleDateFormat
import java.util.TimeZone

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.Island.EdgeParts
import ch.ninecode.gl.Island.NodeParts
import ch.ninecode.gl.Island.identifier
import ch.ninecode.gl.Island.node_id
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.EnergyConsumer
import ch.ninecode.model.Terminal
import ch.ninecode.util.TestUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfter

import scala.collection.mutable

case class Generator (
                         override val name: String,
                         override val nodes: Iterable[GLMNode],
                         override val edges: Iterable[GLMEdge],
                         override val transformers: Iterable[TransformerEdge],
                         override val swing_nodes: Iterable[GLMNode])
    extends GLMGenerator (emit_voltage_dump = true)

case class Node
(
    id: String,
    nominal_voltage: Double,
    equipment: Iterable[Element]
)
    extends GLMNode
{
    override def emit (generator: GLMGenerator): String =
    {
        val load = equipment.head match
        {
            case consumer: EnergyConsumer ⇒
                """
                  |        object load
                  |        {
                  |            name "%s_load";
                  |            parent "%s";
                  |            phases "%s";
                  |            nominal_voltage %sV;
                  |            %s 1000+0j;
                  |        };
                """.stripMargin.format (id, id, if (generator.isSinglePhase) "AN" else "ABCN", nominal_voltage, if (generator.isSinglePhase) "constant_power_A" else "constant_power_ABCN")
            case _ ⇒ ""
        }
        super.emit (generator) + load
    }
}

case class Maker (session: SparkSession)
{
    val equipment: RDD[(String, ConductingEquipment)] = session.sparkContext.getPersistentRDDs.filter (_._2.name == "ConductingEquipment").head._2.asInstanceOf[RDD[ConductingEquipment]].keyBy (_.id)

    def node_maker (rdd: RDD[NodeParts]): RDD[(identifier, GLMNode)] =
    {
        // ToDo: fix this 1kV multiplier on the voltages
        def voltage (base_voltage: BaseVoltage): Double = base_voltage.nominalVoltage * 1000.0

        val s: RDD[((node_id, Iterable[(identifier, (Terminal, Element, BaseVoltage))]), ConductingEquipment)] = rdd.keyBy (_._2.head._2._2.id).join (equipment).values
        s.map (args ⇒
        {
            val iter = args._1._2
            (
                iter.head._1,
                Node (
                    args._1._1,
                    voltage (iter.head._2._3),
                    iter.map (_._2._2))
            )
        })
    }

    def edge_maker (rdd: RDD[EdgeParts]): RDD[(identifier, GLMEdge)] =
    {
        rdd.map (
            args ⇒
            {
                // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
                val id_cn_1 = args.head._1.head._2.TopologicalNode
                val id_cn_2 = args.head._1.tail.head._2.TopologicalNode
                (args.head._1.head._1, GLMEdge.toGLMEdge (args.map (_._2), id_cn_1, id_cn_2))
            }
        )
    }
}

class GridLABDTestSuite extends TestUtil with BeforeAndAfter
{
    override val classesToRegister: Array[Array[Class[_]]] = Array (CIMClasses.list)
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
        new File (filename1).delete
        new File (filename2).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename1.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename1)
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
            val files = filename1.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename1)
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
            val gridlabd = new GridLABD (session, workdir = "simulation/")
            gridlabd.export (gen)

            val generate = System.nanoTime ()
            println ("generate: " + (generate - read) / 1e9 + " seconds")

            val glm = session.sparkContext.parallelize (List (gen.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            println ("generate: " + (solve - generate) / 1e9 + " seconds")

            assert (results.isEmpty, "should have no errors")

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
            val files = filename1.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename1)
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
            val gridlabd = new GridLABD (session, workdir = "simulation/")
            gridlabd.export (gen1)
            gridlabd.export (gen2)

            val generate = System.nanoTime ()
            println ("generate: " + (generate - read) / 1e9 + " seconds")

            val glm = session.sparkContext.parallelize (List (gen1.name, gen2.name))
            val results = gridlabd.solve (glm)

            val solve = System.nanoTime ()
            println ("generate: " + (solve - generate) / 1e9 + " seconds")

            assert (results.nonEmpty, "should have errors")
            assert (results.length == 2, "should have 2 results")
            assert (results(0).errorMessages.toString.contains ("ERROR    [INIT] : keyword 'foo' is not valid for property powerflow::solver_method"), "foo")
            assert (results(1).errorMessages.toString.contains ("ERROR    [INIT] : keyword 'bar' is not valid for property powerflow::solver_method"), "bar")

            println ("total: " + (solve - start) / 1e9 + " seconds")
    }

    test ("Parallel")
    {
        session: SparkSession ⇒

            val start = System.nanoTime
            val files = filename2.split (",")
            val options = new mutable.HashMap[String, String]()
            options.put ("path", filename2)
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

            val gridlabd = new GridLABD (session, workdir = "simulation/")
            val tsa = TransformerServiceArea (session)
            // only proceed if topological processing was done (there are TopologicalIslands)
            if (tsa.hasIslands)
            {
                val trafos_islands = tsa.getTransformerServiceAreas.map (_.swap) // (trafosetid, islandid)
                val island = new Island (session)
                val maker = Maker (session)
                val (nodes, edges) = island.queryNetwork (
                    trafos_islands,
                    maker.node_maker,
                    maker.edge_maker)

                val _transformers = new Transformers (session)
                val transformer_data = _transformers.getTransformers ()

                // determine the set of transformers to work on
                val transformers = transformer_data
                    .filter (td => (td.v0 != 400.0) && (td.v1 == 400.0)) // ToDo: don't hard code this low voltage value
                    .groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).map (TransformerSet (_))
                    .map (x ⇒ TransformerEdge (x.node0, x.node1, x))
                    .collect

                val glms = for
                    {
                    trafo ← transformers
                    n = nodes.filter (_._1 == trafo.transformer.transformer_name).map (_._2).collect
                    e = edges.filter (_._1 == trafo.transformer.transformer_name).map (_._2).collect
                }
                    yield
                        {
                            gridlabd.export (Generator (trafo.transformer.transformer_name, n, e, List (trafo), List (Node (trafo.cn1, trafo.primary.toDouble, null))))
                            trafo.transformer.transformer_name
                        }

                val generate = System.nanoTime ()
                println ("generate: " + (generate - read) / 1e9 + " seconds")

                val results = gridlabd.solve (session.sparkContext.parallelize (glms))

                val solve = System.nanoTime ()
                println ("solve: " + (solve - generate) / 1e9 + " seconds")

                assert (results.isEmpty, "should have no errors")

                println ("total: " + (solve - start) / 1e9 + " seconds")
            }
    }
}
