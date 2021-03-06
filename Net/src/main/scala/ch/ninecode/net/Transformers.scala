package ch.ninecode.net

;

import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.BasicElement
import ch.ninecode.model.Bay
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.EquivalentEquipment
import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.VoltageLevel
import ch.ninecode.util.Complex

/**
 * Get information about transformers.
 * Joins PowerTransformer, PowerTransformerEnd, Terminal and BaseVoltage objects to form complete details about transformers.
 *
 * @param session                                            the Spark session
 * @param storage_level                                      specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 * @param default_supply_network_short_circuit_power_max     maximum primary side network equivalent power under short circuit conditions
 * @param default_supply_network_short_circuit_impedance_max equivalent impedance for maximum primary side network equivalent power under short circuit conditions
 * @param default_supply_network_short_circuit_angle_max     power factor angle for maximum primary side network equivalent power under short circuit conditions, overrides impedance value if specified, (°)
 * @param default_supply_network_short_circuit_power_min     minimum primary side network equivalent power under short circuit conditions
 * @param default_supply_network_short_circuit_impedance_min equivalent impedance for minimum primary side network equivalent power under short circuit conditions
 * @param default_supply_network_short_circuit_angle_min     power factor angle for minimum primary side network equivalent power under short circuit conditions, overrides impedance value if specified, (°)
 *
 */
