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

