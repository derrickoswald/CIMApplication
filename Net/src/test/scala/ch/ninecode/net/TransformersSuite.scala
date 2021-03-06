package ch.ninecode.net

import org.apache.spark.sql.SparkSession

import ch.ninecode.model.BasicElement
import ch.ninecode.model.ConnectivityNodeContainer
import ch.ninecode.model.EquipmentContainer
import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.Substation

import ch.ninecode.testutil.TestUtil

class TransformersSuite extends TestUtil
{
    val station: Substation = Substation(
        EquipmentContainer(
            ConnectivityNodeContainer(
                PowerSystemResource(
                    IdentifiedObject(
                        BasicElement(mRID = "STA12")
                    )
                )
            )
        )
    )

    test("default_injection with sk=100MVA and angle=-73 16kV")
    {
        session: SparkSession =>
            val trafos = Transformers(
                session,
                default_supply_network_short_circuit_power_max = 100e6,
                default_supply_network_short_circuit_angle_max = -73)
            val injection: EquivalentInjection = trafos.default_injection("TRA12", Some(station), voltage = ("16000V", 16000))
            assert(injection.r == 0.7484715640902061, "impedanze r")
            assert(injection.x == -2.4481401752653706, "impendace x")
    }

    test("default_injection with sk=100MVA and angle=-73 20kV")
    {
        session: SparkSession =>
            val trafos = Transformers(
                session,
                default_supply_network_short_circuit_power_max = 100e6,
                default_supply_network_short_circuit_angle_max = -73)
            val injection: EquivalentInjection = trafos.default_injection("TRA12", Some(station), voltage = ("20000V", 20000))
            assert(injection.r == 1.169486818890947, "impedanze r")
            assert(injection.x == -3.8252190238521417, "impendace x")
    }

    test("default_injection angle max only")
    {
        session: SparkSession =>
            val trafos = Transformers(
                session,
                default_supply_network_short_circuit_angle_max = -73)
            val injection: EquivalentInjection = trafos.default_injection("TRA12", Some(station), voltage = ("16000V", 16000))
            assert(injection.r == 0.37423578204510305, "impedanze r")
            assert(injection.x == -1.2240700876326853, "impendace x")
            assert(injection.minP == 2.9237170472273678E7, "minP")
            assert(injection.maxP == 5.8474340944547355E7, "maxP")
            assert(injection.maxQ == -1.9126095119260707E8, "maxQ")
            assert(injection.minQ == -9.563047559630354E7, "minQ")
    }

    test("default_injection angle max and min")
    {
        session: SparkSession =>
            val trafos = Transformers(
                session,
                default_supply_network_short_circuit_angle_max = -73,
                default_supply_network_short_circuit_angle_min = -83)
            val injection: EquivalentInjection = trafos.default_injection("TRA12", Some(station), voltage = ("16000V", 16000))
            assert(injection.r == 0.37423578204510305, "impedanze r")
            assert(injection.x == -1.2240700876326853, "impendace x")
            assert(injection.minP == 1.218693434051475E7, "minP")
            assert(injection.maxP == 5.8474340944547355E7, "maxP")
            assert(injection.maxQ == -1.9126095119260707E8, "maxQ")
            assert(injection.minQ == -9.92546151641322E7, "minQ")
    }
}
