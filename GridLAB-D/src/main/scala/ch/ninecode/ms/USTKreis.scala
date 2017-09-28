package ch.ninecode.ms

import java.util.Calendar

import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerSet

case class USTKreis (
    hv_transformers: Array[TransformerSet],
    raw_nodes: Iterable[PreNode],
    edges: Iterable[Iterable[PreEdge]],
    loads: Array[TransformerSet])
{
    lazy val start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04T12:00:00")
    lazy val finish_time: Calendar = start_time
    lazy val trafokreis_key: String = hv_transformers.head.transformer_name
    lazy val swing_nodes: Array[SwingNode] = hv_transformers.map (x ⇒ SwingNode (x.node1, x.v1, x.transformer_name))
    // add loads to nodes that are transformer secondary terminals
    def addLoad (raw: PreNode): USTNode =
    {
        loads.find (_.node1 == raw.id) match
        {
            case Some (load) ⇒ USTNode (raw.id, raw.voltage, load)
            case None ⇒ USTNode (raw.id, raw.voltage, null)
        }
    }
    lazy val nodes: Iterable[USTNode] = raw_nodes.map (addLoad)
    // get low voltage transformers for our nodes
    lazy val lv_transformers: Array[TransformerSet] = nodes.filter (null != _.load).map (_.load).toArray
    // the transformers to process is the combination of HV and MV transformers
    lazy val transformers: Array[TransformerSet] = Array.concat (hv_transformers, lv_transformers)
}
