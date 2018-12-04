package ch.ninecode.gl

case class FlowDirection (flags: Int)
{
    def this (serialized: String) = this (FlowDirection.parse (serialized))

    def bits (mask: Int): Boolean = mask == (flags & mask)

    def a: Double = if (bits (0x003)) 0.0 else
        if (bits (0x002)) -1.0 else
            if (bits (0x001)) 1.0 else Double.NaN

    def b: Double = if (bits (0x030)) 0.0 else
        if (bits (0x020)) -1.0 else
            if (bits (0x010)) 1.0 else Double.NaN

    def c: Double = if (bits (0x300)) 0.0 else
        if (bits (0x200)) -1.0 else
            if (bits (0x100)) 1.0 else Double.NaN
}

object FlowDirection
{
    // parse flow direction flags "CN|BN|AN"
    def parse (s: String): Int =
    {
        var i_flags = 0
        s.split ("""\x7c""").foreach
        {
            case "AF" ⇒ i_flags = i_flags | 0x001
            case "AR" ⇒ i_flags = i_flags | 0x002
            case "AN" ⇒ i_flags = i_flags | 0x003
            case "BF" ⇒ i_flags = i_flags | 0x010
            case "BR" ⇒ i_flags = i_flags | 0x020
            case "BN" ⇒ i_flags = i_flags | 0x030
            case "CF" ⇒ i_flags = i_flags | 0x100
            case "CR" ⇒ i_flags = i_flags | 0x200
            case "CN" ⇒ i_flags = i_flags | 0x300
            case "UNKNOWN" ⇒ i_flags = 0
            case _ ⇒
                // OK, so this is a thing
                //            2018-07-19 10:15:15 UTC,+399.862,-0.575961
                //            2018-07-19 10:15:20 UTC,+400,-1.58843e-06
                //            2018-07-19 10:15:25 UTC,+399.862,-0.52,-0.578047
                //            2018-07-19 10:15:30 UTC,+399.861,-0.583124
                //            2018-07-19 10:15:35 UTC,+399.865,-0.595896
                // sometimes GridLAB-D emits a third bogus value, so assume it's not there and use flow direction forward-forward-forward
                i_flags = 0x001 | 0x010 | 0x100
        }
        i_flags
    }

    def apply (serialized: String) = new FlowDirection (parse (serialized))
}