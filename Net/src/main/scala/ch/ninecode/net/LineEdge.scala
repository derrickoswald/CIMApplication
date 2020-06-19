package ch.ninecode.net

import ch.ninecode.model.ACLineSegment
import ch.ninecode.util.Complex

class LineEdge
(
    _data: LineData
)
extends LoadFlowEdge (
    _data.lines.map (_.line).map (_.id).toArray.sortWith (_ < _).mkString ("_"),
    _data.node0,
    _data.node1
)
{
    val data: LineData = _data

    lazy val lines: Iterable[ACLineSegment] = data.lines.map (_.line)

    /**
     * Get the cable/wire type.
     *
     * @param line The ACLineSegment object to get the type from.
     * @return The name of the type.
     */
    def config_name (line: ACLineSegment): String =
    {
        val psr = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource
        if (null != psr.AssetDatasheet)
            psr.AssetDatasheet // ToDo: this is not really what is needed, it should be ConcentricNeutralCableInfo.name e.g. GKN 3x16rm/16 1/0.6 kV
        else
            if (null != psr.IdentifiedObject.name)
                psr.IdentifiedObject.name // ToDo: this is a NIS specific characteristic where the ACLineSegment.name is the article.type
            else
                s"_${line.hashCode.toString}"
    }

    /**
     * Get the configuration name (of the parallel lines).
     *
     * @return An appropriate line configuration name.
     */
    def configurationName: String =
    {
        val n = lines.map (config_name).map (valid_config_name).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
        // limit to 64 bytes (GridLAB-D OBJECTTREE.name is an array of 64 bytes) with null:
        if (n.getBytes.length > 63)
            s"_${Math.abs (n.hashCode ()).toString}"
        else
            n
    }

    /**
     * The length (of the longest) line.
     */
    lazy val length: Double = lines.map (_.Conductor.len)
        .fold (0.0) ((l1, l2) => if (l1 > l2) l1 else l2) // instead of .max

    /**
     * Zero ohms.
     */
    lazy val zero: Complex = Complex (0.0, 0.0)

    /**
     * Convert zero and positive sequence impedance values into the Z matrix diagonal and off diagonal elements.
     *
     * @param z0 Zero sequence impedance value from the CIM ACLineSegment parameters.
     * @param z1 Positive sequence impedance value from the CIM ACLineSegment parameters.
     * @return The diagonal and off-diagonal values for the GridLAB-D Z matrix representation of line impedance.
     */
    def sequence2z (z0: Complex, z1: Complex): (Complex, Complex) =
    {
        val Z0 = z0
        val Z1, Z2 = z1
        val diag = (Z0 + Z1 + Z2) / 3
        val root3 = Math.sqrt (3.0)
        val x1 = Z1 * Complex (-0.5, root3 / 2.0)
        val x2 = Z2 * Complex (-0.5, root3 / -2.0)
        val off = (Z0 + x1 + x2) / 3.0
        (diag, off)
    }
}
