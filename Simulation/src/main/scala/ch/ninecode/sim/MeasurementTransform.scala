package ch.ninecode.sim

import scala.collection.mutable
import scala.tools.reflect.ToolBox

/**
 * End user configurable measurement (input) timeseries transformation.
 *
 */
abstract class MeasurementTransform extends Serializable
{
    val MILLISECONDS_PER_HOUR: Double = 60.0 * 60.0 * 1000.0

    /**
     * Mapping function between meter data and value for simulation input.
     *
     * The default transform advances "energy" type measurements in time by the period and converts them to power.
     *
     * @param data The data to transform. Single phase data will have a SimulationPlayerData.readings array
     *             of size 2, while three phase data will have an array size of 6. Elements are flattened,
     *             either Array(real_a, imag_a) or
     *             Array(real_a, imag_a, real_b, imag_b, real_c, imag_c).
     * @return The transformed data.
     */
    def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
    {
        data.map (
            reading =>
                reading.`type` match
                {
                    // ToDo: should also check units
                    case "energy" =>
                        val factor = MILLISECONDS_PER_HOUR / reading.period
                        val t = reading.time - reading.period
                        reading.copy (time = t, readings = reading.readings.map (_ * factor), units = "VA")
                    case _ =>
                        reading
                }
        )
    }
}

object MeasurementTransform
{
    val cache = new mutable.HashMap[String, MeasurementTransform] ()
    lazy val identity: MeasurementTransform = new MeasurementTransform {}

    def build (program: String): MeasurementTransform =
    {
        val toolbox = scala.reflect.runtime.currentMirror.mkToolBox ()
        val tree = toolbox.parse ("import ch.ninecode.sim._; import ch.ninecode.gl._; " + program)
        val compiledCode = toolbox.compile (tree)
        compiledCode() match
        {
            case cls: Class[_] =>
                cls.newInstance ().asInstanceOf[MeasurementTransform]
            case inst: MeasurementTransform =>
                inst
        }
    }

    def compile (program: String): MeasurementTransform =
    {
        if (null == program)
            identity
        else
        {
            if (cache.contains (program))
                cache(program)
            else
            {
                val transform = build (program)
                cache.put (program, transform)
                transform
            }
        }
    }
}

//class examples
//{
//    import ch.ninecode.gl._
//
//    /**
//     * Increase load by 10%.
//     */
//    new MeasurementTransform
//    {
//        override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
//        {
//            data.map (
//                reading =>
//                    reading.`type` match
//                    {
//                        case "energy" =>
//                            val factor = MILLISECONDS_PER_HOUR / reading.period * 1.1
//                            val t = reading.time - reading.period
//                            reading.copy (time = t, readings = reading.readings.map (_ * factor), units = "VA")
//                        case _ =>
//                            reading
//                    }
//            )
//        }
//    }
//
//    /**
//     * Change load to 0.9 power factor.
//     */
//    new MeasurementTransform
//    {
//        override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
//        {
//            val cosphi = Complex (0.9, Math.sqrt (1.0 - 0.9 * 0.9))
//            data.map (
//                reading =>
//                    reading.`type` match
//                    {
//                        case "energy" =>
//                            val factor = MILLISECONDS_PER_HOUR / reading.period * cosphi
//                            val t = reading.time - reading.period
//                            val new_readings = reading.readings
//                                .grouped (2)
//                                .map (v => Complex (v(0), v(1)) * factor)
//                                .flatMap (x => List (x.re, x.im))
//                                .toArray
//                            reading.copy (time = t, readings = new_readings, units = "VA")
//                        case _ =>
//                            reading
//                    }
//            )
//        }
//    }
//
//    /**
//     * Random cosϕ between 75° and 90°
//     */
//    new MeasurementTransform
//    {
//        override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
//        {
//            data.map (
//                reading =>
//                    reading.`type` match
//                    {
//                        case "energy" =>
//                            val angle = (Math.random () * (90.0 - 75.0) + 75.0) * Math.PI / 180.0
//                            val cosphi = Complex (Math.cos (angle), Math.sin (angle))
//                            val factor = MILLISECONDS_PER_HOUR / reading.period * cosphi
//                            val t = reading.time - reading.period
//                            val new_readings = reading.readings
//                                .grouped (2)
//                                .map (v => Complex (v(0), v(1)) * factor)
//                                .flatMap (x => List (x.re, x.im))
//                                .toArray
//                            reading.copy (time = t, readings = new_readings, units = "VA")
//                        case _ =>
//                            reading
//                    }
//            )
//        }
//    }
//
//    /**
//     * Override the way the energy shift is done, so that it's only 14 minutes before the reading.
//     *
//     * Advances "energy" type measurements in time by the period less a minute and converts them to power.
//     *
//     */
//    new MeasurementTransform
//    {
//        val MILLISECONDS_PER_MINUTE: Int = 60 * 1000
//
//        override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
//        {
//            data.map (
//                reading =>
//                    reading.`type` match
//                    {
//                        case "energy" =>
//                            val factor = MILLISECONDS_PER_HOUR / reading.period
//                            val t = reading.time - (reading.period - MILLISECONDS_PER_MINUTE)
//                            reading.copy (time = t, readings = reading.readings.map (_ * factor), units = "VA")
//                        case _ =>
//                            reading
//                    }
//            )
//        }
//    }
//}

