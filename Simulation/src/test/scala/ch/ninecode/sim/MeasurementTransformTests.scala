package ch.ninecode.sim

import java.util.Calendar

import org.junit.Test

case class Generator (
    transformer: String = "TRA1234",
    mrid: String = "HAS5678",
    `type`: String = "energy",
    units: String = "kWh",
    time: Long = (Calendar.getInstance.getTimeInMillis / (60 * 60 * 1000L)) * (60 * 60 * 1000L),
    period: Int = 15 * 60 * 1000)
{
    def timestamp (index: Long): Long = time + (period * index)
    def record (item: (Double, Int)): SimulationPlayerData =
    {
        val (value, index) = item
        val t = timestamp (index)
        SimulationPlayerData (
            transformer,
            mrid,
            `type`,
            t,
            period,
            units,
            Array (value, 0.0))
    }
}

class MeasurementTransformTests
{
    def generateStuff: Array[SimulationPlayerData] =
    {
        val r = new scala.util.Random
        val data = for (_ <- 0 to 10000) yield r.nextDouble * 1000

        val gen = Generator ()
        data
            .zipWithIndex
            .map (item => gen.record (item))
            .toArray
    }

    val transform_class = """
        MeasurementTransform
        {
            val MILLISECONDS_PER_MINUTE: Int = 60 * 1000

            override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
            {
                data.map (
                    reading =>
                        reading.`type` match
                        {
                            case "energy" =>
                                val factor = MILLISECONDS_PER_HOUR / reading.period
                                val t = reading.time - (reading.period - MILLISECONDS_PER_MINUTE)
                                reading.copy (time = t, readings = reading.readings.map (_ * factor), units = "VA")
                            case _ =>
                                reading
                        }
                )
            }
        }
    """

    @Test def testMeasurementTransform (): Unit =
    {
        val series = generateStuff
        val identity = new MeasurementTransform {}
        val original = identity.transform (series)
        // println (original.take (5).mkString ("\n"))

        /**
         * Override the way the energy shift is done, so that it's only 14 minutes before the reading.
         *
         * Advances "energy" type measurements in time by the period less a minute and converts them to power.
         *
         */
        val my_transform: MeasurementTransform = new MeasurementTransform
        {
            val MILLISECONDS_PER_MINUTE: Int = 60 * 1000

            override def transform (data: Array[SimulationPlayerData]): Array[SimulationPlayerData] =
            {
                data.map (
                    reading =>
                        reading.`type` match
                        {
                            case "energy" =>
                                val factor = MILLISECONDS_PER_HOUR / reading.period
                                val t = reading.time - (reading.period - MILLISECONDS_PER_MINUTE)
                                reading.copy (time = t, readings = reading.readings.map (_ * factor), units = "VA")
                            case _ =>
                                reading
                        }
                )
            }
        }
        val player_data = my_transform.transform (series)
        // println (player_data.take (5).mkString ("\n"))

        val MILLISECONDS_PER_MINUTE = 60 * 1000
        original.zip (player_data).foreach (
            item =>
            {
                assert (item._1.time == item._2.time - MILLISECONDS_PER_MINUTE)
                val array1 = item._1.readings
                val array2 = item._2.readings
                for (i <- array1.indices)
                    assert (array1(i) == array2(i))
                for (i <- array2.indices)
                    assert (array1(i) == array2(i))
            }
        )
    }

    @Test def testMeasurementTransformInstance (): Unit =
    {
        val series = generateStuff
        val identity = new MeasurementTransform {}
        val original = identity.transform (series)
        // println (original.take (5).mkString ("\n"))

        val program = s"new $transform_class"
        val my_transform = MeasurementTransform.build (program)
        val player_data = my_transform.transform (series)
        // println (player_data.take (5).mkString ("\n"))

        val MILLISECONDS_PER_MINUTE = 60 * 1000
        original.zip (player_data).foreach (
            item =>
            {
                assert (item._1.time == item._2.time - MILLISECONDS_PER_MINUTE)
                val array1 = item._1.readings
                val array2 = item._2.readings
                for (i <- array1.indices)
                    assert (array1(i) == array2(i))
                for (i <- array2.indices)
                    assert (array1(i) == array2(i))
            }
        )
    }

    @Test def testMeasurementTransformClass (): Unit =
    {
        val series = generateStuff
        val identity = new MeasurementTransform {}
        val original = identity.transform (series)
        // println (original.take (5).mkString ("\n"))

        val program = s"""class MyTransform extends $transform_class
        classOf[MyTransform]"""
        val my_transform = MeasurementTransform.build (program)
        val player_data = my_transform.transform (series)
        // println (player_data.take (5).mkString ("\n"))

        val MILLISECONDS_PER_MINUTE = 60 * 1000
        original.zip (player_data).foreach (
            item =>
            {
                assert (item._1.time == item._2.time - MILLISECONDS_PER_MINUTE)
                val array1 = item._1.readings
                val array2 = item._2.readings
                for (i <- array1.indices)
                    assert (array1(i) == array2(i))
                for (i <- array2.indices)
                    assert (array1(i) == array2(i))
            }
        )
    }
}
