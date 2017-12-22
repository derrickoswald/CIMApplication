package ch.ninecode.sc

case class ScNode (
    id_seq: String,
    voltage: Double,
    source_obj: StartingTrafos,
    impedance: Impedanzen)
extends
    Graphable
