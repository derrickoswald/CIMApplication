package ch.ninecode.sim

import scala.collection.mutable
import scala.reflect.runtime
import scala.tools.reflect.ToolBox

/**
 * End user configurable measurement (input) timeseries transformation.
 */
abstract class MeasurementTransform extends Serializable
{
    /**
     * Single phase mapping between meter data and value for simulation input.
     *
     * @param real The real part of the measured value (or the apparent value).
     * @param imag The imaginary part of the measured value.
     * @return The real and imaginary part of the value to be applied in the simulation as a Tuple2.
     */
    def transform (real: Double, imag: Double): (Double, Double) = (real, imag)

    def transform3 (reala: Double, imaga: Double, realb: Double, imagb: Double, realc: Double, imagc: Double): (Double, Double, Double, Double, Double, Double) = (reala, imaga, realb, imagb, realc, imagc)
}

object MeasurementTransform
{
    val cache: mutable.HashMap[String, MeasurementTransform] = new mutable.HashMap[String, MeasurementTransform] ()
    val identity: MeasurementTransform = new MeasurementTransform {}
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
                val toolbox: ToolBox[runtime.universe.type] = scala.reflect.runtime.currentMirror.mkToolBox()
                val tree: toolbox.u.Tree = toolbox.parse ("import ch.ninecode.sim._; import ch.ninecode.gl._; " + program)
                val compiledCode: () ⇒ Any = toolbox.compile (tree)
                val transform = compiledCode ().asInstanceOf[MeasurementTransform]
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
//        override def transform (real: Double, imag: Double): (Double, Double) =
//        {
//            val input = Complex (real, imag)
//            val output = input * 1.1
//            (output.re, output.im)
//        }
//    }
//
//    /**
//     * Change load to 0.9 power factor.
//     */
//    new MeasurementTransform
//    {
//        override def transform (real: Double, imag: Double): (Double, Double) =
//        {
//            val cosphi = Complex (0.9, Math.sqrt (1.0 - 0.9 * 0.9))
//            val input = Complex (real, imag)
//            val output = input * cosphi
//            (output.re, output.im)
//        }
//    }
//
//    /**
//     * Random cosϕ between 75° and 90°
//     */
//    new MeasurementTransform
//    {
//        override def transform (real: Double, imag: Double): (Double, Double) =
//        {
//            val angle = (Math.random () * (90.0 - 75.0) + 75.0) * Math.PI / 180.0
//            val cosphi = Complex (Math.cos (angle), Math.sin (angle))
//            val input = Complex (real, imag)
//            val output = input * cosphi
//            (output.re, output.im)
//        }
//    }
//}