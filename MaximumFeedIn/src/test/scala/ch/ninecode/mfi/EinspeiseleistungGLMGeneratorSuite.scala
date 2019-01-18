package ch.ninecode.mfi

import org.scalatest.FunSuite

import ch.ninecode.model.SolarGeneratingUnit
import ch.ninecode.cim.CHIM

class EinspeiseleistungGLMGeneratorSuite extends FunSuite
{

    def parseSolarGen (xml: String): SolarGeneratingUnit =
    {
        val parser = new CHIM (xml)
        val result = CHIM.parse (parser)
        result._1 ("ID123").asInstanceOf [SolarGeneratingUnit]
    }

    def getConstantPower (solargen: SolarGeneratingUnit, one_phase: Boolean = true): List[String] =
    {
        val node_mock = MaxPowerFeedingNodeEEA ("ID123", 400, "", "", "", "", 0.0, null, "", "")
        val glm_gen = new EinspeiseleistungGLMGenerator (one_phase, null, null)
        val load = glm_gen.emit_pv (List (solargen), node_mock)
        val regex_constant_power = "(?<=constant_power_[ABC] )(.*)(?=;)".r
        regex_constant_power.findAllIn (load).toList
    }

    test ("emit pv without cosphi, 1 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>10.0</cim:GeneratingUnit.ratedNetMaxP>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.ratedNetMaxP == 10.0, "ratedNetMaxP should be 10.0")

        val power_list = getConstantPower (solargen)

        assert (power_list.length == 1, "1 phase")
        power_list.foreach (p =>
        {
            assert (p == "-10000.0+0.0j", "active power only")
        })
    }

    test ("emit pv with cosphi 1, 1 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>11.0</cim:GeneratingUnit.ratedNetMaxP>
              |		<cim:GeneratingUnit.normalPF>1.000000000</cim:GeneratingUnit.normalPF>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.normalPF == 1.0, "normalPf should be 1.0")
        assert (solargen.sup.ratedNetMaxP == 11.0, "ratedNetMaxP should be 11.0")

        val power_list = getConstantPower (solargen)
        assert (power_list.length == 1, "1 phase")
        power_list.foreach (p =>
        {
            assert (p == "-11000.0+0.0j", "active power only")
        })
    }

    test ("emit pv with cosphi 0.8, 1 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>5.0</cim:GeneratingUnit.ratedNetMaxP>
              |		<cim:GeneratingUnit.normalPF>0.800</cim:GeneratingUnit.normalPF>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.normalPF == 0.8, "normalPf should be 0.8")
        assert (solargen.sup.ratedNetMaxP == 5.0, "ratedNetMaxP should be 5.0")

        val power_list = getConstantPower (solargen)
        assert (power_list.length == 1, "1 phase")
        power_list.foreach (p =>
        {
            assert (p == "-4000.0+3000.0j", "active power only")
        })
    }

    test ("emit pv with cosphi -0.8, 1 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>5.0</cim:GeneratingUnit.ratedNetMaxP>
              |		<cim:GeneratingUnit.normalPF>-0.800</cim:GeneratingUnit.normalPF>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.normalPF == -0.8, "normalPf should be -0.8")
        assert (solargen.sup.ratedNetMaxP == 5.0, "ratedNetMaxP should be 5.0")

        val power_list = getConstantPower (solargen)
        assert (power_list.length == 1, "1 phase")
        power_list.foreach (p =>
        {
            assert (p == "-4000.0-3000.0j", "active power only")
        })
    }

    test ("emit pv with cosphi 0.8, 3 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>15.0</cim:GeneratingUnit.ratedNetMaxP>
              |		<cim:GeneratingUnit.normalPF>0.800</cim:GeneratingUnit.normalPF>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.normalPF == 0.8, "normalPf should be 0.8")
        assert (solargen.sup.ratedNetMaxP == 15.0, "ratedNetMaxP should be 15.0")

        val power_list = getConstantPower (solargen, false)
        assert (power_list.length == 3, "3 phase")
        power_list.foreach (p =>
        {
            assert (p == "-4000.0+3000.0j", "active power only")
        })
    }

    test ("emit pv with cosphi -0.8, 3 phase")
    {
        val xml =
            """
              |	<cim:SolarGeneratingUnit rdf:ID="ID123">
              |		<cim:GeneratingUnit.ratedNetMaxP>15.0</cim:GeneratingUnit.ratedNetMaxP>
              |		<cim:GeneratingUnit.normalPF>-0.800</cim:GeneratingUnit.normalPF>
              |	</cim:SolarGeneratingUnit>
            """.stripMargin

        val solargen = parseSolarGen (xml)
        assert (solargen.sup.normalPF == -0.8, "normalPf should be -0.8")
        assert (solargen.sup.ratedNetMaxP == 15.0, "ratedNetMaxP should be 15.0")

        val power_list = getConstantPower (solargen, false)
        assert (power_list.length == 3, "3 phase")
        power_list.foreach (p =>
        {
            assert (p == "-4000.0-3000.0j", "active power only")
        })
    }
}