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

    def toTransformerEdge (elements: Iterable[Element], cn1: String, cn2: String): TransformerEdge =
        // find the transformer in the list
        TransformerEdge (cn1, cn2, transformers.find (_.transformers.map (_.transformer.id).contains (elements.head.id)).orNull)
}
