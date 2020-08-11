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
    def closed: Boolean = data.closed

    def ratedCurrent: Double =
        data.switches.map (x => x.asSwitch.ratedCurrent)
            .fold (Double.MaxValue)((a, b) => if (a < b) a else b) // instead of .min

    def fuse: Boolean =
        data.switches.forall (_.fuse)

}

