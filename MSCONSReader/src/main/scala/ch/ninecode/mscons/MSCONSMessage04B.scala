package ch.ninecode.mscons

import java.util.Calendar
import java.util.regex.Pattern

import ch.ninecode.edifact.UNT
import ch.ninecode.edifact.UNZ

//    Pos     Tag Name                                     S   R
//
//    HEADER SECTION
//
//    00010   UNH Message header                           M   1
//    00020   BGM Beginning of message                     M   1
//    00030   DTM Date/time/period                         M   9
//    00040   CUX Currencies                               C   9
//
//    00050       ---- Segment group 1  ------------------ C   9----------------+
//    00060   RFF Reference                                M   1                |
//    00070   DTM Date/time/period                         C   9----------------+
//
//    00080       ---- Segment group 2  ------------------ C   99---------------+
//    00090   NAD Name and address                         M   1                |
//                                                                              |
//    00100       ---- Segment group 3  ------------------ C   9---------------+|
//    00110   RFF Reference                                M   1               ||
//    00120   DTM Date/time/period                         C   9---------------+|
//                                                                              |
//    00130       ---- Segment group 4  ------------------ C   9---------------+|
//    00140   CTA Contact information                      M   1               ||
//    00150   COM Communication contact                    C   9---------------++
//
//    DETAIL SECTION
//
//    00160   UNS Section control                          M   1
//
//    00170       ---- Segment group 5  ------------------ M   99999------------+
//    00180   NAD Name and address                         M   1                |
//                                                                              |
//    00190       ---- Segment group 6  ------------------ M   99999-----------+|
//    00200   LOC Place/location identification            M   1               ||
//    00210   DTM Date/time/period                         C   9               ||
//                                                                             ||
//    00220       ---- Segment group 7  ------------------ C   99-------------+||
//    00230   RFF Reference                                M   1              |||
//    00240   DTM Date/time/period                         C   9--------------+||
//                                                                             ||
//    00250       ---- Segment group 8  ------------------ C   99-------------+||
//    00260   CCI Characteristic/class id                  M   1              |||
//    00270   DTM Date/time/period                         C   99-------------+||
//                                                                             ||
//    00280       ---- Segment group 9  ------------------ C   99999----------+||
//    00290   LIN Line item                                M   1              |||
//    00300   PIA Additional product id                    C   9              |||
//    00310   IMD Item description                         C   9              |||
//    00320   PRI Price details                            C   9              |||
//    00330   NAD Name and address                         C   9              |||
//    00340   MOA Monetary amount                          C   9              |||
//                                                                            |||
//    00350       ---- Segment group 10 ------------------ M   9999----------+|||
//    00360   QTY Quantity                                 M   1             ||||
//    00370   DTM Date/time/period                         C   9             ||||
//    00380   STS Status                                   C   9-------------+|||
//                                                                            |||
//    00390       ---- Segment group 11 ------------------ C   99------------+|||
//    00400   CCI Characteristic/class id                  M   1             ||||
//    00410   MEA Measurements                             C   99            ||||
//    00420   DTM Date/time/period                         C   9-------------++++
//
//    SUMMARY SECTION
//
//    00430   CNT Control total                            C   99
//    00440   UNT Message trailer                          M   1

case class Group1 (
    rff: RFF,
    dtm_p: Option[List[DTM]])

case class Group3 (
    rff: RFF,
    dtm_p: Option[List[DTM]]
)

case class Group4 (
    cta: CTA,
    com: Option[List[COM]])

case class Group2 (
    nad: NAD,
    group3: Option[List[Group3]],
    group4: Option[List[Group4]]
)

case class Group7 (
    reference: RFF,
    dateTimePeriod: Option[List[DTM]]
)

case class Group8 (
    characteristicClassId: CCI,
    dateTimePeriod: Option[List[DTM]]
)

case class Group10 (
    qty: QTY,
    dtm: Option[List[DTM]],
    sts: Option[List[STS]]
)
{
    def getValue: Option[(Calendar, Int, Double)] =
    {
        val value = qty.quantity.toDouble
        if (dtm.isDefined)
        {
            val start = dtm.get.find (x => x.functionCodeQualifier == "163")
            val end = dtm.get.find (x => x.functionCodeQualifier == "164")
            if (start.isDefined && end.isDefined)
            {
                val s = start.get.getTime
                Some ((s, (end.get.getTime.getTimeInMillis - s.getTimeInMillis).toInt, value))
            }
            else
                None
        }
        else
            None
    }
}

