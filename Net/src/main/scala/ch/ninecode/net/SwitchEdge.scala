package ch.ninecode.net

class SwitchEdge
(
    data: SwitchData
)
extends LoadFlowEdge (
    data.switches.map (_.element.id).toArray.sortWith (_ < _).mkString ("_"),
    data.node0,
    data.node1
)
{
    def normalOpen: Boolean =
        data.switches.forall (x => x.asSwitch.normalOpen)

    def ratedCurrent: Double =
        data.switches.map (x => x.asSwitch.ratedCurrent).min

    def fuse: Boolean =
        data.switches.forall (_.fuse)

}

//object SwitchEdge
//{
//    // ToDo: remove me
//import ch.ninecode.model.Element
//import ch.ninecode.model.Terminal
//
//    def apply (
//        cn1: String,
//        cn2: String,
//        switches: Iterable[Element]
//    ): SwitchEdge =
//    {
//        val t1 = Terminal (TopologicalNode = cn1)
//        val t2 = Terminal (TopologicalNode = cn2)
//        SwitchEdge (SwitchData (switches.map (x => SwitchDetails (x, t1, t2, None))))
//    }
//}
