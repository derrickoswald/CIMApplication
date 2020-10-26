package ch.ninecode.mfi

import org.scalatest.funsuite.AnyFunSuite

import ch.ninecode.cim.CHIM
import ch.ninecode.gl.PV
import ch.ninecode.model.PhotoVoltaicUnit
import ch.ninecode.model.PowerElectronicsConnection

@SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
class EinspeiseleistungGLMGeneratorSuite extends AnyFunSuite
{
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def parseSolarGen (xml: String): PhotoVoltaicUnit =
    {
        val parser = new CHIM(xml)
        val result = CHIM.parse(parser)
        result._1("ID123").asInstanceOf[PhotoVoltaicUnit]
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def parsePowerElectronicsConnection (xml: String): PowerElectronicsConnection =
    {
        val parser = new CHIM(xml)
        val result = CHIM.parse(parser)
        result._1("ID123_ID321").asInstanceOf[PowerElectronicsConnection]
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def getConstantPower (pvgen: PhotoVoltaicUnit, pvconn: PowerElectronicsConnection, one_phase: Boolean = true): List[(String, String)] =
    {
        val node_mock = MaxPowerFeedingNodeEEA("ID123", 400.0, "", "", "", "", 0.0, null, "", "")
        val options = EinspeiseleistungOptions()
        val glm_gen = new EinspeiseleistungGLMGenerator(one_phase, null, null, options.base_temperature, options.sim_temperature)
        val load = glm_gen.emit_pv(List(PV(null, pvgen, pvconn)), node_mock)
        val regex_constant_power = "(constant_power_[ABC])\\s+(.*)(?=;)".r
        val g: Seq[scala.util.matching.Regex.Match] = regex_constant_power.findAllMatchIn(load).toList
        g.map(m => (m.group(1), m.group(2))).toList
    }

    test("emit pv without cosphi, 1 phase")
    {
        val xml =
            """
              |	<cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-10.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>0.0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>10.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
            """.stripMargin

        val pvgen = parseSolarGen(xml)
        val pvconnection = parsePowerElectronicsConnection(xml)
        assert(pvconnection.ratedS == 10.0, "ratedS should be 10.0")

        val power_list = getConstantPower(pvgen, pvconnection)
        assert(power_list.length == 1, "1 phase")
        assert(power_list.head._1 == "constant_power_A" && power_list.head._2 == "-10000.0+0.0j", "active power only")
    }

    test("emit pv with cosphi 1, 1 phase")
    {
        val xml =
            """
              |<rdf:RDF>
              |	<cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-11.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>11.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
              |</rdf:RDF>
            """.stripMargin

        val pvgen = parseSolarGen(xml)
        val peconn = parsePowerElectronicsConnection(xml)
        assert(peconn.p == -11.0, "p on electricconnection should be the same as ratedS on Unit")
        assert(peconn.q == 0, "q should be 0 on the connection")
        assert(peconn.ratedS == 11.0, "ratedS should be 11.0")

        val power_list = getConstantPower(pvgen, peconn)
        assert(power_list.length == 1, "1 phase")
        assert(power_list.head._1 == "constant_power_A" && power_list.head._2 == "-11000.0+0.0j", "active power only")
    }

    test("emit pv with cosphi 0.8, 1 phase")
    {
        val xml =
            """
              |	<cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-4.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>3.0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>5.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
            """.stripMargin

        val pvgen = parseSolarGen(xml)
        val peconn = parsePowerElectronicsConnection(xml)
        assert(peconn.p == -4.0, "p should be -4.0")
        assert(peconn.q == 3.0, "q should be 3.0")
        assert(peconn.ratedS == 5.0, "ratedS should be 5.0")

        val power_list = getConstantPower(pvgen, peconn)
        assert(power_list.length == 1, "1 phase")
        assert(power_list.head._1 == "constant_power_A" && power_list.head._2 == "-4000.0+3000.0j", "active and reactive power")
    }

    test("emit pv with cosphi -0.8, 1 phase")
    {
        val xml =
            """
              | <cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-4.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>-3.0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>5.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
            """.stripMargin

        val solargen = parseSolarGen(xml)
        val peconn = parsePowerElectronicsConnection(xml)
        assert(peconn.p == -4.0, "p should be -4.0")
        assert(peconn.q == -3.0, "q should be -3.0")
        assert(peconn.ratedS == 5.0, "ratedS should be 5.0")

        val power_list = getConstantPower(solargen, peconn)
        assert(power_list.length == 1, "1 phase")
        assert(power_list.head._1 == "constant_power_A" && power_list.head._2 == "-4000.0-3000.0j", "active and reactive power")
    }

    test("emit pv with cosphi 0.8, 3 phase")
    {
        val xml =
            """
              |  <cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-12.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>9.0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>15.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
            """.stripMargin

        val solargen = parseSolarGen(xml)
        val peconn = parsePowerElectronicsConnection(xml)
        assert(peconn.p == -12.0, "p")
        assert(peconn.q == 9.0, "q")
        assert(peconn.ratedS == 15.0, "ratedS")

        val power_list = getConstantPower(solargen, peconn, one_phase = false)
        assert(power_list.length == 3, "3 phase")
        power_list.foreach(
            p =>
                p._1 match
                {
                    case "constant_power_A" => assert(p._2 == "-4000.0+3000.0j", "active and reactive power")
                    case "constant_power_B" => assert(p._2 == "-4000.0+3000.0j", "active and reactive power")
                    case "constant_power_C" => assert(p._2 == "-4000.0+3000.0j", "active and reactive power")
                }
        )
    }

    test("emit pv with cosphi -0.8, 3 phase")
    {
        val xml =
            """
              |  <cim:PhotoVoltaicUnit rdf:ID="ID123">
              |     <cim:PhotoVoltaicUnit.PowerElectronicsConnection resource="ID123_ID321"/>
              |	</cim:PhotoVoltaicUnit>
              | <cim:PowerElectronicsConnection rdf:ID="ID123_ID321">
              |     <cim:PowerElectronicsConnection.p>-12.0</cim:PowerElectronicsConnection.p>
              |     <cim:PowerElectronicsConnection.q>-9.0</cim:PowerElectronicsConnection.q>
              |     <cim:PowerElectronicsConnection.ratedS>15.0</cim:PowerElectronicsConnection.ratedS>
              | </cim:PowerElectronicsConnection>
            """.stripMargin

        val solargen = parseSolarGen(xml)
        val peconn = parsePowerElectronicsConnection(xml)
        assert(peconn.p == -12.0, "p")
        assert(peconn.q == -9.0, "q")
        assert(peconn.ratedS == 15.0, "ratedS")

        val power_list = getConstantPower(solargen, peconn, one_phase = false)
        assert(power_list.length == 3, "3 phase")
        power_list.foreach(
            p =>
                p._1 match
                {
                    case "constant_power_A" => assert(p._2 == "-4000.0-3000.0j", "active and reactive power")
                    case "constant_power_B" => assert(p._2 == "-4000.0-3000.0j", "active and reactive power")
                    case "constant_power_C" => assert(p._2 == "-4000.0-3000.0j", "active and reactive power")
                }
        )
    }
}