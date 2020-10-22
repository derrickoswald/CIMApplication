package ch.ninecode.pp

import java.io.File

import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.model.TopologicalIsland
import ch.ninecode.net.Net
import ch.ninecode.net.Island
import ch.ninecode.net.LineDetails
import ch.ninecode.net.LineEdge
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.net.SwitchEdge
import ch.ninecode.testutil.TestUtil
import ch.ninecode.net.TransformerEdge

import org.scalatest.BeforeAndAfter

case class MyPandaPowerGenerator (
    override val name: String,
    basedir: String,
    override val header: String,
    override val nodes: Iterable[LoadFlowNode],
    _edges: Iterable[LoadFlowEdge]) extends PandaPowerGenerator
{
    override val directory: String = s"$basedir$name"
    override val lines: Iterable[LineEdge] = _edges.flatMap(_ match
    { case l: LineEdge => Some(l);
        case _ => None
    })
    override val switches: Iterable[SwitchEdge] = _edges.flatMap(_ match
    { case s: SwitchEdge => Some(s);
        case _ => None
    })
    override val transformers: Iterable[TransformerEdge] = _edges.flatMap(_ match
    { case t: TransformerEdge => Some(t);
        case _ => None
    })
    override val swing_nodes: Iterable[LoadFlowNode] = transformers.map(transformer => PandaPowerExternalGridNode(transformer))
    override val extra: Iterable[String] = Array(
        """sc.calc_sc(net, case="max", ip=True, ith=True, branch_results=True)""",
        """table = pd.concat([net.bus["name"],net.res_bus_sc["ip_ka"]*1000], axis=1)""",
        """table.rename(columns={"name": "Bus", "ip_ka": "Current (A)"}, inplace=True)""",
        s"""table.to_csv ("output_data/GettingStarted.csv", sep=";", columns=["Bus","Current (A)"], index=False)"""
    )
}

class SampleSuite extends TestUtil with BeforeAndAfter
{
    override val classesToRegister: Array[Class[_]] = Array.concat(
        CIMClasses.list,
        Net.classes,
        PandaPower.classes)

    val CIM_EXTENSION = ".rdf"
    val ZIP_EXTENSION = ".zip"
    val FILE_DEPOT = "data/"
    val FILENAME1 = "GettingStarted"

    before
    {
        if (!new File(s"$FILE_DEPOT$FILENAME1$CIM_EXTENSION").exists)
            new Unzip().unzip(s"$FILE_DEPOT$FILENAME1$ZIP_EXTENSION", FILE_DEPOT)
    }

    after
    {
        new File(s"$FILE_DEPOT$FILENAME1$CIM_EXTENSION").delete
    }

    test("Getting Started")
    {
        session =>
            implicit val spark: SparkSession = session
            val WORKDIR = "target/"
            LineDetails.PROPERTIES_ARE_ERRONEOUSLY_PER_KM = false
            val options = Map[String, String]("ch.ninecode.cim.do_topo_islands" -> "true")
            readCIMElements(session, s"$FILE_DEPOT$FILENAME1$CIM_EXTENSION", options)
            val islands = get[TopologicalIsland]
            val island = new Island(session)
            val (nodes, edges) = island.queryNetwork(islands.map(island => ("GettingStarted", island.id)))
            val tasks = nodes.groupByKey.join(edges.groupByKey)
            val scripts = tasks.map(
                task =>
                {
                    val (name, (nodes, edges)) = task
                    val generator = MyPandaPowerGenerator(
                        name = name,
                        basedir = WORKDIR,
                        "Getting Started",
                        nodes = nodes,
                        _edges = edges)
                    (name, generator.makeScript)
                }
            )

            val pp = PandaPower(session = session, workdir = WORKDIR)
            val jobs = scripts.map(
                script =>
                {
                    val (name, contents) = script
                    pp.writeInputFile(s"$name/", s"$name.py", Some(contents.getBytes("UTF-8")), None)
                    pp.writeInputFile(s"$name/", s"output_data/dummy", None, None) // mkdir
                    name
                }
            )

            val result = pp.solve(jobs)
            info(result.toString, None)
            assert(result.success, "PandaPower failed")
            assert(result.errors.isEmpty, "no errors")
    }
}