final case class Transformers (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString("MEMORY_AND_DISK_SER"),
    default_supply_network_short_circuit_power_max: Double = 200.0e6,
    default_supply_network_short_circuit_impedance_max: Complex = Complex(0.437785783, -1.202806555),
    default_supply_network_short_circuit_angle_max: Double = Double.NaN,
    default_supply_network_short_circuit_power_min: Double = 100.0e6,
    default_supply_network_short_circuit_impedance_min: Complex = Complex(0.875571570, -2.405613110),
    default_supply_network_short_circuit_angle_min: Double = Double.NaN
) extends CIMRDD
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Default transformer filter predicate.
     *
     * Eliminates transformers named Messen_Steuern, transformers under 1000VA and public lighting "transformers" (230V Übergang).
     *
     * @param transformer the transformer to test
     * @return <code>true</code> if the transformer should be kept
     */
    def transformer_filter (transformer: TransformerData): Boolean =
    {
        val power_transformer = transformer.transformer.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern"
        val power_significant = transformer.ends.forall(_.ratedS > 0.0)
        val voltage_significant = transformer.voltages.tail.exists(_._2 >= 400.0)
        power_transformer && power_significant && voltage_significant
    }

    /**
     * Default station filter predicate.
     *
     * Eliminates PSRType DistributionBox.
     *
     * @param station the Substation to test
     * @return <code>true</code> if the station should be kept
     */
    def substation_filter (station: Substation): Boolean =
    {
        station.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType != "PSRType_DistributionBox"
    }

    /**
     * Compute equivalent impedance given the power, voltage and power factor angle
     *
     * @param power   network equivalent power under short circuit conditions (VA)
     * @param voltage nominal voltage (V)
     * @param angle   power factor angle (°)
     * @return the impedance
     */
    def z (power: Double, voltage: Double, angle: Double): Complex =
    {
        val magnitude = (voltage * voltage) / power
        val ang = angle * Math.PI / 180.0
        Complex(Math.cos(ang) * magnitude, Math.sin(ang) * magnitude)
    }

    /**
     * Generate a transformer instance.
     *
     * @param transformer joined transformer, ends, terminals, nodes and voltages
     * @return a composite transformer object
     */
    def to_transformer_data (transformer: (PowerTransformer, Iterable[(PowerTransformerEnd, Terminal, (String, Double), TopologicalNode)])): TransformerData =
    {
        val ends = transformer._2.toArray.sortWith(_._1.TransformerEnd.endNumber < _._1.TransformerEnd.endNumber)
        TransformerData(transformer._1, ends.map(_._1), ends.map(_._2), ends.map(_._4), ends.map(_._3), None, None)
    }

    /**
     * Extract the substation mRID from the container.
     *
     * The equipment container for a transformer could be a Bay, VoltageLevel or SubStation.
     * Bay and VoltageLevel have a reference to their SubStation.
     *
     * @param container the transformer container
     * @return the SubStation mRID or an empty string if there is no container
     */
    def station_fn (container: Option[Element]): String =
    {
        container match
        {
            case Some(element) =>
                element match
                {
                    case station: Substation => station.id
                    case bay: Bay => bay.Substation
                    case level: VoltageLevel => level.Substation
                    case _ =>
                        log.error(s"unknown container type (${element.getClass.toString}) for transformer")
                        ""
                }
            case None => ""
        }
    }

    /**
     * Generate an EquivalentInjection CIM element.
     *
     * Builds a object that models the network at the primary of the transformer.
     *
     * @param transformer the PowerTransformer mRID
     * @param station     the containing Substation
     * @param voltage     the nominal primary voltage
     * @return the EquivalentInjection with the primary network model
     */
    def default_injection (transformer: String, station: Option[Substation], voltage: (String, Double)): EquivalentInjection =
    {
        val mRID = "EquivalentInjection_" + transformer
        val description = "default equivalent generation injection"
        val element = BasicElement(mRID = mRID)
        val obj = IdentifiedObject(element, description = description, mRID = mRID)
        obj.bitfields = IdentifiedObject.fieldsToBitfields("description", "mRID")
        val psr = PowerSystemResource(obj)
        psr.bitfields = PowerSystemResource.fieldsToBitfields()
        val s = station match
        {
            case Some(s) => s.id
            case _ => ""
        }
        val equipment = Equipment(psr, inService = true, normallyInService = true, EquipmentContainer = s)
        equipment.bitfields = Equipment.fieldsToBitfields("inService", "normallyInService", "EquipmentContainer")
        val conducting = ConductingEquipment(equipment, BaseVoltage = voltage._1)
        conducting.bitfields = ConductingEquipment.fieldsToBitfields("BaseVoltage")
        val equivalent = EquivalentEquipment(conducting)
        equivalent.bitfields = EquivalentEquipment.fieldsToBitfields()

        // if there is only one supplied angle, apply it to both max and min conditions
        val angle_max = if (default_supply_network_short_circuit_angle_max.isNaN)
            default_supply_network_short_circuit_angle_min
        else
            default_supply_network_short_circuit_angle_max
        val angle_min = if (default_supply_network_short_circuit_angle_min.isNaN)
            default_supply_network_short_circuit_angle_max
        else
            default_supply_network_short_circuit_angle_min

        // compute the impedances
        val impedance_max = if (angle_max.isNaN)
            default_supply_network_short_circuit_impedance_max
        else
            z(default_supply_network_short_circuit_power_max, voltage._2, angle_max)
        val impedance_min = if (angle_min.isNaN)
            default_supply_network_short_circuit_impedance_min
        else
            z(default_supply_network_short_circuit_power_min, voltage._2, angle_min)

        // decompose sk values into P & Q
        val maxP = default_supply_network_short_circuit_power_max * Math.cos(impedance_max.angle)
        val maxQ = default_supply_network_short_circuit_power_max * Math.sin(impedance_max.angle)
        val minP = default_supply_network_short_circuit_power_min * Math.cos(impedance_min.angle)
        val minQ = default_supply_network_short_circuit_power_min * Math.sin(impedance_min.angle)

        // note: use RegulationStatus to indicate this is a default value, and not a real value
        val injection = EquivalentInjection(
            equivalent,
            maxP = maxP,
            maxQ = maxQ,
            minP = minP,
            minQ = minQ,
            p = 0.0,
            q = 0.0,
            r = impedance_max.re,
            r0 = 0.0,
            r2 = 0.0,
            regulationCapability = false,
            regulationStatus = false,
            regulationTarget = 0.0,
            x = impedance_max.im,
            x0 = 0.0,
            x2 = 0.0)
        injection.bitfields = EquivalentInjection.fieldsToBitfields(
            "maxP",
            "maxQ",
            "minP",
            "minQ",
            "p",
            "q",
            "r",
            "r0",
            "r2",
            "regulationCapability",
            "regulationStatus",
            "regulationTarget",
            "x",
            "x0",
            "x2")
        injection
    }

    /**
     * Create an RDD of composite transformer objects.
     *
     * @param transformer_filter the filter to apply that eliminates undesired transformers
     * @param substation_filter  the filter to apply that eliminates undesired substations
     * @return the RDD of transformer instances
     */
    def getTransformers
    (
        transformer_filter: TransformerData => Boolean = transformer_filter,
        substation_filter: Substation => Boolean = substation_filter
    ): RDD[TransformerData] =
    {
        // get ends and terminals
        val ends_terminals = getOrElse[PowerTransformerEnd].keyBy(_.TransformerEnd.Terminal).join(getOrElse[Terminal].keyBy(_.id)).values

        // get a map of voltages
        // ToDo: fix this 1kV multiplier on the voltages
        val voltages = getOrElse[BaseVoltage].map(v => (v.id, v.nominalVoltage * 1000.0)).collectAsMap()

        // attach them to the ends
        val ends_terminals_voltages: RDD[(PowerTransformerEnd, Terminal, (String, Double))] = ends_terminals
            .map(
                x =>
                {
                    val base_voltage = x._1.TransformerEnd.BaseVoltage
                    val voltage = voltages.getOrElse(base_voltage, 0.0)
                    (x._1, x._2, (base_voltage, voltage))
                }
            )

        // attach the nodes
        val ends_terminals_voltages_nodes: RDD[(PowerTransformerEnd, Terminal, (String, Double), TopologicalNode)] = ends_terminals_voltages
            .keyBy(_._2.TopologicalNode)
            .join(getOrElse[TopologicalNode].keyBy(_.id))
            .values
            .map(
                x =>
                {
                    (x._1._1, x._1._2, x._1._3, x._2)
                }
            )

        // get the transformers of interest and join to end information (filter out transformers with less than 2 ends)
        val ends = ends_terminals_voltages_nodes.keyBy(_._1.PowerTransformer).groupByKey.filter(_._2.size >= 2)
        val transformers = getOrElse[PowerTransformer]
            .keyBy(_.id).join(ends)
            .values.map(to_transformer_data)
            .filter(transformer_filter)

        // add station if any
        // ToDo: should we invent a dummy station?
        val substations_by_id = getOrElse[Substation].filter(substation_filter).keyBy(_.id)
        val transformers_stations = transformers.keyBy(_.transformer.ConductingEquipment.Equipment.EquipmentContainer)
            .leftOuterJoin(getOrElse[Element].keyBy(_.id)).values
            .map(x => (station_fn(x._2), x._1))
            .leftOuterJoin(substations_by_id).values
            .map(x => x._1.copy(station = x._2))

        // add equivalent injection, or default
        @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
        val injections_by_node = getOrElse[EquivalentInjection].keyBy(_.id)
            .join(getOrElse[Terminal].keyBy(_.ConductingEquipment)).values
            .map(x => (x._2.TopologicalNode, x._1))
            .groupByKey.mapValues(_.head) // ToDo: should really sum the (different) EquivalentInjection?
        transformers_stations.keyBy(_.node0.id)
            .leftOuterJoin(injections_by_node).values
            .map(x => x._1.copy(shortcircuit = if (x._2.isDefined) x._2 else Some(default_injection(x._1.transformer.id, x._1.station, x._1.voltages(x._1.primary)))))
            .persist(storage_level)
    }
}
