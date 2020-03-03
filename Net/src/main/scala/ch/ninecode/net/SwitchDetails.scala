package ch.ninecode.net;


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.model._

/**
 * A switch element.
 *
 * @param element the switch
 * @param terminal1 associated Terminal one
 * @param terminal2 associated Terminal two
 * @param switch_info asset information for this switch
 */
final case class SwitchDetails (
    element: Element,
    terminal1: Terminal,
    terminal2: Terminal,
    switch_info: Option[Element])
{
    import SwitchDetails._

    lazy val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the generic Switch object.
     *
     * @return the fuse, breaker etc. subclass as the Switch superclass
     */
    def asSwitch: Switch =
    {
        element match
        {
            case s: Switch =>             s
            case c: Cut =>                c.Switch
            case d: Disconnector =>       d.Switch
            case f: Fuse =>               f.Switch
            case g: GroundDisconnector => g.Switch
            case j: Jumper =>             j.Switch
            case m: MktSwitch =>          m.Switch
            case p: ProtectedSwitch =>    p.Switch
            case b: Breaker =>            b.ProtectedSwitch.Switch
            case l: LoadBreakSwitch =>    l.ProtectedSwitch.Switch
            case r: Recloser =>           r.ProtectedSwitch.Switch
            case s: Sectionaliser =>      s.Switch
            case _ =>
                log.error (s"non-switch (${element.getClass}:${element.id}) in SwitchEdge")
                null.asInstanceOf [Switch]
        }
    }

    /**
     * Get the conducting equipment voltage.
     *
     * @param mapping the mapping between BaseVoltage and voltage
     * @return the voltage, or 0.0 if it was not found (V)
     */
    def v (implicit mapping: Array[(String, Double)]): Double =
    {
        mapping.find (_._1 == asSwitch.ConductingEquipment.BaseVoltage) match
        {
            case Some ((_, v)) => v
            case _ => 0.0
        }
    }

    /**
     * Determine if the bitfield is set for the given mask.
     *
     * @param mask single bit mask to check.
     * @return <code>true</code> if the bit is set, <code>false</code> otherwise.
     */
    def isSet (mask: Int): Boolean = 0 != (element.bitfields (mask / 32) & (1 << (mask % 32)))

    /**
     * Get the normalOpen state from the switch.
     *
     * @return <code>true</code> if the switch is normally open, <code>false</code> otherwise
     */
    def normalOpen: Boolean =
    {
        if (isSet (normalOpenMask))
            asSwitch.normalOpen // normalOpen valid
        else
            false
    }

    /**
     * Get the open state from the switch.
     *
     * @return <code>true</code> if the switch is open, <code>false</code> otherwise
     */
    def open: Boolean =
    {
        if (isSet (openMask))
            asSwitch.open // open valid
        else
            false
    }

    def closed: Boolean = !normalOpen && ! open

    /**
     * Get the rated current for the switch.
     *
     * @return the rated current (A)
     */
    def ratedCurrent: Double =
    {
        if (isSet (ratedCurrentMask))
            asSwitch.ratedCurrent // ratedCurrent valid
        else
            switch_info match
            {
                case Some (info: SwitchInfo) =>
                    info.ratedCurrent
                case _ =>
                    0.0
            }
    }

    def fuse: Boolean =
    {
        element match
        {
            case _: Fuse => true
            case _ => false
        }
    }

    /** @return a summary string for this switch */
    override def toString: String = s"""${if (fuse) "fuse" else "switch"} ${element.id} ${if (closed) "closed" else "open"} @${ratedCurrent}"""
}

object SwitchDetails
{
    /**
     * Index of normalOpen field in Switch bitmask.
     */
    lazy val normalOpenMask: Int = Switch.fields.indexOf ("normalOpen")

    /**
     * Index of open field in Switch bitmask.
     */
    lazy val openMask: Int = Switch.fields.indexOf ("open")

    /**
     * Index of ratedCurrent field in Switch bitmask.
     */
    lazy val ratedCurrentMask: Int = Switch.fields.indexOf ("ratedCurrent")
}