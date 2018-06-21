package ch.ninecode.sim

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class SimulationGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    kreis: SimulationTrafoKreis) extends GLMGenerator (one_phase, date_format)
{

    override def name: String = kreis.name

    override def directory: String = kreis.directory

    override def start_time: Calendar = kreis.start_time

    override def finish_time: Calendar = kreis.finish_time

    override def edge_groups: Iterable[Iterable[SimulationEdge]] = kreis.edges

    override def transformers: Array[TransformerSet] = Array(kreis.transformer)

    override def swing_nodes: Iterable[GLMNode] = kreis.swing_nodes

    override def nodes: Iterable[SimulationNode] = kreis.nodes

    override def extra: Iterable[String] = List ("")

    def emit_recorder (recorder: SimulationRecorder): String =
    {
        // ToDo: do all recorders need "_A" for one phase ?
        val property = if (one_phase) recorder.property + "_A" else recorder.property
        """
        |        object recorder
        |        {
        |            name "%s";
        |            parent "%s";
        |            property "%s";
        |            interval "%d";
        |            file "%s";
        |        };
        """.stripMargin.format (recorder.name, recorder.parent, property, recorder.interval, recorder.file)
    }

    def emit_edge_player (edge: Iterable[SimulationEdge]) (player: SimulationPlayer): String =
    {
        // ToDo: do all players need "_A" for one phase ?
        val property = if (one_phase) player.property + "_A" else player.property
        """
          |        object player
          |        {
          |            name "%s";
          |            parent "%s";
          |            property "%s";
          |            file "%s";
          |        };
        """.stripMargin.format (player.name, player.parent, property, player.file)
    }


    def emit_node_player (node: SimulationNode) (player: SimulationPlayer): String =
    {
        // ToDo: do all players need "_A" for one phase ?
        val property = if (one_phase) player.property + "_A" else player.property
        if (player.typ == "energy")
        {
            val load = player.name + "_object"
            val phases = if (one_phase) "AN" else "ABCN"
            val voltage = node.nominal_voltage
            """
            |        object load
            |        {
            |            name "%s";
            |            parent "%s";
            |            phases %s;
            |            nominal_voltage %sV;
            |        };
            |
            |        object player
            |        {
            |            name "%s";
            |            parent "%s";
            |            property "%s";
            |            file "%s";
            |        };
            """.stripMargin.format (load, player.parent, phases, voltage, player.name, load, property, player.file)
        }
        else
            """
              |        object player
              |        {
              |            name "%s";
              |            parent "%s";
              |            property "%s";
              |            file "%s";
              |        };
            """.stripMargin.format (player.name, player.parent, property, player.file)
    }

    override def emit_edge (edges: Iterable[GLMEdge]): String =
    {
        val e = edges.asInstanceOf[Iterable[SimulationEdge]]
        val recorders = e.map (edge ⇒ edge.recorders.map (emit_recorder).mkString ("")).mkString ("")
        val players = e.map (edge ⇒ edge.players.map (emit_edge_player (e)).mkString ("")).mkString ("")
        super.emit_edge (edges) + recorders + players
    }

    override def emit_node (node: GLMNode): String =
    {
        val n = node.asInstanceOf[SimulationNode]
        val recorders = n.recorders.map (emit_recorder).mkString ("")
        val players = n.players.map (emit_node_player (n)).mkString ("")
        super.emit_node (node) + recorders + players
    }

    override def emit_transformer (transformer: TransformerSet): String =
    {
        val name = transformer.transformer_name.split ("_")(0) // only match the first transformer of a set
        super.emit_transformer (transformer) +
        kreis.recorders.filter (
            recorder ⇒
            {
                recorder.parent == name
            }
        )
        .map (_.copy (parent = transformer.transformer_name)) // alter the parent to the transformer set name
        .map (emit_recorder).mkString
    }
}