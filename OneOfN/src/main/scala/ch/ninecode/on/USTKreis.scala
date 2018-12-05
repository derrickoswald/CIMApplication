package ch.ninecode.on

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

case class USTKreis
(
    hv_transformers: Array[TransformerSet],
    raw_nodes: Iterable[PreNode],
    edges: Iterable[PreEdge],
    loads: Array[TransformerSet])
{
    val start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04T12:00:00")
    val finish_time: Calendar = start_time
    val trafokreis_key: String = hv_transformers.head.transformer_name
    val swing_nodes: Array[SwingNode] = hv_transformers.map (x ⇒ SwingNode (x.node1, x.v1, x.transformer_name))

    // add loads to nodes that are transformer secondary terminals
    def addLoad (raw: PreNode): USTNode =
    {
        loads.find (_.node1 == raw.id) match
        {
            case Some (load) ⇒ USTNode (raw.id, raw.nominal_voltage, load)
            case None ⇒ USTNode (raw.id, raw.nominal_voltage, null)
        }
    }

    val nodes: Iterable[USTNode] = raw_nodes.map (addLoad)
    // get low voltage transformers for our nodes
    val lv_transformers: Array[TransformerSet] = nodes.filter (null != _.load).map (_.load).toArray
    // the transformers to process is the combination of HV and MV transformers
    val transformers: Array[TransformerSet] = Array.concat (hv_transformers, lv_transformers)

    /**
     * Create subclasses of GMLEdge for the given elements.
     *
     * @param elements the list of elements comprising the edge (could be parallel cables or ganged transformers).
     *                 ToDo: for ganged transformers and parallel lines we need to match the entire Iterable[Element] to some object like a TransformerSet
     * @param cn1      the mRID of the node connected to one end
     * @param cn2      the mRID of the node connected to the other end
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
                LineEdge (cn1, cn2, elements.map (x ⇒ new ACLineSegment (x.asInstanceOf [Conductor], 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List (), List (), List (), List (), null, null, null)))
            //                base_temperature: Double = 20.0,
            //                target_temperature: Double = 20.0,
            //                DEFAULT_R: Double = 0.225,
            //                DEFAULT_X: Double = 0.068
            case "ACLineSegment" ⇒
                LineEdge (cn1, cn2, elements.map (_.asInstanceOf [ACLineSegment]))
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
