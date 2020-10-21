package ch.ninecode.mfi

import java.text.SimpleDateFormat
import java.util.Calendar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.gl.PV
import ch.ninecode.gl.SwingNode
import ch.ninecode.model.ACDCTerminal
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BasicElement
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.Element
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Terminal
import ch.ninecode.net.LineData
import ch.ninecode.net.LineDetails
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.TransformerSet
import ch.ninecode.util.Complex

class EinspeiseleistungGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, trafokreis: Trafokreis, tbase: Double, tsim: Double)
    extends GLMGenerator (one_phase, tsim, date_format, true)
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    override def name: String = trafokreis.name

    override def header: String = "Einspeiseleistung"

    override def start_time: Calendar = trafokreis.start_time

    override def finish_time: Calendar = trafokreis.finish_time

    lazy val rootClass: String = ConductingEquipment.getClass.getName.replace ("$", "")

    def classname (element: Element): String =
    {
        val clazz = element.getClass.getName
        clazz.substring (clazz.lastIndexOf (".") + 1)
    }

    def baseClass (element: Element): String =
    {
        var ret = element

        while ((null != ret.sup) && (ret.sup.getClass.getName != rootClass))
            ret = ret.sup

        classname (ret)
    }

    def multiconductor (element: Element): Option[ACLineSegment] =
    {
        element match
        {
            case acline: ACLineSegment => Some (acline)
            case conductor: Conductor =>
                Some (new ACLineSegment (conductor))
            case _ =>
                log.error (s"unexpected class in edge elements (${element.getClass})")
                None
        }
    }

    case class fakeEdge (override val id: String, override val cn1: String, override val cn2: String)
        extends LoadFlowEdge (id, cn1, cn2) with GLMEdge

    /**
     * Temporary measure until we figure out how to create subclasses of GMLEdge from:
     *   - PreNode/PreEdge trace results
     *   - Island trace results
     *   - multi-island trace results
     *
     * @param elements the CIM elements comprising the edge
     * @param cn1      the TopologicalNode id of one end of the edge
     * @param cn2      the TopologicalNode id of the other end of the edge
     * @return a type of edge
     */
    def toGLMEdge (elements: Iterable[Element], cn1: String, cn2: String,
        makeTransformerEdge: (Iterable[Element], String, String) => GLMTransformerEdge, tbase: Double = 20.0): GLMEdge =
    {
        def makeEdge (cls: String, elements: Iterable[Element], cn1: String, cn2: String): GLMEdge =
        {
            cls match
            {
                case "Switch" =>
                    GLMSwitchEdge (cn1, cn2, elements)
                case "Conductor" =>
                    val t1 = Terminal (ACDCTerminal (IdentifiedObject (BasicElement (mRID = "terminal_1"))), TopologicalNode = cn1)
                    t1.bitfields = Terminal.fieldsToBitfields ("TopologicalNode")
                    val t2 = Terminal (ACDCTerminal (IdentifiedObject (BasicElement (mRID = "terminal_2"))), TopologicalNode = cn2)
                    t2.bitfields = Terminal.fieldsToBitfields ("TopologicalNode")
                    implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails (CIMBaseTemperature = tbase)
                    GLMLineEdge (LineData (elements.flatMap (multiconductor).map (x => LineDetails (x, t1, t2, None, None))))
                // base_temperature: Double = 20.0,
                // DEFAULT_R: Double = 0.225,
                // DEFAULT_X: Double = 0.068
                case "PowerTransformer" =>
                    makeTransformerEdge (elements, cn1, cn2)
                case _ =>
                    log.error (s"edge from $cn1 to $cn2 has unhandled class type '$cls'")
                    fakeEdge (elements.map (_.id).mkString ("_"), cn1, cn2)
            }
        }

        // for now, we handle Conductor, Switch and eventually PowerTransformer
        val tagged = elements.map (x => (baseClass (x), x))

        // check that all elements are the same base class
        val grouped = tagged.groupBy (_._1).mapValues (_.map (_._2)).toList
        grouped match
        {
            case (cls, elem) :: Nil =>
                makeEdge (cls, elem, cn1, cn2)
            case (cls, elem) :: rest :: Nil =>
                val v = (cls, elem) :: rest :: Nil
                val types = v.map (
                    x =>
                    {
                        val (base, items) = x
                        val details = items.map (element => s"${classname (element)}:${element.id}").mkString (",")
                        s"$base($details)"
                    }
                ).mkString (",")
                log.error (s"edge from $cn1 to $cn2 has conflicting element types: $types")
                makeEdge (cls, elem, cn1, cn2)
            case _ =>
                fakeEdge (s"no_elements_provided_to_toGLMEdge", cn1, cn2)
        }
    }

    def makeTransformerEdge (elements: Iterable[Element], cn1: String, cn2: String): GLMTransformerEdge =
    {
        val ids = elements.map (_.id).toArray
        val trafo = trafokreis.subtransmission_trafos.filter (data => ids.contains (data.transformer.id))
        GLMTransformerEdge (TransformerSet (trafo))
    }

    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    override def edges: Iterable[GLMEdge] = trafokreis.edges.groupBy (_.key).values.map (
        edges =>
        {
            val one = edges.head
            toGLMEdge (edges.map (_.element), one.cn1, one.cn2, makeTransformerEdge, tbase)
        }
    )

    override def transformers: Iterable[GLMTransformerEdge] =
        trafokreis.transformers.transformers.map (GLMTransformerEdge)

    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    override def getTransformerConfigurations (transformers: Iterable[GLMTransformerEdge]): Iterable[String] =
    {
        val subtransmission_trafos = edges.flatMap (edge => edge match
        {
            case trafo: GLMTransformerEdge => Some (trafo)
            case _ => None
        }
        )
        val trafos = transformers ++ subtransmission_trafos
        val configurations = trafos.groupBy (_.configurationName).values
        configurations.map (config => config.head.configuration (this, config.map (_.transformer.transformer_name).mkString (", ")))
    }

    override def swing_nodes: Iterable[GLMNode] =
        trafokreis.transformers.transformers.map (
            transformers =>
                SwingNode (transformers.node0, transformers.v0, transformers.transformer_name)
        )
            // distinct
            .map (node => (node.id, node))
            .toMap
            .values

    override def nodes: Iterable[GLMNode] = trafokreis.nodes

    override def extra: Iterable[String] =
    {
        @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
        def extra_nodes: Iterable[MaxPowerFeedingNodeEEA] = trafokreis.houses
            .filter (_.eea != null)
            .groupBy (_.id_seq)
            .values
            .map (_.head)

        def emit_extra_node (node: MaxPowerFeedingNodeEEA): String =
        {
            emit_pv (node.eea, node)
        }

        extra_nodes.map (emit_extra_node)
    }

    override def emit_node (node: GLMNode): String =
    {
        // or load_from_player_file (name, voltage)
        s"${ super.emit_node (node) }${ generate_load (node) }"
    }

    /**
     * Emit a switch or fuse with large current limit.
     *
     * @param edge      the details about the switch or fuse
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    def emit_switch (edge: GLMSwitchEdge, generator: GLMGenerator): String =
    {
        val status = if (edge.closed) "CLOSED" else "OPEN"
        val current = 9999.0 // override so it never trips
        val fuse_details = if (edge.fuse)
            """
              |            mean_replacement_time 3600.0;
              |            current_limit %sA;""".format (current).stripMargin
        else
            ""

        """
          |        object %s
          |        {
          |            name "%s";
          |            phases %s;
          |            from "%s";
          |            to "%s";
          |            status "%s";%s
          |        };
          |""".stripMargin.format (if (edge.fuse) "fuse" else "switch", edge.id, if (generator.isSinglePhase) "AN" else "ABCN", edge.cn1, edge.cn2, status, fuse_details)
    }

    def generate_current_recorder (edge_id: String): String =
    {
        val phases = if (one_phase)
            "current_in_A.real,current_in_A.imag"
        else
            "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag"
        s"""
           |        object recorder
           |        {
           |            name "${edge_id}_current_recorder";
           |            parent "$edge_id";
           |            property $phases;
           |            interval 5;
           |            file "output_data/${edge_id}_current.csv";
           |        };
           |""".stripMargin
    }

    override def emit_edge (edge: GLMEdge): String =
    {
        edge match
        {
            case cable: GLMLineEdge => s"${ super.emit_edge (cable) }${ generate_current_recorder (edge.id) }"
            case swtch: GLMSwitchEdge => emit_switch (swtch, this)
            case _ => super.emit_edge (edge)
        }
    }

    override def emit_transformer (transformer: GLMTransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name

        lazy val lv_windings: Array[PowerTransformerEnd] =
            for (winding <- transformer.transformer.transformers (0).ends
                 if winding.TransformerEnd.endNumber > 1)
                yield winding
        lazy val multiwinding: Boolean = lv_windings.length > 1


        val recorders = if (!multiwinding)
        {
            addTrafoRecorders (name)
        }
        else
        {
            val recs: Seq[String] = for (index <- lv_windings.indices)
                yield
                    {
                        val number = lv_windings (index).TransformerEnd.endNumber - 1
                        val newName = s"${name}_$number"
                        addTrafoRecorders (newName)
                    }
            recs.mkString
        }
        s"${ super.emit_transformer (transformer)}$recorders"
    }

    def addTrafoRecorders (name: String): String =
    {
        val current_phase = if (one_phase)
            "current_out_A.real,current_out_A.imag"
        else
            "current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag"
        val power_phase = if (one_phase)
            "power_out_A.real,power_out_A.imag"
        else
            "power_out_A.real,power_out_A.imag,power_out_B.real,power_out_B.imag,power_out_C.real,power_out_C.imag"
        s"""
           |        object recorder
           |        {
           |            name "${name}_current_recorder";
           |            parent "$name";
           |            property $current_phase;
           |            interval 5;
           |            file "output_data/${name}_current.csv";
           |        };
           |
           |        object recorder
           |        {
           |            name "${name}_power_recorder";
           |            parent "$name";
           |            property $power_phase;
           |            interval 5;
           |            file "output_data/${name}_power.csv";
           |        };
           |""".stripMargin
    }

    def generate_player (mrid: String): String =
    {
        if (one_phase)
         s"""            object player
            |            {
            |                property "constant_power_A";
            |                file "input_data/$mrid.csv";
            |            };
            |""".stripMargin
        else
         s"""            object player
            |            {
            |                property "constant_power_A";
            |                file "input_data/${mrid}_R.csv";
            |            };
            |            object player
            |            {
            |                property "constant_power_B";
            |                file "input_data/${mrid}_S.csv";
            |            };
            |            object player
            |            {
            |                property "constant_power_C";
            |                file "input_data/${mrid}_T.csv";
            |            };
            |""".stripMargin
    }

    def generate_voltage_recorder (mrid: String, node_id: String): String =
    {
        val property = if (one_phase)
            "voltage_A.real,voltage_A.imag"
        else
            "voltage_AB.real,voltage_AB.imag,voltage_BC.real,voltage_BC.imag,voltage_CA.real,voltage_CA.imag"
        s"""        object recorder
           |        {
           |            name "${mrid}_voltage_recorder";
           |            parent "$node_id";
           |            property $property;
           |            interval 5;
           |            file "output_data/${node_id}_voltage.csv";
           |        };
           |""".stripMargin
    }

    def generate_load (node: GLMNode): String =
    {
        val phases = if (one_phase) "AN" else "ABCN"
        trafokreis.experiments.find (_.node == node.id) match
        {
            case Some (experiment) =>
                s"""|
                    |        object load
                    |        {
                    |            name "${node.id}_load";
                    |            parent "${node.id}";
                    |            phases $phases;
                    |            nominal_voltage ${node.nominal_voltage}V;
                    |${ generate_player (experiment.house) }
                    |        };
                    |
                    |${ generate_voltage_recorder (experiment.house, node.id) }
                    |""".stripMargin
            case None =>
                ""
        }
    }

    def emit_pv (pvUnits: Iterable[PV], node: MaxPowerFeedingNodeEEA): String =
    {
        val parent = node.id_seq
        val voltage = node.voltage
        var load = ""
        var index = 1

        for (photoVoltaicUnit <- pvUnits)
        {
            val ratedP = photoVoltaicUnit.connection.p * 1000 // [W]
            val ratedQ = photoVoltaicUnit.connection.q * 1000 // [var]
            val ratedS = Complex(ratedP, ratedQ) // [VA]
            if (ratedS.modulus != photoVoltaicUnit.connection.ratedS*1000)
                log.warn (s"Calculated different ratedS for ${photoVoltaicUnit.connection.id} (${ratedS.modulus}) and (${photoVoltaicUnit.connection.ratedS})")

            if (ratedS.modulus > 0)
            {
                val phase = if (one_phase) "AN" else "ABCN"
                val power = if (one_phase)
                    s"""            constant_power_A ${ratedS.asString(6)};"""
                else
                {
                    val maxP3 = ratedS / 3.0
                    s"""            constant_power_A $maxP3;
                       |            constant_power_B $maxP3;
                       |            constant_power_C $maxP3;""".stripMargin
                }
                load +=
                    s"""
                       |        object load
                       |        {
                       |            name "${parent}_pv_$index";
                       |            parent "$parent";
                       |            phases $phase;
                       |            nominal_voltage ${voltage}V;
                       |            load_class R;
                       |$power
                       |        }
                       |""".stripMargin
                index += 1
            }
        }
        load
    }
}