package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

/**
 * GridLAB-D glm file generator.
 *
 * @param one_phase            If <code>true</code> generate a single phase .glm file.
 * @param date_format          The date format to use within the .glm file.
 * @param cim_temperature      The temperature of the elements in the CIM file (°C).
 * @param simulation_temperature The temperature of the elements in the .glm file (°C).
 * @param swing_voltage_factor Factor to apply to the nominal slack voltage, e.g. 1.03 = 103% of nominal.
 * @param kreis                The transformer service area to generate a .glm file for.
 */
case class SimulationGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    cim_temperature: Double,
    simulation_temperature: Double,
    swing_voltage_factor: Double,
    kreis: SimulationTrafoKreis) extends GLMGenerator (
        one_phase = one_phase,
        temperature = cim_temperature,
        date_format = date_format,
        swing_voltage_factor = swing_voltage_factor)
{

    override def name: String = kreis.name

    override def directory: String = kreis.directory

    override def start_time: Calendar = kreis.start_time

    override def finish_time: Calendar = kreis.finish_time

    override def targetTemperature: Double = simulation_temperature

    override def edges: Iterable[SimulationEdge] = kreis.edges

    override def transformers: Iterable[TransformerEdge] = List (kreis.transformer_edge)

    override def swing_nodes: Iterable[GLMNode] = kreis.swing_nodes

    override def nodes: Iterable[SimulationNode] = kreis.nodes

    override def extra: Iterable[String] = List ("")

    def emit_recorder (recorder: SimulationRecorder): String =
    {
        // ToDo: handle _AB, _BC, _CA for three phase delta connections
        val property = if (one_phase)
            s"${recorder.property}_A.real,${recorder.property}_A.imag"
        else
            s"${recorder.property}_A.real,${recorder.property}_A.imag,${recorder.property}_B.real,${recorder.property}_B.imag,${recorder.property}_C.real,${recorder.property}_C.imag"
        s"""
        |        object recorder
        |        {
        |            name "${recorder.name}";
        |            parent "${recorder.parent}";
        |            property "$property";
        |            interval "${recorder.interval}";
        |            file "${recorder.file}";
        |        };
        |""".stripMargin
    }

    // relies on the player file being of the form: "input_data/" + player.name + ".csv"
    def phase_file (file: String, suffix: String): String =
    {
        val base = file.substring (0, file.length - 4)
        s"$base$suffix.csv"
    }

    def emit_player (name: String, parent: String, property: String, phase: String, file: String, suffix: String): String =
    {
        s"""
        |        object player
        |        {
        |            name "$name$suffix";
        |            parent "$parent";
        |            property "$property$phase.real,$property$phase.imag";
        |            file "${ phase_file (file, suffix)}";
        |        };
        |""".stripMargin
    }

    def emit_node_player (node: SimulationNode)(player: SimulationPlayer): String =
    {
        val parent = if (player.`type` == "energy") s"${player.name}_object" else player.parent
        val suffixes = if (one_phase) Seq (("_A", "")) else Seq(("_A", "_R"), ("_B", "_S"), ("_C", "_T"))
        val players = for (suffix <- suffixes)
            yield
                emit_player (player.name, parent, player.property, suffix._1, player.file, suffix._2)
        if (player.`type` == "energy")
            s"""
            |        object load
            |        {
            |            name "${player.name}_object";
            |            parent "${player.parent}";
            |            phases ${if (one_phase) "AN" else "ABCN"};
            |            nominal_voltage ${node.nominal_voltage}V;
            |        };
            |${players.mkString}""".stripMargin
        else
            players.mkString
    }

    def emit_edge_player (player: SimulationPlayer): String =
    {
        val suffixes = if (one_phase) Seq (("_A", "")) else Seq(("_A", "_R"), ("_B", "_S"), ("_C", "_T"))
        val players = for (suffix <- suffixes)
            yield
                emit_player (player.name, player.parent, player.property, suffix._1, player.file, suffix._2)
        players.mkString
    }

    override def emit_edge (edge: GLMEdge): String =
    {
        val e = edge.asInstanceOf[SimulationEdge]
        val recorders = e.recorders.map (emit_recorder).mkString ("")
        val players = e.players.map (emit_edge_player).mkString ("")
        super.emit_edge (e.rawedge) + recorders + players
    }

    override def emit_slack (node: GLMNode): String =
    {
        val n = node.asInstanceOf[SimulationNode]
        val recorders = n.recorders.map (emit_recorder).mkString ("")
        val players = n.players.map (emit_node_player (n)).mkString ("")
        super.emit_slack (node) + recorders + players
    }

    override def emit_node (node: GLMNode): String =
    {
        val n = node.asInstanceOf[SimulationNode]
        val recorders = n.recorders.map (emit_recorder).mkString ("")
        val players = n.players.map (emit_node_player (n)).mkString ("")
        super.emit_node (node) + recorders + players
    }

    override def emit_transformer (transformer: TransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name
        super.emit_transformer (transformer) +
            kreis.recorders.filter (_.parent == name).map (emit_recorder).mkString
    }

    /**
     * Emit configurations for all groups of edges that are ACLineSegments.
     *
     * Get one of each type of ACLineSegment and emit a configuration for each of them.
     *
     * @param edges The edges in the model.
     * @return The configuration elements as a single string.
     */
    override def getACLineSegmentConfigurations (edges: Iterable[GLMEdge]): Iterable[String] =
    {
        edges.filter (_.asInstanceOf[SimulationEdge].rawedge.isInstanceOf[LineEdge]).map (_.asInstanceOf[SimulationEdge].rawedge.asInstanceOf[LineEdge]).groupBy (_.configurationName).values.map (_.head.configuration (this))
    }
}