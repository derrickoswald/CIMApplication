package ch.ninecode.sim

import java.util.Calendar

case class SimulationTask
(
    island: String,
    start: Calendar,
    end: Calendar,
    players: Seq[SimulationPlayer],
    recorders: Seq[SimulationRecorder]
)
{

}
