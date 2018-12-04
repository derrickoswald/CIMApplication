package ch.ninecode.gl

import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Breaker
import ch.ninecode.model.Conductor
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.MktSwitch
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch

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
    def multiconductor (element: Element): ACLineSegment =
    {
        element match
        {
            case acline: ACLineSegment ⇒ acline
            case conductor: Conductor ⇒
                new ACLineSegment (conductor, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List (), List (), List (), List (), null, null, null)
            case _ ⇒
                println ("unexpected class in edge elements (%s)".format (element.getClass))
                null
        }
    }

    /**
     * Temporary measure until we figure out how to create subclasses of GMLEdge from:
     *   - PreNode/PreEdge trace results
     *   - Island trace results
     *   - multi-island trace results
     *
     * @param elements
     * @param cn1
     * @param cn2
     * @return a type of edge
     */
    def toGLMEdge (elements: Iterable[Element], cn1: String, cn2: String): GLMEdge =
    {
        // ToDo: check that all elements are the same class, e.g. ACLineSegment
        val element = elements.head
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        cls match
        {
            case "Switch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Switch], false)
            case "Cut" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Cut].Switch, false)
            case "Disconnector" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Disconnector].Switch, false)
            case "Fuse" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Fuse].Switch, true)
            case "GroundDisconnector" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [GroundDisconnector].Switch, false)
            case "Jumper" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Jumper].Switch, false)
            case "MktSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [MktSwitch].Switch, false)
            case "ProtectedSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [ProtectedSwitch].Switch, false)
            case "Breaker" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Breaker].ProtectedSwitch.Switch, false)
            case "LoadBreakSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [LoadBreakSwitch].ProtectedSwitch.Switch, false)
            case "Recloser" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Recloser].ProtectedSwitch.Switch, false)
            case "Sectionaliser" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf [Sectionaliser].Switch, false)
            case "Conductor" ⇒
                LineEdge (cn1, cn2, elements.map (multiconductor))
            //                base_temperature: Double = 20.0,
            //                target_temperature: Double = 20.0,
            //                DEFAULT_R: Double = 0.225,
            //                DEFAULT_X: Double = 0.068
            case "ACLineSegment" ⇒
                LineEdge (cn1, cn2, elements.map (multiconductor))
            //                base_temperature: Double = 20.0,
            //                target_temperature: Double = 20.0,
            //                DEFAULT_R: Double = 0.225,
            //                DEFAULT_X: Double = 0.068
            case "PowerTransformer" ⇒
                println ("""edge %s has PowerTransformer class""".format (element.id)) // ToDo: log somehow
                TransformerEdge (cn1, cn2, null) // ToDo: for ganged transformers and parallel lines we need to match the entire Iterable[Element] to some object like a TranformerSet
            case _ ⇒
                println ("""edge %s has unhandled class '%s'""".format (element.id, cls)) // ToDo: log somehow
            case class fakeEdge (id: String, cn1: String, cn2: String) extends GLMEdge
                fakeEdge (element.id, cn1, cn2)
        }
    }
}
