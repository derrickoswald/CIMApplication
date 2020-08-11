package ch.ninecode.gl

import scala.util.Random

import ch.ninecode.model.ACDCTerminal
import ch.ninecode.model.BasicElement
import ch.ninecode.model.Element
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.Terminal
import ch.ninecode.net.SwitchData
import ch.ninecode.net.SwitchDetails
import ch.ninecode.net.SwitchEdge

case class GLMSwitchEdge (
    data: SwitchData
)
    extends SwitchEdge (data)
        with GLMEdge
{
    /**
     * Emit a switch or fuse.
     *
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val status = if (closed) "CLOSED" else "OPEN"
        // ensure it doesn't trip immediately
        val current = if (ratedCurrent <= 0) 9999.0 else ratedCurrent

        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val fuse_details = if (fuse)
            s"""
               |            mean_replacement_time 3600.0;
               |            current_limit ${current}A;""".stripMargin
        else
            ""
        s"""
           |        object ${if (fuse) "fuse" else "switch"}
           |        {
           |            name "$id";
           |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
           |            from "$cn1";
           |            to "$cn2";
           |            status "$status";$fuse_details
           |        };
           |""".stripMargin
    }
}

object GLMSwitchEdge
{
    // ToDo: remove me
    def apply (
        cn1: String,
        cn2: String,
        switches: Iterable[Element]
    ): GLMSwitchEdge =
    {
        val basic1 = BasicElement (mRID = s"generated_terminal_${Random.nextInt (99999999)}")
        val obj1 = IdentifiedObject (basic1, mRID = basic1.mRID)
        obj1.bitfields = IdentifiedObject.fieldsToBitfields ("mRID")
        val ac1 = ACDCTerminal (obj1, true, 1)
        ac1.bitfields = ACDCTerminal.fieldsToBitfields ("connected", "sequenceNumber")
        val t1 = Terminal (ac1, TopologicalNode = cn1)
        t1.bitfields = Terminal.fieldsToBitfields ("TopologicalNode")

        val basic2 = BasicElement (mRID = s"generated_terminal_${Random.nextInt (99999999)}")
        val obj2 = IdentifiedObject (basic2, mRID = basic2.mRID)
        obj2.bitfields = IdentifiedObject.fieldsToBitfields ("mRID")
        val ac2 = ACDCTerminal (obj2, true, 2)
        ac2.bitfields = ACDCTerminal.fieldsToBitfields ("connected", "sequenceNumber")
        val t2 = Terminal (ac2, TopologicalNode = cn2)
        t2.bitfields = Terminal.fieldsToBitfields ("TopologicalNode")

        GLMSwitchEdge (SwitchData (switches.map (x => SwitchDetails (x, t1, t2, None))))
    }
}