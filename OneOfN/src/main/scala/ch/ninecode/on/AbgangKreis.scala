package ch.ninecode.on

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.model._
import ch.ninecode.net._

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
    def multiconductor (element: Element): ACLineSegment =
    {
        element match
        {
            case acline: ACLineSegment ⇒ acline
            case conductor: Conductor ⇒
                new ACLineSegment (conductor, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, List (), List (), List (), List (), null, null, null)
            case _ ⇒
                println ("unexpected class in edge elements (%s)".format (element.getClass))
                null
        }
    }

    def toSwitch (element: Element): Switch =
    {
        element match
        {
            case s: Switch ⇒ s.asInstanceOf [Switch]
            case c: Cut ⇒ c.asInstanceOf [Cut].Switch
            case d: Disconnector ⇒ d.asInstanceOf [Disconnector].Switch
            case f: Fuse ⇒ f.asInstanceOf [Fuse].Switch
            case g: GroundDisconnector ⇒ g.asInstanceOf [GroundDisconnector].Switch
            case j: Jumper ⇒ j.asInstanceOf [Jumper].Switch
            case m: MktSwitch ⇒ m.asInstanceOf [MktSwitch].Switch
            case p: ProtectedSwitch ⇒ p.asInstanceOf [ProtectedSwitch].Switch
            case b: Breaker ⇒ b.asInstanceOf [Breaker].ProtectedSwitch.Switch
            case l: LoadBreakSwitch ⇒ l.asInstanceOf [LoadBreakSwitch].ProtectedSwitch.Switch
            case r: Recloser ⇒ r.asInstanceOf [Recloser].ProtectedSwitch.Switch
            case s: Sectionaliser ⇒ s.asInstanceOf [Sectionaliser].Switch
            case _ ⇒
                println ("non-switch (%s:%s)".format (element.getClass, element.id))
                null.asInstanceOf [Switch]
        }
    }

    def pickSwitch (elements: Iterable[Element]): Switch =
    {
        var ret: Switch = null
        // try for (the first) closed switch
        for (element ← elements)
        {
            val switch = toSwitch (element)
            if (!switch.normalOpen)
                if (null == ret)
                    ret = switch
        }
        // otherwise fall back to just the first switch
        if (null == ret)
            ret = toSwitch (elements.head)
        ret
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
        case class fakeEdge (id: String, cn1: String, cn2: String) extends GLMEdge

        // ToDo: check that all elements are the same class, e.g. ACLineSegment
        val element = elements.head
        element match
        {
            case _: Switch | _: Cut | _: Disconnector | _: Fuse | _: GroundDisconnector | _: Jumper | _: MktSwitch | _: ProtectedSwitch | _: Breaker | _: LoadBreakSwitch | _: Recloser | _: Sectionaliser ⇒
                PlayerSwitchEdge (cn1, cn2, pickSwitch (elements), fuse = false)
            case _: Conductor | _: ACLineSegment ⇒
                val t1 = Terminal (TopologicalNode = cn1)
                val t2 = Terminal (TopologicalNode = cn2)
                implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails ()
                LineEdge (LineData (elements.map (multiconductor).map (x => LineDetails (x, t1, t2, None, None))))
            //                DEFAULT_R: Double = 0.225,
            //                DEFAULT_X: Double = 0.068
            case _: PowerTransformer ⇒
                // find the transformer in the list
                val t = transformers.find (_.transformers.map (_.transformer.id).contains (element.id)).orNull
                if (null == t)
                {
                    println ("""no transformer found for %s""".format (element.id)) // ToDo: log somehow
                    fakeEdge (element.id, cn1, cn2)
                }
                else
                    TransformerEdge (t)
            case _ ⇒
                println ("""edge %s has unhandled class '%s'""".format (element.id, element.getClass.getName)) // ToDo: log somehow
                fakeEdge (element.id, cn1, cn2)
        }
    }
}