case class Group11 (
    cci: CCI,
    mea: Option[List[MEA]],
    DTM: Option[List[DTM]]
)

case class Group9 (
    lin: LIN,
    pia: Option[List[PIA]],
//    imd: Option[List[IMD]],
//    pri: Option[List[PRI]],
//    nad: Option[List[NAD]],
//    moa: Option[List[MOA]],
    group10: List[Group10],
    group11: Option[List[Group11]]
)

case class Group6 (
    loc: LOC,
    dtm: Option[List[DTM]],
    group7: Option[List[Group7]],
    group8: Option[List[Group8]],
    group9: Option[List[Group9]]
)

case class Group5 (
    nad: NAD,
    group6: List[Group6]
)

case class MSCONSMessage04B (
    bgm: BGM,
    dtm: DTM,
    cux: Option[CUX],
    group1: Option[List[Group1]],
    group2: Option[List[Group2]],
    uns: UNS,
    group5: List[Group5],
    cnt: Option[CNT],
    unt: UNT,
    unz: UNZ
)
{
    /**
     * Parse an OBIS code in the additional product id (PIA) segment.
     *
     * The OBIS code consists of (up to) 6 group sub-identifiers marked
     * by letters A to F. All these may or may not be present in the
     * identifier (e.g. groups A and B are often omitted).
     * In order to decide to which group the sub-identifier belongs,
     * the groups are separated by unique separators:
     *
     *   A-B:C.D.E*F
     *
     *   - The A group defines the medium
     *     (0=abstract objects, 1=electricity, 6=heat, 7=gas, 8=water, ...)
     *   - The B group defines the channel.
     *     Each device with multiple channels generating measurement results,
     *     can separate the results into the channels.
     *   - The C group defines the physical value
     *     (current, voltage, energy, level, temperature, ...)
     *   - The D group defines the quantity computation output of specific algorithm
     *   - The E group specifies the measurement type defined by
     *     groups A to D into individual measurements (e.g. switching ranges)
     *   - The F group separates the results partly defined by groups A to E.
     *     The typical usage is the specification of individual time ranges.
     *
     * Reduced ID codes (e.g. for IEC 62056-21 )
     * To comply with the syntax defined for protocol modes A to D of IEC 62056-21, the range of ID
     * codes is reduced to fulfil the limitations which are usually applied to the number of digits and
     * the ASCII representation of them. All value groups are limited to a range of 0 .. 99 and within
     * that range, to the limits given in the relevant chapters.
     * Some value groups may be suppressed, if they are not relevant to an application:
     *     Optional value groups: A, B, E, F
     *     Mandatory value groups: C, D
     * To allow the interpretation of shortened codes delimiters are
     * inserted between all value groups:
     *
     *   A-B:C.D.E*F
     *
     * The delimiter between value groups E and F can be modified to carry some information about
     * the source of a reset (& instead of * if the reset was performed manually).
     * For compatibility with existing implementations, in value group A an identifier for an energy
     * type may be used even for abstract objects.
     *
     * For CIM mapping could use:
     *   Electricity metering data exchange – The DLMS/COSEM suite –
     *   Part 6-9: Mapping between the Common Information Model message profiles
     *   (IEC 61968-9) and DLMS/COSEM (IEC 62056) data models and protocols
     */
    lazy val obis: Pattern = java.util.regex.Pattern.compile ("""^((\d+)-)*((\d+):)*(\d+)\.(\d+)(\.(\d+))*(\*(\d+))*$""")

    type ID = String
    type Quantity = String
    type Time = Calendar
    type Period = Int
    type Real = Double
    type Imaginary = Double
    type Units = String

    // function that takes a measurement value to (type, real, imaginary, units)
    type MeasurementFunctor = Double => (Quantity, Real, Imaginary, Units)

    def error (message: String): MeasurementFunctor =
        (_: Double) => (message, 0.0, 0.0, "unknown")

    // avoid -0.0
    def mul (value: Double, factor: Double): Double =
        if (value == 0.0)
            0.0
        else
            value * factor

    /**
     * Decode an OBIS code into actionable values.
     *
     * @param code  the OBIS code to deconstruct, e.g. 1-1?:1.29.0*255
     * @return function to convert the quantity into a type, real, imaginary and unit
     */
    def decode_obis (code: String): MeasurementFunctor =
    {
        val matcher = obis.matcher (code)
        if (matcher.find)
        {
            if (1 == matcher.group (2).toInt) // Electricity related objects
            {
                val channel = matcher.group (4).toInt // Channel 1
                val quantity = matcher.group (5).toInt
                val what = matcher.group (6).toInt
                quantity match
                {
                    // active power + = ΣL i Active power+
                    case 1 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", value * 1.0, 0.0, "W")
                            // time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", value * 1.0, 0.0, "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // active power - = ΣL i Active power–
                    case 2 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", mul (value, -1.0), 0.0, "W")
                            // Time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", mul (value, -1.0), 0.0, "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // reactive power Q I = ΣL i Reactive power QI
                    case 5 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", 0.0, value * 1.0, "W")
                            // Time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", 0.0, value * 1.0, "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // reactive power Q II = ΣL i Reactive power QII
                    case 6 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", 0.0, value * 1.0, "W")
                            // Time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", 0.0, value * 1.0, "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // reactive power Q III = ΣL i Reactive power QIII
                    case 7 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", 0.0, mul (value, -1.0), "W")
                            // Time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", 0.0, mul (value, -1.0), "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // reactive power Q IV = ΣL i Reactive power QIV
                    case 8 =>
                        what match
                        {
                            // last average
                            case 5 =>
                                value: Double => ("power", 0.0, mul (value, -1.0), "W")
                            // Time integral 1 or Time integral 5
                            case 8 | 29 =>
                                value: Double => ("energy", 0.0, mul (value, -1.0), "Wh")
                            case _ =>
                                error (s"unrecognized Value group D $what in $code")
                        }
                    // error
                    case _ =>
                        error (s"unrecognized Value group C $quantity in $code")
                }
            }
            else
                error (s"'$code' is not an electric OBIS code")
        }
        else
            error (s"'$code' has an OBIS code format error")
    }

    def getReadings: List[(ID, Quantity, Time, Period, Real, Imaginary, Units)] =
    {
        group5.flatMap (_.group6.flatMap (
                x =>
                {
                    val id = x.loc.locationIdentification.get.locationIdentifier.getOrElse ("")
                    val readings = x.group9.get.flatMap (
                        y =>
                        {
                            val pia = y.pia.get.head.itemNumberIdentification1.itemIdentifier.get
                            val fn = decode_obis (pia)
                            val quantities = y.group10.flatMap (_.getValue)
                            quantities.map (
                                z =>
                                {
                                    val (typ, real, imaginary, units) = fn (z._3)
                                    (typ, z._1, z._2, real, imaginary, units)
                                }
                            )
                        }
                    )
                    readings.map (y => (id, y._1, y._2, y._3, y._4, y._5, y._6))
                }
            )
        )
    }
}

