package ch.ninecode.gl

import java.util.Calendar

import ch.ninecode.model._

trait Problem
{
    def name (): String
    def start_time (): Calendar
    def finish_time (): Calendar
    def swing_node (): String
    def swing_node_voltage (): Double
}
