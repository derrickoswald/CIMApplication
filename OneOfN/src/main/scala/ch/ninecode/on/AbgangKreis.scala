package ch.ninecode.on

import java.util.Calendar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Breaker
import ch.ninecode.model.Conductor
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.MktSwitch
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch
import ch.ninecode.model.Terminal
import ch.ninecode.net.LineData
import ch.ninecode.net.LineDetails
import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.TransformerSet

case class AbgangKreis
(
    feeder: String,
    nodes: Iterable[PreNode],
    edges: Iterable[PreEdge])
{
    val start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2018-09-06T12:00:00")
    val finish_time: Calendar = start_time
    val swing_nodes: Array[SwingNode] = Array () // ToDo: get all N5 level abgang in stations
}

object AbgangKreis
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    def multiconductor (element: Element): Option[ACLineSegment] =
    {
        element match
        {
            case acline: ACLineSegment => Some(acline)
            case conductor: Conductor =>
                Some (ACLineSegment (conductor))
            case _ =>
                log.error (s"unexpected class in edge elements (${element.getClass})")
                None
        }
    }

    def toSwitch (element: Element): Option[Switch] =
    {
        element match
        {
            case s: Switch => Some (s)
            case c: Cut => Some (c.Switch)
            case d: Disconnector => Some (d.Switch)
            case f: Fuse => Some (f.Switch)
            case g: GroundDisconnector => Some (g.Switch)
            case j: Jumper => Some (j.Switch)
            case m: MktSwitch => Some (m.Switch)
            case p: ProtectedSwitch => Some (p.Switch)
            case b: Breaker => Some (b.ProtectedSwitch.Switch)
            case l: LoadBreakSwitch => Some (l.ProtectedSwitch.Switch)
            case r: Recloser => Some (r.ProtectedSwitch.Switch)
            case s: Sectionaliser => Some (s.Switch)
            case _ =>
                log.error (s"non-switch (${element.getClass}:${element.id})")
                None
        }
    }

    def pickSwitch (elements: Iterable[Element]): Option[Switch] =
    {
        // try for a closed switch
        val switches = for (
            element <- elements;
            switch = toSwitch (element)
            )
            yield
                switch
        // otherwise fall back to just the first switch
        val none: Option[Switch] = None
        switches.foldLeft (none) (
            (last, switch) => last match
            {
                case Some (s) =>
                    if (!s.normalOpen) last else switch
                case _ =>
                    switch
            }
        )
    }

    /**
     * Create subclasses of GMLEdge for the given elements.
     *
     * @param elements the list of elements comprising the edge (could be parallel cables or ganged transformers).
     *                 ToDo: for ganged transformers and parallel lines we need to match the entire Iterable[Element] to some object like a TransformerSet
     * @param cn1      the mRID of the node connected to one end
     * @param cn2      the mRID of the node connected to the other end
     * @return a type of edge
     */
    def toGLMEdge (transformers: Array[TransformerSet])(elements: Iterable[Element], cn1: String, cn2: String): GLMEdge =
    {
        case class fakeEdge (override val id: String, override val cn1: String, override val cn2: String)
        extends LoadFlowEdge (id, cn1, cn2)
        with GLMEdge

        // ToDo: check that all elements are the same class, e.g. ACLineSegment
        val element = elements.head
        element match
        {
            case _: Switch | _: Cut | _: Disconnector | _: Fuse | _: GroundDisconnector | _: Jumper | _: MktSwitch | _: ProtectedSwitch | _: Breaker | _: LoadBreakSwitch | _: Recloser | _: Sectionaliser =>
                PlayerSwitchEdge (cn1, cn2, pickSwitch (elements).get, fuse = false)
            case _: Conductor | _: ACLineSegment =>
                val t1 = Terminal (TopologicalNode = cn1)
                val t2 = Terminal (TopologicalNode = cn2)
                implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails ()
                GLMLineEdge (LineData (elements.flatMap (multiconductor).map (x => LineDetails (x, t1, t2, None, None))))
            //                DEFAULT_R: Double = 0.225,
            //                DEFAULT_X: Double = 0.068
            case _: PowerTransformer =>
                // find the transformer in the list
                transformers.find (_.transformers.map (_.transformer.id).contains (element.id)) match
                {
                    case Some (t) =>
                        GLMTransformerEdge (t)
                    case _ =>
                        log.error (s"no transformer found for ${element.id}") // ToDo: log somehow
                        fakeEdge (element.id, cn1, cn2)
                }
            case _ =>
                log.error (s"edge ${element.id} has unhandled class '${element.getClass.getName}'") // ToDo: log somehow
                fakeEdge (element.id, cn1, cn2)
        }
    }
}