object MSCONSMessage04B extends MSCONSMessage
{
    lazy val bgm: Parser[BGM] = expect ("BGM", x => BGM (x))
    lazy val dtm: Parser[DTM] = expect ("DTM", x => DTM (x))
    lazy val dtms: Parser[Option[List[DTM]]] = repAtMostN (9, false, dtm).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get) else None)
    lazy val cux: Parser[Option[CUX]] = expect ("CUX", x => CUX (x)).?
    lazy val rff: Parser[RFF] = expect ("RFF", x => RFF (x))
    lazy val group1: Parser[Option[List[Group1]]] = repAtMostN (9, false, rff ~ dtms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case rff ~ dtms => Group1 (rff, dtms) })) else None)

    lazy val nad: Parser[NAD] = expect ("NAD", x => NAD (x))
    lazy val group3: Parser[Option[List[Group3]]] = repAtMostN (9, false, rff ~ dtms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case rff ~ dtms => Group3 (rff, dtms) })) else None)

    lazy val cta: Parser[CTA] = expect ("CTA", x => CTA (x))
    lazy val com: Parser[COM] = expect ("COM", x => COM (x))
    lazy val coms: Parser[Option[List[COM]]] = repAtMostN (9, false, com).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get) else None)
    lazy val group4: Parser[Option[List[Group4]]] = repAtMostN (9, false, cta ~ coms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case cta ~ coms => Group4 (cta, coms) })) else None)

    lazy val group2: Parser[Option[List[Group2]]] = repAtMostN (99, false, nad ~ group3 ~ group4).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case nad ~ g3 ~ g4 => Group2 (nad, g3, g4) })) else None)

    lazy val uns: Parser[UNS] = expect ("UNS", x => UNS (x))

    lazy val group7: Parser[Option[List[Group7]]] = repAtMostN (99, false, rff ~ dtms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case rff ~ dtms => Group7 (rff, dtms) })) else None)

    lazy val group8: Parser[Option[List[Group8]]] = repAtMostN (99, false, cci ~ dtms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case cci ~ dtms => Group8 (cci, dtms) })) else None)

    lazy val qty: Parser[QTY] = expect ("QTY", x => QTY (x))
    lazy val sts: Parser[STS] = expect ("STS", x => STS (x))
    lazy val stss: Parser[Option[List[STS]]] = repAtMostN (9, false, sts).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get) else None)
    lazy val group10: Parser[List[Group10]] = repAtMostN (9999, true, qty ~ dtms ~ stss) ^^
        (g => g.map ({ case qty ~ dtms ~ stss => Group10 (qty, dtms, stss) }))

    lazy val cci: Parser[CCI] = expect ("CCI", x => CCI (x))
    lazy val mea: Parser[MEA] = expect ("MEA", x => MEA (x))
    lazy val meas: Parser[Option[List[MEA]]] = repAtMostN (99, false, mea).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get) else None)
    lazy val group11: Parser[Option[List[Group11]]] = repAtMostN (99, false, cci ~ meas ~ dtms).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case cci ~ meas ~ dtms => Group11 (cci, meas, dtms) })) else None)

    lazy val lin: Parser[LIN] = expect ("LIN", x => LIN (x))
    lazy val pia: Parser[PIA] = expect ("PIA", x => PIA (x))
    lazy val pias: Parser[Option[List[PIA]]] = repAtMostN (9, false, pia).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get) else None)
    lazy val group9: Parser[Option[List[Group9]]] = repAtMostN (99999, false, lin ~ pias ~ /* ... */ group10 ~ group11 ).? ^^
        (g => if (g.isDefined && 0 < g.get.length) Some (g.get.map ({ case lin ~ pias ~ group10 ~ group11 => Group9 (lin, pias, group10, group11) })) else None)

    lazy val loc: Parser[LOC] = expect ("LOC", x => LOC (x))
    lazy val group6: Parser[List[Group6]] = repAtMostN (99999, true, loc ~ dtms ~ group7 ~ group8 ~ group9 ) ^^
        (g => g.map ({ case loc ~ dtms ~ group7 ~ group8 ~ group9 => Group6 (loc, dtms, group7, group8, group9) }))

    lazy val group5: Parser[List[Group5]] = repAtMostN (99999, true, nad ~ group6 ) ^^
        (g => g.map ({ case nad ~ group6 => Group5 (nad, group6) }))

    lazy val cnt: Parser[CNT] = expect ("CNT", x => CNT (x))

    lazy val unt: Parser[UNT] = expect ("UNT", x => UNT (x))

    lazy val unz: Parser[UNZ] = expect ("UNZ", x => UNZ (x))

    val phrase: Parser[MSCONSMessage04B] = bgm ~ dtm ~ cux ~ group1 ~ group2 ~ uns ~ group5 ~ cnt.? ~ unt ~ unz ^^
        { case bgm ~ dtm ~ cux ~ group1 ~ group2 ~ uns ~ group5 ~ cnt ~ unt ~ unz => MSCONSMessage04B (bgm, dtm, cux, group1, group2, uns, group5, cnt, unt, unz) }
}
