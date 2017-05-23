package ch.ninecode.gl

import java.util.Calendar

import ch.ninecode.model._

trait Problem
{
    import Problem._
    def node (t: Terminal) = if (USE_TOPOLOGICAL_NODES) t.TopologicalNode else t.ConnectivityNode

    def name (): String
    def start_time (): Calendar
    def finish_time (): Calendar
    def swing_node (): String
    def swing_node_voltage (): Double
}

object Problem
{
    var USE_TOPOLOGICAL_NODES: Boolean = true
}

