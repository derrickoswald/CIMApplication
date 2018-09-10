package ch.ninecode.mv

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.SwitchEdge
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.gl.TransformerSet
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

case class AbgangKreis (
    feeder: String,
    nodes: Iterable[PreNode],
    edges: Iterable[PreEdge])
{
    val start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2018-09-06T12:00:00")
    val finish_time: Calendar = start_time
    val swing_nodes: Array[SwingNode] = Array() // ToDo: get all N5 level abgang in stations
}

object AbgangKreis
{
    def multiconductor (element: Element): ACLineSegment =
    {
        element match
        {
            case acline: ACLineSegment ⇒ acline
            case conductor: Conductor ⇒
                new ACLineSegment (conductor, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List(), List(), List(), List(), null, null, null)
            case _ ⇒
                println ("unexpected class in edge elements (%s)".format (element.getClass))
                null
        }
    }

    /**
     * Create subclasses of GMLEdge for the given elements.
     *
     * @param elements the list of elements comprising the edge (could be parallel cables or ganged transformers).
     * ToDo: for ganged transformers and parallel lines we need to match the entire Iterable[Element] to some object like a TransformerSet
     * @param cn1 the mRID of the node connected to one end
     * @param cn2 the mRID of the node connected to the other end
     * @return a type of edge
     */
    def toGLMEdge (transformers: Array[TransformerSet]) (elements: Iterable[Element], cn1: String, cn2: String): GLMEdge =
    {
        // ToDo: check that all elements are the same class, e.g. ACLineSegment
        val element = elements.head
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        cls match
        {
            case "Switch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Switch],  false)
            case "Cut" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Cut].Switch,  false)
            case "Disconnector" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Disconnector].Switch,  false)
            case "Fuse" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Fuse].Switch,  true)
            case "GroundDisconnector" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[GroundDisconnector].Switch,  false)
            case "Jumper" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Jumper].Switch,  false)
            case "MktSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[MktSwitch].Switch,  false)
            case "ProtectedSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[ProtectedSwitch].Switch,  false)
            case "Breaker" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Breaker].ProtectedSwitch.Switch,  false)
            case "LoadBreakSwitch" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch,  false)
            case "Recloser" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Recloser].ProtectedSwitch.Switch,  false)
            case "Sectionaliser" ⇒
                SwitchEdge (cn1, cn2, element.asInstanceOf[Sectionaliser].Switch,  false)
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
                // find the transformer in the list
                TransformerEdge (cn1, cn2, transformers.find (_.transformers.map (_.transformer.id).contains (element.id)).orNull)
            case _ ⇒
                println ("""edge %s has unhandled class '%s'""".format (element.id, cls)) // ToDo: log somehow
                case class fakeEdge (id: String, cn1: String, cn2: String) extends GLMEdge
                fakeEdge (element.id, cn1, cn2)
        }
    }
}
