package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.net.LineData
import ch.ninecode.net.LineDetails
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.Element
import ch.ninecode.model.Terminal
import ch.ninecode.net.LoadFlowEdge

/**
 * An edge in the GLM file.
 */
trait GLMEdge extends LoadFlowEdge
{
    /**
     * Return the .glm text for the edge, by default, make a link.
     *
     * @param generator The generator object with details on what/how to generate the .glm.
     * @return The string value to be included in the .glm file for this edge.
     */
    def emit (generator: GLMGenerator): String =
        s"""
        |        object link
        |        {
        |            name "$id";
        |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
        |            from "$cn1";
        |            to "$cn2";
        |        };
        |""".stripMargin
}

object GLMEdge
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    val rootClass:String = ConductingEquipment.getClass.getName.replace ("$", "")

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

    def multiconductor (element: Element): ACLineSegment =
    {
        element match
        {
            case acline: ACLineSegment => acline
            case conductor: Conductor =>
                new ACLineSegment (conductor, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List (), List (), List (), List (), null, null, null)
            case _ =>
                println ("unexpected class in edge elements (%s)".format (element.getClass))
                null
        }
    }

    def transformermaker (elements: Iterable[Element], cn1: String, cn2: String): GLMTransformerEdge =
    {
        log.error (s"edge from $cn1 to $cn2 has PowerTransformer class: ${elements.head.id}")
        GLMTransformerEdge (null)
    }

    /**
     * Temporary measure until we figure out how to create subclasses of GMLEdge from:
     *   - PreNode/PreEdge trace results
     *   - Island trace results
     *   - multi-island trace results
     *
     * @param elements the CIM elements comprising the edge
     * @param cn1 the TopologicalNode id of one end of the edge
     * @param cn2 the TopologicalNode id of the other end of the edge
     * @return a type of edge
     */
    def toGLMEdge (elements: Iterable[Element], cn1: String, cn2: String,
        makeTransformerEdge: (Iterable[Element], String, String) => GLMTransformerEdge = transformermaker, tbase: Double = 20.0): GLMEdge =
    {
        // for now, we handle Conductor, Switch and eventually PowerTransformer
        var tagged = elements.map (x => (baseClass (x), x))

        // check that all elements are the same base class
        if (!tagged.tail.forall (x => x._1 == tagged.head._1))
        {
            log.error ("edge from %s to %s has conflicting element types: %s".format (cn1, cn2, tagged.map (x => "%s(%s:%s)".format (x._1, classname (x._2), x._2.id))).mkString (","))
            tagged = tagged.take (1)
        }

        tagged.head._1 match
        {
            case "Switch" =>
                GLMSwitchEdge (cn1, cn2, elements)
            case "Conductor" =>
                val t1 = Terminal (TopologicalNode = cn1)
                val t2 = Terminal (TopologicalNode = cn2)
                implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails ()
                GLMLineEdge (LineData (elements.map (multiconductor).map (x => LineDetails (x, t1, t2, None, None))), tbase)
            // base_temperature: Double = 20.0,
            // DEFAULT_R: Double = 0.225,
            // DEFAULT_X: Double = 0.068
            case "PowerTransformer" =>
                makeTransformerEdge (elements, cn1, cn2)
            case _ =>
                log.error ("edge from %s to %s has unhandled class type '%s'".format (cn1, cn2, tagged.head._1))
                case class fakeEdge (override val id: String, override val cn1: String, override val cn2: String)
                    extends LoadFlowEdge (id, cn1, cn2) with GLMEdge
                fakeEdge (tagged.head._2.id, cn1, cn2)
        }
    }
}
