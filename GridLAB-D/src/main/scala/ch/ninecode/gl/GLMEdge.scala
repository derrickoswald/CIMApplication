package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.net.LineData
import ch.ninecode.net.LineDetails
import ch.ninecode.model._
import ch.ninecode.util.Graphable

/**
 * Basic properties of an edge.
 */
trait GLMEdge extends Graphable with Serializable
{
    /**
     * The unique edge identifier.
     *
     * @return The ID of the edge (the mRID of the electrical element).
     */
    def id: String

    /**
     * The node id connected to the first terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 1.
     */
    def cn1: String

    /**
     * The node id connected to the second terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 2.
     */
    def cn2: String

    /**
     * Ordered key.
     * Provide a key on the two connections, independent of to-from from-to ordering.
     */
    def key: String = if (cn1 < cn2) cn1 + cn2 else cn2 + cn1

    /**
     * Return the .glm text for the edge.
     *
     * @param generator The generator object with details on what/how to generate the .glm.
     * @return The string value to be included in the .glm file for this edge.
     */
    def emit (generator: GLMGenerator): String =
    // by default, make a link
        """
          |        object link
          |        {
          |            name "%s";
          |            phases %s;
          |            from "%s";
          |            to "%s";
          |        };
          |""".stripMargin.format (id, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2)

    /**
     * Generate a valid configuration name.
     *
     * Use the given string, usually a library type description (e.g. "GKN 3x95se/95 1/0.6 kV" or "4x95, Ceanderkabel",
     * to create a valid GridLAB-D configuration name.
     * The intent is to allow human-readable configuration names while adhering to GrdLAB-D rules such as:
     *
     * - no leading digits: ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
     * - no decimal points: KLE199604 (underground_line:227) reference to TT 3x2.5 is missing match value
     *
     */
    def valid_config_name (string: String): String =
    {
        val s = if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
        s.replace (".", "d").replace (":", "$")
    }
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

    def transformermaker (elements: Iterable[Element], cn1: String, cn2: String): TransformerEdge =
    {
        log.error (s"edge from $cn1 to $cn2 has PowerTransformer class: ${elements.head.id}")
        TransformerEdge (null)
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
       makeTransformerEdge: (Iterable[Element], String, String) => TransformerEdge = transformermaker): GLMEdge =
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
                SwitchEdge (cn1, cn2, elements)
            case "Conductor" =>
                val t1 = Terminal (TopologicalNode = cn1)
                val t2 = Terminal (TopologicalNode = cn2)
                implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails ()
                LineEdge (LineData (elements.map (multiconductor).map (x => LineDetails (x, t1, t2, None, None))))
                // base_temperature: Double = 20.0,
                // DEFAULT_R: Double = 0.225,
                // DEFAULT_X: Double = 0.068
            case "PowerTransformer" =>
                makeTransformerEdge (elements, cn1, cn2)
            case _ =>
                log.error ("edge from %s to %s has unhandled class type '%s'".format (cn1, cn2, tagged.head._1))
                case class fakeEdge (id: String, cn1: String, cn2: String) extends GLMEdge
                fakeEdge (tagged.head._2.id, cn1, cn2)
        }
    }
}
