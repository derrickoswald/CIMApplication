package ch.ninecode.net

import ch.ninecode.model.Element
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.Island.island_id

/**
 * Composite terminal object
 *
 * @param terminal the terminal
 * @param node     the node it is connected to
 * @param voltage  the voltage of the terminal if available, otherwise zero
 * @param element  the conducting equipment
 */
case class TerminalPlus (id: island_id, terminal: Terminal, node: TopologicalNode, voltage: Double, element: Element)

